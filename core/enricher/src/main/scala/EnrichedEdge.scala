package enricher

import core.Message
import io.circe.generic.semiauto.{deriveEncoder, deriveDecoder}
import io.circe.{Decoder, Encoder, HCursor}

final case class EnrichedEdge (
  fromId: Int,
  toId: Int,
  allowedMessages: Set[Message]
)

object EnrichedEdge {
  given Decoder[EnrichedEdge] = Decoder.instance { c =>
    for
      fromId <- c.get[Int]("fromId")
      toId <- c.get[Int]("toId")
      allowedMessages <- c.get[Set[Message]]("allowedMessages")
    yield EnrichedEdge(fromId, toId, allowedMessages)
  }
}