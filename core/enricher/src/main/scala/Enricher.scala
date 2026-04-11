package enricher

import com.typesafe.scalalogging.LazyLogging
import core.Message

object Enricher extends LazyLogging {
  def run(inputPath: String, outputPath: String): Unit = {
    val graph = GraphIO.load(inputPath)
    val enriched = enrich(graph.get)
    GraphIO.write(outputPath, enriched)
  }

  private def enrich(graph: EnrichedGraph): EnrichedGraph = {
    val nodes = graph.nodes.map { node =>
      EnrichedNode(
        id = node.id,
        pdf = assignPdf(node)
      )
    }

    val edges = graph.edges.map { edge =>
      EnrichedEdge(
        fromId = edge.fromId,
        toId = edge.toId,
        allowedMessages = assignEdgeLabels(edge)
      )
    }
    logger.info("Successfully enriched the graph")
    EnrichedGraph(nodes, edges)
  }

  private def assignPdf(node: EnrichedNode): Map[Message, Double] =
    Map(
      Message.Control -> 0.5,
      Message.Ping -> 0.3,
      Message.Work -> 0.2
    )

  private def assignEdgeLabels(edge: EnrichedEdge): Set[Message] =
    Set(Message.Control, Message.Ping)
}