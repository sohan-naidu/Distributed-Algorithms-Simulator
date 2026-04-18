package algorithms

import akka.actor.typed.scaladsl.{ActorContext, Behaviors, TimerScheduler}
import akka.actor.typed.{ActorRef, Behavior, javadsl, scaladsl}
import com.uic.cs553.distributed.framework.{CommonMessages, DistributedMessage, NetworkMessage}
import core.Message
import enricher.EnrichedNode

enum Direction:
  case Left, Right

  def reverse: Direction = this match
    case Left => Right
    case Right => Left

class HirschbergSinclairNode(node: EnrichedNode, edges: Map[Int, Set[Message]],
                             left: Int, right: Int, graphSize: Int)
  extends BaseLeaderElectionNode(node, edges) {

  // Set once during Initialize before any algorithm messages are processed. Done to avoid chicken-and-egg 
  // problem of actor refs not existing until after construction, but construction requires knowing neighbors.
  // These are never changed after assignment, so technically read-only var 
  private var leftPeer: ActorRef[DistributedMessage] = _
  private var rightPeer: ActorRef[DistributedMessage] = _

  override protected def handleBackgroundChatter(ctx: ActorContext[DistributedMessage],
                                                 timers: TimerScheduler[DistributedMessage],
                                                 message: DistributedMessage): Option[Behavior[DistributedMessage]] =
    message match
      case CommonMessages.Initialize(peerRefs) =>
        val peerNames = peerRefs.map(_.path.name)
        leftPeer = peerRefs.find(_.path.name == left.toString).getOrElse(
          throw new RuntimeException(s"Node $nodeId: leftId $left not found in peers $peerNames")
        )
        rightPeer = peerRefs.find(_.path.name == right.toString).getOrElse(
          throw new RuntimeException(s"Node $nodeId: rightId $right not found in peers $peerNames")
        )
        Some(super.handleBackgroundChatter(ctx, timers, message).getOrElse(Behaviors.same))
      case _ =>
        super.handleBackgroundChatter(ctx, timers, message)

  private def getNewRepliesMap: Map[Direction, Boolean] =
    Map(Direction.Left -> false, Direction.Right -> false)

  override def behavior(): Behavior[DistributedMessage] = {
    Behaviors.withTimers { timers =>
      Behaviors.setup { ctx =>
        Behaviors.withStash(capacity = 100) { stash =>
          def idle(): Behavior[DistributedMessage] =
            Behaviors.receiveMessage { msg =>
              handleBackgroundChatter(ctx, timers, msg) match {
                case Some(next) => next
                case None =>
                  msg match {
                    case NetworkMessage(_, _, ELECTION) =>
                      ctx.log.info("Starting HS election")
                      broadcastProbe(0, 0)
                      running(active = true, phase = 0, leader = None, epoch = 0, replies = getNewRepliesMap, N = graphSize)
                    case _: NetworkMessage =>
                      stash.stash(msg)
                      stash.unstashAll(running(active = true, phase = 0, leader = None,
                        epoch = 0, replies = getNewRepliesMap, N = graphSize))
                  }
              }
            }

          def running(active: Boolean, phase: Int, leader: Option[Int], epoch: Int,
                      replies: Map[Direction, Boolean], N: Int): Behavior[DistributedMessage] =
            Behaviors.receiveMessage { msg =>
              handleBackgroundChatter(ctx, timers, msg) match {
                case Some(next) => next
                case None =>
                  msg match {
                    case NetworkMessage(from, _, PROBE(cid, p, h, ep, dir)) =>
                      ctx.log.info(s"$nodeId - Received PROBE from $cid with phase $p for epoch $ep")
                      if ep < epoch then
                        Behaviors.same
                      else if ep > epoch then {
                        stash.stash(msg)
                        broadcastProbe(0, ep)
                        stash.unstashAll(running(active = true, phase = 0, leader = None, epoch = ep,
                          replies = getNewRepliesMap, N = graphSize))
                      } else if cid.toInt < nodeId.toInt then {
                        sendIfAllowed(ctx, getPeerInOppositeDirection(dir),
                          REPLY(cid, p, ep, false, dir.reverse))
                        Behaviors.same
                      } else if cid.toInt > nodeId.toInt then
                        if h > 0 then {
                          sendIfAllowed(ctx, getPeerInSameDirection(dir), PROBE(cid, p, h - 1, ep, dir))
                          running(active = false, phase, leader, epoch, replies, N)
                        } else
                          sendIfAllowed(ctx, getPeerInOppositeDirection(dir),
                            REPLY(cid, p, ep, true, dir.reverse))
                          running(active = false, phase, leader, epoch, replies, N)
                      else
                        sendIfAllowed(ctx, getPeerInOppositeDirection(dir), LEADER(nodeId, ep, dir.reverse))
                        done(nodeId, epoch)

                    case NetworkMessage(from, _, REPLY(cid, p, ep, lw, dir)) =>
                      ctx.log.info(s"$nodeId - Received REPLY for candidate $cid, phase $p, epoch $ep, localWinner=$lw")
                      if cid != nodeId then
                        sendIfAllowed(ctx, getPeerInSameDirection(dir), REPLY(cid, p, ep, lw, dir))
                        Behaviors.same
                      else if ep < epoch || !active || p != phase then
                        Behaviors.same
                      else if !lw then
                        running(active = false, phase, leader, epoch, replies, N)
                      else
                        val updatedReplies = replies.updated(dir, true)
                        if updatedReplies.values.count(identity) < 2 then
                          running(active, phase, leader, epoch, updatedReplies, N)
                        else
                          val nextPhase = phase + 1
                          if (1 << nextPhase) >= N then
                            sendIfAllowed(ctx, getPeerInOppositeDirection(dir), LEADER(nodeId, ep, dir.reverse))
                            done(nodeId, epoch)
                          else
                            broadcastProbe(nextPhase, ep)
                            running(active, nextPhase, leader, epoch, getNewRepliesMap, N)

                    case NetworkMessage(from, _, LEADER(l, ep, dir)) =>
                      if ep < epoch then
                        Behaviors.same
                      else if l == nodeId then
                        ctx.log.info(s"LEADER announcement has traversed the ring, leader is $l")
                        done(l, ep)
                      else {
                        sendIfAllowed(ctx, getPeerInSameDirection(dir), LEADER(l, ep, dir))
                        ctx.log.info(s"Leader has been elected: $l")
                        done(l, epoch)
                      }

                    case NetworkMessage(_, _, ELECTION) =>
                      val newEpoch = epoch + 1
                      ctx.log.info(s"$nodeId restarting election in new epoch $newEpoch")
                      broadcastProbe(0, epoch + 1)
                      running(active = true, phase = 0, leader = None,
                        epoch = newEpoch, replies = getNewRepliesMap, N)

                    case _ =>
                      Behaviors.same
                  }
              }
            }

          def done(leaderId: String, epoch: Int): Behavior[DistributedMessage] =
            Behaviors.receiveMessage { msg =>
              handleBackgroundChatter(ctx, timers, msg) match {
                case Some(next) => next
                case None =>
                  msg match {
                    case NetworkMessage(from, _, LEADER(l, ep, dir)) =>
                      if ep < epoch then
                        Behaviors.same
                      else if l == nodeId then
                        ctx.log.info("LEADER announcement has traversed the ring, leader is {}", leaderId)
                        Behaviors.same
                      else
                        sendIfAllowed(ctx, getPeerInSameDirection(dir), LEADER(l, ep, dir))
                        Behaviors.same
                    case _ =>
                      Behaviors.same
                  }
              }
            }
          idle()
        }
      }
    }
  }

  private def getPeerInSameDirection(direction: Direction): ActorRef[DistributedMessage] =
    direction match {
      case Direction.Left => leftPeer
      case Direction.Right => rightPeer
    }

  private def getPeerInOppositeDirection(direction: Direction): ActorRef[DistributedMessage] =
    direction match {
      case Direction.Left => rightPeer
      case Direction.Right => leftPeer
    }

  protected override def broadcast(msg: DistributedMessage): Unit =
    getPeers.foreach(peer => peer ! NetworkMessage(nodeId, peer.path.name, msg))

  private def broadcastProbe(phase: Int, epoch: Int): Unit =
    leftPeer ! NetworkMessage(nodeId, leftPeer.path.name, PROBE(nodeId, phase, 1 << phase, epoch, Direction.Left))
    rightPeer ! NetworkMessage(nodeId, rightPeer.path.name, PROBE(nodeId, phase, 1 << phase, epoch, Direction.Right))
}

object HirschbergSinclairNode {
  def apply(node: EnrichedNode, edges: Map[Int, Set[Message]], left: Int, right: Int, graphSize: Int): Behavior[DistributedMessage] =
    new HirschbergSinclairNode(node, edges, left, right, graphSize).behavior()
}