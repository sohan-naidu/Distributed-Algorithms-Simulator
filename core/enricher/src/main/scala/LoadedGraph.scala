package enricher

sealed trait LoadedGraph

object LoadedGraph {
  final case class Raw(value: RawGraph) extends LoadedGraph
  final case class Enriched(value: EnrichedGraph) extends LoadedGraph
}