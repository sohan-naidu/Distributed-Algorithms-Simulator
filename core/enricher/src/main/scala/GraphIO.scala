package enricher

import com.typesafe.scalalogging.LazyLogging
import io.circe.generic.auto.*
import io.circe.parser.decode
import io.circe.syntax.*

import java.io.PrintWriter
import scala.io.Source
import scala.util.Using

object GraphIO extends LazyLogging{
  def load(path: String): Either[String, LoadedGraph] =
    Using(Source.fromFile(path)) { source =>
      val lines = source.getLines().toList

      if lines.length < 2 then
        Left(s"Expected at least 2 lines, found ${lines.length}")
      else
        val enrichedAttempt =
          for
            nodes <- decode[List[EnrichedNode]](lines.head).left.map(err =>
              s"Enriched node decode failed: ${err.getMessage}"
            )
            edges <- decode[List[EnrichedEdge]](lines(1)).left.map(err =>
              s"Enriched edge decode failed: ${err.getMessage}"
            )
          yield LoadedGraph.Enriched(EnrichedGraph(nodes, edges))

        val rawAttempt =
          for
            nodes <- decode[List[RawNode]](lines.head).left.map(err =>
              s"Raw node decode failed: ${err.getMessage}"
            )
            edges <- decode[List[RawEdge]](lines(1)).left.map(err =>
              s"Raw edge decode failed: ${err.getMessage}"
            )
          yield LoadedGraph.Raw(RawGraph(nodes, edges))

        (enrichedAttempt, rawAttempt) match
          case (Right(g), _) =>
            Right(g)

          case (_, Right(g)) =>
            Right(g)

          case (Left(e1), Left(e2)) =>
            Left(s"Unknown graph format.\n$e1\n$e2")
    } match
      case scala.util.Success(result) =>
        result

      case scala.util.Failure(err) =>
        logger.error(s"Failed to open/read file '$path': ${err.getMessage}", err)
        Left(s"Failed to open/read file '$path': ${err.getMessage}")

  def write(path: String, graph: EnrichedGraph): Unit =
    Using.resource(new PrintWriter(path)) { pw =>
      pw.println(graph.nodes.asJson.noSpaces)
      pw.println(graph.edges.asJson.noSpaces)
    }
}