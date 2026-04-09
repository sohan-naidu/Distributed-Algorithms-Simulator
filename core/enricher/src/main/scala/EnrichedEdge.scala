package enricher

import core.Message

case class EnrichedEdge (
  fromId: Int,
  toId: Int,
  allowedMessages: Set[Message]
)