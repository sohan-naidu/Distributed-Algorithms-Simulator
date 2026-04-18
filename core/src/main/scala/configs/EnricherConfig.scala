package core

final case class EnricherConfig (
  genOutputFilePath: String,
  topology: String,
  messages: Set[Message],
  nodes: NodesConfig,
  edges: EdgesConfig,
  enrichedOutputFilePath: String
)

enum Distribution:
  case Uniform
  case Zipf(exponent: Double = 1.0)

final case class NodesConfig (
  distribution: Distribution = Distribution.Uniform,
  seed: Long = 42L,
  defaultPdf: Map[Message, Double],
  overrides: List[NodeOverride] = Nil
)

final case class NodeOverride(
  id: Int,
  pdf: Option[Map[Message, Double]],
  tickIntervalMs: Option[Int],
  isInput: Option[Boolean]
)

final case class EdgesConfig(
  defaultAllowedMessages: Set[Message],
  overrides: List[EdgeOverride] = Nil
)

final case class EdgeOverride(
  from: Int,
  to: Int,
  allow: Set[Message]
)