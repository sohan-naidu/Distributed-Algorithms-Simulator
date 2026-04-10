package core

import io.circe.{Decoder, Encoder, KeyDecoder, KeyEncoder}

enum Message:
  case Control, Ping, Work, Ack

object Message:
  given Encoder[Message] =
    Encoder.encodeString.contramap(_.toString)

  given Decoder[Message] =
    Decoder.decodeString.emap {
      case "Control" => Right(Message.Control)
      case "Ping"    => Right(Message.Ping)
      case "Work"    => Right(Message.Work)
      case "Ack"     => Right(Message.Ack)
      case other     => Left(s"Unknown message: $other")
    }

  given KeyEncoder[Message] =
    KeyEncoder.encodeKeyString.contramap(_.toString)

  given KeyDecoder[Message] =
    KeyDecoder.instance {
      case "Control" => Some(Message.Control)
      case "Ping"    => Some(Message.Ping)
      case "Work"    => Some(Message.Work)
      case "Ack"     => Some(Message.Ack)
      case _         => None
    }