package enricher

import io.circe.generic.semiauto.deriveEncoder
import io.circe.{Decoder, Encoder, HCursor}

case class EnrichedNode (
  id: Int,
)

object EnrichedNode {
  given Decoder[EnrichedNode] = (c: HCursor) =>
    for
      id <- c.downField("id").as[Int]
    yield EnrichedNode(id)
  given Encoder[EnrichedNode] = deriveEncoder
}