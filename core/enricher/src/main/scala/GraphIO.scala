package enricher

import enricher.EnrichedGraph
import io.circe.syntax.*
import io.circe.parser.decode

import scala.io.Source
import java.io.PrintWriter
import scala.util.Using

import io.circe.generic.auto.*

object GraphIO {
  def load(path: String): Option[EnrichedGraph] =
    Using(Source.fromFile(path)) { source =>
      val lines = source.getLines().toList
      for
        nodes <- decode[List[EnrichedNode]](lines.head).toOption
        edges <- decode[List[EnrichedEdge]](lines(1)).toOption
      yield EnrichedGraph(nodes, edges)
    }.toOption.flatten

  def write(path: String, graph: EnrichedGraph): Unit =
    Using.resource(new PrintWriter(path)) { pw =>
      pw.println(graph.nodes.asJson.noSpaces)
      pw.println(graph.edges.asJson.noSpaces)
    }
}