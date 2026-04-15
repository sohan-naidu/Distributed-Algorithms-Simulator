package translator

import akka.actor.typed.{ActorRef, ActorSystem}
import akka.actor.typed.scaladsl.Behaviors
import com.typesafe.scalalogging.LazyLogging
import com.uic.cs553.distributed.framework.{CommonMessages, DistributedMessage}
import enricher.{EnrichedEdge, EnrichedGraph, GraphIO}
import translator.HirschbergSinclairNode

enum Algorithm:
  case HirschbergSinclair, TreeElection

object Translator extends LazyLogging:

  def run(inputPath: String, algorithm: String): Unit =
    GraphIO.load(inputPath) match
      case Some(graph) =>
        logger.info("Graph loaded")

        val algo = Algorithm.valueOf(algorithm)
        logger.info(s"Parsed algorithm: $algo")

        val (system, actors) = translate(graph, algo)
        logger.info("Translate done")

        initializeNeighbors(actors, graph.edges)
        logger.info("Neighbors initialized")

        startActors(actors)
        logger.info("Actors started")

        Thread.sleep(30_000)
        system.terminate()

      case None =>
        logger.error(s"Failed to load graph from $inputPath")

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