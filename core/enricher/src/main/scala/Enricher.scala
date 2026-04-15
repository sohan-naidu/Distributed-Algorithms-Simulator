package enricher

import com.typesafe.scalalogging.LazyLogging
import core.{EdgeOverride, EdgesConfig, Message, NodeOverride, NodesConfig}

object Enricher extends LazyLogging {
  def run(inputPath: String, nodesConfig: NodesConfig,
          edgesConfig: EdgesConfig, outputPath: String): Unit = {
    val graph = GraphIO.load(inputPath)
    val enriched = enrich(graph.get, nodesConfig, edgesConfig)
    GraphIO.write(outputPath, enriched)
  }

  private def validateNodesConfigOverrides(nodes: List[EnrichedNode], overrides: List[NodeOverride]): Unit = {
    overrides.foreach { o =>
      require(nodes.exists(_.id == o.id), s"Node override id ${o.id} not found in graph")
      o.pdf.foreach { pdf =>
        require(Math.abs(pdf.values.sum - 1.0) < 1e-9, s"Node override id ${o.id} pdf must sum to 1")
      }
    }
  }

  private def validateEdgesConfigOverrides(edges: List[EnrichedEdge], overrides: List[EdgeOverride]): Unit = {
    overrides.foreach { o =>
      require(
        edges.exists(e => e.fromId == o.from && e.toId == o.to),
        s"Edge override (${o.from} -> ${o.to}) not found in graph"
      )
    }
  }

  private def enrich(graph: EnrichedGraph, nodesConfig: NodesConfig, edgesConfig: EdgesConfig): EnrichedGraph = {
    validateNodesConfigOverrides(graph.nodes, nodesConfig.overrides)
    validateEdgesConfigOverrides(graph.edges, edgesConfig.overrides)
    val enrichedNodes = applyNodeConfigs(graph.nodes, nodesConfig)
    val enrichedEdges = applyEdgeConfigs(graph.edges, edgesConfig)
    logger.info("Successfully enriched the graph")
    EnrichedGraph(enrichedNodes, enrichedEdges)
  }

  private def applyNodeConfigs(nodes: List[EnrichedNode], config: NodesConfig): List[EnrichedNode] =
    val overrideMap = config.overrides.map(o => o.id -> o).toMap
    nodes.map { node =>
      overrideMap.get(node.id) match
        case None => node.copy(pdf = config.defaultPdf)
        case Some(o) => node.copy(
          pdf = o.pdf.getOrElse(config.defaultPdf),
          tickIntervalMs = o.tickIntervalMs.orElse(node.tickIntervalMs),
          isInput = o.isInput.getOrElse(false)
        )
    }

  private def applyEdgeConfigs(edges: List[EnrichedEdge], config: EdgesConfig): List[EnrichedEdge] =
    val overrideMap = config.overrides.map(o => (o.from, o.to) -> o).toMap
    edges.map { edge =>
      overrideMap.get((edge.fromId, edge.toId)) match
        case None => edge.copy(allowedMessages = config.defaultAllowedMessages)
        case Some(o) => edge.copy(allowedMessages = o.allow)
    }

  private def assignEdgeLabels(edge: EnrichedEdge): Set[Message] =
    Set(Message.Election, Message.Ping)
}