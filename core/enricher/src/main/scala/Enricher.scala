package enricher

import com.typesafe.scalalogging.LazyLogging
import core.*

object Enricher extends LazyLogging {
  def run(inputPath: String, nodesConfig: NodesConfig,
          edgesConfig: EdgesConfig, topology: String, outputPath: String): Unit = {
    val graph = GraphIO.load(inputPath)
    val rawGraph = graph match
      case Right(LoadedGraph.Raw(raw)) =>
        raw
      case Right(LoadedGraph.Enriched(_)) =>
        throw new RuntimeException("Expected raw graph but file is already enriched")
      case Left(err) =>
        throw new RuntimeException(err)

    val enriched = enrich(rawGraph, nodesConfig, edgesConfig, topology)
    GraphIO.write(outputPath, enriched)
  }

  private def validateNodesConfigOverrides(nodes: List[RawNode], overrides: List[NodeOverride]): Unit = {
    overrides.foreach { o =>
      require(nodes.exists(_.id == o.id), s"Node override id ${o.id} not found in graph")
      o.pdf.foreach { pdf =>
        require(Math.abs(pdf.values.sum - 1.0) < 1e-9, s"Node override id ${o.id} pdf must sum to 1")
      }
    }
  }

  private def validateEdgesConfigOverrides(edges: List[RawEdge], overrides: List[EdgeOverride]): Unit = {
    overrides.foreach { o =>
      require(
        edges.exists(e => e.fromNode == o.from && e.toNode == o.to),
        s"Edge override (${o.from} -> ${o.to}) not found in graph"
      )
    }
  }

  private def enrich(graph: RawGraph, nodesConfig: NodesConfig, edgesConfig: EdgesConfig,
                     topology: String): EnrichedGraph = {
    val topo = Topology.parse(topology).getOrElse {
      throw new IllegalArgumentException(s"Unknown topology: $topology")
    }
    val updatedGraph = topo match {
      case Topology.Ring => graph.copy(edges = constructRing(graph.edges))
      case Topology.Tree => graph.copy(edges = extractMST(graph.edges))
    }

    val bidirectionalGraph = updatedGraph.copy(edges = makeBidirectional(updatedGraph.edges))
    validateNodesConfigOverrides(bidirectionalGraph.nodes, nodesConfig.overrides)
    validateEdgesConfigOverrides(bidirectionalGraph.edges, edgesConfig.overrides)
    val enrichedNodes = applyNodeConfigs(bidirectionalGraph.nodes, nodesConfig)
    val enrichedEdges = applyEdgeConfigs(bidirectionalGraph.edges, edgesConfig)
    logger.info("Successfully enriched the graph")
    EnrichedGraph(enrichedNodes, enrichedEdges)
  }

  private def applyNodeConfigs(nodes: List[RawNode], config: NodesConfig): List[EnrichedNode] =
    val overrideMap = config.overrides.map(o => o.id -> o).toMap
    nodes.map { node =>
      overrideMap.get(node.id) match
        case None =>
          EnrichedNode(id = node.id, pdf = config.defaultPdf,
            tickIntervalMs = None)
        case Some(o) =>
          EnrichedNode(id = node.id, pdf = o.pdf.getOrElse(config.defaultPdf),
            tickIntervalMs = o.tickIntervalMs, isInput = o.isInput.getOrElse(false))
    }

  private def applyEdgeConfigs(edges: List[RawEdge], config: EdgesConfig): List[EnrichedEdge] =
    val overrideMap = config.overrides.map(o => (o.from, o.to) -> o).toMap
    edges.map { edge =>
      overrideMap.get((edge.fromNode, edge.toNode)) match
        case None =>
          EnrichedEdge(fromId = edge.fromNode, toId = edge.toNode, allowedMessages = config.defaultAllowedMessages)
        case Some(o) =>
          EnrichedEdge(fromId = edge.fromNode, toId = edge.toNode,
            allowedMessages = o.allow)
    }

  private def constructRing(edges: List[RawEdge]): List[RawEdge] =
    val degreeCount: Map[Int, Int] =
      edges
        .flatMap(e => List(e.fromNode, e.toNode))
        .groupBy(identity)
        .view
        .mapValues(_.size)
        .toMap

    val endpoints: List[Int] =
      degreeCount.collect {
        case (node, count) if count == 1 => node
      }.toList

    require(
      endpoints.size == 2,
      s"Cannot convert graph to a ring. Expected exactly 2 endpoints, found ${endpoints.size}"
    )
    logger.info("Successfully constructed a ring")
    edges :+ RawEdge(fromNode = endpoints.head, toNode = endpoints(1), weight = 0.0)

  private def makeBidirectional(edges: List[RawEdge]): List[RawEdge] = {
    edges.flatMap { e =>
      List(e, e.copy(fromNode = e.toNode, toNode = e.fromNode))
    }.distinct
  }

  private def extractMST(edges: List[RawEdge]): List[RawEdge] =
    val nodes = edges.flatMap(e => List(e.fromNode, e.toNode)).distinct

    val parent = scala.collection.mutable.Map[Int, Int]()
    nodes.foreach(n => parent(n) = n)

    def find(x: Int): Int =
      if parent(x) != x then
        parent(x) = find(parent(x))
      parent(x)

    def union(a: Int, b: Int): Unit =
      parent(find(a)) = find(b)

    val sorted = edges.sortBy(_.weight)

    val mst = scala.collection.mutable.ListBuffer[RawEdge]()

    sorted.foreach { edge =>
      val rootA = find(edge.fromNode)
      val rootB = find(edge.toNode)

      if rootA != rootB then
        mst += edge
        union(rootA, rootB)
    }
    mst.toList
}