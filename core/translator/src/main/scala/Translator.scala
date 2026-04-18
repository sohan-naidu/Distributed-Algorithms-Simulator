package translator

import akka.actor.typed.{ActorRef, ActorSystem}
import akka.actor.typed.scaladsl.Behaviors
import com.typesafe.scalalogging.LazyLogging
import com.uic.cs553.distributed.framework.{CommonMessages, DistributedMessage, NetworkMessage}
import enricher.{EnrichedEdge, EnrichedGraph, GraphIO, LoadedGraph, RawEdge}
import algorithms.{HirschbergSinclairNode, MessageConverter, TreeElectionNode}
import core.{Constants, Message, Topology}
import translator.{Algorithm, InjectionMode}

import scala.concurrent.duration.DurationInt
import java.util.IllegalFormatException
import scala.io.Source
import scala.util.Using
import io.circe.parser.decode
import translator.InjectionMode.{File, Interactive}
import akka.actor.CoordinatedShutdown

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.FiniteDuration

object Translator extends LazyLogging:

  def run(inputPath: String, algorithm: String,
          injectionMode: String, injectionFilePath: String, duration: Int): Unit = {
    val im = InjectionMode.parse(injectionMode).getOrElse(throw new IllegalArgumentException("Invalid injection mode"))
    val algo = Algorithm.parse(algorithm).getOrElse(throw new IllegalArgumentException("Invalid algorithm"))
    val graph = GraphIO.load(inputPath)
    val enrichedGraph = graph match
      case Right(LoadedGraph.Enriched(enriched)) =>
        enriched
      case Right(LoadedGraph.Raw(_)) =>
        throw new RuntimeException("Expected raw graph but file is already enriched")
      case Left(err) =>
        throw new RuntimeException(err)
    val (system, actors, inputNodes) = translate(enrichedGraph, algo)
    logger.info("Translated graph to Akka system")
    if im == InjectionMode.File then
      inject(system, actors, inputNodes, readInputFile(injectionFilePath))
    initializeNeighbors(actors, enrichedGraph.edges)
    logger.info("Neighbors initialized")
    startActors(actors)
    logger.info("Actors started")
    if im == Interactive then
      runInteractiveLoop(system, actors, inputNodes)
    shutdown(system, duration.seconds)
  }

  private def translate(graph: EnrichedGraph, algorithm: Algorithm,
                       ): (ActorSystem[Nothing], Map[Int, ActorRef[DistributedMessage]], Set[Int]) =
    val system = ActorSystem(Behaviors.empty, "distributed-algorithms-simulator")

    val edgesMap: Map[Int, Map[Int, Set[Message]]] =
      graph.edges
        .groupBy(_.fromId)
        .view
        .mapValues { edges =>
          edges.groupMapReduce(_.toId)(_.allowedMessages)(_ union _)
        }
        .toMap

    val actors: Map[Int, ActorRef[DistributedMessage]] = graph.nodes.distinctBy(_.id).map{ node =>
      node.id -> system.systemActorOf(
        algorithm match
          case Algorithm.HirschbergSinclair =>
            val ringNeighbors: Map[Int, (Int, Int)] = extractRingNeighbors(graph.edges)
            val (leftId, rightId) = ringNeighbors(node.id)
            HirschbergSinclairNode(node, edgesMap.getOrElse(node.id, Map.empty),
              leftId, rightId, graph.nodes.size)

          case Algorithm.TreeElection =>
            val treeStructure: Map[Int, (Option[Int], List[Int])] = buildTreeStructure(0, graph.edges)
            val (parent, children) = treeStructure(node.id)
            TreeElectionNode(node, edgesMap.getOrElse(node.id, Map.empty), parent, children)
        ,
        s"${node.id}"
      )
    }.toMap

    val inputNodes: Set[Int] = graph.nodes.collect {
      case node if node.isInput => node.id
    }.toSet

    (system, actors, inputNodes)

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

  private def readInputFile(inputPath: String): List[Injectable] = {
    Using(Source.fromFile(inputPath)) { source =>
      val lines = source.getLines().toList
      require(lines.size == 1, "The input file is not formatted correctly")
      decode[List[Injectable]](lines.head).fold(err =>
          throw new IllegalArgumentException(s"Input file decode failed: ${err.getMessage}"), identity)
    }.get
  }

  private def inject(system: ActorSystem[Nothing], actors: Map[Int, ActorRef[DistributedMessage]],
                      inputNodes: Set[Int], injectables: List[Injectable]): Unit = {
    given ExecutionContext = system.executionContext

    injectables.foreach { injectable =>
      require(
        inputNodes(injectable.nodeId),
        s"nodeId ${injectable.nodeId} is not an input node"
      )
      require(
        injectable.atTimeMs >= 0,
        s"atTimeMs must be >= 0, got ${injectable.atTimeMs}"
      )
      require(
        Message.values.contains(injectable.message),
        s"invalid message type ${injectable.message}"
      )

      actors.get(injectable.nodeId) match
        case Some(ref) =>
          val msg =
            NetworkMessage(
              Constants.INJECTED,
              ref.path.name,
              MessageConverter.toNodeMessage(injectable.message)
            )

          if injectable.atTimeMs == 0 then
            ref ! msg
          else
            system.scheduler.scheduleOnce(
              injectable.atTimeMs.millis,
              new Runnable {
                override def run(): Unit = ref ! msg
              }
            )

        case None =>
          logger.error(s"Cannot inject to non-existant node ${injectable.nodeId}")
    }
  }

  private def getUndirectedAdjacency(edges: List[EnrichedEdge]): Map[Int, List[Int]] =
    edges.groupBy(_.fromId).map((k, v) => k -> v.map(_.toId))

  private def extractRingNeighbors(edges: List[EnrichedEdge]): Map[Int, (Int, Int)] = {
    val adj = getUndirectedAdjacency(edges)

    @annotation.tailrec
    def buildRing(prev: Int, cur: Int, acc: Vector[Int]): Vector[Int] =
      if acc.size == adj.size then acc
      else
        val next = adj(cur).find(_ != prev).get
        buildRing(cur, next, acc :+ cur)

    val ring = buildRing(-1, adj.keys.min, Vector.empty)
    val n = ring.size

    ring.zipWithIndex.map { case (nodeId, i) =>
      nodeId -> (
        ring((i - 1 + n) % n),
        ring((i + 1) % n)
      )
    }.toMap
  }

  private def buildTreeStructure(rootId: Int, edges: List[EnrichedEdge]): Map[Int, (Option[Int], List[Int])] = {
    val adj = getUndirectedAdjacency(edges)

    @annotation.tailrec
    def bfs(queue: List[Int], parent: Map[Int, Option[Int]], children: Map[Int, List[Int]]): (Map[Int, Option[Int]], Map[Int, List[Int]]) =
      queue match
        case Nil => (parent, children)

        case node :: rest =>
          val unseen = adj.getOrElse(node, Nil).filterNot(parent.contains)

          val newParent =
            parent ++ unseen.map(child => child -> Some(node)).toMap

          val newChildren =
            children.updated(
              node,
              children.getOrElse(node, Nil) ++ unseen
            )

          bfs(rest ++ unseen, newParent, newChildren)

    val (parent, children) =
      bfs(
        List(rootId),
        Map(rootId -> None),
        Map.empty.withDefaultValue(Nil)
      )

    parent.keys.map { id =>
      id -> (
        parent(id),
        children.getOrElse(id, Nil)
      )
    }.toMap
  }

  private def shutdown(system: ActorSystem[Nothing], after: scala.concurrent.duration.FiniteDuration): Unit = {

    given ExecutionContext = system.executionContext

    system.scheduler.scheduleOnce(
      after,
      new Runnable {
        override def run(): Unit = {
          logger.info(s"Shutting down after $after")
          CoordinatedShutdown(system).run(CoordinatedShutdown.UnknownReason)
        }
      }
    )
  }

  private def runInteractiveLoop(system: ActorSystem[Nothing], actors: Map[Int, ActorRef[DistributedMessage]],
                                  inputNodes: Set[Int]): Unit = {
    println("Interactive mode started.")
    println("Commands:")
    println("  inject <nodeId> <message> [atTimeMs]")
    println("  exit")

    @annotation.tailrec
    def loop(): Unit =
      Option(scala.io.StdIn.readLine("> ")) match
        case None =>
          ()

        case Some(line) =>
          parseInteractiveInject(line) match
            case Right(None) =>
              loop()

            case Right(Some(Left(()))) =>
              ()

            case Right(Some(Right(injectable))) =>
              inject(system, actors, inputNodes, List(injectable))
              loop()

            case Left(err) =>
              println(s"Invalid command: $err")
              loop()

    loop()
  }

  private def parseInteractiveInject(line: String): Either[String, Option[Either[Unit, Injectable]]] = {
    val parts = line.trim.split("\\s+").toList
    parts match
      case Nil =>
        Right(None)
      case "" :: Nil =>
        Right(None)
      case "exit" :: Nil =>
        Right(Some(Left(())))
      case "inject" :: nodeIdStr :: msgStr :: Nil =>
        for
          nodeId <- nodeIdStr.toIntOption.toRight(s"invalid node id: $nodeIdStr")
          message <- Message.values.find(_.toString == msgStr)
            .toRight(s"invalid message type: $msgStr")
        yield Some(Right(Injectable(nodeId = nodeId, atTimeMs = 0, message = message)))

      case "inject" :: nodeIdStr :: msgStr :: atTimeMsStr :: Nil =>
        for
          nodeId <- nodeIdStr.toIntOption.toRight(s"invalid node id: $nodeIdStr")
          message <- Message.values.find(_.toString == msgStr)
            .toRight(s"invalid message type: $msgStr")
          atTimeMs <- atTimeMsStr.toIntOption.toRight(s"invalid atTimeMs: $atTimeMsStr")
        yield Some(Right(Injectable(nodeId = nodeId, atTimeMs = atTimeMs, message = message)))

      case _ =>
        Left("expected: inject <nodeId> <message> [atTimeMs] | exit")
  }
