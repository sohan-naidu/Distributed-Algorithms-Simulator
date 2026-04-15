package enricher

import io.circe.generic.semiauto.{deriveEncoder, deriveDecoder}
import io.circe.{Decoder, Encoder, HCursor}
import core.Message

final case class EnrichedNode (
  id: Int,
  pdf: Map[Message, Double],
  tickIntervalMs: Option[Int] = None,
  isInput: Boolean = false
)

object EnrichedNode {
  given Decoder[EnrichedNode] = deriveDecoder
  given Encoder[EnrichedNode] = deriveEncoder
}