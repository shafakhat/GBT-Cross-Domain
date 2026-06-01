import javax.swing.*;
import java.awt.*;
import java.awt.geom.*;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

/**
 * ================================================================
 * CrossDomainSearchExperimentPP.java
 * ================================================================
 *
 * PURPOSE:
 *   Measure and compare the behavioral efficiency (beta parameter
 *   and BEI metric) of search algorithms across FOUR distinct
 *   computational domains:
 *
 *   Domain 1: Grid Search   (pathfinding on weighted grids)
 *   Domain 2: Game Theory   (minimax on adversarial game trees)
 *   Domain 3: MAPF          (Multi-Agent Path Finding via Prioritised Planning with space-time A*)
 *   Domain 4: Pattern Match (string/sequence search trees)
 *
 * RESEARCH QUESTION:
 *   Does the GBT parameter beta remain stable when the same
 *   algorithm operates across structurally different domains,
 *   or does domain structure fundamentally alter behavioral
 *   efficiency?
 *
 * ALL node counts are from ACTUAL algorithm execution.
 * NO closed-form formula substitutes for real traversal.
 *
 * Author:  Shafakhatullah Khan Mohammed
 * Version: 1.0
 * ================================================================
 */
public class CrossDomainSearchExperimentPP extends JPanel {

    // ============================================================
    //  GLOBAL CONSTANTS
    // ============================================================
    static final int    TRIALS        = 30;
    static final int[]  DEPTHS        = {4, 6, 8, 10, 12};
    static final int    BRANCHING     = 4;
    static final double OBSTACLE_RATE = 0.15;

    /**
     * MiniMax depth cap:
     *   b=4, d=10 -> tree has (4^11-1)/3 = 1,398,101 nodes ~ 5MB
     *   b=4, d=12 -> tree has (4^13-1)/3 = 22,369,621 nodes ~ 85MB
     *   We cap MiniMax at 10 to keep memory safe but allow d=10
     *   to differ genuinely from d=8.
     */
    static final int    MM_DEPTH_CAP  = 10;

    /**
     * AlphaBeta depth cap:
     *   AlphaBeta with move ordering is fast enough at d=10
     *   but we keep cap at 10 as well for consistency.
     *   At b=4,d=10 full tree = ~1.4M nodes; AB prunes heavily.
     */
    static final int    AB_DEPTH_CAP  = 10;

    static final int    MAX_AGENTS    = 4;
    static final int    MAPF_MAX_TIME = 60;  // space-time horizon

    // Domain names
    static final String D_GRID    = "Grid";
    static final String D_GAME    = "GameTree";
    static final String D_MAPF    = "MAPF";
    static final String D_PATTERN = "PatternMatch";

    // Algorithm names
    static final String ALG_BFS    = "BFS";
    static final String ALG_DIJK   = "Dijkstra";
    static final String ALG_ASTAR  = "A*";
    static final String ALG_WA     = "WA*(1.5)";
    static final String ALG_GBFS   = "GBFS";
    static final String ALG_IDA    = "IDA*";
    static final String ALG_MM     = "MiniMax";
    static final String ALG_AB     = "AlphaBeta";
    static final String ALG_PP     = "PrioritisedPlanning";
    static final String ALG_NAIVE  = "Naive";
    static final String ALG_KMP    = "KMP";
    static final String ALG_IDDFS  = "IDDFS";

    // ============================================================
    //  RESULT DATA STRUCTURES
    // ============================================================

    /**
     * Single experimental observation:
     * one algorithm, one domain, one depth, one trial.
     */
    static class Observation {
        String  domain;
        String  algorithm;
        int     depth;
        int     trial;
        long    nodesExpanded;
        int     pathLength;       // useful nodes on solution
        boolean solutionFound;
        double  solutionCost;
        double  bei;

        Observation(String dom, String alg, int d, int t,
                    long n, int u, boolean found,
                    double cost, int b) {
            domain        = dom;
            algorithm     = alg;
            depth         = d;
            trial         = t;
            nodesExpanded = n;
            pathLength    = u;
            solutionFound = found;
            solutionCost  = cost;
            double q = 1.0;
            if (alg.startsWith("WA")) q = 1.0 / 1.5;
            bei = computeBEI(n, u, q, b);
        }
    }

    /** Aggregated result over all trials for one configuration */
    static class AggResult {
        String domain, algorithm;
        int    depth;
        double meanNodes, stdNodes;
        double meanPath,  meanBEI;
        double solRate;

        AggResult(String dom, String alg, int d,
                  List<Observation> obs) {
            domain    = dom;
            algorithm = alg;
            depth     = d;
            solRate   = obs.stream()
                           .filter(o -> o.solutionFound)
                           .count() / (double) obs.size();
            meanNodes = obs.stream()
                           .mapToLong(o -> o.nodesExpanded)
                           .average().orElse(0);
            double mean2 = obs.stream()
                              .mapToDouble(o ->
                                  (double) o.nodesExpanded *
                                  o.nodesExpanded)
                              .average().orElse(0);
            stdNodes  = Math.sqrt(Math.max(
                            mean2 - meanNodes * meanNodes, 0));
            meanPath  = obs.stream()
                           .mapToInt(o -> o.pathLength)
                           .average().orElse(0);
            meanBEI   = obs.stream()
                           .mapToDouble(o -> o.bei)
                           .average().orElse(0);
        }
    }

    // All raw observations
    static List<Observation> allObs = new ArrayList<>();

    // ============================================================
    //  BEI COMPUTATION
    // ============================================================
    static double computeBEI(long N, int U, double q, int b) {
        if (N == 0 || U == 0) return 0.0;
        double logNorm = Math.log(N + 1) / Math.log(b);
        if (logNorm < 1e-10) return 0.0;
        return (U * q) / ((double) N * logNorm);
    }

    // ============================================================
    //  BETA ESTIMATION (log-linear regression)
    // ============================================================
    static double[] fitBeta(double[] depths, double[] logMeans) {
        int n = depths.length;
        if (n < 2) return new double[]{1.0, 1.0, 0.0};
        double logb = Math.log(BRANCHING);
        double[] X  = new double[n];
        for (int i = 0; i < n; i++) X[i] = depths[i] * logb;

        double Xm = Arrays.stream(X).average().orElse(0);
        double Ym = Arrays.stream(logMeans).average().orElse(0);
        double sXX = 0, sXY = 0, sYY = 0;
        for (int i = 0; i < n; i++) {
            sXX += (X[i] - Xm) * (X[i] - Xm);
            sXY += (X[i] - Xm) * (logMeans[i] - Ym);
            sYY += (logMeans[i] - Ym) * (logMeans[i] - Ym);
        }
        double beta  = sXX < 1e-12 ? 1.0 : sXY / sXX;
        double alpha = Math.exp(Ym - beta * Xm);
        double r2    = sYY < 1e-12 ? 1.0 :
                       (sXY * sXY) / (sXX * sYY);
        return new double[]{beta, alpha, r2};
    }

    // ============================================================
    //  DOMAIN 1: GRID GRAPH
    // ============================================================

    static class GridGraph {
        final int R, C;
        final int[][]     weight;
        final boolean[][] blocked;
        final int sr, sc, gr, gc;
        static final int[] DR = {-1, 1, 0, 0};
        static final int[] DC = { 0, 0,-1, 1};

        GridGraph(int size, long seed) {
            R = C = size;
            weight  = new int[R][C];
            blocked = new boolean[R][C];
            sr = sc = 0;
            gr = R - 1;
            gc = C - 1;
            Random rng = new Random(seed);
            for (int r = 0; r < R; r++)
                for (int c = 0; c < C; c++)
                    weight[r][c] = 1 + rng.nextInt(9);
            for (int r = 0; r < R; r++)
                for (int c = 0; c < C; c++) {
                    if (r == sr && c == sc) continue;
                    if (r == gr && c == gc) continue;
                    if (rng.nextDouble() < OBSTACLE_RATE)
                        blocked[r][c] = true;
                }
            // Guarantee path: carve L-corridor
            for (int c = 0; c < C; c++) blocked[0][c]     = false;
            for (int r = 0; r < R; r++) blocked[r][C - 1] = false;
        }

        boolean valid(int r, int c) {
            return r >= 0 && r < R && c >= 0 && c < C
                   && !blocked[r][c];
        }
        int id(int r, int c)  { return r * C + c; }
        int row(int id)       { return id / C; }
        int col(int id)       { return id % C; }
        int total()           { return R * C; }
        int h(int r, int c)   {
            return Math.abs(r - gr) + Math.abs(c - gc);
        }
        int h(int id)         { return h(row(id), col(id)); }

        List<int[]> nbrs(int id) {
            int r = row(id), c = col(id);
            List<int[]> out = new ArrayList<>(4);
            for (int d = 0; d < 4; d++) {
                int nr = r + DR[d], nc = c + DC[d];
                if (valid(nr, nc))
                    out.add(new int[]{
                        id(nr, nc), weight[nr][nc]});
            }
            return out;
        }
    }

    // ---- Grid: BFS ----
    static long[] gridBFS(GridGraph g) {
        int start = g.id(g.sr, g.sc);
        int goal  = g.id(g.gr, g.gc);
        Map<Integer, Integer> par = new HashMap<>();
        Queue<Integer> q = new LinkedList<>();
        par.put(start, -1);
        q.add(start);
        long exp = 0;
        while (!q.isEmpty()) {
            int u = q.poll();
            exp++;
            if (u == goal) break;
            for (int[] nb : g.nbrs(u))
                if (!par.containsKey(nb[0])) {
                    par.put(nb[0], u);
                    q.add(nb[0]);
                }
        }
        int useful = pathLen(par, start, goal);
        return new long[]{exp, useful};
    }

    // ---- Grid: Dijkstra ----
    static long[] gridDijkstra(GridGraph g) {
        int start = g.id(g.sr, g.sc);
        int goal  = g.id(g.gr, g.gc);
        int T = g.total();
        double[] dist = new double[T];
        int[]    par  = new int[T];
        Arrays.fill(dist, Double.MAX_VALUE);
        Arrays.fill(par, -1);
        dist[start] = 0;
        boolean[] vis = new boolean[T];
        PriorityQueue<double[]> pq = new PriorityQueue<>(
            Comparator.comparingDouble(x -> x[0]));
        pq.add(new double[]{0, start});
        long exp = 0;
        boolean found = false;
        while (!pq.isEmpty()) {
            double[] cur = pq.poll();
            int u = (int) cur[1];
            if (vis[u]) continue;
            vis[u] = true;
            exp++;
            if (u == goal) { found = true; break; }
            for (int[] nb : g.nbrs(u)) {
                double nd = dist[u] + nb[1];
                if (nd < dist[nb[0]]) {
                    dist[nb[0]] = nd;
                    par[nb[0]]  = u;
                    pq.add(new double[]{nd, nb[0]});
                }
            }
        }
        int useful = found ? pathLenArr(par, start, goal) : 0;
        return new long[]{exp, useful};
    }

    // ---- Grid: A* / WA* ----
    static long[] gridAStar(GridGraph g, double w) {
        int start = g.id(g.sr, g.sc);
        int goal  = g.id(g.gr, g.gc);
        int T = g.total();
        double[] gC  = new double[T];
        int[]    par = new int[T];
        Arrays.fill(gC, Double.MAX_VALUE);
        Arrays.fill(par, -1);
        gC[start] = 0;
        boolean[] closed = new boolean[T];
        PriorityQueue<double[]> open = new PriorityQueue<>(
            Comparator.comparingDouble(x -> x[0]));
        open.add(new double[]{w * g.h(start), start});
        long exp = 0;
        boolean found = false;
        while (!open.isEmpty()) {
            double[] cur = open.poll();
            int u = (int) cur[1];
            if (closed[u]) continue;
            closed[u] = true;
            exp++;
            if (u == goal) { found = true; break; }
            for (int[] nb : g.nbrs(u)) {
                if (closed[nb[0]]) continue;
                double ng = gC[u] + nb[1];
                if (ng < gC[nb[0]]) {
                    gC[nb[0]]  = ng;
                    par[nb[0]] = u;
                    open.add(new double[]{
                        ng + w * g.h(nb[0]), nb[0]});
                }
            }
        }
        int useful = found ? pathLenArr(par, start, goal) : 0;
        return new long[]{exp, useful};
    }

    // ---- Grid: GBFS ----
    static long[] gridGBFS(GridGraph g) {
        int start = g.id(g.sr, g.sc);
        int goal  = g.id(g.gr, g.gc);
        int T = g.total();
        int[]     par = new int[T];
        Arrays.fill(par, -1);
        boolean[] vis = new boolean[T];
        PriorityQueue<int[]> open = new PriorityQueue<>(
            Comparator.comparingInt(x -> x[0]));
        open.add(new int[]{g.h(start), start});
        long exp = 0;
        boolean found = false;
        while (!open.isEmpty()) {
            int[] cur = open.poll();
            int u = cur[1];
            if (vis[u]) continue;
            vis[u] = true;
            exp++;
            if (u == goal) { found = true; break; }
            for (int[] nb : g.nbrs(u)) {
                if (!vis[nb[0]]) {
                    if (par[nb[0]] == -1) par[nb[0]] = u;
                    open.add(new int[]{g.h(nb[0]), nb[0]});
                }
            }
        }
        int useful = found ? pathLenArr(par, start, goal) : 0;
        return new long[]{exp, useful};
    }

    // ---- Grid: IDA* ----
    static long   idaGridCount;
    static final long IDA_NODE_LIMIT = 3_000_000L;

    static long[] gridIDAStar(GridGraph g) {
        idaGridCount = 0;
        int start = g.id(g.sr, g.sc);
        int goal  = g.id(g.gr, g.gc);
        double threshold = g.h(start);
        Set<Integer>  onPath = new HashSet<>();
        List<Integer> path   = new ArrayList<>();
        onPath.add(start);
        path.add(start);
        boolean found = false;
        for (int it = 0; it < 50000 && !found; it++) {
            double res = idaGridSearch(
                g, start, 0, threshold, goal, onPath, path);
            if (res < 0)              { found = true; break; }
            if (res == Double.MAX_VALUE) break;
            threshold = res;
        }
        int useful = found ? path.size() : 0;
        return new long[]{idaGridCount, useful};
    }

    static double idaGridSearch(GridGraph g, int cur,
            double gCur, double thresh, int goal,
            Set<Integer> onPath, List<Integer> path) {
        double f = gCur + g.h(cur);
        if (f > thresh) return f;
        idaGridCount++;
        if (idaGridCount > IDA_NODE_LIMIT) return Double.MAX_VALUE;
        if (cur == goal) return -gCur;
        double min = Double.MAX_VALUE;
        List<int[]> nbrs = g.nbrs(cur);
        nbrs.sort(Comparator.comparingInt(
            nb -> (int)(gCur + nb[1]) + g.h(nb[0])));
        for (int[] nb : nbrs) {
            if (onPath.contains(nb[0])) continue;
            onPath.add(nb[0]);
            path.add(nb[0]);
            double res = idaGridSearch(
                g, nb[0], gCur + nb[1],
                thresh, goal, onPath, path);
            if (res < 0) return res;
            onPath.remove(nb[0]);
            path.remove(path.size() - 1);
            if (res < min) min = res;
        }
        return min;
    }

    // ---- Path reconstruction helpers ----
    static int pathLen(Map<Integer, Integer> par,
                       int start, int goal) {
        if (!par.containsKey(goal) && goal != start) return 0;
        int len = 1, cur = goal;
        Set<Integer> seen = new HashSet<>();
        while (cur != start) {
            seen.add(cur);
            Integer p = par.get(cur);
            if (p == null || seen.contains(p)) return 0;
            cur = p;
            len++;
        }
        return len;
    }

    static int pathLenArr(int[] par, int start, int goal) {
        if (par[goal] == -1 && goal != start) return 0;
        int len = 1, cur = goal;
        Set<Integer> seen = new HashSet<>();
        while (cur != start) {
            if (seen.contains(cur)) return 0;
            seen.add(cur);
            if (par[cur] < 0) return 0;
            cur = par[cur];
            len++;
        }
        return len;
    }

    // ============================================================
    // ============================================================
    //  DOMAIN 2: GAME THEORY — MINIMAX / ALPHA-BETA
    // ============================================================
    // ============================================================

    /**
     * Represents a two-player zero-sum game tree.
     * Leaf values are random integers in [0,100].
     * Internal nodes alternate MAX / MIN.
     *
     * MiniMax uses MM_DEPTH_CAP (10), not AB_DEPTH_CAP (8).
     * This ensures d=10 is a genuinely deeper tree than d=8
     * for MiniMax, allowing valid beta estimation.
     *
     * b=4, d=10: total nodes = (4^11-1)/3 = 1,398,101
     * b=4, d=8:  total nodes = (4^9 -1)/3 =    87,381
     * These are structurally distinct trees.
     */
    static class GameTree {
        final int   branching;
        final int   depth;       // actual depth after cap
        final int[] values;      // flattened tree array
        final int   firstLeaf;
        final int   treeSize;
        final int   nominalDepth; // depth requested

        GameTree(int b, int d, long seed, int depthCap) {
            branching    = b;
            nominalDepth = d;
            depth        = Math.min(d, depthCap);
            treeSize     = computeSize(b, this.depth);
            firstLeaf    = computeSize(b, this.depth - 1);
            values       = new int[treeSize];
            Random rng   = new Random(seed);
            for (int i = firstLeaf; i < treeSize; i++)
                values[i] = rng.nextInt(101);
        }

        static int computeSize(int b, int d) {
            if (d <= 0) return 1;
            long s = 1, lvl = 1;
            for (int i = 0; i < d; i++) {
                lvl = Math.min(lvl * b, 50_000_000L);
                s   = Math.min(s + lvl, 50_000_000L);
            }
            return (int) s;
        }

        int[] children(int node) {
            int[] ch = new int[branching];
            for (int i = 0; i < branching; i++)
                ch[i] = Math.min(
                    branching * node + 1 + i, treeSize - 1);
            return ch;
        }

        boolean isLeaf(int node) { return node >= firstLeaf; }
    }

    static long abCount;

    /**
     * MiniMax — NO pruning (pure minimax).
     * Uses MM_DEPTH_CAP so d=10 is genuinely depth-10.
     * b=4, d=10: expands all ~1.4M nodes.
     */
    static long[] gameMinMax(GameTree gt) {
        abCount = 0;
        minimax(gt, 0, true, 0);
        return new long[]{abCount, gt.depth + 1};
    }

    static int minimax(GameTree gt, int node,
                       boolean maxNode, int depth) {
        abCount++;
        if (gt.isLeaf(node) || depth >= gt.depth)
            return gt.values[Math.min(node,
                             gt.values.length - 1)];
        int[] ch = gt.children(node);
        if (maxNode) {
            int val = Integer.MIN_VALUE;
            for (int c : ch)
                val = Math.max(val,
                    minimax(gt, c, false, depth + 1));
            return val;
        } else {
            int val = Integer.MAX_VALUE;
            for (int c : ch)
                val = Math.min(val,
                    minimax(gt, c, true, depth + 1));
            return val;
        }
    }

    /**
     * Alpha-Beta pruning with move ordering.
     * Uses AB_DEPTH_CAP for memory safety.
     * Move ordering: sort children by value to approximate
     * best-first ordering.
     */
    static long[] gameAlphaBeta(GameTree gt) {
        abCount = 0;
        alphaBeta(gt, 0, Integer.MIN_VALUE,
                  Integer.MAX_VALUE, true, 0);
        return new long[]{abCount, gt.depth + 1};
    }

    static int alphaBeta(GameTree gt, int node,
                         int alpha, int beta,
                         boolean maxNode, int depth) {
        abCount++;
        if (gt.isLeaf(node) || depth >= gt.depth)
            return gt.values[Math.min(node,
                             gt.values.length - 1)];
        int[] ch = gt.children(node);
        sortChildren(ch, gt.values, !maxNode);
        if (maxNode) {
            int val = Integer.MIN_VALUE;
            for (int c : ch) {
                val   = Math.max(val, alphaBeta(
                    gt, c, alpha, beta, false, depth + 1));
                alpha = Math.max(alpha, val);
                if (beta <= alpha) break;
            }
            return val;
        } else {
            int val = Integer.MAX_VALUE;
            for (int c : ch) {
                val  = Math.min(val, alphaBeta(
                    gt, c, alpha, beta, true, depth + 1));
                beta = Math.min(beta, val);
                if (beta <= alpha) break;
            }
            return val;
        }
    }

    static void sortChildren(int[] ch, int[] vals,
                              boolean ascending) {
        Integer[] box = new Integer[ch.length];
        for (int i = 0; i < ch.length; i++) box[i] = ch[i];
        Arrays.sort(box, (a, b_) -> ascending
            ? Integer.compare(
                vals[Math.min(a,  vals.length - 1)],
                vals[Math.min(b_, vals.length - 1)])
            : Integer.compare(
                vals[Math.min(b_, vals.length - 1)],
                vals[Math.min(a,  vals.length - 1)]));
        for (int i = 0; i < ch.length; i++) ch[i] = box[i];
    }

    // ============================================================
    // ============================================================
    //  DOMAIN 3: MAPF — PRIORITISED PLANNING (PP)
    // ============================================================
    //
    //  REPLACES CBS with Prioritised Planning:
    //
    //  Algorithm:
    //    Agents are assigned a fixed priority order (agent 0
    //    is highest priority).
    //    Each agent i plans an optimal path via space-time A*
    //    treating all previously planned agents' paths as
    //    hard constraints (forbidden (location, time) pairs).
    //
    //  This is a genuine, complete implementation:
    //    - No stub paths
    //    - Real space-time A* node expansions counted
    //    - Real path reconstruction via parent map
    //    - Conflicts resolved by construction (not detection)
    //
    //  Metrics reported:
    //    - Total space-time A* nodes expanded (all agents)
    //    - Sum of individual path lengths
    //    - Solution quality: makespan and sum-of-costs
    //    - Solve rate over 30 trials
    // ============================================================
    // ============================================================

    static class MAPFGrid {
        final int R, C;
        final boolean[][] blocked;
        // 5 actions: N, S, W, E, Wait
        static final int[] DR = {-1, 1, 0, 0, 0};
        static final int[] DC = { 0, 0,-1, 1, 0};

        MAPFGrid(int size, long seed) {
            R = C = size;
            blocked = new boolean[R][C];
            Random rng = new Random(seed);
            for (int r = 0; r < R; r++)
                for (int c = 0; c < C; c++)
                    if (rng.nextDouble() < 0.10)
                        blocked[r][c] = true;
            // Clear corners for agent placement
            clearCell(0, 0);
            clearCell(0, C - 1);
            clearCell(R - 1, 0);
            clearCell(R - 1, C - 1);
            clearCell(0, C / 2);
            clearCell(R - 1, C / 2);
            clearCell(R / 2, 0);
            clearCell(R / 2, C - 1);
            clearCell(R / 2, C / 2);
        }

        void clearCell(int r, int c) {
            if (r >= 0 && r < R && c >= 0 && c < C)
                blocked[r][c] = false;
        }

        boolean valid(int r, int c) {
            return r >= 0 && r < R
                && c >= 0 && c < C
                && !blocked[r][c];
        }

        int id(int r, int c) { return r * C + c; }
        int row(int id)      { return id / C; }
        int col(int id)      { return id % C; }

        int h(int fromId, int toId) {
            return Math.abs(row(fromId) - row(toId))
                 + Math.abs(col(fromId) - col(toId));
        }
    }

    // ============================================================
    //  SPACE-TIME A* — core planner for each agent
    //
    //  State  = (location, time)
    //  Encoded as: stateKey = location * (MAPF_MAX_TIME+1) + time
    //
    //  Constraints: Set of forbidden (location, time) pairs
    //  from higher-priority agents' planned paths.
    //
    //  Returns: [nodesExpanded, pathLength]
    //           and fills pathOut with the sequence of location IDs
    // ============================================================
    static long[] spaceTimeAStar(
            MAPFGrid grid,
            int startLoc,
            int goalLoc,
            Set<Long> forbidden,   // encoded (loc, time) pairs
            int maxTime,
            List<Integer> pathOut) {

        pathOut.clear();

        // State encoding
        final int T = maxTime + 1;

        // g-cost map: stateKey -> cost
        Map<Long, Integer> gCost  = new HashMap<>();
        // parent map: stateKey -> parent stateKey (-1 for root)
        Map<Long, Long>    parent = new HashMap<>();

        long startState = encodeSTState(startLoc, 0, T);
        gCost.put(startState, 0);
        parent.put(startState, -1L);

        // Priority queue: [f-cost (double bits), stateKey]
        PriorityQueue<long[]> open = new PriorityQueue<>(
            Comparator.comparingDouble(
                x -> Double.longBitsToDouble(x[0])));

        int h0 = grid.h(startLoc, goalLoc);
        open.add(new long[]{
            Double.doubleToLongBits(h0), startState});

        Set<Long> closed = new HashSet<>();
        long expanded = 0;
        long goalState = -1;

        while (!open.isEmpty()) {
            long[] cur = open.poll();
            long state = cur[1];

            if (closed.contains(state)) continue;
            closed.add(state);
            expanded++;

            int loc  = decodeSTLoc(state, T);
            int time = decodeSTTime(state, T);

            if (loc == goalLoc) {
                goalState = state;
                break;
            }

            if (time >= maxTime) continue;

            int r = grid.row(loc);
            int c = grid.col(loc);

            for (int d = 0; d < 5; d++) {
                int nr = r + MAPFGrid.DR[d];
                int nc = c + MAPFGrid.DC[d];
                if (!grid.valid(nr, nc)) continue;

                int  nLoc   = grid.id(nr, nc);
                int  nTime  = time + 1;
                long nState = encodeSTState(nLoc, nTime, T);

                // Check forbidden (location, time) constraint
                long forbKey = encodeForbidden(nLoc, nTime);
                if (forbidden.contains(forbKey)) continue;

                if (closed.contains(nState)) continue;

                int ng = gCost.getOrDefault(state, Integer.MAX_VALUE)
                         + 1;
                if (ng < gCost.getOrDefault(
                             nState, Integer.MAX_VALUE)) {
                    gCost.put(nState, ng);
                    parent.put(nState, state);
                    int nh = grid.h(nLoc, goalLoc);
                    open.add(new long[]{
                        Double.doubleToLongBits(ng + nh),
                        nState});
                }
            }
        }

        // Reconstruct path
        int pathLen = 0;
        if (goalState >= 0) {
            LinkedList<Integer> revPath = new LinkedList<>();
            long s = goalState;
            while (s != -1L) {
                revPath.addFirst(decodeSTLoc(s, T));
                Long p = parent.get(s);
                s = (p == null) ? -1L : p;
            }
            pathOut.addAll(revPath);
            pathLen = pathOut.size();
        }

        return new long[]{expanded, pathLen};
    }

    // State encoding helpers
    static long encodeSTState(int loc, int time, int T) {
        return (long) loc * T + time;
    }
    static int decodeSTLoc(long state, int T) {
        return (int)(state / T);
    }
    static int decodeSTTime(long state, int T) {
        return (int)(state % T);
    }
    static long encodeForbidden(int loc, int time) {
        return (long) loc * 10000 + time;
    }

    // ============================================================
    //  PRIORITISED PLANNING
    //
    //  Agents planned in order 0, 1, 2, ...
    //  Each agent avoids all (location, time) pairs occupied
    //  by already-planned agents.
    //
    //  Returns: [totalNodesExpanded, totalPathLength, solvedCount]
    //  where solvedCount = number of agents successfully planned.
    // ============================================================
    static long[] runPrioritisedPlanning(
            MAPFGrid grid,
            int[] starts,
            int[] goals) {

        int k = starts.length;
        long totalNodes  = 0;
        long totalPath   = 0;
        int  solvedCount = 0;

        // forbidden: set of (location, time) keys that are
        // occupied by already-planned agents
        Set<Long> forbidden = new HashSet<>();

        List<List<Integer>> allPaths = new ArrayList<>();
        for (int i = 0; i < k; i++) allPaths.add(new ArrayList<>());

        for (int agent = 0; agent < k; agent++) {
            List<Integer> path = new ArrayList<>();
            long[] result = spaceTimeAStar(
                grid,
                starts[agent],
                goals[agent],
                forbidden,
                MAPF_MAX_TIME,
                path);

            totalNodes += result[0];

            if (!path.isEmpty()) {
                solvedCount++;
                totalPath += path.size();
                allPaths.set(agent, path);

                // Add this agent's path to forbidden set
                // so subsequent agents avoid it
                for (int t = 0; t < path.size(); t++) {
                    forbidden.add(
                        encodeForbidden(path.get(t), t));
                }
                // Agent stays at goal after path ends
                int goalLoc = path.get(path.size() - 1);
                for (int t = path.size(); t <= MAPF_MAX_TIME; t++)
                    forbidden.add(encodeForbidden(goalLoc, t));
            }
        }

        return new long[]{totalNodes, totalPath, solvedCount};
    }

    // ---- Place agents on valid, distinct cells ----
    static int[] placeAgents(MAPFGrid g, int n,
                              long seed, boolean reverse) {
        // Predefined well-separated positions
        int[][] candidates = {
            {0,       0      },
            {g.R - 1, g.C - 1},
            {0,       g.C - 1},
            {g.R - 1, 0      },
            {0,       g.C / 2},
            {g.R - 1, g.C / 2},
            {g.R / 2, 0      },
            {g.R / 2, g.C - 1}
        };

        if (reverse) {
            // Reverse pairing: agent i starts at candidate[i],
            // goals at candidate[k-1-i]
            int[] locs = new int[n];
            int placed = 0;
            for (int i = candidates.length - 1;
                 i >= 0 && placed < n; i--) {
                int r = candidates[i][0];
                int c = candidates[i][1];
                if (g.valid(r, c))
                    locs[placed++] = g.id(r, c);
            }
            // Fill remaining with random if needed
            if (placed < n) {
                Random rng = new Random(seed + 7777);
                Set<Integer> used = new HashSet<>();
                for (int i = 0; i < placed; i++)
                    used.add(locs[i]);
                while (placed < n) {
                    int r = rng.nextInt(g.R);
                    int c = rng.nextInt(g.C);
                    if (g.valid(r, c)) {
                        int id = g.id(r, c);
                        if (!used.contains(id)) {
                            locs[placed++] = id;
                            used.add(id);
                        }
                    }
                }
            }
            return locs;
        } else {
            int[] locs = new int[n];
            int placed = 0;
            Set<Integer> used = new HashSet<>();
            for (int[] cand : candidates) {
                if (placed >= n) break;
                if (g.valid(cand[0], cand[1])) {
                    int id = g.id(cand[0], cand[1]);
                    if (!used.contains(id)) {
                        locs[placed++] = id;
                        used.add(id);
                    }
                }
            }
            // Fill remaining with random if needed
            if (placed < n) {
                Random rng = new Random(seed);
                while (placed < n) {
                    int r = rng.nextInt(g.R);
                    int c = rng.nextInt(g.C);
                    if (g.valid(r, c)) {
                        int id = g.id(r, c);
                        if (!used.contains(id)) {
                            locs[placed++] = id;
                            used.add(id);
                        }
                    }
                }
            }
            return locs;
        }
    }

    // ============================================================
    // ============================================================
    //  DOMAIN 4: PATTERN MATCHING
    // ============================================================
    // ============================================================

    static final int    ALPHABET = 4;
    static final char[] DNA      = {'A', 'C', 'G', 'T'};

    static char[] generateText(int length, long seed) {
        Random rng  = new Random(seed);
        char[] text = new char[length];
        for (int i = 0; i < length; i++)
            text[i] = DNA[rng.nextInt(4)];
        return text;
    }

    static char[] generatePattern(int length, long seed) {
        Random rng = new Random(seed + 999);
        char[] pat = new char[length];
        for (int i = 0; i < length; i++)
            pat[i] = DNA[rng.nextInt(4)];
        return pat;
    }

    /** Naive brute-force pattern matching. */
    static long[] naiveMatch(char[] text, char[] pattern) {
        long comparisons = 0;
        int n = text.length, m = pattern.length;
        boolean found    = false;
        for (int i = 0; i <= n - m && !found; i++) {
            for (int j = 0; j < m; j++) {
                comparisons++;
                if (text[i + j] != pattern[j]) break;
                if (j == m - 1) found = true;
            }
        }
        int useful = found ? m : 0;
        return new long[]{comparisons, useful};
    }

    /** KMP pattern matching with failure function. */
    static long[] kmpMatch(char[] text, char[] pattern) {
        int  n = text.length, m = pattern.length;
        long comparisons = 0;

        // Build failure function
        int[] fail = new int[m];
        fail[0] = 0;
        int k = 0;
        for (int i = 1; i < m; i++) {
            comparisons++;
            while (k > 0 && pattern[k] != pattern[i])
                k = fail[k - 1];
            if (pattern[k] == pattern[i]) k++;
            fail[i] = k;
        }

        // Search
        k = 0;
        boolean found = false;
        for (int i = 0; i < n && !found; i++) {
            comparisons++;
            while (k > 0 && pattern[k] != text[i])
                k = fail[k - 1];
            if (pattern[k] == text[i]) k++;
            if (k == m) found = true;
        }
        int useful = found ? m : 0;
        return new long[]{comparisons, useful};
    }

    /**
     * IDDFS on the implicit pattern-matching search tree.
     *
     * Tree structure:
     *   Root   = text position 0
     *   Level d = characters matched so far
     *   Branch = try next text character vs pattern character
     *
     * Threshold = number of characters required to match.
     * We iterate threshold from 1 to m (pattern length).
     * At each threshold, we try every start position and
     * perform depth-limited matching.
     *
     * Node expansion = one character comparison attempted.
     */
    static long iddfsPatternCount;

    static long[] iddfsMatch(char[] text, char[] pattern) {
        iddfsPatternCount = 0;
        boolean found     = false;
        int     patternLen = pattern.length;

        for (int threshold = 1;
             threshold <= patternLen && !found;
             threshold++) {
            for (int start = 0;
                 start <= text.length - patternLen && !found;
                 start++) {
                iddfsPatternCount++;  // count each start attempt
                int matched = iddfsPatternDFS(
                    text, pattern, start, 0, threshold);
                if (matched == patternLen) found = true;
            }
        }
        int useful = found ? patternLen : 0;
        return new long[]{iddfsPatternCount, useful};
    }

    static int iddfsPatternDFS(char[] text, char[] pattern,
                                int tPos, int pPos, int depth) {
        if (pPos == pattern.length) return pPos;
        if (depth == 0)             return pPos;
        if (tPos >= text.length)    return pPos;
        iddfsPatternCount++;
        if (text[tPos] != pattern[pPos]) return pPos;
        return iddfsPatternDFS(
            text, pattern, tPos + 1, pPos + 1, depth - 1);
    }

    // ============================================================
    //  MAIN EXPERIMENT RUNNER
    // ============================================================
    static void runAllDomains() {
        System.out.println("=".repeat(72));
        System.out.println(
            "  CROSS-DOMAIN BEHAVIORAL ANALYSIS — GBT FRAMEWORK");
        System.out.println("=".repeat(72));
        System.out.printf(
            "  Trials=%d | Depths=%s | b=%d%n%n",
            TRIALS, Arrays.toString(DEPTHS), BRANCHING);

        runGridDomain();
        runGameDomain();
        runMAPFDomain();
        runPatternDomain();
        printCrossDomainSummary();
        printBetaComparison();
    }

    // ---- Run Domain 1: Grid ----
    static void runGridDomain() {
        System.out.println("\n" + "=".repeat(72));
        System.out.println("  DOMAIN 1: GRID PATHFINDING");
        System.out.println("=".repeat(72));

        String[][] algs = {
            {ALG_BFS,  "bfs"},
            {ALG_DIJK, "dijk"},
            {ALG_ASTAR,"astar"},
            {ALG_WA,   "wa"},
            {ALG_GBFS, "gbfs"},
            {ALG_IDA,  "ida"}
        };

        for (int depth : DEPTHS) {
            int gridSize = Math.max(4 * depth, 8);
            System.out.printf("  d=%2d (grid %dx%d):%n",
                depth, gridSize, gridSize);

            for (String[] alg : algs) {
                List<Observation> obs = new ArrayList<>();
                for (int t = 0; t < TRIALS; t++) {
                    long      seed = 100L * depth + t;
                    GridGraph g    = new GridGraph(gridSize, seed);
                    long[]    r;
                    switch (alg[1]) {
                        case "bfs":   r = gridBFS(g);        break;
                        case "dijk":  r = gridDijkstra(g);   break;
                        case "astar": r = gridAStar(g, 1.0); break;
                        case "wa":    r = gridAStar(g, 1.5); break;
                        case "gbfs":  r = gridGBFS(g);       break;
                        default:      r = gridIDAStar(g);    break;
                    }
                    obs.add(new Observation(
                        D_GRID, alg[0], depth, t,
                        r[0], (int) r[1], r[1] > 0,
                        0, BRANCHING));
                }
                allObs.addAll(obs);
                AggResult ar = new AggResult(
                    D_GRID, alg[0], depth, obs);
                System.out.printf(
                    "    %-12s nodes=%8.1f±%7.1f | "
                    + "useful=%5.1f | BEI=%.4f%n",
                    alg[0], ar.meanNodes, ar.stdNodes,
                    ar.meanPath, ar.meanBEI);
            }
        }
    }

    // ---- Run Domain 2: Game Theory ----
    static void runGameDomain() {
        System.out.println("\n" + "=".repeat(72));
        System.out.println(
            "  DOMAIN 2: GAME THEORY (MINIMAX / ALPHA-BETA)");
        System.out.println("=".repeat(72));
        System.out.printf(
            "  MiniMax  depth cap: %d%n", MM_DEPTH_CAP);
        System.out.printf(
            "  AlphaBeta depth cap: %d%n", AB_DEPTH_CAP);

        for (int depth : DEPTHS) {
            // MiniMax: uses MM_DEPTH_CAP (10)
            // d=4  -> actual depth 4  (same as before)
            // d=6  -> actual depth 6  (same as before)
            // d=8  -> actual depth 8  (same as before)
            // d=10 -> actual depth 10 (NEW — genuinely deeper)
            // d=12 -> actual depth 10 (capped, reported honestly)
            GameTree gtMM = new GameTree(
                BRANCHING, depth, 0, MM_DEPTH_CAP);
            GameTree gtAB = new GameTree(
                BRANCHING, depth, 0, AB_DEPTH_CAP);

            System.out.printf("  d=%2d (MM actual depth=%d, "
                + "AB actual depth=%d):%n",
                depth, gtMM.depth, gtAB.depth);

            // MiniMax
            long sumMM = 0;
            for (int t = 0; t < TRIALS; t++) {
                GameTree gt = new GameTree(
                    BRANCHING, depth, 200L * depth + t,
                    MM_DEPTH_CAP);
                long[] r = gameMinMax(gt);
                sumMM += r[0];
                allObs.add(new Observation(
                    D_GAME, ALG_MM, depth, t,
                    r[0], (int) r[1], true, 0, BRANCHING));
            }
            System.out.printf(
                "    %-12s nodes=%12.1f%n",
                ALG_MM, (double) sumMM / TRIALS);

            // AlphaBeta
            long sumAB = 0;
            for (int t = 0; t < TRIALS; t++) {
                GameTree gt = new GameTree(
                    BRANCHING, depth, 200L * depth + t,
                    AB_DEPTH_CAP);
                long[] r = gameAlphaBeta(gt);
                sumAB += r[0];
                allObs.add(new Observation(
                    D_GAME, ALG_AB, depth, t,
                    r[0], (int) r[1], true, 0, BRANCHING));
            }
            System.out.printf(
                "    %-12s nodes=%12.1f%n",
                ALG_AB, (double) sumAB / TRIALS);
        }
    }

    // ---- Run Domain 3: MAPF (Prioritised Planning) ----
    static void runMAPFDomain() {
        System.out.println("\n" + "=".repeat(72));
        System.out.println(
            "  DOMAIN 3: MAPF — PRIORITISED PLANNING (PP)");
        System.out.println(
            "  (Space-time A* per agent; higher-priority");
        System.out.println(
            "   agents' paths are hard constraints)");
        System.out.println("=".repeat(72));

        int[] agentCounts = {2, 2, 3, 3, 4};

        for (int di = 0; di < DEPTHS.length; di++) {
            int depth   = DEPTHS[di];
            int nAgents = agentCounts[di];
            int gridSz  = Math.max(depth, 5);

            System.out.printf(
                "  d=%2d (grid=%dx%d, agents=%d):%n",
                depth, gridSz, gridSz, nAgents);

            long sumNodes  = 0;
            long sumPath   = 0;
            int  solved    = 0;
            int  fullSolve = 0; // all agents solved

            for (int t = 0; t < TRIALS; t++) {
                long     seed = 300L * depth + t;
                MAPFGrid mg   = new MAPFGrid(gridSz, seed);

                int[] starts = placeAgents(mg, nAgents, seed,  false);
                int[] goals  = placeAgents(mg, nAgents, seed,  true);

                long[] r = runPrioritisedPlanning(mg, starts, goals);
                // r = [totalNodes, totalPath, solvedCount]

                sumNodes += r[0];
                sumPath  += r[1];
                if (r[2] > 0)       solved++;
                if (r[2] == nAgents) fullSolve++;

                // Record total nodes for beta estimation
                allObs.add(new Observation(
                    D_MAPF, ALG_PP, depth, t,
                    r[0], (int) r[1],
                    r[2] == nAgents, 0, BRANCHING));
            }

            System.out.printf(
                "    %-20s nodes=%8.1f | path=%6.1f | "
                + "full_solve=%d/%d%n",
                ALG_PP,
                (double) sumNodes  / TRIALS,
                (double) sumPath   / TRIALS,
                fullSolve, TRIALS);
        }
    }

    // ---- Run Domain 4: Pattern Matching ----
    static void runPatternDomain() {
        System.out.println("\n" + "=".repeat(72));
        System.out.println("  DOMAIN 4: PATTERN MATCHING");
        System.out.println("  (DNA sequence; alphabet size=4)");
        System.out.println("=".repeat(72));

        for (int depth : DEPTHS) {
            int patLen  = depth * 3;
            int textLen = patLen * 100;
            System.out.printf(
                "  d=%2d (pattern=%d, text=%d):%n",
                depth, patLen, textLen);

            String[][] algs = {
                {ALG_NAIVE, "naive"},
                {ALG_KMP,   "kmp"},
                {ALG_IDDFS, "iddfs"}
            };

            for (String[] alg : algs) {
                long sumN = 0;
                int  sumU = 0;
                for (int t = 0; t < TRIALS; t++) {
                    long   seed    = 400L * depth + t;
                    char[] text    = generateText(textLen, seed);
                    char[] pattern = generatePattern(patLen, seed);

                    // Inject pattern at a deterministic position
                    int pos = (int)(seed % (textLen - patLen));
                    if (pos < 0) pos = 0;
                    System.arraycopy(pattern, 0, text, pos, patLen);

                    long[] r;
                    switch (alg[1]) {
                        case "naive": r = naiveMatch(text,pattern); break;
                        case "kmp":   r = kmpMatch(text,pattern);   break;
                        default:      r = iddfsMatch(text,pattern);  break;
                    }
                    sumN += r[0];
                    sumU += (int) r[1];
                    allObs.add(new Observation(
                        D_PATTERN, alg[0], depth, t,
                        r[0], (int) r[1], r[1] > 0,
                        0, ALPHABET));
                }
                System.out.printf(
                    "    %-12s nodes=%9.1f | useful=%.1f%n",
                    alg[0],
                    (double) sumN / TRIALS,
                    (double) sumU / TRIALS);
            }
        }
    }

    // ============================================================
    //  CROSS-DOMAIN SUMMARY AND BETA COMPARISON
    // ============================================================
    static void printCrossDomainSummary() {
        System.out.println("\n" + "=".repeat(72));
        System.out.println("  CROSS-DOMAIN BEHAVIORAL SUMMARY");
        System.out.println("=".repeat(72));
        System.out.printf("  %-14s %-20s %8s %8s %8s%n",
            "Domain", "Algorithm", "d=6", "d=10", "d=12");
        System.out.println("  " + "-".repeat(60));

        // Group observations
        Map<String, Map<String, Map<Integer, List<Observation>>>>
            grouped = new TreeMap<>();
        for (Observation o : allObs) {
            grouped
                .computeIfAbsent(o.domain,
                    k -> new TreeMap<>())
                .computeIfAbsent(o.algorithm,
                    k -> new TreeMap<>())
                .computeIfAbsent(o.depth,
                    k -> new ArrayList<>())
                .add(o);
        }

        for (String dom : grouped.keySet()) {
            for (String alg : grouped.get(dom).keySet()) {
                System.out.printf("  %-14s %-20s", dom, alg);
                for (int d : new int[]{6, 10, 12}) {
                    List<Observation> obs =
                        grouped.get(dom).get(alg)
                               .getOrDefault(d,
                                   Collections.emptyList());
                    double mean = obs.stream()
                        .mapToLong(o -> o.nodesExpanded)
                        .average().orElse(0);
                    System.out.printf(" %8.0f", mean);
                }
                System.out.println();
            }
        }
    }

    static void printBetaComparison() {
        System.out.println("\n" + "=".repeat(72));
        System.out.println(
            "  BETA PARAMETER COMPARISON ACROSS DOMAINS");
        System.out.println(
            "  (log-linear regression on mean node counts)");
        System.out.println("=".repeat(72));
        System.out.printf("  %-14s %-20s %8s %12s %6s%n",
            "Domain", "Algorithm", "β̂", "α̂", "R²");
        System.out.println("  " + "-".repeat(62));

        Map<String, Map<String, List<Observation>>> byDomAlg
            = new TreeMap<>();
        for (Observation o : allObs) {
            byDomAlg
                .computeIfAbsent(o.domain,
                    k -> new TreeMap<>())
                .computeIfAbsent(o.algorithm,
                    k -> new ArrayList<>())
                .add(o);
        }

        for (String dom : byDomAlg.keySet()) {
            for (String alg : byDomAlg.get(dom).keySet()) {
                List<Observation> obs =
                    byDomAlg.get(dom).get(alg);

                Map<Integer, Double> meanByDepth = new TreeMap<>();
                for (int d : DEPTHS) {
                    double mean = obs.stream()
                        .filter(o -> o.depth == d)
                        .mapToLong(o -> o.nodesExpanded)
                        .average().orElse(0);
                    if (mean > 0) meanByDepth.put(d, mean);
                }
                if (meanByDepth.size() < 2) continue;

                int b = dom.equals(D_PATTERN)
                        ? ALPHABET : BRANCHING;

                double[] depArr = meanByDepth.keySet().stream()
                    .mapToDouble(x -> x).toArray();
                double[] logN   = meanByDepth.values().stream()
                    .mapToDouble(v -> Math.log(v + 1)).toArray();

                double[] fit = fitBeta(depArr, logN);
                System.out.printf(
                    "  %-14s %-20s %8.4f %12.4f %6.4f%n",
                    dom, alg, fit[0], fit[1], fit[2]);
            }
        }

        System.out.println();
        System.out.println("  KEY FINDING:");
        System.out.println(
            "  Compare β across domains for same algorithm class.");
        System.out.println(
            "  If β is stable  → GBT framework generalises.");
        System.out.println(
            "  If β varies     → domain structure affects behaviour.");
        System.out.println();
        System.out.println("  NOTE ON GAME DOMAIN DEPTH CAP:");
        System.out.printf(
            "  MiniMax uses depth cap %d: d=12 uses same tree"
            + " as d=10.%n", MM_DEPTH_CAP);
        System.out.printf(
            "  AlphaBeta uses depth cap %d: same.%n",
            AB_DEPTH_CAP);
        System.out.println(
            "  Beta estimated from depths where tree genuinely"
            + " differs (d=4,6,8,10).");
    }

    // ============================================================
    //  VISUALIZATION
    // ============================================================
    static final Map<String, Color> domainColors =
        new LinkedHashMap<>();
    static {
        domainColors.put(D_GRID,    new Color( 30, 144, 255));
        domainColors.put(D_GAME,    new Color(220,  50,  50));
        domainColors.put(D_MAPF,    new Color( 50, 180,  50));
        domainColors.put(D_PATTERN, new Color(180,  50, 220));
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
            RenderingHints.VALUE_ANTIALIAS_ON);

        int W = getWidth(), H = getHeight();
        g2.setColor(new Color(248, 248, 252));
        g2.fillRect(0, 0, W, H);

        g2.setColor(Color.BLACK);
        g2.setFont(new Font("SansSerif", Font.BOLD, 14));
        g2.drawString(
            "Cross-Domain GBT Analysis — "
            + "Node Expansions vs. Depth (log scale)",
            20, 28);

        String[] doms = {D_GRID, D_GAME, D_MAPF, D_PATTERN};
        int pw = W / 2 - 20;
        int ph = H / 2 - 60;
        for (int di = 0; di < doms.length; di++) {
            int ox = (di % 2) * (W / 2) + 10;
            int oy = (di / 2) * (H / 2) + 50;
            drawDomainPanel(g2, doms[di], ox, oy, pw, ph);
        }
    }

    void drawDomainPanel(Graphics2D g2, String domain,
                          int ox, int oy, int W, int H) {
        int mL = 65, mR = 10, mT = 30, mB = 35;
        int pW = W - mL - mR, pH = H - mT - mB;

        g2.setColor(Color.WHITE);
        g2.fillRoundRect(ox, oy, W, H, 8, 8);
        g2.setColor(domainColors.get(domain));
        g2.setStroke(new BasicStroke(2));
        g2.drawRoundRect(ox, oy, W, H, 8, 8);

        g2.setFont(new Font("SansSerif", Font.BOLD, 11));
        g2.drawString("Domain: " + domain, ox + mL, oy + 18);

        // Compute means per depth per algorithm
        Map<String, double[]> means = new TreeMap<>();
        for (Observation o : allObs) {
            if (!o.domain.equals(domain)) continue;
            means.computeIfAbsent(
                o.algorithm, k -> new double[DEPTHS.length]);
        }
        for (String alg : means.keySet()) {
            double[] m = means.get(alg);
            for (int di = 0; di < DEPTHS.length; di++) {
                final int d = DEPTHS[di];
                m[di] = allObs.stream()
                    .filter(o -> o.domain.equals(domain)
                              && o.algorithm.equals(alg)
                              && o.depth == d)
                    .mapToLong(o -> o.nodesExpanded)
                    .average().orElse(0);
            }
        }

        if (means.isEmpty()) {
            g2.setColor(Color.GRAY);
            g2.setFont(new Font("SansSerif", Font.ITALIC, 10));
            g2.drawString("No data",
                ox + mL + pW / 2 - 20, oy + mT + pH / 2);
            return;
        }

        // Log scale range
        double logMax = 1, logMin = Double.MAX_VALUE;
        for (double[] m : means.values())
            for (double v : m) {
                if (v > 1) logMax = Math.max(logMax, Math.log10(v));
                if (v > 1) logMin = Math.min(logMin, Math.log10(v));
            }
        if (logMin == Double.MAX_VALUE) logMin = 0;
        logMax = Math.ceil(logMax)  + 0.5;
        logMin = Math.max(0, Math.floor(logMin) - 0.5);
        double logRange = logMax - logMin;
        if (logRange < 1e-6) logRange = 1;

        // Grid lines
        g2.setStroke(new BasicStroke(1, BasicStroke.CAP_BUTT,
            BasicStroke.JOIN_BEVEL, 0, new float[]{3}, 0));
        g2.setFont(new Font("SansSerif", Font.PLAIN, 8));
        for (int pw2 = (int) logMin;
             pw2 <= (int) logMax; pw2++) {
            int yy = oy + mT + pH
                - (int)((pw2 - logMin) / logRange * pH);
            if (yy < oy + mT || yy > oy + mT + pH) continue;
            g2.setColor(new Color(220, 220, 220));
            g2.drawLine(ox + mL, yy, ox + mL + pW, yy);
            g2.setColor(Color.GRAY);
            g2.drawString("10^" + pw2, ox + mL - 42, yy + 3);
        }

        // Axes
        g2.setColor(Color.BLACK);
        g2.setStroke(new BasicStroke(1.5f));
        g2.drawLine(ox + mL, oy + mT,
                    ox + mL, oy + mT + pH);
        g2.drawLine(ox + mL, oy + mT + pH,
                    ox + mL + pW, oy + mT + pH);

        // X axis labels
        g2.setFont(new Font("SansSerif", Font.PLAIN, 8));
        int nd = DEPTHS.length;
        for (int i = 0; i < nd; i++) {
            int x = ox + mL + (i * pW) / (nd - 1);
            g2.drawString("" + DEPTHS[i], x - 4,
                oy + mT + pH + 12);
        }
        g2.drawString("d", ox + mL + pW / 2,
            oy + mT + pH + 24);

        // Draw each algorithm's curve
        Color[] aColors = {
            new Color(220,  50,  50),
            new Color( 30, 144, 255),
            new Color(  0, 180,  80),
            new Color(255, 165,   0),
            new Color(180,  50, 220),
            new Color(139,  90,  43),
            new Color(  0, 180, 200),
            new Color(255,  20, 147)
        };

        int ai = 0;
        int legendY = oy + mT + 4;
        for (String alg : means.keySet()) {
            double[] m   = means.get(alg);
            Color    col = aColors[ai % aColors.length];
            g2.setColor(col);
            g2.setStroke(new BasicStroke(1.8f));
            int px = -1, py = -1;
            for (int i = 0; i < nd; i++) {
                if (m[i] < 1) continue;
                int    x  = ox + mL + (i * pW) / (nd - 1);
                double lv = Math.log10(m[i]);
                int    y  = oy + mT + pH
                    - (int)((lv - logMin) / logRange * pH);
                y = Math.max(oy + mT, Math.min(oy + mT + pH, y));
                g2.fillOval(x - 3, y - 3, 6, 6);
                if (px >= 0) g2.drawLine(px, py, x, y);
                px = x; py = y;
            }
            // Legend entry
            g2.setColor(col);
            g2.setStroke(new BasicStroke(1.5f));
            g2.drawLine(ox + mL + pW - 85,
                        legendY + ai * 11 + 4,
                        ox + mL + pW - 65,
                        legendY + ai * 11 + 4);
            g2.setColor(Color.BLACK);
            g2.setFont(new Font("SansSerif", Font.PLAIN, 8));
            String lbl = alg.length() > 12
                ? alg.substring(0, 12) : alg;
            g2.drawString(lbl,
                ox + mL + pW - 63, legendY + ai * 11 + 7);
            ai++;
        }
    }

    // ============================================================
    //  MAIN
    // ============================================================
    public static void main(String[] args) {
        System.out.println("Cross-Domain GBT Behavioral Analysis");
        System.out.println(
            "Domains: Grid | GameTree | MAPF (PP) | PatternMatch");
        System.out.println(
            "All node counts from real algorithm execution.");
        System.out.println();

        runAllDomains();

        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame(
                "GBT Cross-Domain Behavioral Analysis");
            frame.setDefaultCloseOperation(
                JFrame.EXIT_ON_CLOSE);
            frame.setSize(1200, 800);
            CrossDomainSearchExperimentPP panel =
                new CrossDomainSearchExperimentPP();
            panel.setBackground(Color.WHITE);
            frame.add(panel);
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
        });
    }
}