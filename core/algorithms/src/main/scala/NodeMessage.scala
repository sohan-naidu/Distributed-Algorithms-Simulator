package algorithms

import com.uic.cs553.distributed.framework.DistributedMessage

sealed trait NodeMessage extends DistributedMessage

case object PING extends NodeMessage
case object PONG extends NodeMessage
case object TICK extends NodeMessage
case object WORK extends NodeMessage
case object ACK extends NodeMessage