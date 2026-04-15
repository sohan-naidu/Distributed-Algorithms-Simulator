package enricher

import com.typesafe.scalalogging.LazyLogging
import enricher.EnrichedGraph
import io.circe.syntax.*
import io.circe.parser.decode

import scala.io.Source
import java.io.PrintWriter
import scala.util.Using
import io.circe.generic.auto.*

object GraphIO extends LazyLogging{
  def load(path: String): Option[EnrichedGraph] =
    Using(Source.fromFile(path)) { source =>
      val lines = source.getLines().toList

      if lines.length < 2 then
        logger.error(s"Expected at least 2 lines, found ${lines.length}")
        None
      else
        decode[List[EnrichedNode]](lines.head) match
          case Left(err) =>
            logger.error(s"Node decode failed: ${err.getMessage}", err)
            None

          case Right(nodes) =>
            decode[List[EnrichedEdge]](lines(1)) match
              case Left(err) =>
                logger.error(s"Edge decode failed: ${err.getMessage}", err)
                None

              case Right(edges) =>
                logger.info(s"Decoded ${nodes.length} nodes and ${edges.length} edges")
                Some(EnrichedGraph(nodes, edges))
    } match
      case scala.util.Success(result) => result
      case scala.util.Failure(err) =>
        logger.error(s"Failed to open/read file '$path': ${err.getMessage}", err)
        None

  def write(path: String, graph: EnrichedGraph): Unit =
    Using.resource(new PrintWriter(path)) { pw =>
      pw.println(graph.nodes.asJson.noSpaces)
      pw.println(graph.edges.asJson.noSpaces)
    }
}