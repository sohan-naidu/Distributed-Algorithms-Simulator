package translator

import akka.actor.typed.{ActorRef, ActorSystem}
import akka.actor.typed.scaladsl.Behaviors
import com.typesafe.scalalogging.LazyLogging
import com.uic.cs553.distributed.framework.{CommonMessages, DistributedMessage}
import enricher.{EnrichedEdge, EnrichedGraph, GraphIO, LoadedGraph}
import algorithms.HirschbergSinclairNode

enum Algorithm:
  case HirschbergSinclair, TreeElection

object Translator extends LazyLogging:

  def run(inputPath: String, algorithm: String): Unit = {
    val graph = GraphIO.load(inputPath)
    val enrichedGraph = graph match
      case Right(LoadedGraph.Enriched(enriched)) =>
        enriched
      case Right(LoadedGraph.Raw(_)) =>
        throw new RuntimeException("Expected raw graph but file is already enriched")
      case Left(err) =>
        throw new RuntimeException(err)
    val algo = Algorithm.valueOf(algorithm)
    val (system, actors) = translate(enrichedGraph, algo)
    logger.info("Translated graph to Akka system")
    initializeNeighbors(actors, enrichedGraph.edges)
    logger.info("Neighbors initialized")
    startActors(actors)
    logger.info("Actors started")
    Thread.sleep(30_000)
    system.terminate()
  }

  private def translate(graph: EnrichedGraph, algorithm: Algorithm
                       ): (ActorSystem[Nothing], Map[Int, ActorRef[DistributedMessage]]) =
    val system = ActorSystem(Behaviors.empty, "distributed-algorithms-simulator")
    val actors = graph.nodes.map { node =>
      val behavior = algorithm match
        case Algorithm.HirschbergSinclair => HirschbergSinclairNode(node)
//        case Algorithm.TreeElection       => TreeElectionNode(node)
      node.id -> system.systemActorOf(behavior, s"${node.id}")
    }.toMap

    (system, actors)

  private def initializeNeighbors(actors: Map[Int, ActorRef[DistributedMessage]], edges: List[EnrichedEdge]
                                 ): Unit =
    val neighborMap: Map[Int, Set[ActorRef[DistributedMessage]]] =
      edges
        .flatMap(e => List(e.fromId -> e.toId, e.toId -> e.fromId))
        .groupMap(_._1)(_._2)
        .map { case (id, neighborIds) =>
          id -> neighborIds.flatMap(actors.get).toSet
        }

    neighborMap.foreach { case (id, neighbors) =>
      actors.get(id).foreach(_ ! CommonMessages.Initialize(neighbors))
    }

  private def startActors(actors: Map[Int, ActorRef[DistributedMessage]]): Unit =
    actors.values.foreach(_ ! CommonMessages.Start())