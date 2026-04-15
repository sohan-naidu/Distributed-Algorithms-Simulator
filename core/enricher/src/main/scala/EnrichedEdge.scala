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
  given Decoder[EnrichedEdge] = deriveDecoder
  given Encoder[EnrichedEdge] = deriveEncoder
}