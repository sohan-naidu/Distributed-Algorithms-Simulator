package enricher

import com.typesafe.scalalogging.LazyLogging
import guru.nidi.graphviz.attribute.{Color, Label}
import guru.nidi.graphviz.engine.{Format, Graphviz, GraphvizCmdLineEngine}
import io.circe.generic.auto.*
import io.circe.parser.decode
import io.circe.syntax.*

import java.io.{File, PrintWriter}
import java.util.concurrent.TimeUnit
import scala.io.Source
import scala.util.{Try, Using, Success, Failure}
import guru.nidi.graphviz.model.Factory.{graph, linkAttrs, node, to}
import guru.nidi.graphviz.model.{Graph, Node}

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

  def write(path: String, graph: EnrichedGraph): Unit = {
    Using.resource(new PrintWriter(path)) { pw =>
      pw.println(graph.nodes.asJson.noSpaces)
      pw.println(graph.edges.asJson.noSpaces)
    }
    writeToDotFormat(graph) match
      case Success(file) =>
        logger.info(s"Successfully wrote enriched graph to ${file.getName}")
      case Failure(e) =>
        logger.error("Failed to render the graph to enriched.dot", e)
  }

  private def writeToDotFormat(enrichedGraph: EnrichedGraph): Try[File] = {
    val nodes = enrichedGraph.nodes
    if !nodes.exists(_.id == 0) then
      logger.warn("The graph does not contain a start node with id 0")

    val edges = enrichedGraph.edges.sortBy(_.fromId)

    val nodesMap: Map[Int, Node] =
      nodes.iterator.map { nd =>
        val graphNode =
          if nd.id == 0 then
            node(nd.id.toString)
              .`with`(Color.RED)
              .`with`(Label.markdown("**Init**"), Color.rgb("1020d0").font())
          else
            node(nd.id.toString)
        nd.id -> graphNode
      }.toMap

    val linkedGraph =
      edges.map(edge => nodesMap(edge.fromId).link(to(nodesMap(edge.toId))))

    val g =
      graph("enriched")
        .directed()
        .`with`(nodesMap.values.toSeq: _*)
        .linkAttr()
        .`with`("class", "link-class")
        .`with`(linkedGraph: _*)

    Try {
      val cmdlnEngine = new GraphvizCmdLineEngine()
      cmdlnEngine.timeout(2, TimeUnit.MINUTES)
      Graphviz.useEngine(cmdlnEngine)
      Graphviz.fromGraph(g).render(Format.DOT).toFile(new File("output/enriched/enriched.dot"))
    }
  }
}