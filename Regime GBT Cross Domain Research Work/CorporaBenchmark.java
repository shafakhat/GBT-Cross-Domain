import java.io.*;
import java.nio.file.*;
import java.util.*;

/**
 * External-validation artifact (companion to CrossDomainSearchExperiment.java):
 * pattern-matching regimes on REAL corpora (human mitochondrial DNA, NC_012920.1;
 * Project Gutenberg text, "The Adventures of Sherlock Holmes").
 *
 * Protocol mirrors the synthetic pattern domain: for d in {4,6,8,10,12}, pattern length m = 3d,
 * text = the first n = 300d characters of the corpus, pattern = the corpus substring at seeded
 * position s mod (n - m) (a natural occurrence of real characters), 30 trials per configuration.
 * Counting rules are copied verbatim from the main artifact (naive stops at first match; KMP counts
 * failure-function and backtracking comparisons; IDDFS re-scans at every depth limit).
 *
 * Outputs (results/benchmark/): corpora_summary.csv, corpora_fits.csv.
 */
public final class CorporaBenchmark {

    static final double T975_1 = 12.7062;

    record Result(long comparisons, int len, boolean found) {}

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

    static boolean eq(char a, char b, long[] cnt) { cnt[0]++; return a == b; }

    static int[] buildFailure(char[] pattern, long[] cnt) {
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

    static int attempt(char[] text, char[] pattern, int start, int limit, long[] cnt) {
        int matched = 0;
        for (int j = 0; j < limit && j < pattern.length; j++) {
            cnt[0]++;
            if (text[start + j] != pattern[j]) return matched;
            matched++;
        }
        return matched;
    }

    static Result iddfs(char[] text, char[] pattern) {
        int n = text.length, m = pattern.length;
        if (m == 0) return new Result(0, 0, true);
        long[] cnt = {0};
        boolean found = false;
        for (int limit = 1; limit <= m && !found; limit++) {
            for (int start = 0; start <= n - m && !found; start++) {
                if (attempt(text, pattern, start, limit, cnt) == m) found = true;
            }
        }
        return new Result(cnt[0], found ? m : 0, found);
    }

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

    /** Trial-level bootstrap percentile CI of the log-scale slope (B=2000, fixed seed). */
    static double[] bootCI(Map<Integer, double[]> perD, int[] train, boolean expForm) {
        if (perD == null) return new double[]{Double.NaN, Double.NaN};
        for (int d : train) if (!perD.containsKey(d)) return new double[]{Double.NaN, Double.NaN};
        int B = 2000;
        double[] slopes = new double[B];
        java.util.Random rng = new java.util.Random(20260906L);
        for (int b = 0; b < B; b++) {
            double sx = 0, sy = 0, sxx = 0, sxy = 0;
            for (int d : train) {
                double[] v = perD.get(d);
                double sum = 0;
                for (int j = 0; j < v.length; j++) sum += v[rng.nextInt(v.length)];
                double x = expForm ? d * Math.log(4) : Math.log(d);
                double y = Math.log(Math.max(1.0, sum / v.length));
                sx += x; sy += y; sxx += x * x; sxy += x * y;
            }
            int n = train.length;
            slopes[b] = (n * sxy - sx * sy) / (n * sxx - sx * sx);
        }
        Arrays.sort(slopes);
        return new double[]{slopes[(int) (0.025 * B)], slopes[(int) (0.975 * B) - 1]};
    }

    static char[] loadCorpus(String path, String keep) throws IOException {
        StringBuilder sb = new StringBuilder();
        for (String line : Files.readAllLines(Paths.get(path))) {
            if (line.startsWith(">")) continue;
            for (char c : line.toCharArray()) {
                char u = Character.toUpperCase(c);
                if (keep.indexOf(u) >= 0) sb.append(u);
            }
        }
        return sb.toString().toCharArray();
    }

    public static void main(String[] args) throws IOException {
        String base = args.length > 0 ? args[0] : "benchmark_data";
        String out = args.length > 1 ? args[1] : "results/benchmark";
        Files.createDirectories(Paths.get(out));
        Map<String, char[]> corpora = new LinkedHashMap<>();
        corpora.put("mtDNA", loadCorpus(base + "/mtdna.fasta", "ACGT"));
        corpora.put("Gutenberg", loadCorpus(base + "/sherlock.txt", "ABCDEFGHIJKLMNOPQRSTUVWXYZ"));
        for (var e : corpora.entrySet()) System.out.println(e.getKey() + " chars=" + e.getValue().length);

        int[] depths = {4, 6, 8, 10, 12};
        String[] algs = {"Naive", "KMP", "IDDFS"};
        List<String> summ = new ArrayList<>();
        summ.add("corpus,algorithm,depth,trials,mean_comparisons,std_comparisons");
        List<String> rawL = new ArrayList<>();
        rawL.add("corpus,algorithm,depth,trial,seed,comparisons");
        Map<String, Map<Integer, double[]>> trialVals = new LinkedHashMap<>();
        Map<String, Map<Integer, Double>> means = new LinkedHashMap<>();
        for (var ce : corpora.entrySet()) {
            char[] full = ce.getValue();
            for (int d : depths) {
                int m = 3 * d, n = 300 * d;
                if (n > full.length) { System.out.println("skip " + ce.getKey() + " d=" + d); continue; }
                char[] text = Arrays.copyOfRange(full, 0, n);
                for (String alg : algs) {
                    long kappa = ce.getKey().equals("mtDNA") ? 500 : 600;
                    double[] vals = new double[30];
                    for (int t = 1; t <= 30; t++) {
                        long seed = kappa * d + t;
                        int pos = (int) Math.floorMod(seed, (long) (n - m));
                        char[] pattern = Arrays.copyOfRange(text, pos, pos + m);
                        Result r = switch (alg) {
                            case "Naive" -> naive(text, pattern);
                            case "KMP" -> kmp(text, pattern);
                            default -> iddfs(text, pattern);
                        };
                        vals[t - 1] = r.comparisons;
                        rawL.add(String.join(",", ce.getKey(), alg, String.valueOf(d), String.valueOf(t),
                                String.valueOf(seed), String.valueOf(r.comparisons)));
                    }
                    trialVals.computeIfAbsent(ce.getKey() + "|" + alg, k -> new TreeMap<>()).put(d, vals.clone());
                    double mean = 0; for (double v : vals) mean += v; mean /= vals.length;
                    double var = 0; for (double v : vals) var += (v - mean) * (v - mean); var /= (vals.length - 1);
                    summ.add(String.join(",", ce.getKey(), alg, String.valueOf(d), "30",
                            String.format(Locale.US, "%.1f", mean), String.format(Locale.US, "%.1f", Math.sqrt(var))));
                    means.computeIfAbsent(ce.getKey() + "|" + alg, k -> new TreeMap<>()).put(d, mean);
                }
            }
        }
        Files.write(Paths.get(out + "/corpora_summary.csv"), summ);
        Files.write(Paths.get(out + "/corpora_raw.csv"), rawL);
        List<String> fits = new ArrayList<>();
        fits.add("corpus,algorithm,form,slope,boot95_low,boot95_high,r2_train,mape_heldout");
        for (var e : means.entrySet()) {
            String[] k = e.getKey().split("\\|");
            Fit fe = fit(e.getValue(), new int[]{4, 6, 8}, new int[]{10, 12}, true);
            Fit fp = fit(e.getValue(), new int[]{4, 6, 8}, new int[]{10, 12}, false);
            double[] be = bootCI(trialVals.get(e.getKey()), new int[]{4, 6, 8}, true);
            double[] bp = bootCI(trialVals.get(e.getKey()), new int[]{4, 6, 8}, false);
            fits.add(String.join(",", k[0], k[1], "exp", f(fe.slope()), f(be[0]), f(be[1]), f(fe.r2()), f(fe.mape())));
            fits.add(String.join(",", k[0], k[1], "pow", f(fp.slope()), f(bp[0]), f(bp[1]), f(fp.r2()), f(fp.mape())));
        }
        Files.write(Paths.get(out + "/corpora_fits.csv"), fits);
        System.out.println("Wrote corpora_summary.csv and corpora_fits.csv");
        for (String line : fits) System.out.println("  " + line);
    }
}
