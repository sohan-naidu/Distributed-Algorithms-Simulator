package translator

import akka.actor.typed.{ActorRef, ActorSystem, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import com.typesafe.scalalogging.LazyLogging
import com.uic.cs553.distributed.framework.{DistributedMessage, CommonMessages}
import enricher.{EnrichedEdge, EnrichedGraph, EnrichedNode, GraphIO}

object Translator extends LazyLogging {

  def run(inputPath: String, nodeFactory: String => Behavior[DistributedMessage]): Unit = {
    val enrichedGraph = GraphIO.load(inputPath)
    translate(enrichedGraph.get, nodeFactory)
  }

  private def translate(enrichedGraph: EnrichedGraph, nodeFactory: String => Behavior[DistributedMessage]): Unit = {
    val system = ActorSystem(Behaviors.empty, "distributed-algorithms-simulator")
    val actors = createActors(system, enrichedGraph.nodes, nodeFactory)
    createChannels(actors, enrichedGraph.edges)
  }

  private def createActors(system: ActorSystem[?], nodes: List[EnrichedNode],
                           nodeFactory: String => Behavior[DistributedMessage]): Map[String, ActorRef[DistributedMessage]] = {
    nodes.map { node =>
      val id = node.id.toString
      id -> system.systemActorOf(nodeFactory(id), s"node-$id")
    }.toMap
  }

  private def createChannels(actors: Map[String, ActorRef[DistributedMessage]], edges: List[EnrichedEdge]): Unit = {
    edges.foreach { edge =>
      val from = edge.fromId.toString
      val to = edge.toId.toString
      (actors.get(from), actors.get(to)) match {
        case (Some(fromActor), Some(toActor)) =>
          logger.info(s"Channel: $from -> $to")
        case _ =>
          logger.warn(s"Missing actor for edge $from -> $to")
      }
    }
  }
}