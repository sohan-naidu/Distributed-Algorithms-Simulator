package translator

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import com.uic.cs553.distributed.framework.{BaseDistributedNode, DistributedMessage, NetworkMessage, CommonMessages}
import enricher.EnrichedNode
import core.Message

class HirschbergSinclairNode(node: EnrichedNode) extends BaseDistributedNode(node.id.toString) {

  val pdf: Map[Message, Double] = node.pdf
  val tickIntervalMs: Option[Int] = node.tickIntervalMs
  val isInput: Boolean = node.isInput

  override protected def onMessage(ctx: ActorContext[DistributedMessage],
                                   msg: DistributedMessage): Behavior[DistributedMessage] = {
    msg match {
      case CommonMessages.Start() =>
        getPeers.foreach { peer =>
          peer ! NetworkMessage(nodeId, peer.path.name, NodeMessage.Ping)
        }
        Behaviors.same

      case NetworkMessage(from, to, payload) =>
        payload match {
          case NodeMessage.Ping =>
            ctx.log.info("Ping received")
            getPeers.find(_.path.name == s"$from")
              .foreach(_ ! NetworkMessage(nodeId, from, NodeMessage.Pong))
            Behaviors.same

          case NodeMessage.Pong =>
            ctx.log.info("Pong received")
            Behaviors.same
        }

      case _ =>
        Behaviors.same
    }
  }
}

object HirschbergSinclairNode {

  def apply(node: EnrichedNode): Behavior[DistributedMessage] = {
    new HirschbergSinclairNode(node).behavior()
  }

}