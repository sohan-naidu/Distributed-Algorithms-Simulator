package enricher

import io.circe.{Decoder, HCursor}

final case class RawNode (
  id: Int,
)

// Decode only the fields required from NetGameSim
object RawNode {
  given Decoder[RawNode] = (c: HCursor) =>
    for
      id <- c.downField("id").as[Int]
    yield RawNode(id)
}