package algorithms

import akka.actor.typed.scaladsl.{ActorContext, Behaviors, TimerScheduler}
import akka.actor.typed.{ActorRef, Behavior}
import algorithms.BaseLeaderElectionNode
import com.uic.cs553.distributed.framework.{CommonMessages, DistributedMessage, NetworkMessage}
import core.Message
import enricher.EnrichedNode

class TreeElectionNode(node: EnrichedNode, edges: Map[Int, Set[Message]], par: Option[Int], chil: List[Int])
  extends BaseLeaderElectionNode(node, edges) {

  // Set once during Initialize before any algorithm messages are processed. Done to avoid chicken-and-egg
  // problem of actor refs not existing until after construction, but construction requires knowing neighbors.
  // These are never changed after assignment, so technically read-only var
  private var parent: Option[ActorRef[DistributedMessage]] = None
  private var children: List[ActorRef[DistributedMessage]] = _

  override protected def handleBackgroundChatter(ctx: ActorContext[DistributedMessage],
                                                 timers: TimerScheduler[DistributedMessage],
                                                 message: DistributedMessage): Option[Behavior[DistributedMessage]] =
    message match
      case CommonMessages.Initialize(peerRefs) =>
        val peerNames = peerRefs.map(_.path.name)
        parent = par.map(p =>
          peerRefs.find(_.path.name == p.toString).getOrElse(
            throw new RuntimeException(s"Node $nodeId: Parent $p not found in peers $peerNames")
          )
        )
        children = chil.map(c => peerRefs.find(_.path.name == c.toString).getOrElse(
          throw new RuntimeException(s"Node $nodeId: Child $c not found in peers $peerNames")
        ))
        Some(super.handleBackgroundChatter(ctx, timers, message).getOrElse(Behaviors.same))
      case _ =>
        super.handleBackgroundChatter(ctx, timers, message)

  override def behavior(): Behavior[DistributedMessage] = {
    Behaviors.withTimers{ timers =>
      Behaviors.setup{ ctx =>
        Behaviors.withStash(capacity = 100){ stash =>
          def idle(): Behavior[DistributedMessage] = {
            Behaviors.receiveMessage{ msg =>
              handleBackgroundChatter(ctx, timers, msg) match {
                case Some(next) => next
                case None =>
                  msg match {
                    case NetworkMessage(_, _, ELECTION) =>
                      parent match {
                        case Some(p) =>
                          sendIfAllowed(ctx, p, ELECTION)
                          idle()
                        case None =>
                          ctx.log.info(s"$nodeId (root) - Starting election in tree")
                          children.foreach(child => sendIfAllowed(ctx, child, START))
                          running(0, nodeId.toInt)
                      }

                    case NetworkMessage(_, _, START) =>
                      if children.nonEmpty then
                        ctx.log.info(s"$nodeId forwarding start")
                        children.foreach(child => sendIfAllowed(ctx, child, START))
                        running(0, nodeId.toInt)
                      else
                        ctx.log.info(s"$nodeId at leaf - sending vote back up")
                        sendIfAllowed(ctx, parent.getOrElse(
                          throw new IllegalArgumentException("Impossible: No parent, no child")),
                          VOTE(nodeId.toInt))
                        running(0, nodeId.toInt)
                  }
              }
            }
          }

          def running(responded: Int, localMax: Int): Behavior[DistributedMessage] = {
            Behaviors.receiveMessage{ msg =>
              handleBackgroundChatter(ctx, timers, msg) match {
                case Some(next) => next
                case None =>
                  msg match {
                    case NetworkMessage(c, _, VOTE(childrenMax)) =>
                      ctx.log.info(s"$nodeId - VOTE received from child $c")
                      val newMax = math.max(localMax, childrenMax)
                      val newResponded = responded + 1
                      if newResponded == children.size then
                        parent match {
                          case Some(p) =>
                            ctx.log.info(s"$nodeId sending subtree max $newMax back to root")
                            sendIfAllowed(ctx, p, VOTE(newMax))
                            running(responded, newMax)
                          case None =>
                            ctx.log.info(s"$nodeId - Leader has been elected to be $newMax")
                            children.foreach(child => sendIfAllowed(ctx, child, TREE_LEADER(newMax)))
                            idle()
                        }
                      else
                        running(newResponded, newMax)

                    case NetworkMessage(_, _, TREE_LEADER(leaderId)) =>
                      if children.nonEmpty then
                        children.foreach(child => sendIfAllowed(ctx, child, TREE_LEADER(leaderId)))
                        idle()
                      else {
                        ctx.log.info(s"LEADER as ${leaderId} announcement has reached a child node")
                        idle()
                      }

                    case _ =>
                      Behaviors.same
                  }
                }
            }
          }

          idle()
        }
      }
    }
  }

}

object TreeElectionNode {
  def apply(node: EnrichedNode, edges: Map[Int, Set[Message]],
            par: Option[Int], chil: List[Int]): Behavior[DistributedMessage] =
    new TreeElectionNode(node, edges, par, chil).behavior()
}
