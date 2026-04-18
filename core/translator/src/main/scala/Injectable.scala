package translator

import core.Message
import io.circe.Decoder
import io.circe.generic.semiauto.deriveDecoder

final case class Injectable(
  atTimeMs: Int,
  nodeId: Int,
  message: Message
)

object Injectable:
  given Decoder[Injectable] = deriveDecoder[Injectable]