package core

sealed trait Message
case object Control extends Message
case object Ping extends Message
case object Work extends Message
case object Ack extends Message
