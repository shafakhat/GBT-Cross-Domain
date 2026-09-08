# Cross-Domain Search Experiment — Artifact README
## Generalised Behavioural Tree (GBT) — Behavioural Analysis
Reproducible measurement artifact for:
**"Regime Structure in Tree Search: Theory and Experimental Cross-Domain Behavioural Analysis"** (S. K. Mohammed, revised manuscript `../revised_article/article_v2.tex`).

[![Java](https://img.shields.io/badge/Java-8%2B-blue.svg)](https://www.java.com/)
[![License](https://img.shields.io/badge/License-MIT-green.svg)](LICENSE)
[![Research](https://img.shields.io/badge/Research-AI%20Search%20Algorithms-red.svg)]()
[![Domains](https://img.shields.io/badge/Domains-4-orange.svg)]()
[![Algorithms](https://img.shields.io/badge/Algorithms-12-purple.svg)]()
[![Trials](https://img.shields.io/badge/Trials-30%20per%20config-yellow.svg)]()

---
## Contents
- `CrossDomainSearchExperiment.java` — single dependency-free Java file: 12 algorithms × 4 domains × depths {4,6,8,10,12} × 30 seeded trials; embedded 14-case validation suite; writes raw trial CSVs, processed summary, and Tables 2–8 of the paper.
- `config/experiment.yaml` — documented configuration (seeds κ∈{100,200,300,400}, trials t=1…30, IDA* cap 3,000,000, game depth cap 10, obstacle rates, MAPF geometry/agent schedule).
- `data/` — run-3 raw trial-level CSVs (1,800 rows: grid 900, game 300, mapf 150, pattern 450).
- `data/processed/summary.csv` — per-configuration mean ± SD and solved rate (source for Figures 1, 3a/3b, 4, 5).
- `results/tables/table6_heldout_beta.csv` — exponential fits: β̂, 95% CI, α̂, train R², held-out MAPE (source for Fig 3a).
- `results/tables/table8_powerlaw.csv` — power-law fits: k̂, 95% CI, ĉ, R², MAPE (source for Figs 1–3).
- `results/tables/table7_bei.csv` — Behavioural Efficiency Index at d=10 (source for Fig 4).
- `data_run1/`, `results_run1/` — run-1 snapshot used for the determinism diff.
- `run_log.txt` — run-3 console log (12/12 tests PASS, exit 0).

## Requirements & run
- JDK 17+ (switch expressions require ≥14; tested on Temurin 17).
- Deep recursion (IDA*/IDDFS on 48×48 grids) needs a larger stack: `-Xss64m`.

```bash
javac -encoding UTF-8 CrossDomainSearchExperiment.java
java -Xss64m CrossDomainSearchExperiment
```

`-encoding UTF-8` is required (source contains UTF-8 dashes). The program re-reads
`config/experiment.yaml`, re-runs all trials, re-runs the validation suite (aborts on any failure),
and overwrites `data/`, `data/processed/`, `results/tables/`.

## Determinism
All randomness derives from `java.util.Random(seed)` with `seed = κ·d + t`. Two independent runs on
the same JVM produced byte-identical raw CSVs except the `runtime_ms` column (run 1 vs run 3 diff).
Seed scope: uniqueness is enforced *within* each (domain, algorithm, depth) configuration. Because
κ·d can coincide across domains at different depths (e.g. grid d=8 and game tree d=4 both yield
800+t), one stream may underlie instances of two domains; generators are structurally disjoint and
no reported statistic pools draws across domains. A globally unique scheme is future hygiene.

## Counting rules (what each integer counter measures)
- Grid/MAPF: states popped from the open list and processed (closed-set deduplicated where applicable).
- Game trees: tree nodes evaluated (MiniMax/Alpha-Beta).
- Pattern: character-vs-character equality tests (KMP includes failure-function and backtracking comparisons).
- Means in the paper are over **solved** trials; unsolved counts are in the raw CSVs (`solved` column).

## Instrumented columns (run 4)
- `data/raw/grid.csv`: `path_cost` = per-trial solution cost (entry-weight sum); enables measured
  solution quality q = Dijkstra-optimum / algorithm cost per instance (summary column `mean_q`).
- `data/raw/game_tree.csv`: `retained_sum`, `internal_nodes` = per-trial retained-branching totals
  (children actually evaluated per internal node); summary column `r_mean`.
- Headline instrumented results: MiniMax r_mean = 4.000000 at all depths (beta_mean = 1 = beta-hat);
  Alpha-Beta pooled r_mean 2.278 (d=4) to 1.979 (d=10); measured q at d=10: A*/Dijkstra/WA* 1.000,
  BFS 0.692, GBFS 0.624. Run-4 expansion counts are byte-identical to run 3 (instrumentation is
  non-invasive); 14/14 tests PASS.

## Validation suite (14/14 PASS)
Exact MiniMax count (4^{d+1}−1)/3 at d=10; depth-cap collapse d=12 ≡ d=10 shape; Alpha-Beta ≡
MiniMax game value; BFS reachability on obstacle grids; KMP vs `String.indexOf` equivalence; MAPF
vertex/edge collision and horizon constraints; power-law synthetic recovery (N=3d² ⇒ k̂=2);
exponential synthetic recovery (N=2·4^{0.5d} ⇒ β̂=0.5); plus consistency checks (30 trials per
configuration, no duplicate seeds, no non-positive counters).

## Exclusions (mirrored in the manuscript §5.2)
- IDA*: saturated at the 3,000,000-node cap (29/30 trials at d=4; 30/30 at d≥6) → excluded from all fits and BEI.
- Game trees d=12: identical capped shape as d=10 → excluded from MiniMax/Alpha-Beta fits and held-out MAPE.

## Figures
Figures are numbered by appearance in the manuscript and live in `../revised_article/figures/`:
`fig1_protocol_schematic.png` (protocol, §5), `fig2_regime_overview.png` (grid results, §6.3),
`fig3_theory_check.png` (game trees, §6.4), `fig4_forest_beta_k.png` (held-out fits, §6.7),
`fig5_mape_model_form.png` (model-form comparison, §6.8), `fig6_bei.png` (BEI, §6.9). The BEI
figure is regenerated by `../revised_article/figures/make_fig6_bei.py` (matplotlib, 300 dpi serif;
reads `data/processed/summary.csv` and `results/tables/*.csv`); the remaining figures are the
rendered PNG assets used by the manuscript. (Old draft names: protocol=fig6, overview=fig1,
theory=fig5, forest=fig3, mape=fig2, bei=fig4.)

## Benchmark data footprint
`benchmark_data/` is pruned to exactly the 28 files the benchmark artifacts use (9 `mapf` maps +
their 9 official scenarios, 2 `maze` + 2 `random` v2 maps with `.map.scen`, `mtdna.fasta`,
`sherlock.txt`); full sets are re-downloadable from movingai.com/benchmarks (ODC-BY).

## External-validation artifacts (run 5: benchmark companion programs)
Two additional dependency-free Java files validate the main artifact's regimes and protocol on
external data. They re-use the main artifact's search kernels, comparison counters, OLS fitter,
CI construction, and train/held-out split conventions unchanged.

### `MovingAIBenchmark.java` — MovingAI grid benchmarks (Sturtevant 2012, ODC-BY data)
- Run: `javac -encoding UTF-8 -d <cls> MovingAIBenchmark.java && java -Xss64m -cp <cls> MovingAIBenchmark benchmark_data results/benchmark`
- Data (`benchmark_data/`): empty ladder `mapf/empty-{8-8,16-16,32-32,48-48}.map` with official
  `mapf/scen-random/<map>-random-1.scen`; random10 ladder `mapf/random-{32-32,64-64}-10.map`
  (train) → `random/random512-10-0.map` (held out, v2 set with its `.map.scen`); panel maps
  `maze/maze512-1-0.map`, `maze/maze512-32-0.map`, `random/random512-40-0.map` (v2 sets) and street
  maps `mapf/Berlin_1_256.map`, `mapf/Boston_0_256.map`, `mapf/Paris_1_256.map` with official
  scenarios.
- Protocol: 25 instances per map sampled at evenly spaced positions across the scenario file
  (scenario files are bucketed by path length — a prefix sample would be degenerate); 4-connected
  unit-cost searches; IDA* node cap 3,000,000 with a saturation exclusion rule (≥50% capped at any
  ladder depth → family/algorithm excluded from fits).
- **q convention:** official scenario optima assume *octile* (8-connected) movement, while this
  artifact searches 4-connected like the main experiment. q is therefore measured against the
  artifact's own per-instance 4-connected Dijkstra reference; `scen_opt_octile` is retained in the
  raw CSV as an instance descriptor only.
- Outputs: `results/benchmark/movingai_raw.csv` (per instance × algorithm: expansions, cost,
  dijkstra_cost, scen_opt_octile, solved, capped), `movingai_fits.csv` (exp/pow slope, 95% CI,
  R²_train, held-out MAPE; random ladder has 2 training sizes → CI undefined),
  `movingai_panel.csv` (maze512 corridor 1/32, random512-40, Berlin/Boston/Paris: mean expansions,
  mean q).
- Key results: empty ladder Dijkstra pow k̂=1.9776 (held-out MAPE 10.75% vs exp 141.27%);
  A* 1.5850 (6.54% vs 108.53%); WA*=GBFS=IDA* 0.9524 (9.83% vs 44.34%) — identical because on
  obstacle-free maps all three follow the same first monotone staircase (shared neighbour
  ordering); random10 ladder Dijkstra 1.7751 (44.43% vs exp 4.19e7%), A* 1.4373 (72.79%),
  WA* 0.7239, GBFS 0.6989, IDA* excluded_saturated. Panel: corridor-1 maze all solvers q=1.000
  (unique paths); GBFS q 0.781–0.892 elsewhere; A*/Dijkstra/IDA* exactly optimal on all panels;
  IDA* expands 19×–95× Dijkstra (2.0–2.8M nodes).

### `CorporaBenchmark.java` — real text corpora
- Run: `java -cp <cls> CorporaBenchmark benchmark_data results/benchmark`
- Data: `benchmark_data/mtdna.fasta` (human mtDNA NC_012920.1, 16,568 bases ACGT) and
  `benchmark_data/sherlock.txt` (Project Gutenberg, 446,808 chars filtered A–Z).
- Protocol mirrors the synthetic pattern domain: m=3d patterns (natural occurrences at seeded
  positions) in the first n=300d corpus characters, d ∈ {4,6,8,10,12}, 30 trials/config,
  train {4,6,8} → held-out {10,12}. Naive/KMP/IDDFS counters are verbatim ports of the main
  artifact's.
- Outputs: `results/benchmark/corpora_summary.csv`, `corpora_fits.csv`.
- Key results: mtDNA Naive k̂=1.0004 CI[0.5893,1.4116] MAPE 1.05%, KMP 1.0002 MAPE 0.92%,
  IDDFS 2.0521 CI[1.8019,2.3023] MAPE 1.39%; Gutenberg Naive 0.7720 MAPE 2.58%, KMP 0.8708
  MAPE 1.57%, IDDFS 2.0486 CI[2.0092,2.0881] MAPE 0.10%. Exp-form MAPEs 16.3–62.8%. Linear and
  quadratic string regimes replicate on real data; exponential form rejected.

Manuscript integration: §6.10 + Table 9 (`tab:movingai-fits`) + Table 10 (`tab:corpora-fits`);
all 112 numeric cells machine-verified against the CSVs above.

## Hardening pass (run 6)
- Both benchmark artifacts now report **bootstrap percentile CIs** (B=2000, seed 20260906,
  instance/trial-level resampling per training size) in `movingai_fits.csv` / `corpora_fits.csv`
  (columns `boot95_low/boot95_high`, replacing t-CIs). Exp-form bootstrap x-axis matches `fit()`:
  x = d·ln4. All 60 point estimates lie inside their bootstrap intervals (machine-checked).
- `CorporaBenchmark` additionally writes trial-level `corpora_raw.csv` (901 rows).
- Tables 9–10 of the manuscript are generated programmatically from these CSVs; 207/207 numeric
  cells across all ten manuscript tables trace to artifact CSVs.

## Benchmark data provenance (accessed September 2026)

SHA-256 checksums of every external benchmark input used by MovingAIBenchmark.java and CorporaBenchmark.java:

```
c1be6a222e9b138e64d65ad50da92fca447f75191af9aa3022994fe3487abe56  ./mapf/Berlin_1_256.map
bc9c572a3d1c5b0273e17ca9af2faa3c685eff669dfbd2561b007c0340ae0e04  ./mapf/Boston_0_256.map
85ec535004685c9fb474954a24bd14395a21cea338bdcfdf7d9a2f21c587f87d  ./mapf/Paris_1_256.map
27a570a564cf8de828619efa09d510df95f0cbfe2840376b7c3e63c96413689a  ./mapf/empty-16-16.map
5b11a28f65d09a0ba260b77cb698bb22c73cfe1e1f5e159997de6108cd31bf68  ./mapf/empty-32-32.map
9d13ddc8f39d3e64f2cabea9a81c8d298d0f4a955b104589aea71b07b2606d4f  ./mapf/empty-48-48.map
42776e4904ec90689dd034fbd84524d671dc554472cc4221432b0c523d51f152  ./mapf/empty-8-8.map
4240fddfa77d88b72ce779e02acf46a5ff056a3b04af5a4e35f7bc86cdfba3ec  ./mapf/random-32-32-10.map
b31c671228f884a113ca11c41b83630dc042e58e07f9b36da74ec508f82a5659  ./mapf/random-64-64-10.map
1804b34f97378035a128da24c17733cfd2a5d175d54023ea904a8f4ea85b55bf  ./mapf/scen-random/Berlin_1_256-random-1.scen
1aa768b8cc79ee862198d7d2c97a25f97013de2096444325be88fa429990a2f7  ./mapf/scen-random/Boston_0_256-random-1.scen
adaa088fdaecf1e115f6a777e110da62ca7cb78bf1d5b7a0f9ac9c3584c6f915  ./mapf/scen-random/Paris_1_256-random-1.scen
6eb8c10975b76bce9ea8d725490dac134e94a0dec5bdfae23ff427afbbd9c511  ./mapf/scen-random/empty-16-16-random-1.scen
4ce49401f9d9b9aece505bddb5da1e720450a9325c17f3817e0fa98069454342  ./mapf/scen-random/empty-32-32-random-1.scen
1e1256c73066a526affd08df4d93c9eeafc4ecafc5577dde9cdb360920bf2bac  ./mapf/scen-random/empty-48-48-random-1.scen
f989c5aebaedff586a18ee763a8d01fd78c602e307e1ec2ae1617d868bf98b64  ./mapf/scen-random/empty-8-8-random-1.scen
277dc2ed57625d4fb62e28a3d974e192d598d54496d726bc68c755da312a6bec  ./mapf/scen-random/random-32-32-10-random-1.scen
6c1701dc331408bd92f0762abb039abddf47a0d3e81a1d097b374f29de3e75a7  ./mapf/scen-random/random-64-64-10-random-1.scen
4a053733e15477598968ae98fa1508d8a14d6181bcb3d79ca26c427904acc985  ./maze/maze512-1-0.map
e1553353e3fb90031c2b1ac95e1294f785613046c5b50949fca2bc4ea1982ca8  ./maze/maze512-1-0.map.scen
b25a0b21f0366e973b603ab700e24de99570c0f8a2c61a78c44a0807057b37fc  ./maze/maze512-32-0.map
c6624a27d975183d19813b91a017afe88e4cf5eda0fa2d39b9982e5b315cfc7f  ./maze/maze512-32-0.map.scen
fc392cde8e63b4d2e3a870bb97cc0626dea33d46dfb8abdebffada040f42ec92  ./mtdna.fasta
df47a4030f8967d8be60b33fecd1f6aa059132a2fe1e1b19e463dc52c0838654  ./random/random512-10-0.map
4321aa42397455bafef06c8d50c0a0e343c48b270ac51cec0c10acb9db45f418  ./random/random512-10-0.map.scen
d62707cbef07f43ddc8db58bc4e22def6af8ebab3059dc96ceafcbe2c8af8241  ./random/random512-40-0.map
4ca0797f0274e76440478b67bb24ca683c9b7cf6f661ec954046b7ae69e076cd  ./random/random512-40-0.map.scen
922e2a12ccb43a4c9544c260b2166c6ad2097aeb5957faeee113f173bb857cd0  ./sherlock.txt
```

