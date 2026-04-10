package enricher

import core.Message
import io.circe.generic.semiauto.deriveEncoder
import io.circe.{Decoder, Encoder, HCursor}

case class EnrichedEdge (
  fromId: Int,
  toId: Int,
  allowedMessages: Set[Message]
)

object EnrichedEdge {
  given Decoder[EnrichedEdge] = (c: HCursor) =>
    for
      fromId <- c.downField("fromNode").downField("id").as[Int]
      toId <- c.downField("toNode").downField("id").as[Int]
    yield EnrichedEdge(fromId, toId, Set.empty[core.Message])
  given Encoder[EnrichedEdge] = deriveEncoder
}