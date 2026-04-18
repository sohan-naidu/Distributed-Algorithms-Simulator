# CLI
The CLI is relatively extensive.
```
sbt "cli/run <command> <options>"
```

## Usage

| Command                 | Description                                                                                  | Options                                                                  |
|-------------------------|----------------------------------------------------------------------------------------------|--------------------------------------------------------------------------|
| `generate`              | Generate a graph using NetGameSim                                                            | `--config <string>, --clear`                                             |
| `enrich`                | Enrich the graph by adding edge labels and a probability distribution function for each node | `--config <string>, --clear`                                             |
| `simulate`              | Simulate an algorithm                                                                        | `--algorithm <string> --inject <file> interactive> --duration <seconds>` |
| `pipeline`              | Run generate, enrich, and simulate in sequence                                               |                                                                          |

**Help:** `--help` — Display help text.

# Generator

The generator is simply the NetGameSim repository cloned as a submodule. It is treated as a black box. It requires the jar
for the project to be built as internally it is called as a subprocess. All arguments to the generator are piped by reading
the config values defined under the `generator` subentry in `application.conf`.

```
cd core/generator
sbt clean compile assembly
```

By default, this generates a jar file under `core/generator/target/scala-3.2.2/netmodelsim.jar`.

## Configuration

`application.conf` supports the following parameters which are all passed to NetGameSim:

| Parameter        | Description                        |
|------------------|------------------------------------|
| `jarPath`        | Path to the NetGameSim jar file    |
| `minimumMemory`  | Minimum JVM heap memory (GB)       |
| `maximumMemory`  | Maximum JVM heap memory (GB)       |
| `ngsConfigPath`  | Path to the NetGameSim config file |
| `outputFileName` | Name of the output file            |

The CLI allows to provide a path to a different config if required, using `--config`. A sample config is provided in
`core/src/main/resources/generator-override.conf`

Example usage:
```
sbt "cli/run generate --config core\src\main\resources\generator-override.conf"
```


# Enricher

The enricher loads the generated `.ngs` file and expects two `json` lists. It ignores the perturbed files for now.
It requires the generated file to exist in `output/` or wherever the output file is located.

## Configuration
### Caveat: The enrichment will fail if overrides are defined and the generated graph does not have the required node or edge.
| Parameter                        | Description                                                                                                                                 |
|----------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------|
| `gen-output-file-path`           | Path to the generated `.ngs` file. Defaults to the output path defined in the generator config. Typically, `output/generated/generated.ngs` |
| `topology`                       | Defines the topology it should enrich for. Use `ring` or `tree`                                                                             |
| `messages`                       | Defines the messages used. The implemented ones are: "Election", "Ping", "Pong", "Work", "Ack". Case-sensitive                              |
| `nodes.distribution`             | The distribution function that should be used by nodes to generate messages. `Uniform` or `Zipf`                                            |
| `nodes.seed`                     | Seed for determinism                                                                                                                        |
| `nodes.default-pdf`              | Default pdf distribution nodes should use                                                                                                   |
| `nodes.overrides`                | Per node overrides. Accepts `id` (required), `pdf`, `tick-interval-ms`, `is-input`                                                          |
| `edges.default-allowed-messages` | Default set of messages that are allowed on each edge. Typically, all are allowed and per edge overrides are used as defined below          |
| `nodes.overrides`                | Per edge overrides. Accepts `from`, `to` and `allow`. `allow` must be a list with any or no `messages` defined                              |
| `enriched-output-file-path`      | Where the enricher should write the enriched graph and `.dot` file to. Default is `output/enriched/enricher.sim`                            | 

The CLI allows to override the config by passing `--config`. Sample override is provided in
`core/src/main/resources/enricher-override.conf`

Example usage:
```
sbt "cli/run enrich --config core\src\main\resources\enricher-override.conf"
```

# Translator
The translator is an internal module that converts the enriched graph into an Akka system. It then simulates the algorithm provided and allows for
injecting messages either through a file or interactively through the CLI, and terminates after a stipulated duration defined
in seconds. The CLI exposes the `simulate` command to run the simulation.
Example usage:
```
sbt "cli/run simulate --algorithm hs --inject file --duration 30"
sbt "cli/run simulate --algorithm te --inject interactive --duration 30"
```
The `algorithm` command allows for different args for the same algorithm implementation. For example, `hirschbergsinclair`,
`hirschberg`, `sinclair` and `hs` are all valid. `treeelection`, `tree`, `te` for tree election

### Caveat: The topology defined during the enrichment phase is crucial. Hirschberg-Sinclair requires the graph to be a bidirectional ring. On the other hand, the tree election algorithm requires the tree topology.