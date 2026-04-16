package enricher

import io.circe.Decoder

final case class RawEdge (
  fromNode: Int,
  toNode: Int
)

// Decode only the fields required from NetGameSim
object RawEdge {
  given Decoder[RawEdge] = Decoder.instance { c =>
    for
      fromNode <- c.downField("fromNode").downField("id").as[Int]
      toNode <- c.downField("toNode").downField("id").as[Int]
    yield RawEdge(fromNode, toNode)
  }
}