package enricher

import io.circe.generic.semiauto.deriveEncoder
import io.circe.{Decoder, Encoder, HCursor}
import core.Message

final case class EnrichedNode (
  id: Int,
  pdf: Map[Message, Double],
  tickIntervalMs: Option[Int] = None,
  isInput: Boolean = false
)

object EnrichedNode {
  given Decoder[EnrichedNode] = (c: HCursor) =>
    for
      id <- c.downField("id").as[Int]
    yield EnrichedNode(id, Map.empty[Message, Double])
  given Encoder[EnrichedNode] = deriveEncoder
}