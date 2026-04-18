# Distributed Algorithms Simulator

## Overview

This project implements a distributed systems simulator that maps graph topologies to Akka actors. Each graph node becomes one actor and each directed edge becomes an outgoing communication channel to a destination ActorRef. The simulator supports configurable workloads, message filtering, randomized traffic generation, interactive and file-driven message injection, and leader-election algorithms.

## Implemented Features

* Graph to actor runtime translation
* Configurable graph generation
* Graph enrichment / edge labeling
* Message filtering based on labels
* File-driven injection mode
* Interactive injection mode
* Timer-based randomized message generation with configurable probabilities
* Metrics via Cinnamon / Akka Insights
* Two distributed algorithms:

    * Hirschberg-Sinclair Leader Election
    * Tree Election

## Repository Structure

* `core/generator` - graph generation - NetGameSim submodule
* `core/enricher` - graph enrichment / labels
* `core/translator` - runtime simulator
* `core/algorithms` - distributed algorithms
* `core/framework` - shared runtime framework - CS553 as submodule

## Requirements

* Java 17+
* sbt
* Internet connection for dependency resolution
* Optional: Akka token for Cinnamon dependencies

## Environment Setup

### macOS / Linux

```bash
export AKKA_TOKEN="your-token"
```

### Windows PowerShell

```powershell
$env:AKKA_TOKEN="your-token"
```

## Clean Build

```
git clone --recursive https://github.com/sohan-naidu/Distributed-Algorithms-Simulator.git
cd Distributed-Algorithms-Simulator/
cd generator && sbt clean compile assembly
cd ../..
sbt clean compile test
```

## Sample Configuration for Hirschberg-Sinclair

### Step 1: Generate Graph
### Requires Bidirectional Chain Structure. Replace the contents of `application.conf` with `sim-hs.conf` before running.
### The conf files can be found in `core/src/main/resources/`
```bash
sbt "cli/run generate"
```

Generated files are written to `output/generated/`.

## Step 2 - Enrich Graphs

Attach labels / metadata to edges.

```bash
sbt "cli/run enrich"
```
The enriched output can be seen in `output/enriched/`

## Step 3 - Run the Simulator

```bash
sbt "cli/run simulate --algorithm hs --inject file --duration 60"
```

## Sample Configuration for Tree Election

### Replace the contents of `application.conf` with `sim-te.conf` before running.

## Step 1: Generate Graph
```bash
sbt "cli/run generate"
```

Generated files are written to `output/generated/`.

## Step 2 - Enrich Graphs

Attach labels / metadata to edges.

```bash
sbt "cli/run enrich"
```
The enriched output can be seen in `output/enriched/`

## Step 3 - Run the Simulator

```bash
sbt "cli/run simulate --algorithm te --inject interactive --duration 30"
```

## Injection Modes

## File Mode

All injections are read from `input/input.json` if set.

## Interactive Mode

Start the simulator in interactive mode and type commands:

```text
inject <sourceNode> <message>
exit
```

Examples:

```text
inject 3 Ping
```

```text
inject 10 Election
```

## Metrics

Cinnamon metrics are emitted to logs. These can be found in `output/cinnamon-metrics.log`

## Testing

Run all tests:

```bash
sbt test
```

## Reproducibility

* Configuration files define graph topology and runtime parameters
* Seeded per-node random generators are used for repeatable behavior
* Same config + same input graph reproduces runs deterministically where applicable

## Troubleshooting

### Dependency Resolution Fails

Ensure Java and sbt are installed and internet access is available.

### Cinnamon Dependency Errors

Set `AKKA_TOKEN` before running sbt.

### File Not Found

Verify relative paths from repository root.
