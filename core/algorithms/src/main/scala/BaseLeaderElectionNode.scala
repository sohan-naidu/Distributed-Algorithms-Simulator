package algorithms

import akka.actor.typed.scaladsl.{ActorContext, Behaviors, TimerScheduler}
import akka.actor.typed.{ActorRef, Behavior}
import com.uic.cs553.distributed.framework.{BaseDistributedNode, CommonMessages, DistributedMessage, NetworkMessage}
import core.Message
import enricher.EnrichedNode

import scala.concurrent.duration.DurationInt

abstract class BaseLeaderElectionNode(node: EnrichedNode, edges: Map[Int, Set[Message]])
  extends BaseDistributedNode(node.id.toString) {

  private val pdf: Map[Message, Double] = node.pdf
  private val tickIntervalMs: Option[Int] = node.tickIntervalMs
  private val isInput: Boolean = node.isInput
  private val edgeConstrains: Map[Int, Set[Message]] = edges

  private def onStart(ctx: ActorContext[DistributedMessage],
                      timers: TimerScheduler[DistributedMessage]): Behavior[DistributedMessage] = {
    tickIntervalMs.filter(_ > 0).foreach {ms =>
      timers.startTimerWithFixedDelay(TICK, TICK, ms.millis)
      ctx.log.info(s"$nodeId has a timer $tickIntervalMs")
    }
    Behaviors.same
  }

  private def getRandomNeighbor: ActorRef[DistributedMessage] = {
    val peers = getPeers.toVector
    val idx = scala.util.Random.nextInt(peers.size)
    peers(idx)
  }

  private def maybeGenerateMessage(): Option[Message] = {
    if (pdf.isEmpty) None
    else {
      val r = scala.util.Random.nextDouble()
      var acc = 0.0
      pdf.iterator.collectFirst {
        case (msg, p) if {
          acc += p
          r <= acc
        } => msg
      }.orElse(pdf.keys.lastOption)
    }
  }

  private def getPayload(message: Message): NodeMessage =
    message match
      case Message.Ping => PING
      case Message.Pong => PONG
      case Message.Work => WORK
      case Message.Ack => ACK
  
  private def onTick(ctx: ActorContext[DistributedMessage]): Behavior[DistributedMessage] = {
    maybeGenerateMessage() match {
      case Some(message) if getPeers.nonEmpty =>
        val peer = getRandomNeighbor
        ctx.log.info(s"$nodeId sampled $message")
        sendIfAllowed(ctx, peer, message)

      case _ =>
        ()
    }
    Behaviors.same
  }

  private def sendIfAllowed(ctx: ActorContext[DistributedMessage], toPeer: ActorRef[DistributedMessage],
                     message: Message): Unit = {
    val to = toPeer.path.name.toInt
    edgeConstrains.get(to) match {
      case None =>
        ctx.log.warn(s"Dropping $message from $nodeId to $to: no edge exists")
      case Some(allowed) if !allowed.contains(message) =>
        ctx.log.warn(s"Dropping $message from $nodeId to $to: edge constraint does not allow it")
      case Some(_) =>
        toPeer ! NetworkMessage(nodeId, toPeer.path.name, getPayload(message))
    }
  }


  override def behavior(): Behavior[DistributedMessage] = {
    Behaviors.withTimers{timers =>
      Behaviors.setup{ ctx =>
        Behaviors.receiveMessage {
          case CommonMessages.Initialize(peerRefs) =>
            super.onInitialize(ctx, peerRefs)

          case CommonMessages.Stop() =>
            ctx.log.info(s"Node $nodeId stopping")
            Behaviors.stopped

          case CommonMessages.Start() =>
            onStart(ctx, timers)

          case TICK =>
            onTick(ctx)

          case NetworkMessage(from, to, payload) =>
            payload match {
              case PING =>
                ctx.log.info(s"$nodeId - Ping received")
                getPeers.find(_.path.name == s"$from")
                  .foreach(_ ! NetworkMessage(nodeId, from, PONG))
                Behaviors.same

              case PONG =>
                ctx.log.info(s"$nodeId - Pong received")
                Behaviors.same

              case WORK =>
                ctx.log.info(s"$nodeId - Enqueuing work")
                getPeers.find(_.path.name == s"$from")
                  .foreach(_ ! NetworkMessage(nodeId, from, ACK))
                Behaviors.same

              case ACK =>
                ctx.log.info(s"$nodeId - Ack received")
                Behaviors.same
            }
        }
      }
    }
  }
}