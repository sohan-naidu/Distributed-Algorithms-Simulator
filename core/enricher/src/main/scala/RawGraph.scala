package enricher

final case class RawGraph (
  nodes: List[RawNode],
  edges: List[RawEdge]
)