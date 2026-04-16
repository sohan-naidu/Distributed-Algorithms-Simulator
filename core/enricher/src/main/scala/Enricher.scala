package enricher

import com.typesafe.scalalogging.LazyLogging
import core.{EdgeOverride, EdgesConfig, Message, NodeOverride, NodesConfig}

object Enricher extends LazyLogging {
  def run(inputPath: String, nodesConfig: NodesConfig,
          edgesConfig: EdgesConfig, outputPath: String): Unit = {
    val graph = GraphIO.load(inputPath)
    val rawGraph = graph match
      case Right(LoadedGraph.Raw(raw)) =>
        raw
      case Right(LoadedGraph.Enriched(_)) =>
        throw new RuntimeException("Expected raw graph but file is already enriched")
      case Left(err) =>
        throw new RuntimeException(err)

    val enriched = enrich(rawGraph, nodesConfig, edgesConfig)
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

  private def enrich(graph: RawGraph, nodesConfig: NodesConfig, edgesConfig: EdgesConfig): EnrichedGraph = {
    validateNodesConfigOverrides(graph.nodes, nodesConfig.overrides)
    validateEdgesConfigOverrides(graph.edges, edgesConfig.overrides)
    val enrichedNodes = applyNodeConfigs(graph.nodes, nodesConfig)
    val enrichedEdges = applyEdgeConfigs(graph.edges, edgesConfig)
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
}