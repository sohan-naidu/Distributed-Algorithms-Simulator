package algorithms

import com.uic.cs553.distributed.framework.DistributedMessage

sealed trait NodeMessage extends DistributedMessage

case object PING extends NodeMessage
case object PONG extends NodeMessage
case object TICK extends NodeMessage
case object WORK extends NodeMessage
case object ACK extends NodeMessage

case object ELECTION extends NodeMessage

case class PROBE(candidateId: String, phase: Int, hops: Int, epoch: Int, direction: Direction) extends NodeMessage
case class REPLY(candidateId: String, phase: Int, epoch: Int, localWinner: Boolean, direction: Direction) extends NodeMessage
case class LEADER(candidateId: String, epoch: Int, direction: Direction) extends NodeMessage