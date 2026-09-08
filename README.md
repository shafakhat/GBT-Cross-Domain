# GBT Cross-Domain Tree-Search Experiment

Reproducible measurement artifact and data archive for:

> **"Regime Structure in Tree Search: Theory and Experimental Cross-Domain Behavioural Analysis"**
> Shafakhatullah Khan Mohammed, Shaik Nagur Vali, Mourad Mohammed Henchiri, Thota SivaLakshmaiah.
> Preprint: SSRN, 7 June 2026 — <https://ssrn.com/abstract=6891367> (DOI: 10.2139/ssrn.6891367).
> Journal version: to appear; the permanent archive link to this repository is added at camera-ready.

## What this repository is

A single-purpose, dependency-free experimental artifact implementing the **Generalised Behavioural
Tree (GBT)** measurement protocol: 12 search algorithms × 4 structurally distinct domains
(grid pathfinding, adversarial game trees, multi-agent pathfinding, sequential pattern matching)
× depths {4, 6, 8, 10, 12} × 30 seeded trials per configuration (1,800 raw trials), plus two
external-validation companions on public MovingAI grid benchmarks and real text corpora.

Headline result: the pruning-efficiency exponent β is not a fixed algorithm property but an
**algorithm–environment joint property**; cross-domain β̂ estimates cluster into three universal
regimes — exponential (β̂ ≈ 0.56–0.80, game trees), polynomial-frontier (β̂ ≈ 0.19–0.20,
grids/MAPF/pattern trees), and sub-linear skip-optimised (β̂ ≈ 0.10, GBFS/KMP/Naive) — with the
regime structure persisting across all four domains (R² ≥ 0.93).

Everything needed to reproduce every number in the paper's tables is in this repository.

## Contents

```
CrossDomainSearchExperiment.java   main artifact: 12 algorithms, 4 domains, 30 seeded trials,
                                   embedded 14-case validation suite; writes all CSVs below
MovingAIBenchmark.java             external validation on MovingAI grid benchmarks (ODC-BY data)
CorporaBenchmark.java              external validation on real corpora (mtDNA, Gutenberg text)
config/experiment.yaml             documented configuration (seeds, caps, rates, geometry)
benchmark_data/                    the exact 28 external input files used (SHA-256 manifest below)
data/raw/{grid,game_tree,mapf,pattern}.csv      run-4 trial-level raw data (1,800 rows total)
data/processed/summary.csv         per-configuration mean ± SD, solved rate, q, r_mean
results/tables/table1..8_*.csv     paper tables (algorithms, grid, game, MAPF, pattern,
                                   held-out β fits, BEI, power-law fits)
results/benchmark/*.csv            MovingAI + corpora companion outputs (raw, fits, panel)
run_log_run4.txt                   console log of the archived run (14/14 tests PASS, exit 0)
README.md                          this file
```

## Requirements

- **JDK 17+** (switch expressions require ≥ 14; tested on Temurin 17). No third-party libraries.
- Deep recursion (IDA*/IDDFS on 48×48 grids) needs a larger stack: `-Xss64m`.
- Optional, for figures only: Python 3 with matplotlib.

## Reproduce everything (three commands)

```bash
# 1. Main experiment: re-runs all 1,800 trials + validation suite (aborts on any failure),
#    then overwrites data/, data/processed/, results/tables/
javac -encoding UTF-8 CrossDomainSearchExperiment.java
java  -Xss64m CrossDomainSearchExperiment

# 2. MovingAI external validation
javac -encoding UTF-8 -d cls MovingAIBenchmark.java
java  -Xss64m -cp cls MovingAIBenchmark benchmark_data results/benchmark

# 3. Corpora external validation
javac -encoding UTF-8 -d cls CorporaBenchmark.java
java  -cp cls CorporaBenchmark benchmark_data results/benchmark
```

`-encoding UTF-8` is required (source contains UTF-8 dashes). Expansion counts are
byte-identical across runs; only the `runtime_ms` column varies with hardware.

## Determinism & seeds

All randomness derives from `java.util.Random(seed)` with `seed = κ·d + t`,
κ ∈ {100, 200, 300, 400}, t = 1…30. Seed uniqueness is enforced within each
(domain, algorithm, depth) configuration; generators are structurally disjoint across domains and
no reported statistic pools draws across domains.

## Counting rules (what each integer counter measures)

- **Grid / MAPF:** states popped from the open list and processed (closed-set deduplicated where applicable).
- **Game trees:** tree nodes evaluated (MiniMax / Alpha-Beta).
- **Pattern:** character-vs-character equality tests (KMP includes failure-function and backtracking comparisons).
- Paper means are over **solved** trials; unsolved counts remain in the raw CSVs (`solved` column).

## Validation suite (14/14 PASS on every run)

Exact MiniMax count (4^{d+1}−1)/3 at d=10; depth-cap collapse d=12 ≡ d=10 shape;
Alpha-Beta ≡ MiniMax game value; BFS reachability on obstacle grids; KMP vs `String.indexOf`
equivalence; MAPF vertex/edge collision and horizon constraints; power-law synthetic recovery
(N=3d² ⇒ k̂=2); exponential synthetic recovery (N=2·4^{0.5d} ⇒ β̂=0.5); plus consistency checks
(30 trials per configuration, no duplicate seeds, no non-positive counters).

## Documented exclusions (mirrored in the paper)

- **IDA\***: saturated at the 3,000,000-node cap (29/30 trials at d=4; 30/30 at d≥6) → excluded from all fits and BEI.
- **Game trees d=12**: identical capped shape as d=10 → excluded from MiniMax/Alpha-Beta fits and held-out MAPE.

## External validation & statistics

- `MovingAIBenchmark.java` (MovingAI grids, Sturtevant 2012, ODC-BY): 25 instances per map at
  evenly spaced scenario positions; 4-connected unit-cost searches; saturation exclusion rule
  (≥50% capped at any ladder depth ⇒ family/algorithm excluded). Solution quality q measured
  against the artifact's own 4-connected Dijkstra reference (official scenario optima assume
  octile movement and are retained as instance descriptors only).
- `CorporaBenchmark.java`: human mtDNA (NC_012920.1, 16,568 bases) and Project Gutenberg text
  (446,808 filtered characters); protocol mirrors the synthetic pattern domain.
- Both companions report **bootstrap percentile CIs** (B=2000, seed 20260906) in
  `movingai_fits.csv` / `corpora_fits.csv` (`boot95_low/boot95_high`); all 60 point estimates lie
  inside their bootstrap intervals (machine-checked).

## Benchmark data provenance & SHA-256 manifest

`benchmark_data/` is pruned to exactly the 28 files the companions use; full sets are
re-downloadable from movingai.com/benchmarks (ODC-BY). Checksums of the archived copies:

```
c1be6a222e9b138e64d65ad50da92fca447f75191af9aa3022994fe3487abe56  benchmark_data/mapf/Berlin_1_256.map
bc9c572a3d1c5b0273e17ca9af2faa3c685eff669dfbd2561b007c0340ae0e04  benchmark_data/mapf/Boston_0_256.map
85ec535004685c9fb474954a24bd14395a21cea338bdcfdf7d9a2f21c587f87d  benchmark_data/mapf/Paris_1_256.map
27a570a564cf8de828619efa09d510df95f0cbfe2840376b7c3e63c96413689a  benchmark_data/mapf/empty-16-16.map
5b11a28f65d09a0ba260b77cb698bb22c73cfe1e1f5e159997de6108cd31bf68  benchmark_data/mapf/empty-32-32.map
9d13ddc8f39d3e64f2cabea9a81c9d4f... (see CHECKSUMS.txt for the complete 28-line manifest)
```

The complete 28-line manifest is provided in **`CHECKSUMS.txt`** at the repository root;
verify with `sha256sum -c CHECKSUMS.txt` from inside `benchmark_data/`-adjusted paths.

## Licensing

- **Code** (the three `.java` files, config): MIT License.
- **Data produced by the artifact** (all CSVs, logs): CC-BY 4.0.
- **Third-party inputs**: MovingAI benchmarks © Nathan R. Sturtevant, ODC-BY (attribution
  retained here); human mtDNA sequence: NCBI NC_012920.1 (public domain); Sherlock Holmes text:
  Project Gutenberg (public domain, per its license terms).

## Citation

```bibtex
@misc{mohammed2026regime-artifact,
  title  = {GBT Cross-Domain Tree-Search Experiment (code and data)},
  author = {Mohammed, Shafakhatullah Khan and Vali, Shaik Nagur and
            Henchiri, Mourad Mohammed and SivaLakshmaiah, Thota},
  year   = {2026},
  publisher = {GitHub},
  url    = {https://github.com/<ACCOUNT>/<REPO>},
  note   = {Companion artifact to the SSRN preprint arXiv-equivalent:
            https://ssrn.com/abstract=6891367}
}
```

## Contact

Shafakhatullah Khan Mohammed — shafakhatullah.khan@aceec.ac.in
Department of Computer Science and Engineering, ACE Engineering College, Hyderabad, India.
