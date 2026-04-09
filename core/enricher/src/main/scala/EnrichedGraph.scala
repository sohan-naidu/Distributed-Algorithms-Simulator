package enricher

case class EnrichedGraph (
  nodes: List[EnrichedNode],
  edges: List[EnrichedEdge]
)