package translator

import com.uic.cs553.distributed.framework.DistributedMessage

sealed trait NodeMessage

object NodeMessage {
  case object Ping extends DistributedMessage
  case object Pong extends DistributedMessage
}