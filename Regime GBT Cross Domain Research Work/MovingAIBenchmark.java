import java.io.*;
import java.nio.file.*;
import java.util.*;

/**
 * External-validation artifact for
 * "Regime Structure in Tree Search" (companion to CrossDomainSearchExperiment.java).
 *
 * Runs the same counting rules on EXTERNAL benchmark data:
 *  - MovingAI grid benchmarks (Sturtevant 2012): official maps + official random scenarios with
 *    stored optimal solution costs, so solution quality q is measured, not assumed.
 *  - Size ladders: empty-{8,16,32}-* (train) -> empty-48-48 (held out), d = L/8;
 *    random-10%-{32,64} (train) -> random512-10-* (held out), d = L/16.
 *  - Topology-diversity panels (no fits): maze512 corridors 1 and 32, random512 density 40,
 *    and the Berlin/Boston/Paris 256 street maps.
 *
 * Outputs (results/benchmark/): movingai_raw.csv, movingai_fits.csv, movingai_panel.csv.
 * Dependency-free; deterministic (scenario order is file order, algorithms are deterministic).
 */
public final class MovingAIBenchmark {

    static final long IDA_CAP = 3_000_000L;
    static final double T975_1 = 12.7062;

    // ---------------------------------------------------------------- map/scen parsing
    static final class MapData {
        int h, w;
        int[] cost;        // per cell, Integer.MAX_VALUE when blocked
        boolean[] blocked;
        final Set<Character> terrains = new TreeSet<>();
    }

    static MapData loadMap(String path) throws IOException {
        MapData m = new MapData();
        List<String> lines = Files.readAllLines(Paths.get(path));
        int i = 0;
        for (; i < lines.size(); i++) {
            String t = lines.get(i).trim();
            if (t.equals("map")) { i++; break; }
            String[] tk = t.split("\\s+");
            if (tk.length == 2 && tk[0].equals("height")) m.h = Integer.parseInt(tk[1]);
            else if (tk.length == 2 && tk[0].equals("width")) m.w = Integer.parseInt(tk[1]);
        }
        m.cost = new int[m.h * m.w];
        m.blocked = new boolean[m.h * m.w];
        for (int r = 0; r < m.h; r++) {
            String row = lines.get(i + r);
            for (int c = 0; c < m.w && c < row.length(); c++) {
                char ch = row.charAt(c);
                m.terrains.add(ch);
                int id = r * m.w + c;
                switch (ch) {
                    case '@', 'O', 'X' -> { m.blocked[id] = true; m.cost[id] = Integer.MAX_VALUE; }
                    case '.', 'G' -> m.cost[id] = 1;
                    case 'S' -> m.cost[id] = 2;
                    case 'T' -> m.cost[id] = 5;
                    case 'W' -> m.cost[id] = 10;
                    default -> m.cost[id] = 1;
                }
            }
        }
        return m;
    }

    record Instance(int sx, int sy, int gx, int gy, double opt) {}

    /** Stratified sample: max instances spread evenly over the whole scenario file, so the
     *  start-goal distance mix is representative (scenario files are bucketed by path length). */
    static List<Instance> loadScen(String path, int max) throws IOException {
        List<Instance> all = new ArrayList<>();
        for (String line : Files.readAllLines(Paths.get(path))) {
            if (line.startsWith("version")) continue;
            String[] c = line.trim().split("\\s+");
            if (c.length < 9) continue;
            all.add(new Instance(Integer.parseInt(c[4]), Integer.parseInt(c[5]),
                    Integer.parseInt(c[6]), Integer.parseInt(c[7]), Double.parseDouble(c[8])));
        }
        List<Instance> out = new ArrayList<>();
        int n = all.size();
        for (int j = 0; j < max && j < n; j++) out.add(all.get((int) ((long) j * n / max)));
        return out;
    }

    // ---------------------------------------------------------------- searches (same counting rules as main artifact)
    record Result(long expansions, int cost, boolean solved, boolean capped) {}

    static int[] NB = {-1, 1, 0, 0, 0, 0, -1, 1}; // dr/dc pairs

    static List<int[]> nbrs(MapData m, int id) {
        List<int[]> out = new ArrayList<>(4);
        int r = id / m.w, c = id % m.w;
        int[][] d = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};
        for (int[] dd : d) {
            int nr = r + dd[0], nc = c + dd[1];
            if (nr < 0 || nc < 0 || nr >= m.h || nc >= m.w) continue;
            int nid = nr * m.w + nc;
            if (m.blocked[nid]) continue;
            out.add(new int[]{nid, m.cost[nid]});
        }
        return out;
    }

    static int manhattan(MapData m, int a, int b) {
        return Math.abs(a / m.w - b / m.w) + Math.abs(a % m.w - b % m.w);
    }

    static Result dijkstraLike(MapData m, int start, int goal, double wHeur, boolean greedy) {
        long exp = 0;
        double[] g = new double[m.h * m.w];
        Arrays.fill(g, Double.POSITIVE_INFINITY);
        g[start] = 0;
        boolean[] closed = new boolean[m.h * m.w];
        PriorityQueue<long[]> pq = new PriorityQueue<>((a, b) -> Double.compare(
                Double.longBitsToDouble(a[0]), Double.longBitsToDouble(b[0])));
        // pack: use double keys via wrapper to avoid bit-compare pitfalls
        PriorityQueue<double[]> pq2 = new PriorityQueue<>(Comparator.comparingDouble(a -> a[0]));
        pq2.add(new double[]{ (greedy ? 0 : 0) + wHeur * manhattan(m, start, goal), start });
        while (!pq2.isEmpty()) {
            double[] top = pq2.poll();
            int u = (int) top[1];
            if (closed[u]) continue;
            closed[u] = true;
            exp++;
            if (u == goal) return new Result(exp, (int) Math.round(g[u]), true, false);
            for (int[] nb : nbrs(m, u)) {
                if (closed[nb[0]]) continue;
                double ng = g[u] + nb[1];
                if (ng < g[nb[0]]) {
                    g[nb[0]] = ng;
                    double f = greedy ? wHeur * manhattan(m, nb[0], goal) : ng + wHeur * manhattan(m, nb[0], goal);
                    pq2.add(new double[]{f, nb[0]});
                }
            }
        }
        return new Result(exp, 0, false, false);
    }

    static Result idaStar(MapData m, int start, int goal) {
        long[] exp = {0};
        double threshold = manhattan(m, start, goal);
        List<Integer> path = new ArrayList<>();
        Set<Integer> onPath = new HashSet<>();
        path.add(start); onPath.add(start);
        boolean found = false, capped = false;
        int[] costOut = {0};
        for (int iter = 0; iter < 100_000 && !found; iter++) {
            double res = idaSearch(m, start, 0, threshold, goal, onPath, path, exp, costOut);
            if (exp[0] >= IDA_CAP) { capped = true; break; }
            if (res == Double.NEGATIVE_INFINITY) { found = true; break; }
            if (res == Double.POSITIVE_INFINITY) break;
            threshold = res;
        }
        return new Result(exp[0], costOut[0], found, capped);
    }

    static double idaSearch(MapData m, int cur, int gCur, double threshold, int goal,
                            Set<Integer> onPath, List<Integer> path, long[] exp, int[] costOut) {
        double f = gCur + manhattan(m, cur, goal);
        if (f > threshold) return f;
        exp[0]++;
        if (exp[0] >= IDA_CAP) return Double.POSITIVE_INFINITY;
        if (cur == goal) { costOut[0] = gCur; return Double.NEGATIVE_INFINITY; }
        double min = Double.POSITIVE_INFINITY;
        List<int[]> nbs = nbrs(m, cur);
        nbs.sort(Comparator.comparingDouble(nb -> (gCur + nb[1]) + manhattan(m, nb[0], goal)));
        for (int[] nb : nbs) {
            if (onPath.contains(nb[0])) continue;
            onPath.add(nb[0]); path.add(nb[0]);
            double res = idaSearch(m, nb[0], gCur + nb[1], threshold, goal, onPath, path, exp, costOut);
            if (res == Double.NEGATIVE_INFINITY) return res;
            onPath.remove(nb[0]); path.remove(path.size() - 1);
            if (res < min) min = res;
            if (exp[0] >= IDA_CAP) return Double.POSITIVE_INFINITY;
        }
        return min;
    }

    // ---------------------------------------------------------------- fitting (identical rules to main artifact)
    record Fit(double slope, double lo, double hi, double r2, double mape) {}

    static Fit fit(Map<Integer, Double> byD, int[] train, int[] test, boolean expForm) {
        List<Double> xs = new ArrayList<>(), ys = new ArrayList<>();
        for (int d : train) if (byD.containsKey(d)) {
            xs.add(expForm ? d * Math.log(4) : Math.log(d));
            ys.add(Math.log(byD.get(d)));
        }
        int n = xs.size();
        if (n < 2) return new Fit(Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN);
        double mx = 0, my = 0;
        for (int i = 0; i < n; i++) { mx += xs.get(i); my += ys.get(i); }
        mx /= n; my /= n;
        double sxx = 0, sxy = 0;
        for (int i = 0; i < n; i++) { sxx += (xs.get(i) - mx) * (xs.get(i) - mx); sxy += (xs.get(i) - mx) * (ys.get(i) - my); }
        double b = sxy / sxx, a = my - b * mx;
        double sse = 0, sst = 0;
        for (int i = 0; i < n; i++) { double e = ys.get(i) - (a + b * xs.get(i)); sse += e * e; sst += (ys.get(i) - my) * (ys.get(i) - my); }
        double r2 = sst > 0 ? 1 - sse / sst : Double.NaN;
        int dof = n - 2;
        double lo = Double.NaN, hi = Double.NaN;
        if (dof >= 1 && sxx > 0) {
            double se = Math.sqrt(sse / dof / sxx);
            lo = b - T975_1 * se; hi = b + T975_1 * se;
        }
        double ape = 0; int cnt = 0;
        for (int d : test) if (byD.containsKey(d)) {
            double x = expForm ? d * Math.log(4) : Math.log(d);
            double pred = Math.exp(a + b * x);
            ape += Math.abs(pred - byD.get(d)) / byD.get(d); cnt++;
        }
        return new Fit(b, lo, hi, r2, cnt > 0 ? ape / cnt * 100 : Double.NaN);
    }

    static String f(double v) { return Double.isNaN(v) ? "NaN" : String.format(Locale.US, "%.4f", v); }

    /** Instance-level bootstrap percentile CI of the log-scale slope (B=2000, fixed seed).
     *  Resamples the per-instance expansion counts at each training size with replacement. */
    static double[] bootCI(String key, int[] train, Map<String, List<Long>> instExp, boolean expForm) {
        List<List<Long>> perD = new ArrayList<>();
        for (int d : train) {
            List<Long> v = instExp.get(key + "|" + d);
            if (v == null || v.isEmpty()) return new double[]{Double.NaN, Double.NaN};
            perD.add(v);
        }
        int B = 2000;
        double[] slopes = new double[B];
        java.util.Random rng = new java.util.Random(20260906L);
        for (int b = 0; b < B; b++) {
            double sx = 0, sy = 0, sxx = 0, sxy = 0;
            for (int i = 0; i < train.length; i++) {
                List<Long> v = perD.get(i);
                double sum = 0;
                for (int j = 0; j < v.size(); j++) sum += v.get(rng.nextInt(v.size()));
                double mean = sum / v.size();
                double x = expForm ? train[i] * Math.log(4) : Math.log(train[i]);
                double y = Math.log(Math.max(1.0, mean));
                sx += x; sy += y; sxx += x * x; sxy += x * y;
            }
            int n = train.length;
            slopes[b] = (n * sxy - sx * sy) / (n * sxx - sx * sx);
        }
        Arrays.sort(slopes);
        return new double[]{slopes[(int) (0.025 * B)], slopes[(int) (0.975 * B) - 1]};
    }

    // ---------------------------------------------------------------- driver
    record Task(String family, String mapFile, String scenFile, int d, boolean ladder) {}

    public static void main(String[] args) throws IOException {
        String base = args.length > 0 ? args[0] : "benchmark_data";
        String out = args.length > 1 ? args[1] : "results/benchmark";
        Files.createDirectories(Paths.get(out));
        List<Task> tasks = new ArrayList<>();
        // empty ladder: train {1,2,4}, held-out {6}
        tasks.add(new Task("empty", base + "/mapf/empty-8-8.map", base + "/mapf/scen-random/empty-8-8-random-1.scen", 1, true));
        tasks.add(new Task("empty", base + "/mapf/empty-16-16.map", base + "/mapf/scen-random/empty-16-16-random-1.scen", 2, true));
        tasks.add(new Task("empty", base + "/mapf/empty-32-32.map", base + "/mapf/scen-random/empty-32-32-random-1.scen", 4, true));
        tasks.add(new Task("empty", base + "/mapf/empty-48-48.map", base + "/mapf/scen-random/empty-48-48-random-1.scen", 6, true));
        // random-10% ladder: train {2,4}, held-out {32}
        tasks.add(new Task("random10", base + "/mapf/random-32-32-10.map", base + "/mapf/scen-random/random-32-32-10-random-1.scen", 2, true));
        tasks.add(new Task("random10", base + "/mapf/random-64-64-10.map", base + "/mapf/scen-random/random-64-64-10-random-1.scen", 4, true));
        tasks.add(new Task("random10", base + "/random/random512-10-0.map", base + "/random/random512-10-0.map.scen", 32, true));
        // panels (no fits)
        tasks.add(new Task("panel-maze512-c1", base + "/maze/maze512-1-0.map", base + "/maze/maze512-1-0.map.scen", 0, false));
        tasks.add(new Task("panel-maze512-c32", base + "/maze/maze512-32-0.map", base + "/maze/maze512-32-0.map.scen", 0, false));
        tasks.add(new Task("panel-random512-40", base + "/random/random512-40-0.map", base + "/random/random512-40-0.map.scen", 0, false));
        tasks.add(new Task("panel-street-Berlin", base + "/mapf/Berlin_1_256.map", base + "/mapf/scen-random/Berlin_1_256-random-1.scen", 0, false));
        tasks.add(new Task("panel-street-Boston", base + "/mapf/Boston_0_256.map", base + "/mapf/scen-random/Boston_0_256-random-1.scen", 0, false));
        tasks.add(new Task("panel-street-Paris", base + "/mapf/Paris_1_256.map", base + "/mapf/scen-random/Paris_1_256-random-1.scen", 0, false));

        String[] algs = {"Dijkstra", "A*", "WA*(1.5)", "GBFS", "IDA*"};
        List<String> raw = new ArrayList<>();
        raw.add("family,map,d,algorithm,instance,expansions,cost,dijkstra_cost,scen_opt_octile,solved,capped");
        // family,alg,d -> list of mean-per-map values handled later; collect per (family,alg,d,instance)
        Map<String, List<double[]>> acc = new LinkedHashMap<>(); // fam|alg|d -> rows {expansions, solved, capped}
        Map<String, double[]> capFrac = new LinkedHashMap<>();   // fam|alg|d -> {capped, total}
        Map<String, List<Long>> instExp = new LinkedHashMap<>(); // fam|alg|d -> per-instance expansions (solved)
        Map<String, double[]> panel = new LinkedHashMap<>();    // panel|alg -> {sumExp, nExp, sumQ, nQ}
        for (Task t : tasks) {
            if (!Files.exists(Paths.get(t.mapFile)) || !Files.exists(Paths.get(t.scenFile))) {
                System.out.println("SKIP missing " + t.mapFile);
                continue;
            }
            MapData m = loadMap(t.mapFile);
            List<Instance> insts = loadScen(t.scenFile, 25);
            String mapName = Paths.get(t.mapFile).getFileName().toString();
            System.out.printf("%-24s terrains=%s instances=%d%n", mapName, m.terrains, insts.size());
            for (int ii = 0; ii < insts.size(); ii++) {
                Instance in = insts.get(ii);
                int s = in.sy() * m.w + in.sx(), g = in.gy() * m.w + in.gx();
                if (m.blocked[s] || m.blocked[g]) continue;
                Result rd = dijkstraLike(m, s, g, 0.0, false); // 4-connected reference optimum
                for (String alg : algs) {
                    Result r = switch (alg) {
                        case "Dijkstra" -> rd;
                        case "A*" -> dijkstraLike(m, s, g, 1.0, false);
                        case "WA*(1.5)" -> dijkstraLike(m, s, g, 1.5, false);
                        case "GBFS" -> dijkstraLike(m, s, g, 1.0, true);
                        default -> idaStar(m, s, g);
                    };
                    double q = (r.solved() && rd.solved() && r.cost() > 0)
                            ? Math.min(1.0, (double) rd.cost() / r.cost()) : Double.NaN;
                    raw.add(String.join(",", t.family(), mapName, String.valueOf(t.d()), alg,
                            String.valueOf(ii), String.valueOf(r.expansions()), String.valueOf(r.cost()),
                            String.valueOf(rd.cost()), String.format(Locale.US, "%.2f", in.opt()),
                            String.valueOf(r.solved()), String.valueOf(r.capped())));
                    if (t.ladder()) {
                        acc.computeIfAbsent(t.family() + "|" + alg + "|" + t.d(), k -> new ArrayList<>())
                           .add(new double[]{r.expansions(), r.solved() ? 1 : 0});
                        if (r.solved()) instExp.computeIfAbsent(t.family() + "|" + alg + "|" + t.d(),
                                k -> new ArrayList<>()).add(r.expansions());
                        double[] cf = capFrac.computeIfAbsent(t.family() + "|" + alg + "|" + t.d(), k -> new double[2]);
                        cf[0] += r.capped() ? 1 : 0; cf[1]++;
                    } else {
                        double[] p = panel.computeIfAbsent(t.family() + "|" + alg, k -> new double[4]);
                        p[0] += r.expansions(); p[1]++;
                        if (!Double.isNaN(q)) { p[2] += q; p[3]++; }
                    }
                }
            }
        }
        Files.write(Paths.get(out + "/movingai_raw.csv"), raw);

        // per (family,alg,d): mean expansions over solved instances
        Map<String, Map<Integer, Double>> means = new LinkedHashMap<>();
        for (var e : acc.entrySet()) {
            String[] k = e.getKey().split("\\|");
            double sx = 0, ns = 0;
            for (double[] row : e.getValue()) if (row[1] == 1) { sx += row[0]; ns++; }
            if (ns > 0) means.computeIfAbsent(k[0] + "|" + k[1], q -> new TreeMap<>())
                    .put(Integer.parseInt(k[2]), sx / ns);
        }
        List<String> fits = new ArrayList<>();
        fits.add("family,algorithm,form,slope,boot95_low,boot95_high,r2_train,mape_heldout");
        int[] trainE = {1, 2, 4}, testE = {6};
        int[] trainR = {2, 4}, testR = {32};
        for (var e : means.entrySet()) {
            String[] k = e.getKey().split("\\|");
            int[] tr = k[0].equals("empty") ? trainE : trainR;
            int[] te = k[0].equals("empty") ? testE : testR;
            boolean saturated = false;
            for (int d : tr) {
                double[] cf = capFrac.get(k[0] + "|" + k[1] + "|" + d);
                if (cf != null && cf[1] > 0 && cf[0] / cf[1] >= 0.5) saturated = true;
            }
            for (int d : te) {
                double[] cf = capFrac.get(k[0] + "|" + k[1] + "|" + d);
                if (cf != null && cf[1] > 0 && cf[0] / cf[1] >= 0.5) saturated = true;
            }
            if (saturated) {
                fits.add(String.join(",", k[0], k[1], "excluded_saturated", "NaN", "NaN", "NaN", "NaN", "NaN"));
                continue;
            }
            Fit fe = fit(e.getValue(), tr, te, true);
            Fit fp = fit(e.getValue(), tr, te, false);
            double[] be = bootCI(k[0] + "|" + k[1], tr, instExp, true);
            double[] bp = bootCI(k[0] + "|" + k[1], tr, instExp, false);
            fits.add(String.join(",", k[0], k[1], "exp", f(fe.slope()), f(be[0]), f(be[1]), f(fe.r2()), f(fe.mape())));
            fits.add(String.join(",", k[0], k[1], "pow", f(fp.slope()), f(bp[0]), f(bp[1]), f(fp.r2()), f(fp.mape())));
        }
        Files.write(Paths.get(out + "/movingai_fits.csv"), fits);

        List<String> pan = new ArrayList<>();
        pan.add("panel,algorithm,mean_expansions,mean_q");
        for (var e : panel.entrySet()) {
            double[] p = e.getValue();
            pan.add(String.join(",", e.getKey().split("\\|")[0], e.getKey().split("\\|")[1],
                    f(p[0] / Math.max(1, p[1])), p[3] > 0 ? f(p[2] / p[3]) : "NaN"));
        }
        Files.write(Paths.get(out + "/movingai_panel.csv"), pan);
        System.out.println("Wrote " + out + "/movingai_raw.csv, movingai_fits.csv, movingai_panel.csv");
        for (String line : fits) System.out.println("  " + line);
    }
}
