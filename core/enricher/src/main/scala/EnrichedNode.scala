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
  given Decoder[EnrichedNode] = Decoder.instance { c =>
    for
      id <- c.get[Int]("id")
      pdf <- c.get[Map[Message, Double]]("pdf")
      tickIntervalMs <- c.get[Option[Int]]("tickIntervalMs")
      isInput <- c.get[Boolean]("isInput")
    yield EnrichedNode(id, pdf, tickIntervalMs, isInput)
  }
}