package enricher

final case class EnrichedGraph (
  nodes: List[EnrichedNode],
  edges: List[EnrichedEdge]
)