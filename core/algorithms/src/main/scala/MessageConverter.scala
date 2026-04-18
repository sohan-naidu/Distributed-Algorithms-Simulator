package algorithms

import com.uic.cs553.distributed.framework.DistributedMessage
import core.Message

object MessageConverter:
  def toNodeMessage(message: Message): NodeMessage =
    message match
      case Message.Ping     => PING
      case Message.Pong     => PONG
      case Message.Work     => WORK
      case Message.Ack      => ACK
      case Message.Election => ELECTION

  def toCoreMessage(message: DistributedMessage): Message =
    message match
      case PING                         => Message.Ping
      case PONG                         => Message.Pong
      case WORK                         => Message.Work
      case ACK                          => Message.Ack
      case ELECTION | _: PROBE | _: REPLY | _: LEADER | START | _: VOTE | _: TREE_LEADER =>
        Message.Election