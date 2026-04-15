package core

import io.circe.{Decoder, Encoder, KeyDecoder, KeyEncoder}
import pureconfig.ConfigConvert.catchReadError
import pureconfig.{ConfigConvert, ConfigReader}
import pureconfig.error.CannotConvert
import pureconfig.configurable.genericMapReader

enum Message:
  case Election, Ping, Pong, Work, Ack

object Message:
  private[core] def fromString(s: String): Option[Message] =
    Message.values.find(_.toString.equalsIgnoreCase(s))

  given Encoder[Message] =
    Encoder.encodeString.contramap(_.toString)

  given Decoder[Message] =
    Decoder.decodeString.emap { s =>
      fromString(s).toRight(s"Unknown message: $s")
    }

  given KeyEncoder[Message] =
    KeyEncoder.encodeKeyString.contramap(_.toString)

  given KeyDecoder[Message] =
    KeyDecoder.instance(fromString)

  given ConfigReader[Message] =
    ConfigReader.fromString { s =>
      fromString(s).toRight(
        CannotConvert(
          s,
          "Message",
          s"Expected one of: ${Message.values.mkString(", ")}"
        )
      )
    }