# Distributed-Algorithms-Simulator

## Getting Started
This project uses [NetGameSim](https://github.com/0x1DOCD00D/NetGameSim) and my fork of UIC's [CS553 course repository](https://github.com/0x1DOCD00D/CS553_2026/tree/main) taught during 2026 as submodules.
The fork has an updated `build.sbt` to use a newer version of Akka typed and Scala 
```
git clone --recursive https://github.com/sohan-naidu/Distributed-Algorithms-Simulator.git
```

## Quick Start
Set Akka token. If on Windows,
```
set $env:AKKA_TOKEN=<your-akka-token>
```
On MacOS/Linux
```
export AKKA_TOKEN="<your-akka-token>"
```
Optionally, these can be set in your `.bashrc` or `.zsh` or Windows environmental variables

For testing
```
sbt clean compile test
```

NetGameSim's jar needs to be built as the primary step. Ignore if the jar is built. However, the path to the jar will have to 
be passed each time a new graph needs to be generated.
```
cd core/generator && sbt clean compile assembly
```

Finally, build the root project from the project root
```
cd ../..             <---- only if the jar was built in the previous step
sbt clean reload compile
```

There are three main commands:
```
sbt "cli/run generate"
sbt "cli/run enrich"
sbt "cli/run simulate --algorithm <hs|te> --inject <file|interactive> --duration <in seconds>
```



## Detailed Project Outline
The project is split into 4 main parts. Mainly, `cli/`, `core/enricher`, `core/translator`, and `core/algorithms`

### CLI
This is the main entrypoint for the project. It provides subcommands to generate, enrich, simulate, and optionally, clear the output directory
```
sbt "cli/run --help"
```
Each subcommand has its own optional parameters that you can pass.

For example, if a different config needs to be used for enriching a graph, `--config` can be used with the enrich command
```
sbt "cli/run enrich --config path/to/config
```
More details can be found by running `--help` for each subcommand

> Note: There is also a `pipeline` command that runs all the above steps sequentially. However, there may be conflicts
> in the .conf file and it may fail fast for conflicting values

### Enricher
The enricher reads the generated NetGameSim file and enriches the graph by adding additional node properties like `isTimerNode`
and `isInputNode` etc., and additional edge properties like `allowedMessages`

### Translator
This is the driver that translates the enriched graph from a `json` file to an Akka system. It also injects any external 
messages defined. Depending on the injection mode, it reads from `input/input.json` if it is a file driven injection, or
through the CLI if interactive.

### Algorithms
This module implements the assigned algorithms, namely Hirschberg-Sinclair leader election in bidirectional rings and leader
election in trees. It extends the existing implementation for `DistributedNode` defined in the course repository.





