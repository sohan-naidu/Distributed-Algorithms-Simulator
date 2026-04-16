package algorithms

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import com.uic.cs553.distributed.framework.{CommonMessages, DistributedMessage, NetworkMessage}
import enricher.EnrichedNode

class HirschbergSinclairNode(node: EnrichedNode) extends BaseLeaderElectionNode(node) {
  
  override protected def onMessage(ctx: ActorContext[DistributedMessage],
                                   msg: DistributedMessage): Behavior[DistributedMessage] = {
    msg match {
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