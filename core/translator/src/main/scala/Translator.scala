package translator

import akka.actor.typed.{ActorRef, ActorSystem}
import akka.actor.typed.scaladsl.Behaviors
import com.typesafe.scalalogging.LazyLogging
import com.uic.cs553.distributed.framework.{CommonMessages, DistributedMessage, NetworkMessage}
import enricher.{EnrichedEdge, EnrichedGraph, GraphIO, LoadedGraph, RawEdge}
import algorithms.{HirschbergSinclairNode, MessageConverter, TreeElectionNode}
import core.{Message, Topology}
import translator.{Algorithm, InjectionMode}

import scala.concurrent.duration.DurationInt
import java.util.IllegalFormatException
import scala.io.Source
import scala.util.Using
import io.circe.parser.decode
import translator.InjectionMode.File

import scala.concurrent.ExecutionContext

object Translator extends LazyLogging:

  def run(inputPath: String, algorithm: String, injectionMode: String): Unit = {
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
      inject(system, actors, inputNodes, readInputFile("C:\\Users\\sohan\\Projects\\Distributed-Algorithms-Simulator\\input\\input.json"))
    initializeNeighbors(actors, enrichedGraph.edges)
    logger.info("Neighbors initialized")
    startActors(actors)
    logger.info("Actors started")
    Thread.sleep(30_000)
    system.terminate()
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
    injectables.foreach(injectable =>
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
      implicit val ec: ExecutionContext = system.executionContext
      actors.get(injectable.nodeId) match {
        case Some(ref) =>
          system.scheduler.scheduleOnce(injectable.atTimeMs.millis,
            new Runnable {
              override def run(): Unit =
                ref ! NetworkMessage("INJECTED", ref.path.name, MessageConverter.toNodeMessage(injectable.message)
                )
            })
        case None => logger.error(s"Cannot inject to non-existant node ${injectable.nodeId}")
      }
    )
  }

  private def getUndirectedAdjacency(edges: List[EnrichedEdge]): Map[Int, List[Int]] =
    edges.groupBy(_.fromId).map((k, v) => k -> v.map(_.toId))

  private def extractRingNeighbors(edges: List[EnrichedEdge]): Map[Int, (Int, Int)] = {
    val adj = getUndirectedAdjacency(edges)

    val ring = scala.collection.mutable.ArrayBuffer[Int]()
    var prev = -1
    var cur = adj.keys.min
    while ring.size < adj.size do
      ring += cur
      val next = adj(cur).find(_ != prev).get
      prev = cur
      cur = next

    val n = ring.size
    ring.zipWithIndex.map { (nodeId, i) =>
      nodeId -> (ring((i - 1 + n) % n), ring((i + 1) % n))
    }.toMap
  }

  private def buildTreeStructure(rootId: Int, edges: List[EnrichedEdge]): Map[Int, (Option[Int], List[Int])] = {
    // Deduplicate edges
    val adj = getUndirectedAdjacency(edges)

    val parent = scala.collection.mutable.Map[Int, Option[Int]](rootId -> None)
    val children = scala.collection.mutable.Map[Int, List[Int]]().withDefaultValue(Nil)
    val queue = scala.collection.mutable.Queue(rootId)

    while queue.nonEmpty do
      val node = queue.dequeue()
      adj.getOrElse(node, Nil).foreach { neighbor =>
        if !parent.contains(neighbor) then
          parent(neighbor) = Some(node)
          children(node) = children(node) :+ neighbor
          queue.enqueue(neighbor)
      }

    parent.keys.map { id =>
      id -> (parent(id), children(id))
    }.toMap
  }