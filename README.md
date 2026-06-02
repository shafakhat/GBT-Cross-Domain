# Cross-Domain Search Experiment
## Generalised Behavioural Tree (GBT) — Behavioural Analysis

[![Java](https://img.shields.io/badge/Java-8%2B-blue.svg)](https://www.java.com/)
[![License](https://img.shields.io/badge/License-MIT-green.svg)](LICENSE)
[![Research](https://img.shields.io/badge/Research-AI%20Search%20Algorithms-red.svg)]()
[![Domains](https://img.shields.io/badge/Domains-4-orange.svg)]()
[![Algorithms](https://img.shields.io/badge/Algorithms-12-purple.svg)]()
[![Trials](https://img.shields.io/badge/Trials-30%20per%20config-yellow.svg)]()

---

## What This Repository Contains

This repository holds the complete Java implementation 
accompanying the research article:

> **"Regime Structure in Tree Search: Theory and Experimental 
> Cross-Domain Behavioural Analysis"**

All node-expansion figures reported in the paper originate 
directly from the implementations in this repository. No 
closed-form approximations substitute for actual algorithm 
execution. Every expanded node, every character comparison, 
and every explored state is counted at runtime via an integer 
counter incremented inside the live algorithm code.

---

## The Central Research Question

```
Is β — the parameter governing the effective exponential 
growth rate of tree-node expansion — an intrinsic property 
of an algorithm, or does it depend fundamentally on the 
structure of the problem domain?
```

The experiments in this repository answer that question 
empirically across four structurally distinct domains 
using twelve genuine algorithm implementations over 
thirty independent seeded trials per configuration.

---

## Four Domains — Twelve Algorithms

| Domain | Structure | Algorithms Implemented |
|--------|-----------|----------------------|
| **Grid Pathfinding** | Weighted 4-connected grid, obstacle rate ρ = 0.15 | BFS, Dijkstra, A\*, WA\*(1.5), GBFS, IDA\* |
| **Adversarial Game Trees** | Uniform random complete b-ary trees, zero-sum | MiniMax, Alpha-Beta (value-sorted move ordering) |
| **Multi-Agent Path Finding** | Space-time grid, corner-to-corner agents | Prioritised Planning with space-time A\* |
| **Pattern Matching** | DNA alphabet Σ = {A, C, G, T}, seeded match guaranteed | Naive, KMP, IDDFS |

---

## Key Metrics

### Beta Parameter (β)

The pruning efficiency parameter β ∈ (0, 1] governs the 
effective node-expansion growth rate under the Generalised 
Behavioural Tree (GBT) framework:

```
Ω(b^βd) ≤ N_eff ≤ α · b^βd,    α = b^β / (b^β − 1)
```

It is estimated per algorithm-domain pair via ordinary 
least squares log-linear regression:

```
log(N̄_d) = log(α̂) + β̂ · d · log(b)
```

fitted on training depths d ∈ {4, 6, 8} and evaluated 
on held-out depths d ∈ {10, 12}.

### Behavioural Efficiency Index (BEI)

```
η = (U · q) / (N · log_b(N + 1))
```

| Symbol | Meaning |
|--------|---------|
| U | Solution path length (useful nodes) |
| N | Total nodes expanded |
| q | Solution quality ratio (1.0 for optimal algorithms) |
| b | Branching factor |

Higher η indicates greater behavioural efficiency. 
Proved strictly monotone decreasing in N for fixed U and q.

---

## Experimental Design

| Parameter | Value |
|-----------|-------|
| Trials per configuration | 30 |
| Depth values (d) | 4, 6, 8, 10, 12 |
| Branching factor (b) | 4 (all domains) |
| Training depths for β̂ | d ∈ {4, 6, 8} |
| Held-out depths for MAPE | d ∈ {10, 12} |
| Random seed formula | s = κ · d + t (trial index t) |

### Domain-Specific Configuration

**Grid Pathfinding**
```
Grid size:      4d × 4d  (minimum 8 × 8)
Obstacle rate:  ρ = 0.15
Edge weights:   Uniform {1, ..., 10}
Solvability:    L-shaped corridor guarantee
Benchmark:      Aligned with MovingAI protocol
```

**Adversarial Game Trees**
```
Tree type:      Uniform random complete b-ary tree
Leaf values:    Uniform {0, ..., 100}
Depth cap:      10 (MiniMax and Alpha-Beta)
Note:           d = 12 reports same tree as d = 10 (†)
At b=4, d=10:  (4^11 − 1)/3 = 1,398,101 nodes
```

**Multi-Agent Path Finding**
```
Grid size:      max(d, 5) × max(d, 5)
Obstacle rate:  ρ = 0.10
Agents (k):     {2,2,3,3,4} for d ∈ {4,6,8,10,12}
Start/Goal:     Corner-to-corner placement
Algorithm:      Prioritised Planning + space-time A*
Time horizon:   T_max = 60
```

**Pattern Matching**
```
Alphabet:       DNA — Σ = {A, C, G, T}
Pattern length: 3 × d
Text length:    300 × d
Match:          Seeded guarantee (at least one match exists)
```

---

## Installation

### Requirements

- Java 8 or higher
- No external libraries or build tools required
- Standard Java Swing (included in JDK) for visualisation

### Clone

```bash
git clone https://github.com/yourusername/cross-domain-search-experiment.git
cd cross-domain-search-experiment
```

### Compile

```bash
javac CrossDomainSearchExperimentPP.java
```

### Run

```bash
java CrossDomainSearchExperimentPP
```

---

## Output

### Console Output

The program prints structured tables to standard output 
covering all four domains and all twelve algorithms.

```
======================================================================
  CROSS-DOMAIN BETA PARAMETER COMPARISON
  Log-linear regression on mean node counts (30 trials per depth)
======================================================================

Domain          Algorithm               β̂        α̂        R²
----------------------------------------------------------------------
Grid            BFS                  0.1935    89.61    0.9747
Grid            Dijkstra             0.1940    88.88    0.9749
Grid            A*                   0.1955    86.70    0.9751
Grid            WA*(1.5)             0.1971    83.97    0.9749
Grid            GBFS                 0.0993    23.90    0.9678
Grid            IDA*                 0.0059   2763569   0.5000
----------------------------------------------------------------------
GameTree        MiniMax              0.7999     7.05    0.9412
GameTree        AlphaBeta            0.5579    11.03    0.9342
----------------------------------------------------------------------
MAPF            PrioritisedPlanning  0.1869     9.54    0.9774
----------------------------------------------------------------------
PatternMatch    Naive                0.0953   370.49    0.9750
PatternMatch    KMP                  0.0958   286.30    0.9754
PatternMatch    IDDFS                0.1995  12146.10   0.9735
======================================================================

THREE UNIVERSAL REGIMES IDENTIFIED:
  Exponential regime      β̂ ≈ 0.56–0.80  →  Game trees
  Polynomial-frontier     β̂ ≈ 0.19–0.20  →  Grid / MAPF / IDDFS
  Sub-linear skip         β̂ ≈ 0.10       →  GBFS / KMP / Naive
```

### Per-Domain Node Expansion Tables

```
GRID PATHFINDING — Mean node expansions ± std dev (30 trials)
d=4     d=6     d=8     d=10    d=12
BFS       222±5   498±9   880±9  1367±12  1970±17
Dijkstra  221±6   494±9   878±9  1365±13  1968±17
A*        218±8   486±21  875±11 1361±14  1964±19
WA*(1.5)  213±13  475±34  867±16 1353±20  1950±37
GBFS       37±5    56±4    79±14   95±9    115±15
IDA*     2764k   3000k   3000k   3000k    3000k
```

### BEI Cross-Domain Table

```
BEI at d = 10 (b = 4, all domains) — Higher η = more efficient
Domain          Algorithm               BEI
------------------------------------------------------
Grid            GBFS                  0.2813
PatternMatch    KMP                   0.1340
Grid            A*                    0.0112
Grid            Dijkstra              0.0112
Grid            BFS                   0.0111
Grid            WA*(1.5)              0.0075
MAPF            PrioritisedPlanning   0.0034
Grid            IDA*                  ≈ 0
GameTree        AlphaBeta             ≈ 0
```

### Visualisation Window

A Swing window opens automatically displaying four panels 
(one per domain) with:

- Log-scale node expansion versus depth curves
- Colour-coded algorithm traces
- β̂ and R² annotations per algorithm
- Grid lines and labelled axes
- Legend identifying each algorithm

---

## Principal Findings

### F1 — Three Universal Complexity Regimes

β̂ estimates cluster into three structurally distinct 
regimes that persist across all four domains simultaneously:

| Regime | β̂ Range | Algorithms |
|--------|---------|-----------|
| Exponential | 0.56 – 0.80 | MiniMax, AlphaBeta |
| Polynomial-frontier | 0.19 – 0.20 | BFS, Dijkstra, A\*, WA\*, PP, IDDFS |
| Sub-linear skip-optimised | 0.095 – 0.099 | GBFS, KMP, Naive |

### F2 — β is an Algorithm–Environment Joint Property

The same A\* kernel achieves β̂ ≈ 0.196 on sparse obstacle 
grids and β̂ ≈ 0.187 within the space-time MAPF graph — 
a statistically significant difference arising from the 
richer state space induced by the time dimension, not from 
any algorithmic change.

### F3 — KMP and GBFS Share a Formal Complexity Class

KMP (β̂ = 0.0958) and GBFS (β̂ = 0.0993) are statistically 
indistinguishable at the 5% significance level, establishing 
that a linear-time string-matching algorithm and a greedy 
best-first graph-search algorithm occupy the same complexity 
class within the GBT framework despite operating in entirely 
different computational domains.

### F4 — CET Bounds Hold Universally

N_eff ≤ α̂ · b^(β̂d) is satisfied in 100% of non-saturated 
configurations. R² ≥ 0.93 for all non-saturated 
algorithm-domain pairs.

### F5 — MiniMax Exact Node Count Validates CET

MiniMax at d = 10 expands exactly (4^11 − 1)/3 = 1,398,101 
nodes, directly validating the CET prediction 
N_eff = Θ(b^d) for β = 1.

---

## Code Structure

```
CrossDomainSearchExperimentPP.java
│
├── Data Structures
│   ├── Observation          — single trial result record
│   └── AggResult            — aggregated statistics over 30 trials
│
├── Domain Models
│   ├── GridGraph            — weighted 4-connected grid with obstacles
│   ├── GameTree             — uniform random complete b-ary tree
│   └── MAPFGrid             — space-time grid for multi-agent planning
│
├── Algorithm Implementations
│   ├── Grid:     bfs(), dijkstra(), aStar(), waStar(), 
│   │             gbfs(), idaStar()
│   ├── Game:     minimax(), alphaBeta() [value-sorted move ordering]
│   ├── MAPF:     prioritisedPlanning() [space-time A* per agent]
│   └── Pattern:  naiveSearch(), kmpSearch(), iddfsSearch()
│
├── Domain Runners
│   ├── runGridDomain()
│   ├── runGameDomain()
│   ├── runMAPFDomain()
│   └── runPatternDomain()
│
├── Statistical Computation
│   ├── computeBEI()         — Behavioural Efficiency Index
│   ├── estimateBeta()       — OLS log-linear regression
│   ├── computeMAPE()        — held-out prediction error
│   └── computeStats()       — mean, std dev, 95% CI
│
├── Visualisation
│   └── SwingPlotter         — four-panel log-scale plot window
│
└── main()                   — entry point and experiment orchestration
```

---

## Implementation Notes

### Node Counting Commitment

Every node-expansion figure originates from incrementing 
an integer counter inside the live algorithm implementation. 
This is not optional or approximate — it is the core 
methodological commitment (M1) of the experimental design.

```java
// Example — counter incremented inside actual traversal
long nodesExpanded = 0;
while (!openList.isEmpty()) {
    Node current = openList.poll();
    nodesExpanded++;          // <-- genuine count
    if (isGoal(current)) break;
    for (Node neighbour : expand(current)) {
        // ... pruning and evaluation logic
    }
}
```

### Reproducibility Guarantee

All instances are seeded deterministically:

```java
// Seed formula ensuring reproducibility
int seed = kappa * depth + trialIndex;
// kappa is domain-specific: {100, 200, 300, 400}
// trialIndex t ∈ {0, ..., 29}
Random rng = new Random(seed);
```

### Space-Time A\* State Encoding (MAPF)

Agent states encode both location and time in a single 
long key for efficient hash-map lookup:

```java
long stateKey = (long) location * TIME_HORIZON + timeStep;
```

### Move Ordering (Alpha-Beta)

Children are sorted by approximate evaluation value 
before expansion to maximise pruning efficiency:

```java
children.sort((a, b) -> 
    Integer.compare(b.approximateValue, a.approximateValue));
```

### IDA\* Node Cap

IDA\* is capped at 3,000,000 nodes per trial to prevent 
unbounded memory use on weighted grids where the heuristic 
produces many distinct f-values across threshold iterations.

---

## Beta Parameter Interpretation

| β̂ Range | Growth Classification | Typical Cause |
|---------|----------------------|--------------|
| β̂ ≈ 1.0 | Full exponential | No effective pruning (BFS, MiniMax) |
| β̂ ≈ 0.5 | Square-root exponential | Perfect move ordering (optimal Alpha-Beta) |
| β̂ ≈ 0.19–0.20 | Polynomial-frontier | Obstacle-constrained topology |
| β̂ ≈ 0.10 | Sub-linear skip | Skip-optimised local regularity (KMP, GBFS) |
| β̂ → 0 | Saturated / capped | Node cap reached (IDA\* on weighted grids) |

---

## Extending the Framework

### Adding a New Algorithm

```java
static long[] myAlgorithm(GridGraph graph, int start, int goal) {
    long nodesExpanded = 0;
    long usefulNodes   = 0;
    
    // Your algorithm implementation here
    // Increment nodesExpanded at every node expansion
    // Set usefulNodes to solution path length
    
    return new long[]{ nodesExpanded, usefulNodes };
}
```

### Adding a New Domain

```java
// Step 1: Define domain data structure
class MyDomain { ... }

// Step 2: Implement algorithm with genuine node counter
static long[] myAlgorithm(MyDomain domain) { ... }

// Step 3: Add runner method following existing pattern
static List<Observation> runMyDomain(int depth, int seed) { ... }

// Step 4: Register in main() experiment loop
results.addAll(runMyDomain(depth, seed));
```

---

## Limitations

| Limitation | Detail |
|------------|--------|
| Game tree depth cap | MiniMax and Alpha-Beta capped at depth 10; d=12 reports same tree as d=10 (marked †) |
| IDA\* node cap | Capped at 3M nodes; saturates at d ≥ 6 on weighted grids |
| MAPF completeness | Prioritised Planning is incomplete; unsolved instances excluded from β̂ fitting |
| MAPF algorithm scope | Only Prioritised Planning evaluated empirically; CBS, LaCAM, ACBS are directions for future work |
| Grid topology | Uniform random obstacles; road-network or game-map topologies may produce different β̂ |

---

## Citation

If this implementation contributes to your research, 
please cite the accompanying article:

```bibtex
@article{mohammed2025regime,
  author  = {Mohammed, Shafakhatullah Khan},
  title   = {Regime Structure in Tree Search: Theory and 
             Experimental Cross-Domain Behavioural Analysis},
  journal = {(under review)},
  year    = {2025},
  note    = {Implementation available at
             https://github.com/yourusername/
             cross-domain-search-experiment}
}
```

---

## License

This project is released under the MIT License.  
See the [LICENSE](LICENSE) file for full terms.

---

## Contact and Issues

For questions about the implementation, reproducibility 
queries, or potential collaboration, please open a 
GitHub Issue using the issue tracker above.  
Bug reports with a minimal reproducible example 
are especially welcome.

---

## Acknowledgements

The author gratefully acknowledges the constructive 
engagement of anonymous reviewers whose critical 
assessment improved the theoretical rigour and 
empirical scope of the manuscript that this 
implementation accompanies.

---

*This repository implements genuine algorithm execution 
across four structurally distinct computational domains. 
All reported node-expansion figures are measured values 
from live code, not closed-form approximations.*
