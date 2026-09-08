
import java.io.BufferedWriter; 
import java.io.FileWriter; 
import java.io.IOException; 
import java.nio.file.Files; 
import java.nio.file.Path; 
import java.nio.file.Paths; 
import java.util.*; 
 
/** 
 * CrossDomainSearchExperiment 
 * ============================ 
 * Monolithic, single-file, dependency-free implementation of the cross-domain 
 * search study described in "Regime Structure in Tree Search: Theory and  * Experimental Cross-Domain Behavioural Analysis". 
 * 
 * The ENTIRE pipeline — raw trial generation, summarization, log-linear model 
 * fitting on d in {4,6,8}, held-out MAPE evaluation on d in {10,12}, and final 
 * table generation — is implemented in this one Java file and communicates 
 * exclusively through CSV files. No Python step is required. 
 * 
 * Compile & run: 
 *   javac CrossDomainSearchExperiment.java 
 *   java  CrossDomainSearchExperiment --config config/experiment.yaml 
 * 
 * Optional flags: 
 *   --skip-tests      Skip the built-in validation-test suite before running. 
 *   --config <path>   Path to a YAML config file (documented defaults are 
 *                     used verbatim if the file is absent). 
 * 
 * Produced artifacts: 
 *   data/raw/grid.csv 
 *   data/raw/game_tree.csv 
 *   data/raw/mapf.csv 
 *   data/raw/pattern.csv 
 *   data/processed/summary.csv 
 *   results/tables/table1_algorithms.csv 
 *   results/tables/table2_grid.csv 
 *   results/tables/table3_game_tree.csv 
 *   results/tables/table4_mapf.csv 
 *   results/tables/table5_pattern.csv 
 *   results/tables/table6_heldout_beta.csv 
 *   results/tables/table7_bei.csv 
 *   results/tables/table8_powerlaw.csv   (power-law model-form comparison)
 */ 
public class CrossDomainSearchExperiment { 
 
    // ============================================================ 
    // GLOBAL, DOCUMENTED CONSTANTS 
    // ============================================================ 
 
    /** Seed rule: s = kappa * depth + trial, trial in [1, trials] (documented, 1-indexed). */ 
    static final long GRID_KAPPA = 100L; 
    static final long GAME_KAPPA = 200L; 
    static final long MAPF_KAPPA = 300L; 
    static final long PATTERN_KAPPA = 400L; 
 
    static final int[] DEPTHS = {4, 6, 8, 10, 12}; 
    static final int[] TRAIN_DEPTHS = {4, 6, 8}; 
    static final int[] TEST_DEPTHS = {10, 12}; 
 
    static final int[] MAPF_AGENT_COUNTS = {2, 2, 3, 3, 4}; 
 
    /** Documented model base: branching factor for grid/game/mapf and alphabet size for 
     *  pattern matching both equal 4 in this study's configuration, so a single value 
     *  is used for log N(d) = log(alpha) + beta * d * log(b). */ 
    static final double MODEL_BASE_B = 4.0; 
 
    public static void main(String[] args) throws Exception { 
        String configPath = "config/experiment.yaml"; 
        boolean skipTests = false; 
        for (int i = 0; i < args.length; i++) { 
            if ("--config".equals(args[i]) && i + 1 < args.length) configPath = args[++i]; 
            if ("--skip-tests".equals(args[i])) skipTests = true; 
        } 
 
        if (!skipTests) { 
            System.out.println("=".repeat(72)); 
            System.out.println(" RUNNING BUILT-IN VALIDATION TESTS"); 
            System.out.println("=".repeat(72)); 
            boolean ok = ValidationTests.runAll(); 
            if (!ok) { 
                System.err.println("VALIDATION TESTS FAILED — aborting experiment run."); 
                System.exit(1); 
            } 
            System.out.println("All validation tests passed.\n"); 
        } 
 
        ExperimentConfig cfg = ExperimentConfig.load(configPath); 
 
        System.out.println("=".repeat(72)); 
        System.out.println(" CROSS-DOMAIN SEARCH EXPERIMENT — RAW DATA GENERATION"); 
        System.out.println("=".repeat(72)); 
        System.out.printf(" trials=%d | training_depths=%s | test_depths=%s%n", 
                cfg.trials, Arrays.toString(cfg.trainingDepths), Arrays.toString(cfg.testDepths)); 
 
        ExperimentRunner runner = new ExperimentRunner(cfg); 
        runner.runAll(); 
 
        System.out.println(); 
        System.out.println("Raw trial-level CSV files written to data/raw/:"); 
        System.out.println("  data/raw/grid.csv"); 
        System.out.println("  data/raw/game_tree.csv"); 
        System.out.println("  data/raw/mapf.csv"); 
        System.out.println("  data/raw/pattern.csv"); 
 
        System.out.println(); 
        System.out.println("=".repeat(72)); 
        System.out.println(" ANALYSIS PIPELINE (pure Java, CSV in -> CSV out)"); 
        System.out.println("=".repeat(72)); 
        try { 
            List<SummaryRow> summary = new ArrayList<>(); 
            summary.addAll(ResultsAggregator.aggregateDomain( 
                    "data/raw/grid.csv", "Grid", "node_expansions", "path_length", cfg.trials)); 
            summary.addAll(ResultsAggregator.aggregateDomain( 
                    "data/raw/game_tree.csv", "GameTree", "node_expansions", "solution_length", 
cfg.trials)); 
            summary.addAll(ResultsAggregator.aggregateDomain( 
                    "data/raw/mapf.csv", "MAPF", "node_expansions", "path_length", cfg.trials)); 
            summary.addAll(ResultsAggregator.aggregateDomain( 
                    "data/raw/pattern.csv", "PatternMatch", "comparisons", "pattern_length", 
cfg.trials)); 
 
            ResultsAggregator.augmentGridQuality(summary, "data/raw/grid.csv");
            ResultsAggregator.augmentGameRetained(summary, "data/raw/game_tree.csv");
            ResultsAggregator.writeSummary("data/processed/summary.csv", summary); 
            System.out.println("Wrote data/processed/summary.csv (" + summary.size() + " rows)"); 
 
            List<ModelFitter.FitRow> fits = ModelFitter.fitAll(summary, cfg); 
            ModelFitter.writeFits("results/tables/table6_heldout_beta.csv", fits); 
            System.out.println("Wrote results/tables/table6_heldout_beta.csv (" + fits.size() + " rows)");

            List<PowerLawFitter.FitRow> powFits = PowerLawFitter.fitAll(summary, cfg);
            PowerLawFitter.writeFits("results/tables/table8_powerlaw.csv", powFits);
            System.out.println("Wrote results/tables/table8_powerlaw.csv (" + powFits.size() + " rows)"); 
 
            TableGenerator.generateAll(summary, fits); 
            System.out.println("Wrote results/tables/table1_algorithms.csv"); 
            System.out.println("Wrote results/tables/table2_grid.csv"); 
            System.out.println("Wrote results/tables/table3_game_tree.csv"); 
            System.out.println("Wrote results/tables/table4_mapf.csv"); 
            System.out.println("Wrote results/tables/table5_pattern.csv"); 
            System.out.println("Wrote results/tables/table7_bei.csv"); 
        } catch (IllegalStateException consistencyFailure) { 
            System.err.println(); 
            System.err.println("CONSISTENCY CHECK FAILED: " + consistencyFailure.getMessage()); 
            System.err.println("Tables were NOT regenerated. Fix the raw data or configuration and re-run."); 
            System.exit(1); 
        } 
 
        System.out.println(); 
    } 
 
    // ============================================================ 
    // METRICS — a single, unambiguous counter object 
    // ============================================================ 
 
    /** 
     * Naming convention: a field is never called "nodes" because that word is 
     * ambiguous between "generated" and "expanded". Each domain's counting 
     * rule is documented at the call site: 
     * 
     *   Grid          -> incrementExpansions(): a state popped from the open 
     *                     list and processed. The start state IS counted. 
     *   Game tree     -> incrementExpansions(): a tree node evaluated/visited. 
     *                     The root node IS counted. 
     *   MAPF          -> incrementExpansions(): a space-time state popped from 
     *                     an individual agent's open list, summed over agents. 
     *                     Each agent's start state IS counted. 
     *   Pattern match -> incrementComparisons(): exactly one character-vs- 
     *                     character equality test. 
     */ 
    static final class SearchMetrics { 
        private long expansions = 0; 
        private long comparisons = 0; 
        private long generated = 0; 
        private long reopened = 0; 
        private long retainedSum = 0; 
        private long internalNodes = 0; 

        /** Records how many candidate children were actually evaluated (retained)
         *  at one internal node; used to measure mean retained branching. */
        void addRetained(int k) { retainedSum += k; internalNodes++; } 
 
        void incrementExpansions() { expansions++; } 
        void incrementComparisons() { comparisons++; } 
        void incrementGenerated() { generated++; } 
        void incrementReopened() { reopened++; } 
 
        long getExpansions() { return expansions; } 
        long getComparisons() { return comparisons; } 
        long getGenerated() { return generated; } 
        long getReopened() { return reopened; } 
        long getRetainedSum() { return retainedSum; } 
        long getInternalNodes() { return internalNodes; } 
    } 
 
    // ============================================================ 
    // CONFIG — minimal built-in YAML reader (no external dependency) 
    // ============================================================ 
 
    static final class ExperimentConfig { 
        int trials = 30; 
        int[] trainingDepths = {4, 6, 8}; 
        int[] testDepths = {10, 12}; 
 
        int gridSideMultiplier = 4; 
        double gridObstacleRate = 0.15; 
        int gridMinWeight = 1; 
        int gridMaxWeight = 10; 
 
        int gameBranchingFactor = 4; 
        int gameDepthCap = 10; 
        int gameLeafMin = 0; 
        int gameLeafMax = 100; 
 
        double mapfObstacleRate = 0.10; 
        int mapfTimeHorizon = 60; 
 
        String patternAlphabet = "ACGT"; 
        int patternMultiplier = 3; 
        int textMultiplier = 300; 
 
        static ExperimentConfig defaultConfig() { return new ExperimentConfig(); } 
 
        static ExperimentConfig load(String path) { 
            ExperimentConfig cfg = defaultConfig(); 
            Path p = Paths.get(path); 
            if (!Files.exists(p)) { 
                System.out.println("Config file not found at " + path + " — using documented defaults."); 
                return cfg; 
            } 
            try { 
                List<String> lines = Files.readAllLines(p); 
                String section = null; 
                for (String raw : lines) { 
                    String noComment = raw.replaceAll("#.*$", ""); 
                    if (noComment.isBlank()) continue; 
                    int indent = noComment.length() - noComment.stripLeading().length(); 
                    String line = noComment.strip(); 
 
                    if (indent == 0 && line.endsWith(":") && !line.contains(" ")) { 
                        section = line.substring(0, line.length() - 1).trim(); 
                        continue; 
                    } 
                    int colon = line.indexOf(':'); 
                    if (colon < 0) continue; 
                    String key = line.substring(0, colon).trim(); 
                    String value = line.substring(colon + 1).trim(); 
                    if (indent == 0) { 
                        applyTopLevel(cfg, key, value); 
                        section = null; 
                    } else if (section != null) { 
                        applyNested(cfg, section, key, value); 
                    } 
                } 
                System.out.println("Loaded configuration from " + path); 
            } catch (IOException e) { 
                System.out.println("Failed to read " + path + " (" + e.getMessage() + ") — using defaults."); 
            } 
            return cfg; 
        } 
 
        private static int[] parseIntArray(String value) { 
            String cleaned = value.replaceAll("[\\[\\]\"]", "").trim();             if (cleaned.isEmpty()) return new int[0];             String[] parts = cleaned.split(",");             int[] out = new int[parts.length];             for (int i = 0; i < parts.length; i++) out[i] = Integer.parseInt(parts[i].trim());             return out;         } 
 
        private static String stripQuotes(String s) { 
            return s.replaceAll("^\"|\"$", ""); 
        } 
 
        private static void applyTopLevel(ExperimentConfig cfg, String key, String value) { 
            switch (key) { 
                case "trials" -> cfg.trials = Integer.parseInt(value.trim()); 
                case "training_depths" -> cfg.trainingDepths = parseIntArray(value); 
                case "test_depths" -> cfg.testDepths = parseIntArray(value); 
                default -> { /* ignore unknown top-level keys */ } 
            } 
        } 
 
        private static void applyNested(ExperimentConfig cfg, String section, String key, String 
value) { 
            switch (section) { 
                case "grid" -> { 
                    switch (key) { 
                        case "side_multiplier" -> cfg.gridSideMultiplier = Integer.parseInt(value); 
                        case "obstacle_rate" -> cfg.gridObstacleRate = Double.parseDouble(value); 
                        case "edge_weights" -> { 
                            int[] ew = parseIntArray(value); 
                            if (ew.length == 2) { cfg.gridMinWeight = ew[0]; cfg.gridMaxWeight = ew[1]; } 
                        } 
                        default -> {} 
                    } 
                } 
                case "game_tree" -> { 
                    switch (key) { 
                        case "branching_factor" -> cfg.gameBranchingFactor = Integer.parseInt(value); 
                        case "depth_cap" -> cfg.gameDepthCap = Integer.parseInt(value); 
                        case "leaf_value_range" -> { 
                            int[] lv = parseIntArray(value); 
                            if (lv.length == 2) { cfg.gameLeafMin = lv[0]; cfg.gameLeafMax = lv[1]; } 
                        } 
                        default -> {} 
                    } 
                } 
                case "mapf" -> { 
                    switch (key) { 
                        case "obstacle_rate" -> cfg.mapfObstacleRate = Double.parseDouble(value); 
                        case "time_horizon" -> cfg.mapfTimeHorizon = Integer.parseInt(value); 
                        default -> {} 
                    } 
                } 
                case "pattern" -> { 
                    switch (key) { 
                        case "alphabet" -> cfg.patternAlphabet = stripQuotes(value); 
                        case "pattern_multiplier" -> cfg.patternMultiplier = Integer.parseInt(value); 
                        case "text_multiplier" -> cfg.textMultiplier = Integer.parseInt(value); 
                        default -> {} 
                    } 
                } 
                default -> {} 
            } 
        } 
    } 
 
    // ============================================================ 
    // DOMAIN 1: GRID GRAPH 
    // ============================================================ 
 
    static final class GridGraph { 
        final int rows, cols; 
        final int[][] weight; 
        final boolean[][] blocked; 
        final int startRow, startCol, goalRow, goalCol; 
 
        static final int[] DR = {-1, 1, 0, 0}; 
        static final int[] DC = {0, 0, -1, 1}; 
 
        GridGraph(int size, double obstacleRate, int minWeight, int maxWeight, long seed) { 
            this.rows = size; 
            this.cols = size; 
            this.weight = new int[rows][cols]; 
            this.blocked = new boolean[rows][cols]; 
            this.startRow = 0; 
            this.startCol = 0; 
            this.goalRow = rows - 1; 
            this.goalCol = cols - 1; 
 
            Random rng = new Random(seed); 
            int span = maxWeight - minWeight + 1; 
            for (int r = 0; r < rows; r++) 
                for (int c = 0; c < cols; c++) 
                    weight[r][c] = minWeight + rng.nextInt(span); 
 
            for (int r = 0; r < rows; r++) { 
                for (int c = 0; c < cols; c++) { 
                    if (r == startRow && c == startCol) continue; 
                    if (r == goalRow && c == goalCol) continue; 
                    if (rng.nextDouble() < obstacleRate) blocked[r][c] = true; 
                } 
            } 
            for (int c = 0; c < cols; c++) blocked[0][c] = false; 
            for (int r = 0; r < rows; r++) blocked[r][cols - 1] = false; 
        } 
 
        boolean valid(int r, int c) { 
            return r >= 0 && r < rows && c >= 0 && c < cols && !blocked[r][c]; 
        } 
 
        int id(int r, int c) { return r * cols + c; } 
        int row(int id) { return id / cols; } 
        int col(int id) { return id % cols; } 
        int totalCells() { return rows * cols; } 
        int manhattan(int r, int c) { return Math.abs(r - goalRow) + Math.abs(c - goalCol); } 
        int manhattan(int id) { return manhattan(row(id), col(id)); } 
 
        List<int[]> neighbors(int id) { 
            int r = row(id), c = col(id); 
            List<int[]> out = new ArrayList<>(4); 
            for (int d = 0; d < 4; d++) { 
                int nr = r + DR[d], nc = c + DC[d]; 
                if (valid(nr, nc)) out.add(new int[]{id(nr, nc), weight[nr][nc]}); 
            } 
            return out; 
        } 
    } 
 
    static final class GridSearchAlgorithms { 
 
        static final long IDA_NODE_CAP = 3_000_000L; 
 
        static final class Result { 
            final long expansions; 
            final int pathLength; 
            final boolean solved; 
            final boolean capped; 
            final int cost; 
            Result(long e, int p, boolean s, boolean capped, int cost) { 
                expansions = e; pathLength = p; solved = s; this.capped = capped; this.cost = cost; 
            } 
        } 
 
        static Result bfs(GridGraph g) { 
            SearchMetrics m = new SearchMetrics(); 
            int start = g.id(g.startRow, g.startCol); 
            int goal = g.id(g.goalRow, g.goalCol); 
            Map<Integer, Integer> parent = new HashMap<>(); 
            Deque<Integer> queue = new ArrayDeque<>(); 
            parent.put(start, -1); 
            queue.add(start); 
            boolean found = false; 
            while (!queue.isEmpty()) { 
                int u = queue.poll(); 
                m.incrementExpansions(); 
                if (u == goal) { found = true; break; } 
                for (int[] nb : g.neighbors(u)) { 
                    if (!parent.containsKey(nb[0])) { 
                        parent.put(nb[0], u); 
                        queue.add(nb[0]); 
                    } 
                } 
            } 
            int len = found ? pathLength(parent, start, goal) : 0; 
            int cost = found ? pathCost(parent, start, goal, g) : 0; 
            return new Result(m.getExpansions(), len, found, false, cost); 
        } 
 
        static Result dijkstra(GridGraph g) { 
            SearchMetrics m = new SearchMetrics(); 
            int start = g.id(g.startRow, g.startCol); 
            int goal = g.id(g.goalRow, g.goalCol); 
            int n = g.totalCells(); 
            double[] dist = new double[n]; 
            int[] parent = new int[n]; 
            Arrays.fill(dist, Double.POSITIVE_INFINITY); 
            Arrays.fill(parent, -1); 
            dist[start] = 0; 
            boolean[] closed = new boolean[n]; 
            PriorityQueue<double[]> open = new PriorityQueue<>(Comparator.comparingDouble(a -> a[0])); 
            open.add(new double[]{0, start}); 
            boolean found = false; 
            while (!open.isEmpty()) { 
                double[] cur = open.poll(); 
                int u = (int) cur[1]; 
                if (closed[u]) continue; 
                closed[u] = true; 
                m.incrementExpansions(); 
                if (u == goal) { found = true; break; } 
                for (int[] nb : g.neighbors(u)) { 
                    if (closed[nb[0]]) continue; 
                    double nd = dist[u] + nb[1]; 
                    if (nd < dist[nb[0]]) { 
                        dist[nb[0]] = nd; 
                        parent[nb[0]] = u; 
                        open.add(new double[]{nd, nb[0]}); 
                    } 
                } 
            } 
            int len = found ? pathLengthArr(parent, start, goal) : 0; 
            int cost = found ? pathCostArr(parent, start, goal, g) : 0; 
            return new Result(m.getExpansions(), len, found, false, cost); 
        } 
 
        static Result aStar(GridGraph g, double weight) { 
            SearchMetrics m = new SearchMetrics(); 
            int start = g.id(g.startRow, g.startCol); 
            int goal = g.id(g.goalRow, g.goalCol); 
            int n = g.totalCells(); 
            double[] gCost = new double[n]; 
            int[] parent = new int[n]; 
            Arrays.fill(gCost, Double.POSITIVE_INFINITY); 
            Arrays.fill(parent, -1); 
            gCost[start] = 0; 
            boolean[] closed = new boolean[n]; 
            PriorityQueue<double[]> open = new PriorityQueue<>(Comparator.comparingDouble(a -> a[0])); 
            open.add(new double[]{weight * g.manhattan(start), start}); 
            boolean found = false; 
            while (!open.isEmpty()) { 
                double[] cur = open.poll(); 
                int u = (int) cur[1]; 
                if (closed[u]) continue; 
                closed[u] = true; 
                m.incrementExpansions(); 
                if (u == goal) { found = true; break; } 
                for (int[] nb : g.neighbors(u)) { 
                    if (closed[nb[0]]) continue; 
                    double ng = gCost[u] + nb[1]; 
                    if (ng < gCost[nb[0]]) { 
                        gCost[nb[0]] = ng; 
                        parent[nb[0]] = u; 
                        open.add(new double[]{ng + weight * g.manhattan(nb[0]), nb[0]}); 
                    } 
                } 
            } 
            int len = found ? pathLengthArr(parent, start, goal) : 0; 
            int cost = found ? pathCostArr(parent, start, goal, g) : 0; 
            return new Result(m.getExpansions(), len, found, false, cost); 
        } 
 
        static Result greedyBestFirst(GridGraph g) { 
            SearchMetrics m = new SearchMetrics(); 
            int start = g.id(g.startRow, g.startCol); 
            int goal = g.id(g.goalRow, g.goalCol); 
            int n = g.totalCells(); 
            int[] parent = new int[n]; 
            Arrays.fill(parent, -1); 
            boolean[] visited = new boolean[n]; 
            PriorityQueue<int[]> open = new PriorityQueue<>(Comparator.comparingInt(a -> a[0])); 
            open.add(new int[]{g.manhattan(start), start}); 
            boolean found = false; 
            while (!open.isEmpty()) { 
                int[] cur = open.poll(); 
                int u = cur[1]; 
                if (visited[u]) continue; 
                visited[u] = true; 
                m.incrementExpansions(); 
                if (u == goal) { found = true; break; } 
                for (int[] nb : g.neighbors(u)) { 
                    if (!visited[nb[0]]) { 
                        if (parent[nb[0]] == -1) parent[nb[0]] = u; 
                        open.add(new int[]{g.manhattan(nb[0]), nb[0]}); 
                    } 
                } 
            } 
            int len = found ? pathLengthArr(parent, start, goal) : 0; 
            int cost = found ? pathCostArr(parent, start, goal, g) : 0; 
            return new Result(m.getExpansions(), len, found, false, cost); 
        } 
 
        static Result idaStar(GridGraph g) { 
            SearchMetrics m = new SearchMetrics(); 
            int start = g.id(g.startRow, g.startCol); 
            int goal = g.id(g.goalRow, g.goalCol); 
            double threshold = g.manhattan(start); 
            List<Integer> path = new ArrayList<>(); 
            Set<Integer> onPath = new HashSet<>(); 
            path.add(start); 
            onPath.add(start); 
            boolean found = false; 
            boolean capped = false; 
            for (int iter = 0; iter < 100_000 && !found; iter++) { 
                double res = idaSearch(g, start, 0, threshold, goal, onPath, path, m); 
                if (m.getExpansions() >= IDA_NODE_CAP) { capped = true; break; } 
                if (res == Double.NEGATIVE_INFINITY) { found = true; break; } 
                if (res == Double.POSITIVE_INFINITY) break; 
                threshold = res; 
            } 
            int len = found ? path.size() : 0; 
            int cost = 0;
            if (found) for (int i = 1; i < path.size(); i++) { int id = path.get(i); cost += g.weight[id / g.cols][id % g.cols]; }
            return new Result(m.getExpansions(), len, found, capped, cost); 
        } 
 
        private static double idaSearch(GridGraph g, int cur, double gCur, double threshold, int goal, 
                                         Set<Integer> onPath, List<Integer> path, SearchMetrics m) { 
            double f = gCur + g.manhattan(cur); 
            if (f > threshold) return f; 
            m.incrementExpansions(); 
            if (m.getExpansions() >= IDA_NODE_CAP) return Double.POSITIVE_INFINITY; 
            if (cur == goal) return Double.NEGATIVE_INFINITY; 
            double min = Double.POSITIVE_INFINITY; 
            List<int[]> nbrs = g.neighbors(cur); 
            nbrs.sort(Comparator.comparingDouble(nb -> (gCur + nb[1]) + g.manhattan(nb[0]))); 
            for (int[] nb : nbrs) { 
                if (onPath.contains(nb[0])) continue; 
                onPath.add(nb[0]); 
                path.add(nb[0]); 
                double res = idaSearch(g, nb[0], gCur + nb[1], threshold, goal, onPath, path, m); 
                if (res == Double.NEGATIVE_INFINITY) return res; 
                onPath.remove(nb[0]); 
                path.remove(path.size() - 1); 
                if (res < min) min = res; 
                if (m.getExpansions() >= IDA_NODE_CAP) return Double.POSITIVE_INFINITY; 
            } 
            return min; 
        } 
 
        static int pathLength(Map<Integer, Integer> parent, int start, int goal) { 
            if (goal != start && !parent.containsKey(goal)) return 0; 
            int len = 1, cur = goal; 
            Set<Integer> seen = new HashSet<>(); 
            while (cur != start) { 
                if (!seen.add(cur)) return 0; 
                Integer p = parent.get(cur); 
                if (p == null) return 0; 
                cur = p; len++; 
            } 
            return len; 
        } 
 
        /** Cost of the reconstructed path: sum of entry weights of all cells except the start. */
        static int pathCost(Map<Integer, Integer> parent, int start, int goal, GridGraph g) {
            if (goal == start || !parent.containsKey(goal)) return 0;
            int cost = 0, cur = goal;
            while (cur != start) { cost += g.weight[cur / g.cols][cur % g.cols]; cur = parent.get(cur); }
            return cost;
        }

        static int pathCostArr(int[] parent, int start, int goal, GridGraph g) {
            if (goal == start || parent[goal] == -1) return 0;
            int cost = 0, cur = goal;
            while (cur != start) { cost += g.weight[cur / g.cols][cur % g.cols]; cur = parent[cur]; }
            return cost;
        }

        static int pathLengthArr(int[] parent, int start, int goal) { 
            if (goal != start && parent[goal] == -1) return 0; 
            int len = 1, cur = goal; 
            Set<Integer> seen = new HashSet<>(); 
            while (cur != start) { 
                if (!seen.add(cur)) return 0; 
                if (parent[cur] < 0) return 0; 
                cur = parent[cur]; len++; 
            } 
            return len; 
        } 
    } 
 
    // ============================================================ 
    // DOMAIN 2: GAME TREE (MINIMAX / ALPHA-BETA) 
    // ============================================================ 
 
    static final class GameTree { 
        final int branching; 
        final int nominalDepth; 
        final int effectiveDepth; 
        final int[] values; 
        final int firstLeafIndex; 
        final int size; 
 
        GameTree(int branching, int nominalDepth, int depthCap, int leafMin, int leafMax, long 
seed) { 
            this.branching = branching; 
            this.nominalDepth = nominalDepth; 
            this.effectiveDepth = Math.min(nominalDepth, depthCap); 
            this.size = computeSize(branching, effectiveDepth); 
            this.firstLeafIndex = computeSize(branching, effectiveDepth - 1); 
            this.values = new int[size]; 
            Random rng = new Random(seed); 
            int span = leafMax - leafMin + 1; 
            for (int i = firstLeafIndex; i < size; i++) { 
                values[i] = leafMin + rng.nextInt(span); 
            } 
        } 
 
        static int computeSize(int b, int d) { 
            if (d < 0) return 0; 
            long total = 1, level = 1; 
            for (int i = 0; i < d; i++) { 
                level *= b; 
                total += level; 
                if (total > Integer.MAX_VALUE) { 
                    throw new IllegalStateException( 
                        "GameTree.computeSize overflow: b=" + b + ", d=" + d 
                        + " exceeds Integer.MAX_VALUE; reduce branching_factor or depth_cap."); 
                } 
            } 
            return (int) total; 
        } 
 
        int[] children(int node) { 
            int[] ch = new int[branching]; 
            for (int i = 0; i < branching; i++) { 
                ch[i] = Math.min(branching * node + 1 + i, size - 1); 
            } 
            return ch; 
        } 
 
        boolean isLeaf(int node) { return node >= firstLeafIndex; } 
        boolean isDepthCapped() { return effectiveDepth < nominalDepth; } 
    } 
 
    static final class GameSearchAlgorithms { 
 
        static final class Result { 
            final long expansions; 
            final int value; 
            final long retainedSum; 
            final long internalNodes; 
            Result(long e, int v, long rs, long in) { expansions = e; value = v; retainedSum = rs; internalNodes = in; } 
        } 
 
        static Result miniMax(GameTree gt) { 
            SearchMetrics m = new SearchMetrics(); 
            int v = minimax(gt, 0, true, 0, m); 
            return new Result(m.getExpansions(), v, m.getRetainedSum(), m.getInternalNodes()); 
        } 
 
        private static int minimax(GameTree gt, int node, boolean maximizing, int depth, 
SearchMetrics m) { 
            m.incrementExpansions(); 
            if (gt.isLeaf(node) || depth >= gt.effectiveDepth) { 
                return gt.values[Math.min(node, gt.values.length - 1)]; 
            } 
            int[] children = gt.children(node); 
            m.addRetained(children.length); 
            if (maximizing) { 
                int best = Integer.MIN_VALUE; 
                for (int c : children) best = Math.max(best, minimax(gt, c, false, depth + 1, m)); 
                return best; 
            } else { 
                int best = Integer.MAX_VALUE; 
                for (int c : children) best = Math.min(best, minimax(gt, c, true, depth + 1, m)); 
                return best; 
            } 
        } 
 
        static Result alphaBeta(GameTree gt) { 
            SearchMetrics m = new SearchMetrics(); 
            int v = alphaBeta(gt, 0, Integer.MIN_VALUE, Integer.MAX_VALUE, true, 0, m); 
            return new Result(m.getExpansions(), v, m.getRetainedSum(), m.getInternalNodes()); 
        } 
 
        private static int alphaBeta(GameTree gt, int node, int alpha, int beta, boolean maximizing, 
                                      int depth, SearchMetrics m) { 
            m.incrementExpansions(); 
            if (gt.isLeaf(node) || depth >= gt.effectiveDepth) { 
                return gt.values[Math.min(node, gt.values.length - 1)]; 
            } 
            int[] children = gt.children(node); 
            orderChildren(gt, children, !maximizing); 
            int evaluated = 0; 
            if (maximizing) { 
                int best = Integer.MIN_VALUE; 
                for (int c : children) { 
                    evaluated++; 
                    best = Math.max(best, alphaBeta(gt, c, alpha, beta, false, depth + 1, m)); 
                    alpha = Math.max(alpha, best); 
                    if (beta <= alpha) break; 
                } 
                return best; 
            } else { 
                int best = Integer.MAX_VALUE; 
                for (int c : children) { 
                    evaluated++; 
                    best = Math.min(best, alphaBeta(gt, c, alpha, beta, true, depth + 1, m)); 
                    beta = Math.min(beta, best); 
                    if (beta <= alpha) break; 
                } 
                m.addRetained(evaluated); 
                return best; 
            } 
        } 
 
        private static void orderChildren(GameTree gt, int[] children, boolean ascending) { 
            Integer[] box = new Integer[children.length]; 
            for (int i = 0; i < children.length; i++) box[i] = children[i]; 
            Arrays.sort(box, (a, b) -> { 
                int va = gt.values[Math.min(a, gt.values.length - 1)]; 
                int vb = gt.values[Math.min(b, gt.values.length - 1)]; 
                return ascending ? Integer.compare(va, vb) : Integer.compare(vb, va); 
            }); 
            for (int i = 0; i < children.length; i++) children[i] = box[i]; 
        } 
    } 
 
    // ============================================================ 
    // DOMAIN 3: MAPF — PRIORITISED PLANNING WITH SPACE-TIME A* 
    // ============================================================ 
 
    static final class MAPFGrid { 
        final int rows, cols; 
        final boolean[][] blocked; 
 
        static final int[] DR = {-1, 1, 0, 0, 0}; 
        static final int[] DC = {0, 0, -1, 1, 0}; 
 
        MAPFGrid(int size, double obstacleRate, long seed) { 
            this.rows = size; 
            this.cols = size; 
            this.blocked = new boolean[rows][cols]; 
            Random rng = new Random(seed); 
            for (int r = 0; r < rows; r++) 
                for (int c = 0; c < cols; c++) 
                    if (rng.nextDouble() < obstacleRate) blocked[r][c] = true; 
 
            clear(0, 0); clear(0, cols - 1); clear(rows - 1, 0); clear(rows - 1, cols - 1); 
            clear(0, cols / 2); clear(rows - 1, cols / 2); clear(rows / 2, 0); 
            clear(rows / 2, cols - 1); clear(rows / 2, cols / 2); 
        } 
 
        private void clear(int r, int c) { 
            if (r >= 0 && r < rows && c >= 0 && c < cols) blocked[r][c] = false; 
        } 
 
        boolean valid(int r, int c) { 
            return r >= 0 && r < rows && c >= 0 && c < cols && !blocked[r][c]; 
        } 
 
        int id(int r, int c) { return r * cols + c; } 
        int row(int id) { return id / cols; } 
        int col(int id) { return id % cols; } 
        int manhattan(int a, int b) { return Math.abs(row(a) - row(b)) + Math.abs(col(a) - col(b)); } 
    } 
 
    static final class PrioritizedPlanning { 
 
        static final class AgentResult { 
            final long expansions; 
            final List<Integer> path; 
            AgentResult(long e, List<Integer> p) { expansions = e; path = p; } 
        } 
 
        static final class TeamResult { 
            final long totalExpansions; 
            final int totalPathLength; 
            final int solvedAgents; 
            final int totalAgents; 
            final List<List<Integer>> paths; 
            TeamResult(long e, int pl, int solved, int total, List<List<Integer>> paths) { 
                totalExpansions = e; totalPathLength = pl; solvedAgents = solved; 
                totalAgents = total; this.paths = paths; 
            } 
        } 
 
        static long vKey(int loc, int time) { return (long) loc * 100_000L + time; } 
        static long eKey(int from, int to, int time) { return ((long) from * 1_000_000L + to) * 1000L + 
time; } 
 
        static AgentResult spaceTimeAStar(MAPFGrid grid, int startLoc, int goalLoc, 
                                           Set<Long> forbiddenVertex, Set<Long> forbiddenEdge, 
                                           int maxTime) { 
            SearchMetrics m = new SearchMetrics(); 
            int T = maxTime + 1; 
            Map<Long, Integer> gCost = new HashMap<>(); 
            Map<Long, Long> parent = new HashMap<>(); 
            long startState = (long) startLoc * T; 
            gCost.put(startState, 0); 
            parent.put(startState, -1L); 
 
            PriorityQueue<long[]> open = new PriorityQueue<>( 
                    Comparator.comparingDouble(a -> Double.longBitsToDouble(a[0]))); 
            open.add(new long[]{Double.doubleToLongBits(grid.manhattan(startLoc, goalLoc)), 
startState}); 
            Set<Long> closed = new HashSet<>(); 
            long goalState = -1; 
 
            while (!open.isEmpty()) { 
                long[] cur = open.poll(); 
                long state = cur[1]; 
                if (closed.contains(state)) continue; 
                closed.add(state); 
                m.incrementExpansions(); 
 
                int loc = (int) (state / T); 
                int time = (int) (state % T); 
                if (loc == goalLoc) { goalState = state; break; } 
                if (time >= maxTime) continue; 
 
                int r = grid.row(loc), c = grid.col(loc); 
                for (int d = 0; d < 5; d++) { 
                    int nr = r + MAPFGrid.DR[d], nc = c + MAPFGrid.DC[d]; 
                    if (!grid.valid(nr, nc)) continue; 
                    int nLoc = grid.id(nr, nc); 
                    int nTime = time + 1; 
 
                    if (forbiddenVertex.contains(vKey(nLoc, nTime))) continue; 
                    if (forbiddenEdge.contains(eKey(nLoc, loc, time))) continue; 
 
                    long nState = (long) nLoc * T + nTime; 
                    if (closed.contains(nState)) continue; 
                    int ng = gCost.getOrDefault(state, Integer.MAX_VALUE) + 1; 
                    if (ng < gCost.getOrDefault(nState, Integer.MAX_VALUE)) { 
                        gCost.put(nState, ng); 
                        parent.put(nState, state); 
                        int h = grid.manhattan(nLoc, goalLoc); 
                        open.add(new long[]{Double.doubleToLongBits(ng + h), nState}); 
                    } 
                } 
            } 
 
            List<Integer> path = new ArrayList<>(); 
            if (goalState >= 0) { 
                LinkedList<Integer> rev = new LinkedList<>(); 
                long s = goalState; 
                while (s != -1L) { 
                    rev.addFirst((int) (s / T)); 
                    Long pr = parent.get(s); 
                    s = (pr == null) ? -1L : pr; 
                } 
                path.addAll(rev); 
            } 
            return new AgentResult(m.getExpansions(), path); 
        } 
 
        static TeamResult plan(MAPFGrid grid, int[] starts, int[] goals, int maxTime) { 
            int k = starts.length; 
            long totalExpansions = 0; 
            int totalPath = 0, solved = 0; 
            Set<Long> forbiddenVertex = new HashSet<>(); 
            Set<Long> forbiddenEdge = new HashSet<>(); 
            List<List<Integer>> allPaths = new ArrayList<>(); 
 
            for (int agent = 0; agent < k; agent++) { 
                AgentResult res = spaceTimeAStar(grid, starts[agent], goals[agent], forbiddenVertex, 
forbiddenEdge, maxTime); 
                totalExpansions += res.expansions; 
                if (!res.path.isEmpty()) { 
                    solved++; 
                    totalPath += res.path.size(); 
                    allPaths.add(res.path); 
                    for (int t = 0; t < res.path.size(); t++) { 
                        forbiddenVertex.add(vKey(res.path.get(t), t)); 
                        if (t > 0) forbiddenEdge.add(eKey(res.path.get(t - 1), res.path.get(t), t - 1)); 
                    } 
                    int goalLoc = res.path.get(res.path.size() - 1); 
                    for (int t = res.path.size(); t <= maxTime; t++) forbiddenVertex.add(vKey(goalLoc, t)); 
                } else { 
                    allPaths.add(Collections.emptyList()); 
                } 
            } 
            return new TeamResult(totalExpansions, totalPath, solved, k, allPaths); 
        } 
 
        static int[] placeAgents(MAPFGrid g, int n, long seed, boolean reversed) { 
            int[][] candidates = { 
                    {0, 0}, {g.rows - 1, g.cols - 1}, {0, g.cols - 1}, {g.rows - 1, 0}, 
                    {0, g.cols / 2}, {g.rows - 1, g.cols / 2}, {g.rows / 2, 0}, {g.rows / 2, g.cols - 1} 
            }; 
            int[] locs = new int[n]; 
            int placed = 0; 
            Set<Integer> used = new HashSet<>(); 
            if (reversed) { 
                for (int i = candidates.length - 1; i >= 0 && placed < n; i--) { 
                    int r = candidates[i][0], c = candidates[i][1]; 
                    if (g.valid(r, c)) { 
                        int id = g.id(r, c); 
                        if (used.add(id)) locs[placed++] = id; 
                    } 
                } 
            } else { 
                for (int[] cand : candidates) { 
                    if (placed >= n) break; 
                    if (g.valid(cand[0], cand[1])) { 
                        int id = g.id(cand[0], cand[1]); 
                        if (used.add(id)) locs[placed++] = id; 
                    } 
                } 
            } 
            if (placed < n) { 
                Random rng = new Random(seed + (reversed ? 7777 : 1111)); 
                while (placed < n) { 
                    int r = rng.nextInt(g.rows), c = rng.nextInt(g.cols); 
                    if (g.valid(r, c)) { 
                        int id = g.id(r, c); 
                        if (used.add(id)) locs[placed++] = id; 
                    } 
                } 
            } 
            return locs; 
        } 
 
        static boolean noObstacleOccupied(MAPFGrid grid, List<List<Integer>> paths) { 
            for (List<Integer> path : paths) 
                for (int loc : path) 
                    if (grid.blocked[grid.row(loc)][grid.col(loc)]) return false; 
            return true; 
        } 
 
        static boolean noVertexCollision(List<List<Integer>> paths) { 
            int maxLen = paths.stream().mapToInt(List::size).max().orElse(0); 
            for (int t = 0; t < maxLen; t++) { 
                Set<Integer> occupied = new HashSet<>(); 
                for (List<Integer> path : paths) { 
                    if (path.isEmpty()) continue; 
                    int loc = path.get(Math.min(t, path.size() - 1)); 
                    if (!occupied.add(loc)) return false; 
                } 
            } 
            return true; 
        } 
 
        static boolean noSwapCollision(List<List<Integer>> paths) { 
            int maxLen = paths.stream().mapToInt(List::size).max().orElse(0); 
            for (int t = 0; t + 1 < maxLen; t++) { 
                for (int i = 0; i < paths.size(); i++) { 
                    if (paths.get(i).isEmpty() || t + 1 >= paths.get(i).size()) continue; 
                    int a0 = paths.get(i).get(t), a1 = paths.get(i).get(t + 1); 
                    for (int j = i + 1; j < paths.size(); j++) { 
                        if (paths.get(j).isEmpty() || t + 1 >= paths.get(j).size()) continue; 
                        int b0 = paths.get(j).get(t), b1 = paths.get(j).get(t + 1); 
                        if (a0 == b1 && a1 == b0) return false; 
                    } 
                } 
            } 
            return true; 
        } 
    } 
 
    // ============================================================ 
    // DOMAIN 4: PATTERN MATCHING 
    // ============================================================ 
 
    static final class PatternAlgorithms { 
 
        static final class Result { 
            final long comparisons; 
            final int matchedLength; 
            final boolean solved; 
            Result(long c, int ml, boolean s) { comparisons = c; matchedLength = ml; solved = s; } 
        } 
 
        static char[] generateText(int length, char[] alphabet, long seed) { 
            Random rng = new Random(seed); 
            char[] text = new char[length]; 
            for (int i = 0; i < length; i++) text[i] = alphabet[rng.nextInt(alphabet.length)]; 
            return text; 
        } 
 
        static char[] generatePattern(int length, char[] alphabet, long seed) { 
            Random rng = new Random(seed + 999); 
            char[] pat = new char[length]; 
            for (int i = 0; i < length; i++) pat[i] = alphabet[rng.nextInt(alphabet.length)]; 
            return pat; 
        } 
 
        static Result naive(char[] text, char[] pattern) { 
            int n = text.length, m = pattern.length; 
            if (m == 0) return new Result(0, 0, true); 
            long comparisons = 0; 
            boolean found = false; 
            for (int i = 0; i <= n - m && !found; i++) { 
                for (int j = 0; j < m; j++) { 
                    comparisons++; 
                    if (text[i + j] != pattern[j]) break; 
                    if (j == m - 1) found = true; 
                } 
            } 
            return new Result(comparisons, found ? m : 0, found); 
        } 
 
        static Result kmp(char[] text, char[] pattern) { 
            int n = text.length, m = pattern.length; 
            if (m == 0) return new Result(0, 0, true); 
            long[] cnt = {0}; 
            int[] fail = buildFailure(pattern, cnt); 
            int k = 0; 
            boolean found = false; 
            for (int i = 0; i < n && !found; i++) { 
                while (k > 0 && !eq(pattern[k], text[i], cnt)) k = fail[k - 1]; 
                if (eq(pattern[k], text[i], cnt)) k++; 
                if (k == m) found = true; 
            } 
            return new Result(cnt[0], found ? m : 0, found); 
        } 
 
        private static int[] buildFailure(char[] pattern, long[] cnt) { 
            int m = pattern.length; 
            int[] fail = new int[m]; 
            int k = 0; 
            for (int i = 1; i < m; i++) { 
                while (k > 0 && !eq(pattern[k], pattern[i], cnt)) k = fail[k - 1]; 
                if (eq(pattern[k], pattern[i], cnt)) k++; 
                fail[i] = k; 
            } 
            return fail; 
        } 
 
        private static boolean eq(char a, char b, long[] cnt) { 
            cnt[0]++; 
            return a == b; 
        } 
 
        static Result iddfs(char[] text, char[] pattern) { 
            int n = text.length, m = pattern.length; 
            if (m == 0) return new Result(0, 0, true); 
            long[] cnt = {0}; 
            boolean found = false; 
            for (int limit = 1; limit <= m && !found; limit++) { 
                for (int start = 0; start <= n - m && !found; start++) { 
                    int matched = attempt(text, pattern, start, limit, cnt); 
                    if (matched == m) found = true; 
                } 
            } 
            return new Result(cnt[0], found ? m : 0, found); 
        } 
 
        private static int attempt(char[] text, char[] pattern, int start, int limit, long[] cnt) { 
            int matched = 0; 
            for (int j = 0; j < limit && j < pattern.length; j++) { 
                cnt[0]++; 
                if (text[start + j] != pattern[j]) return matched; 
                matched++; 
            } 
            return matched; 
        } 
    } 
 
    // ============================================================ 
    // CSV WRITER / READER 
    // ============================================================ 
 
    static final class CsvWriter implements AutoCloseable { 
        private final BufferedWriter writer; 
 
        CsvWriter(String path, String header) throws IOException { 
            Path p = Paths.get(path); 
            if (p.getParent() != null) Files.createDirectories(p.getParent()); 
            writer = new BufferedWriter(new FileWriter(path, false)); 
            writer.write(header); 
            writer.newLine(); 
        } 
 
        void writeRow(Object... fields) throws IOException { 
            StringBuilder sb = new StringBuilder(); 
            for (int i = 0; i < fields.length; i++) { 
                if (i > 0) sb.append(','); 
                sb.append(fields[i]); 
            } 
            writer.write(sb.toString()); 
            writer.newLine(); 
        } 
 
        @Override 
        public void close() throws IOException { 
            writer.flush(); 
            writer.close(); 
        } 
    } 
 
    /** Minimal CSV reader: assumes no embedded commas (true for every raw file produced 
here). */ 
    static final class CsvUtil { 
        static List<Map<String, String>> readCsv(String path) throws IOException { 
            List<String> lines = Files.readAllLines(Paths.get(path)); 
            if (lines.isEmpty()) return Collections.emptyList(); 
            String[] header = lines.get(0).split(",", -1); 
            List<Map<String, String>> rows = new ArrayList<>(); 
            for (int i = 1; i < lines.size(); i++) { 
                if (lines.get(i).isBlank()) continue; 
                String[] parts = lines.get(i).split(",", -1); 
                Map<String, String> row = new LinkedHashMap<>(); 
                for (int j = 0; j < header.length && j < parts.length; j++) { 
                    row.put(header[j], parts[j]); 
                } 
                rows.add(row); 
            } 
            return rows; 
        } 
    } 
 
    // ============================================================ 
    // EXPERIMENT RUNNER — generates the raw, trial-level CSV files 
    // ============================================================ 
 
    static final class ExperimentRunner { 
        private final ExperimentConfig cfg; 
 
        ExperimentRunner(ExperimentConfig cfg) { this.cfg = cfg; } 
 
        void runAll() throws IOException { 
            runGrid(); 
            runGameTree(); 
            runMAPF(); 
            runPattern(); 
        } 
 
        private void runGrid() throws IOException { 
            String header = 
"domain,algorithm,depth,trial,seed,node_expansions,path_length,solved,capped,path_cost,runtime_ms"; 
            String[] algs = {"BFS", "Dijkstra", "A*", "WA*(1.5)", "GBFS", "IDA*"}; 
            try (CsvWriter w = new CsvWriter("data/raw/grid.csv", header)) { 
                for (int depth : DEPTHS) { 
                    int size = Math.max(cfg.gridSideMultiplier * depth, 8); 
                    for (String alg : algs) { 
                        for (int trial = 1; trial <= cfg.trials; trial++) { 
                            long seed = GRID_KAPPA * depth + trial; 
                            GridGraph g = new GridGraph(size, cfg.gridObstacleRate, cfg.gridMinWeight, 
cfg.gridMaxWeight, seed); 
                            long t0 = System.nanoTime(); 
                            GridSearchAlgorithms.Result r = switch (alg) { 
                                case "BFS" -> GridSearchAlgorithms.bfs(g); 
                                case "Dijkstra" -> GridSearchAlgorithms.dijkstra(g); 
                                case "A*" -> GridSearchAlgorithms.aStar(g, 1.0); 
                                case "WA*(1.5)" -> GridSearchAlgorithms.aStar(g, 1.5); 
                                case "GBFS" -> GridSearchAlgorithms.greedyBestFirst(g); 
                                default -> GridSearchAlgorithms.idaStar(g); 
                            }; 
                            long ms = (System.nanoTime() - t0) / 1_000_000; 
                            w.writeRow("Grid", alg, depth, trial, seed, r.expansions, r.pathLength, r.solved, 
r.capped, r.cost, ms); 
                        } 
                    } 
                } 
            } 
        } 
 
        private void runGameTree() throws IOException { 
            String header = 
"domain,algorithm,depth,trial,seed,node_expansions,solution_length,depth_capped,solved,retained_sum,internal_nodes,runtime_ms"; 
            try (CsvWriter w = new CsvWriter("data/raw/game_tree.csv", header)) { 
                for (int depth : DEPTHS) { 
                    for (int trial = 1; trial <= cfg.trials; trial++) { 
                        long seed = GAME_KAPPA * depth + trial; 
 
                        GameTree gtMM = new GameTree(cfg.gameBranchingFactor, depth, 
cfg.gameDepthCap, 
                                cfg.gameLeafMin, cfg.gameLeafMax, seed); 
                        long t0 = System.nanoTime(); 
                        GameSearchAlgorithms.Result mm = GameSearchAlgorithms.miniMax(gtMM); 
                        long mmMs = (System.nanoTime() - t0) / 1_000_000; 
                        w.writeRow("GameTree", "MiniMax", depth, trial, seed, mm.expansions, 
                                gtMM.effectiveDepth + 1, gtMM.isDepthCapped(), true, mm.retainedSum, mm.internalNodes, mmMs); 
 
                        GameTree gtAB = new GameTree(cfg.gameBranchingFactor, depth, 
cfg.gameDepthCap, 
                                cfg.gameLeafMin, cfg.gameLeafMax, seed); 
                        long t1 = System.nanoTime(); 
                        GameSearchAlgorithms.Result ab = GameSearchAlgorithms.alphaBeta(gtAB); 
                        long abMs = (System.nanoTime() - t1) / 1_000_000; 
                        w.writeRow("GameTree", "AlphaBeta", depth, trial, seed, ab.expansions, 
                                gtAB.effectiveDepth + 1, gtAB.isDepthCapped(), true, ab.retainedSum, ab.internalNodes, abMs); 
                    } 
                } 
            } 
        } 
 
        private void runMAPF() throws IOException { 
            String header = 
"domain,algorithm,depth,trial,seed,node_expansions,path_length,solved,solved_agents,total_agents,runtime_ms"; 
            try (CsvWriter w = new CsvWriter("data/raw/mapf.csv", header)) { 
                for (int di = 0; di < DEPTHS.length; di++) { 
                    int depth = DEPTHS[di]; 
                    int nAgents = MAPF_AGENT_COUNTS[di]; 
                    int gridSize = Math.max(depth, 5); 
                    for (int trial = 1; trial <= cfg.trials; trial++) { 
                        long seed = MAPF_KAPPA * depth + trial; 
                        MAPFGrid grid = new MAPFGrid(gridSize, cfg.mapfObstacleRate, seed); 
                        int[] starts = PrioritizedPlanning.placeAgents(grid, nAgents, seed, false); 
                        int[] goals = PrioritizedPlanning.placeAgents(grid, nAgents, seed, true); 
                        long t0 = System.nanoTime(); 
                        PrioritizedPlanning.TeamResult res = PrioritizedPlanning.plan(grid, starts, goals, 
cfg.mapfTimeHorizon); 
                        long ms = (System.nanoTime() - t0) / 1_000_000; 
                        boolean fullySolved = res.solvedAgents == res.totalAgents; 
                        w.writeRow("MAPF", "PrioritisedPlanning", depth, trial, seed, 
                                res.totalExpansions, res.totalPathLength, fullySolved, 
                                res.solvedAgents, res.totalAgents, ms); 
                    } 
                } 
            } 
        } 
 
        private void runPattern() throws IOException { 
            String header = 
"domain,algorithm,depth,trial,seed,comparisons,pattern_length,solved,runtime_ms"; 
            char[] alphabet = cfg.patternAlphabet.toCharArray(); 
            String[] algs = {"Naive", "KMP", "IDDFS"}; 
            try (CsvWriter w = new CsvWriter("data/raw/pattern.csv", header)) { 
                for (int depth : DEPTHS) { 
                    int patLen = depth * cfg.patternMultiplier; 
                    int textLen = depth * cfg.textMultiplier; 
                    for (String alg : algs) { 
                        for (int trial = 1; trial <= cfg.trials; trial++) { 
                            long seed = PATTERN_KAPPA * depth + trial; 
                            char[] text = PatternAlgorithms.generateText(textLen, alphabet, seed); 
                            char[] pattern = PatternAlgorithms.generatePattern(patLen, alphabet, seed); 
 
                            int maxStart = Math.max(textLen - patLen, 0); 
                            int pos = maxStart == 0 ? 0 : (int) Math.floorMod(seed, maxStart); 
                            System.arraycopy(pattern, 0, text, pos, patLen); 
 
                            long t0 = System.nanoTime(); 
                            PatternAlgorithms.Result r = switch (alg) { 
                                case "Naive" -> PatternAlgorithms.naive(text, pattern); 
                                case "KMP" -> PatternAlgorithms.kmp(text, pattern); 
                                default -> PatternAlgorithms.iddfs(text, pattern); 
                            }; 
                            long ms = (System.nanoTime() - t0) / 1_000_000; 
                            w.writeRow("PatternMatch", alg, depth, trial, seed, r.comparisons, patLen, 
r.solved, ms); 
                        } 
                    } 
                } 
            } 
        } 
    } 
 
    // ============================================================ 
    // ANALYSIS PIPELINE (replaces summarize_results.py / fit_models.py / 
    // reproduce_tables.py) — pure Java, CSV in, CSV out. 
    // ============================================================ 
 
    /** One aggregated (domain, algorithm, depth) row, equivalent to a row of summary.csv. */ 
    static final class SummaryRow { 
        String domain, algorithm; 
        double meanQ = Double.NaN;   // measured mean solution quality vs per-instance Dijkstra optimum (Grid only)
        double rMean = Double.NaN;   // measured mean retained branching (GameTree only)
        int depth; 
        int trials; 
        double meanMetric; 
        double stdMetric; 
        double meanU;       // mean solution length / path length / pattern length 
        double solvedRate; 
        String metricName;  // "node_expansions" or "comparisons" 
    } 
 
    /** 
     * Reads a raw CSV, groups by (algorithm, depth), applies the mandatory 
     * consistency checks, and returns one SummaryRow per group. Throws 
     * IllegalStateException (causing the whole run to fail loudly) on any 
     * violation, per the "Automatic consistency checks" specification. 
     */ 
    static final class ResultsAggregator { 
 
        static List<SummaryRow> aggregateDomain(String rawCsvPath, String domain, String 
metricColumn, 
                                                 String uColumn, int expectedTrials) throws IOException { 
            List<Map<String, String>> rows = CsvUtil.readCsv(rawCsvPath); 
            Map<String, Map<Integer, List<Map<String, String>>>> grouped = new TreeMap<>(); 
            for (Map<String, String> row : rows) { 
                String alg = row.get("algorithm"); 
                int depth = Integer.parseInt(row.get("depth")); 
                grouped.computeIfAbsent(alg, k -> new TreeMap<>()) 
                       .computeIfAbsent(depth, k -> new ArrayList<>()) 
                       .add(row); 
            } 
 
            List<SummaryRow> out = new ArrayList<>(); 
            for (var algEntry : grouped.entrySet()) { 
                String alg = algEntry.getKey(); 
                for (var depthEntry : algEntry.getValue().entrySet()) { 
                    int depth = depthEntry.getKey(); 
                    List<Map<String, String>> group = depthEntry.getValue(); 
 
                    // Check: exactly `expectedTrials` rows per configuration. 
                    if (group.size() != expectedTrials) { 
                        throw new IllegalStateException(String.format( 
                                "%s/%s/d=%d: expected %d trials, found %d", 
                                domain, alg, depth, expectedTrials, group.size())); 
                    } 
 
                    // Check: no duplicate seeds within this configuration. 
                    Set<Long> seeds = new HashSet<>(); 
                    for (var r : group) { 
                        long seed = Long.parseLong(r.get("seed")); 
                        if (!seeds.add(seed)) { 
                            throw new IllegalStateException(String.format( 
                                    "%s/%s/d=%d: duplicate seed %d", domain, alg, depth, seed)); 
                        } 
                    } 
 
                    // Unsolved trials are reported, never silently dropped from the raw set. 
                    boolean hasSolvedCol = group.get(0).containsKey("solved"); 
                    List<Map<String, String>> solvedRows = new ArrayList<>(); 
                    for (var r : group) { 
                        boolean solved = !hasSolvedCol || Boolean.parseBoolean(r.get("solved")); 
                        if (solved) solvedRows.add(r); 
                    } 
                    int solvedCount = solvedRows.size(); 
                    // Means are computed over solved trials only (matches the paper's MAPF 
                    // convention); if nothing solved, fall back to the full group so a mean 
                    // is still reported rather than silently omitted. 
                    List<Map<String, String>> statRows = solvedRows.isEmpty() ? group : solvedRows; 
 
                    double[] metricVals = statRows.stream() 
                            .mapToDouble(r -> Double.parseDouble(r.get(metricColumn))).toArray(); 
                    for (double v : metricVals) { 
                        if (v <= 0) throw new IllegalStateException(String.format( 
                                "%s/%s/d=%d: non-positive %s encountered", domain, alg, depth, 
metricColumn)); 
                    } 
                    double mean = Arrays.stream(metricVals).average().orElse(0); 
                    double mean2 = Arrays.stream(metricVals).map(v -> v * v).average().orElse(0); 
                    double std = Math.sqrt(Math.max(mean2 - mean * mean, 0)); 
                    double meanU = statRows.stream() 
                            .mapToDouble(r -> Double.parseDouble(r.get(uColumn))).average().orElse(0); 
                    double solvedRate = (double) solvedCount / group.size(); 
 
                    if (solvedCount < group.size()) { 
                        System.out.printf( 
                                "  [notice] %s/%s/d=%d: %d/%d unsolved trial(s) retained in raw CSV " 
                                        + "(excluded only from the mean, not dropped)%n", 
                                domain, alg, depth, group.size() - solvedCount, group.size()); 
                    } 
 
                    SummaryRow sr = new SummaryRow(); 
                    sr.domain = domain; sr.algorithm = alg; sr.depth = depth; 
                    sr.trials = group.size(); sr.meanMetric = mean; sr.stdMetric = std; 
                    sr.meanU = meanU; sr.solvedRate = solvedRate; sr.metricName = metricColumn; 
                    out.add(sr); 
                } 
            } 
            return out; 
        } 
 
        static void writeSummary(String path, List<SummaryRow> rows) throws IOException { 
            try (CsvWriter w = new CsvWriter(path, 
                    
"domain,algorithm,depth,trials,mean_metric,std_metric,mean_u,solved_rate,mean_q,r_mean,metric_name")) { 
                for (SummaryRow s : rows) { 
                    w.writeRow(s.domain, s.algorithm, s.depth, s.trials, 
                            fmt(s.meanMetric), fmt(s.stdMetric), fmt(s.meanU), fmt(s.solvedRate), 
fmtq(s.meanQ), fmtq(s.rMean), s.metricName); 
                } 
            } 
        } 
 
        private static String fmtq(double v) { return Double.isNaN(v) ? "NaN" : String.format(Locale.US, "%.6f", v); } 

        /** Measured solution quality per Grid configuration: mean over jointly-solved trials of
         *  (Dijkstra optimal cost) / (algorithm cost); 1.0 for Dijkstra itself. */
        static void augmentGridQuality(List<SummaryRow> summary, String rawPath) throws IOException {
            Map<Long, Map<String, int[]>> byKey = new HashMap<>(); // depth*1000+trial -> alg -> {solved,cost}
            List<String> lines = java.nio.file.Files.readAllLines(java.nio.file.Paths.get(rawPath));
            String[] hdr = lines.get(0).split(",");
            int iAlg = -1, iDepth = -1, iTrial = -1, iSolved = -1, iCost = -1;
            for (int k = 0; k < hdr.length; k++) {
                switch (hdr[k]) { case "algorithm" -> iAlg = k; case "depth" -> iDepth = k;
                    case "trial" -> iTrial = k; case "solved" -> iSolved = k; case "path_cost" -> iCost = k; }
            }
            for (int li = 1; li < lines.size(); li++) {
                String[] c = lines.get(li).split(",");
                long key = Long.parseLong(c[iDepth]) * 1000 + Long.parseLong(c[iTrial]);
                byKey.computeIfAbsent(key, k -> new HashMap<>())
                     .put(c[iAlg], new int[]{ Boolean.parseBoolean(c[iSolved]) ? 1 : 0, Integer.parseInt(c[iCost]) });
            }
            for (SummaryRow sr : summary) {
                if (!sr.domain.equals("Grid")) continue;
                double sum = 0; int n = 0;
                for (Map.Entry<Long, Map<String, int[]>> e : byKey.entrySet()) {
                    int[] dij = e.getValue().get("Dijkstra");
                    int[] me = e.getValue().get(sr.algorithm);
                    if (dij == null || me == null || dij[0] != 1 || me[0] != 1 || me[1] <= 0) continue;
                    sum += Math.min(1.0, (double) dij[1] / (double) me[1]); n++;
                }
                if (n > 0) sr.meanQ = sum / n;
            }
        }

        /** Measured mean retained branching per GameTree configuration: pooled retained/internal. */
        static void augmentGameRetained(List<SummaryRow> summary, String rawPath) throws IOException {
            Map<String, long[]> byCfg = new HashMap<>(); // alg|depth -> {retainedSum, internalNodes}
            List<String> lines = java.nio.file.Files.readAllLines(java.nio.file.Paths.get(rawPath));
            String[] hdr = lines.get(0).split(",");
            int iAlg = -1, iDepth = -1, iRet = -1, iInt = -1;
            for (int k = 0; k < hdr.length; k++) {
                switch (hdr[k]) { case "algorithm" -> iAlg = k; case "depth" -> iDepth = k;
                    case "retained_sum" -> iRet = k; case "internal_nodes" -> iInt = k; }
            }
            for (int li = 1; li < lines.size(); li++) {
                String[] c = lines.get(li).split(",");
                long[] acc = byCfg.computeIfAbsent(c[iAlg] + "|" + c[iDepth], k -> new long[2]);
                acc[0] += Long.parseLong(c[iRet]); acc[1] += Long.parseLong(c[iInt]);
            }
            for (SummaryRow sr : summary) {
                if (!sr.domain.equals("GameTree")) continue;
                long[] acc = byCfg.get(sr.algorithm + "|" + sr.depth);
                if (acc != null && acc[1] > 0) sr.rMean = (double) acc[0] / (double) acc[1];
            }
        }

        private static String fmt(double v) { return String.format(Locale.US, "%.6f", v); } 
    } 
 
    /** 
     * Fits log N(d) = log(alpha) + beta * d * log(b) on d in TRAIN_DEPTHS only, 
     * and evaluates held-out MAPE on d in TEST_DEPTHS, with: 
     *   - depth-cap exclusion for MiniMax/AlphaBeta (any test depth beyond the 
     *     game tree's depth cap is excluded, since it is not a genuinely 
     *     deeper instance); 
     *   - node-cap saturation exclusion for IDA* (flagged rather than fitted). 
     */ 
    static final class ModelFitter { 
 
        static final class FitRow { 
            String domain, algorithm; 
            double betaHat = Double.NaN, alphaHat = Double.NaN, r2Train = Double.NaN, mapeTest = Double.NaN; 
            double betaCiLow = Double.NaN, betaCiHigh = Double.NaN; // 95% CI, t-based, dof = n_train - 2 
            String status = "unknown"; 
        } 

        /** Two-sided 95% Student-t critical values for the small dof produced by 
         *  short training-depth windows (dof = n - 2). NaN when dof <= 0. */ 
        static double tCritical95(int dof) { 
            switch (dof) { 
                case 1: return 12.7062; 
                case 2: return 4.3027; 
                case 3: return 3.1824; 
                case 4: return 2.7764; 
                default: return Double.NaN; 
            } 
        } 
 
        static List<FitRow> fitAll(List<SummaryRow> summary, ExperimentConfig cfg) { 
            Map<String, Map<String, Map<Integer, SummaryRow>>> byDomainAlg = new 
TreeMap<>(); 
            for (SummaryRow s : summary) { 
                byDomainAlg.computeIfAbsent(s.domain, k -> new TreeMap<>()) 
                           .computeIfAbsent(s.algorithm, k -> new TreeMap<>()) 
                           .put(s.depth, s); 
            } 
 
            Map<String, Integer> depthCap = new HashMap<>(); 
            depthCap.put("MiniMax", cfg.gameDepthCap); 
            depthCap.put("AlphaBeta", cfg.gameDepthCap); 
 
            double logb = Math.log(MODEL_BASE_B); 
            List<FitRow> out = new ArrayList<>(); 
 
            for (var domEntry : byDomainAlg.entrySet()) { 
                String domain = domEntry.getKey(); 
                for (var algEntry : domEntry.getValue().entrySet()) { 
                    String alg = algEntry.getKey(); 
                    Map<Integer, SummaryRow> byDepth = algEntry.getValue(); 
 
                    FitRow fr = new FitRow(); 
                    fr.domain = domain; fr.algorithm = alg; 
 
                    // Assert: no test depth may leak into the training set. 
                    for (int td : TEST_DEPTHS) { 
                        for (int trd : TRAIN_DEPTHS) { 
                            if (td == trd) throw new IllegalStateException("TEST_DEPTHS and TRAIN_DEPTHS overlap"); 
                        } 
                    } 
 
                    if ("IDA*".equals(alg) && isSaturated(byDepth, TRAIN_DEPTHS)) { 
                        fr.status = "excluded_saturated"; 
                        out.add(fr); 
                        continue; 
                    } 
 
                    List<Integer> trainPresent = new ArrayList<>(); 
                    for (int d : TRAIN_DEPTHS) if (byDepth.containsKey(d)) trainPresent.add(d); 
                    if (trainPresent.size() < 2) { 
                        fr.status = "insufficient_training_data"; 
                        out.add(fr); 
                        continue; 
                    } 
 
                    double[] x = new double[trainPresent.size()]; 
                    double[] y = new double[trainPresent.size()]; 
                    for (int i = 0; i < trainPresent.size(); i++) { 
                        int d = trainPresent.get(i); 
                        x[i] = d * logb; 
                        y[i] = Math.log(byDepth.get(d).meanMetric); 
                    } 
                    double xm = mean(x), ym = mean(y); 
                    double sxx = 0, sxy = 0, syy = 0; 
                    for (int i = 0; i < x.length; i++) { 
                        sxx += (x[i] - xm) * (x[i] - xm); 
                        sxy += (x[i] - xm) * (y[i] - ym); 
                        syy += (y[i] - ym) * (y[i] - ym); 
                    } 
                    double beta = sxx > 1e-12 ? sxy / sxx : Double.NaN; 
                    double alpha = Math.exp(ym - beta * xm); 
                    double r2 = (sxx > 1e-12 && syy > 1e-12) ? (sxy * sxy) / (sxx * syy) : Double.NaN; 
                    // 95% CI for the slope: SSE from the training residuals, dof = n - 2. 
                    int dof = x.length - 2; 
                    double sse = Math.max(syy - beta * sxy, 0.0); 
                    double seBeta = (dof > 0 && sxx > 1e-12) ? Math.sqrt(sse / dof / sxx) : Double.NaN; 
                    double tcrit = tCritical95(dof); 
                    if (!Double.isNaN(seBeta) && !Double.isNaN(tcrit) && !Double.isNaN(beta)) { 
                        fr.betaCiLow = beta - tcrit * seBeta; 
                        fr.betaCiHigh = beta + tcrit * seBeta; 
                    } 
 
                    Integer cap = depthCap.get(alg); 
                    List<Double> apes = new ArrayList<>(); 
                    for (int d : TEST_DEPTHS) { 
                        if (!byDepth.containsKey(d)) continue; 
                        if (cap != null && d > cap) continue; // depth-cap contamination excluded 
                        double actual = byDepth.get(d).meanMetric; 
                        double predicted = alpha * Math.exp(beta * d * logb); 
                        apes.add(Math.abs(predicted - actual) / actual * 100.0); 
                    } 
 
                    fr.betaHat = beta; fr.alphaHat = alpha; fr.r2Train = r2; 
                    if (apes.isEmpty()) { 
                        fr.status = "no_valid_test_depth"; 
                    } else { 
                        fr.mapeTest = apes.stream().mapToDouble(v -> v).average().orElse(Double.NaN); 
                        fr.status = "ok"; 
                    } 
                    out.add(fr); 
                } 
            } 
            return out; 
        } 
 
        static boolean isSaturatedPub(Map<Integer, SummaryRow> byDepth, int[] depths) { 
            return isSaturated(byDepth, depths); 
        } 

        private static boolean isSaturated(Map<Integer, SummaryRow> byDepth, int[] depths) { 
            for (int d : depths) { 
                SummaryRow s = byDepth.get(d); 
                if (s == null) continue; 
                boolean nearCap = s.meanMetric >= 0.99 * GridSearchAlgorithms.IDA_NODE_CAP; 
                boolean flat = s.stdMetric <= 0.01 * Math.max(s.meanMetric, 1); 
                if (nearCap && flat) return true; 
            } 
            return false; 
        } 
 
        private static double mean(double[] a) { 
            double s = 0; 
            for (double v : a) s += v; 
            return s / a.length; 
        } 
 
        static void writeFits(String path, List<FitRow> fits) throws IOException { 
            try (CsvWriter w = new CsvWriter(path, "domain,algorithm,beta_hat,beta_ci95_low,beta_ci95_high,alpha_hat,r2_train,mape_test,status")) { 
                for (FitRow f : fits) { 
                    w.writeRow(f.domain, f.algorithm, fmt(f.betaHat), fmt(f.betaCiLow), fmt(f.betaCiHigh), fmt(f.alphaHat), 
                            fmt(f.r2Train), fmt(f.mapeTest), f.status); 
                } 
            } 
        } 
 
        private static String fmt(double v) { return Double.isNaN(v) ? "" : String.format(Locale.US, 
"%.6f", v); } 
    } 
 
    /** 
     * Companion model-form comparison: fits the power law N(d) = c * d^k on the 
     * SAME training depths and evaluates held-out MAPE on the SAME test depths and 
     * with the SAME exclusion rules as ModelFitter (depth-cap exclusion for 
     * MiniMax/AlphaBeta, saturation exclusion for IDA*). Written to table8_powerlaw.csv. 
     * Purpose: distinguish exponential regimes (game trees) from polynomial/area-driven 
     * regimes (bounded-state grids) by out-of-sample predictive form, not by in-sample R^2. 
     */ 
    static final class PowerLawFitter { 

        static final class FitRow { 
            String domain, algorithm; 
            double kHat = Double.NaN, cHat = Double.NaN, r2Train = Double.NaN, mapeTest = Double.NaN; 
            double kCiLow = Double.NaN, kCiHigh = Double.NaN; // 95% CI, t-based, dof = n_train - 2 
            String status = "unknown"; 
        } 

        static List<FitRow> fitAll(List<SummaryRow> summary, ExperimentConfig cfg) { 
            Map<String, Map<String, Map<Integer, SummaryRow>>> byDomainAlg = new TreeMap<>(); 
            for (SummaryRow s : summary) { 
                byDomainAlg.computeIfAbsent(s.domain, k -> new TreeMap<>()) 
                           .computeIfAbsent(s.algorithm, k -> new TreeMap<>()) 
                           .put(s.depth, s); 
            } 

            Map<String, Integer> depthCap = new HashMap<>(); 
            depthCap.put("MiniMax", cfg.gameDepthCap); 
            depthCap.put("AlphaBeta", cfg.gameDepthCap); 

            List<FitRow> out = new ArrayList<>(); 

            for (var domEntry : byDomainAlg.entrySet()) { 
                String domain = domEntry.getKey(); 
                for (var algEntry : domEntry.getValue().entrySet()) { 
                    String alg = algEntry.getKey(); 
                    Map<Integer, SummaryRow> byDepth = algEntry.getValue(); 

                    FitRow fr = new FitRow(); 
                    fr.domain = domain; fr.algorithm = alg; 

                    if ("IDA*".equals(alg) && ModelFitter.isSaturatedPub(byDepth, TRAIN_DEPTHS)) { 
                        fr.status = "excluded_saturated"; 
                        out.add(fr); 
                        continue; 
                    } 

                    List<Integer> trainPresent = new ArrayList<>(); 
                    for (int d : TRAIN_DEPTHS) if (byDepth.containsKey(d)) trainPresent.add(d); 
                    if (trainPresent.size() < 3) { 
                        fr.status = "insufficient_training_data"; 
                        out.add(fr); 
                        continue; 
                    } 

                    // OLS of log N on log d. 
                    double[] x = new double[trainPresent.size()]; 
                    double[] y = new double[trainPresent.size()]; 
                    for (int i = 0; i < trainPresent.size(); i++) { 
                        int d = trainPresent.get(i); 
                        x[i] = Math.log(d); 
                        y[i] = Math.log(byDepth.get(d).meanMetric); 
                    } 
                    double xm = 0, ym = 0; 
                    for (double v : x) xm += v; xm /= x.length; 
                    for (double v : y) ym += v; ym /= y.length; 
                    double sxx = 0, sxy = 0, syy = 0; 
                    for (int i = 0; i < x.length; i++) { 
                        sxx += (x[i] - xm) * (x[i] - xm); 
                        sxy += (x[i] - xm) * (y[i] - ym); 
                        syy += (y[i] - ym) * (y[i] - ym); 
                    } 
                    double k = sxx > 1e-12 ? sxy / sxx : Double.NaN; 
                    double c = Math.exp(ym - k * xm); 
                    double r2 = (sxx > 1e-12 && syy > 1e-12) ? (sxy * sxy) / (sxx * syy) : Double.NaN; 
                    int dof = x.length - 2; 
                    double sse = Math.max(syy - k * sxy, 0.0); 
                    double seK = (dof > 0 && sxx > 1e-12) ? Math.sqrt(sse / dof / sxx) : Double.NaN; 
                    double tcrit = ModelFitter.tCritical95(dof); 
                    if (!Double.isNaN(seK) && !Double.isNaN(tcrit) && !Double.isNaN(k)) { 
                        fr.kCiLow = k - tcrit * seK; 
                        fr.kCiHigh = k + tcrit * seK; 
                    } 

                    Integer cap = depthCap.get(alg); 
                    List<Double> apes = new ArrayList<>(); 
                    for (int d : TEST_DEPTHS) { 
                        if (!byDepth.containsKey(d)) continue; 
                        if (cap != null && d > cap) continue; // depth-cap contamination excluded 
                        double actual = byDepth.get(d).meanMetric; 
                        double predicted = c * Math.pow(d, k); 
                        apes.add(Math.abs(predicted - actual) / actual * 100.0); 
                    } 

                    fr.kHat = k; fr.cHat = c; fr.r2Train = r2; 
                    if (apes.isEmpty()) { 
                        fr.status = "no_valid_test_depth"; 
                    } else { 
                        fr.mapeTest = apes.stream().mapToDouble(v -> v).average().orElse(Double.NaN); 
                        fr.status = "ok"; 
                    } 
                    out.add(fr); 
                } 
            } 
            return out; 
        } 

        static void writeFits(String path, List<FitRow> fits) throws IOException { 
            try (CsvWriter w = new CsvWriter(path, "domain,algorithm,k_hat,k_ci95_low,k_ci95_high,c_hat,r2_train,mape_test,status")) { 
                for (FitRow f : fits) { 
                    w.writeRow(f.domain, f.algorithm, fmt(f.kHat), fmt(f.kCiLow), fmt(f.kCiHigh), fmt(f.cHat), 
                            fmt(f.r2Train), fmt(f.mapeTest), f.status); 
                } 
            } 
        } 

        private static String fmt(double v) { return Double.isNaN(v) ? "" : String.format(Locale.US, "%.6f", v); } 
    } 

    /** Writes the final, manuscript-facing CSV tables from the summary and fit results. */ 
    static final class TableGenerator { 
 
        static void generateAll(List<SummaryRow> summary, List<ModelFitter.FitRow> fits) throws 
IOException { 
            writeTable1(); 
            writeFiltered("results/tables/table2_grid.csv", summary, "Grid"); 
            writeFiltered("results/tables/table3_game_tree.csv", summary, "GameTree"); 
            writeFiltered("results/tables/table4_mapf.csv", summary, "MAPF"); 
            writeFiltered("results/tables/table5_pattern.csv", summary, "PatternMatch"); 
            // table6_heldout_beta.csv is written directly by ModelFitter.writeFits(...) 
            writeTable7(summary); 
        } 
 
        private static void writeTable1() throws IOException { 
            try (CsvWriter w = new CsvWriter("results/tables/table1_algorithms.csv", 
"algorithm,domain")) { 
                String[][] rows = { 
                        {"BFS", "Grid"}, {"Dijkstra", "Grid"}, {"A*", "Grid"}, {"WA*(1.5)", "Grid"}, 
                        {"GBFS", "Grid"}, {"IDA*", "Grid"}, {"MiniMax", "GameTree"}, {"AlphaBeta", 
"GameTree"}, 
                        {"PrioritisedPlanning", "MAPF"}, {"Naive", "PatternMatch"}, {"KMP", 
"PatternMatch"}, 
                        {"IDDFS", "PatternMatch"} 
                }; 
                for (String[] r : rows) w.writeRow(r[0], r[1]); 
            } 
        } 
 
        private static void writeFiltered(String path, List<SummaryRow> summary, String domain) 
throws IOException { 
            try (CsvWriter w = new CsvWriter(path, 
                    
"domain,algorithm,depth,trials,mean_metric,std_metric,mean_u,solved_rate,metric_name")) { 
                for (SummaryRow s : summary) { 
                    if (!s.domain.equals(domain)) continue; 
                    w.writeRow(s.domain, s.algorithm, s.depth, s.trials, 
                            fmt(s.meanMetric), fmt(s.stdMetric), fmt(s.meanU), fmt(s.solvedRate), 
s.metricName); 
                } 
            } 
        } 
 
        /** Behavioural Efficiency Index at d=10: eta = U*q / (N * log_b(N+1)). */ 
        private static void writeTable7(List<SummaryRow> summary) throws IOException { 
            try (CsvWriter w = new CsvWriter("results/tables/table7_bei.csv", 
"domain,algorithm,depth,q,eta")) { 
                for (SummaryRow s : summary) { 
                    if (s.depth != 10) continue; 
                    double q = !Double.isNaN(s.meanQ) ? s.meanQ : 1.0; 
                    double N = s.meanMetric, U = s.meanU; 
                    double denom = N * (Math.log(N + 1) / Math.log(MODEL_BASE_B)); 
                    double eta = denom > 1e-12 ? (U * q) / denom : 0.0; 
                    w.writeRow(s.domain, s.algorithm, s.depth, fmt(q), fmt(eta)); 
                } 
            } 
        } 
 
        private static String fmt(double v) { return String.format(Locale.US, "%.6f", v); } 
    } 
 
    // ============================================================ 
    // BUILT-IN VALIDATION TESTS 
    // ============================================================ 
 
    static final class ValidationTests { 
 
        static boolean runAll() { 
            boolean ok = true; 
            ok &= check("MiniMax exact count at complete 4-ary tree, d=10", 
ValidationTests::testMiniMaxExactCount); 
            ok &= check("MiniMax/AlphaBeta depth-12 collapses onto depth-10", 
ValidationTests::testDepthCapCollapse); 
            ok &= check("Alpha-Beta value matches MiniMax value on random trees", 
ValidationTests::testAlphaBetaMatchesMiniMax); 
            ok &= check("BFS: reachable cells expanded at most once, valid path", 
ValidationTests::testBfsReachability); 
            ok &= check("KMP matches String.indexOf on exact match", () -> 
testKmpVsIndexOf("hello world", "world")); 
            ok &= check("KMP matches String.indexOf on no match", () -> testKmpVsIndexOf("aaaa", 
"bbbb")); 
            ok &= check("KMP matches String.indexOf on repeated characters", () -> 
testKmpVsIndexOf("aaaaaa", "aaa")); 
            ok &= check("KMP matches String.indexOf on pattern longer than text", () -> 
testKmpVsIndexOf("ab", "abcdef")); 
            ok &= check("KMP handles empty pattern", ValidationTests::testKmpEmptyPattern); 
            ok &= check("MAPF: no obstacle occupied / no vertex collision / no swap collision", 
ValidationTests::testMapfConstraints); 
            ok &= check("PowerLawFitter recovers exact exponent on synthetic N = 3*d^2", ValidationTests::testPowerLawSynthetic); 
            ok &= check("ModelFitter recovers exact beta with degenerate CI on synthetic exponential", ValidationTests::testExpFitSynthetic); 
            ok &= check("MiniMax retains all children; Alpha-Beta retains a strict subset", ValidationTests::testRetainedBranching); 
            ok &= check("Dijkstra cost is optimal per instance (measured q well-defined)", ValidationTests::testSolutionCostOptimality); 
            return ok; 
        } 
 
        private interface TestCase { boolean run(); } 
 
        static boolean testRetainedBranching() {
            GameTree gt = new GameTree(4, 8, 8, 0, 100, 4242L);
            GameSearchAlgorithms.Result mm = GameSearchAlgorithms.miniMax(gt);
            GameSearchAlgorithms.Result ab = GameSearchAlgorithms.alphaBeta(gt);
            if (mm.internalNodes == 0 || mm.retainedSum != 4 * mm.internalNodes) return false;
            return ab.internalNodes > 0 && ab.retainedSum > 0
                    && ab.retainedSum <= 4 * ab.internalNodes && ab.retainedSum < mm.retainedSum;
        }

        static boolean testSolutionCostOptimality() {
            GridGraph g = new GridGraph(12, 0.15, 1, 10, 424242L);
            GridSearchAlgorithms.Result dij = GridSearchAlgorithms.dijkstra(g);
            GridSearchAlgorithms.Result a1 = GridSearchAlgorithms.aStar(g, 1.0);
            GridSearchAlgorithms.Result wa = GridSearchAlgorithms.aStar(g, 1.5);
            GridSearchAlgorithms.Result gb = GridSearchAlgorithms.greedyBestFirst(g);
            GridSearchAlgorithms.Result bf = GridSearchAlgorithms.bfs(g);
            if (!dij.solved || dij.cost <= 0) return false;
            for (GridSearchAlgorithms.Result r : new GridSearchAlgorithms.Result[]{a1, wa, gb, bf})
                if (r.solved && r.cost < dij.cost) return false;
            return a1.cost == dij.cost;
        }

        private static boolean check(String name, TestCase tc) { 
            boolean passed; 
            String detail = ""; 
            try { 
                passed = tc.run(); 
            } catch (Throwable t) { 
                passed = false; 
                detail = " (" + t + ")"; 
            } 
            System.out.printf(" [%s] %s%s%n", passed ? "PASS" : "FAIL", name, detail); 
            return passed; 
        } 
 
        static boolean testMiniMaxExactCount() { 
            GameTree gt = new GameTree(4, 10, 10, 0, 100, 12345L); 
            GameSearchAlgorithms.Result r = GameSearchAlgorithms.miniMax(gt); 
            return r.expansions == 1_398_101L; 
        } 
 
        static boolean testDepthCapCollapse() { 
            GameTree gt10 = new GameTree(4, 10, 10, 0, 100, 555L); 
            GameTree gt12 = new GameTree(4, 12, 10, 0, 100, 555L); 
            return gt10.size == gt12.size 
                    && gt10.effectiveDepth == gt12.effectiveDepth 
                    && gt12.isDepthCapped() 
                    && !gt10.isDepthCapped(); 
        } 
 
        static boolean testAlphaBetaMatchesMiniMax() { 
            for (long seed = 0; seed < 20; seed++) { 
                for (int depth : new int[]{2, 4, 6}) { 
                    GameTree gt1 = new GameTree(3, depth, depth, 0, 100, seed); 
                    GameTree gt2 = new GameTree(3, depth, depth, 0, 100, seed); 
                    int mm = GameSearchAlgorithms.miniMax(gt1).value; 
                    int ab = GameSearchAlgorithms.alphaBeta(gt2).value; 
                    if (mm != ab) return false; 
                } 
            } 
            return true; 
        } 
 
        static boolean testBfsReachability() { 
            GridGraph g = new GridGraph(10, 0.0, 1, 1, 42L); 
            GridSearchAlgorithms.Result r = GridSearchAlgorithms.bfs(g); 
            return r.solved && r.expansions <= g.totalCells() && r.pathLength > 0; 
        } 
 
        static boolean testKmpVsIndexOf(String text, String pattern) { 
            boolean expected = text.indexOf(pattern) >= 0; 
            boolean actual = PatternAlgorithms.kmp(text.toCharArray(), 
pattern.toCharArray()).solved; 
            return expected == actual; 
        } 
 
        static boolean testKmpEmptyPattern() { 
            PatternAlgorithms.Result r = PatternAlgorithms.kmp("abc".toCharArray(), 
"".toCharArray()); 
            return r.solved && r.comparisons == 0; 
        } 
 
        static boolean testMapfConstraints() { 
            for (long seed = 300L; seed < 320L; seed++) { 
                MAPFGrid grid = new MAPFGrid(8, 0.10, seed); 
                int[] starts = PrioritizedPlanning.placeAgents(grid, 3, seed, false); 
                int[] goals = PrioritizedPlanning.placeAgents(grid, 3, seed, true); 
                PrioritizedPlanning.TeamResult res = PrioritizedPlanning.plan(grid, starts, goals, 60); 
 
                if (!PrioritizedPlanning.noObstacleOccupied(grid, res.paths)) return false; 
                if (!PrioritizedPlanning.noVertexCollision(res.paths)) return false; 
                if (!PrioritizedPlanning.noSwapCollision(res.paths)) return false; 
                if (res.solvedAgents > res.totalAgents) return false; 
            } 
            return true; 
        } 

        private static List<SummaryRow> synthetic(String alg, double[] nAtDepths) { 
            List<SummaryRow> rows = new ArrayList<>(); 
            int[] depths = {4, 6, 8, 10, 12}; 
            for (int i = 0; i < depths.length; i++) { 
                SummaryRow sr = new SummaryRow(); 
                sr.domain = "Synthetic"; sr.algorithm = alg; sr.depth = depths[i]; 
                sr.trials = 30; sr.meanMetric = nAtDepths[i]; sr.stdMetric = 0; 
                sr.meanU = 1; sr.solvedRate = 1.0; sr.metricName = "node_expansions"; 
                rows.add(sr); 
            } 
            return rows; 
        } 

        static boolean testPowerLawSynthetic() { 
            // N(d) = 3*d^2 exactly => k=2, c=3, R^2=1, zero held-out error. 
            double[] n = new double[5]; 
            int[] depths = {4, 6, 8, 10, 12}; 
            for (int i = 0; i < 5; i++) n[i] = 3.0 * depths[i] * depths[i]; 
            List<PowerLawFitter.FitRow> fits = PowerLawFitter.fitAll(synthetic("SynthPow", n), ExperimentConfig.defaultConfig()); 
            if (fits.size() != 1) return false; 
            PowerLawFitter.FitRow f = fits.get(0); 
            return "ok".equals(f.status) 
                    && Math.abs(f.kHat - 2.0) < 1e-9 
                    && Math.abs(f.cHat - 3.0) < 1e-9 
                    && Math.abs(f.r2Train - 1.0) < 1e-9 
                    && f.mapeTest < 1e-6 
                    && Math.abs(f.kCiLow - 2.0) < 1e-6 
                    && Math.abs(f.kCiHigh - 2.0) < 1e-6; 
        } 

        static boolean testExpFitSynthetic() { 
            // N(d) = 2 * 4^(0.5*d) exactly => beta=0.5, alpha=2, zero held-out error, degenerate CI. 
            double[] n = new double[5]; 
            int[] depths = {4, 6, 8, 10, 12}; 
            for (int i = 0; i < 5; i++) n[i] = 2.0 * Math.pow(4.0, 0.5 * depths[i]); 
            List<ModelFitter.FitRow> fits = ModelFitter.fitAll(synthetic("SynthExp", n), ExperimentConfig.defaultConfig()); 
            if (fits.size() != 1) return false; 
            ModelFitter.FitRow f = fits.get(0); 
            return "ok".equals(f.status) 
                    && Math.abs(f.betaHat - 0.5) < 1e-9 
                    && Math.abs(f.alphaHat - 2.0) < 1e-9 
                    && f.mapeTest < 1e-6 
                    && Math.abs(f.betaCiLow - 0.5) < 1e-6 
                    && Math.abs(f.betaCiHigh - 0.5) < 1e-6; 
        } 
    } 
}
