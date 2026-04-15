import com.monovore.decline.*

import scala.sys.process.*
import cats.syntax.all.*
import com.typesafe.scalalogging.LazyLogging

import java.nio.file.{Files, Path, Paths}
import scala.jdk.CollectionConverters.*
import enricher.Enricher
import core.ConfigLoader
import translator.Translator

sealed trait Command
object Command {
  final case class Generate(configPath: Option[String], clearFlag: Boolean) extends Command
  final case class Enrich(configPath: Option[String], clearFlag: Boolean) extends Command
  final case class Simulate(algorithm: String) extends Command
}

object Main extends CommandApp(
  name = "simulator",
  header = "Distributed Algorithms Simulator",
  main = CommandLineInterface.run
)

object CommandLineInterface extends LazyLogging {
  private val clearFlag: Opts[Boolean] =
    Opts.flag(
      "clear",
      help = "Clear the output directory"
    ).orFalse

  private val generatorConfig: Opts[Option[String]] =
    Opts.option[String](
      "config",
      help = "Path to generator config"
    ).orNone

  private val enricherConfig: Opts[Option[String]] =
    Opts.option[String](
      "config",
      help = "Path to enricher config"
    ).orNone

  private val algorithm: Opts[String] =
    Opts.option[String](
      "algorithm",
      help = "Algorithm to simulate"
    )

  private val generate: Opts[Command] =
    Opts.subcommand("generate", "Generate a graph using NetGameSim") {
      (generatorConfig, clearFlag)
        .mapN(Command.Generate.apply)
    }

  private val enrich: Opts[Command] =
    Opts.subcommand("enrich", "Enrich the graph by adding edge labels and a probability distribution" +
      "function for each node") {
      (enricherConfig, clearFlag).mapN(Command.Enrich.apply)
    }

  private val simulate: Opts[Command] =
    Opts.subcommand("simulate", "Simulate an algorithm") {
      (algorithm).map(Command.Simulate.apply)
    }

  private val command: Opts[Command] =
    generate.orElse(enrich).orElse(simulate)

  def run: Opts[Unit] = command.map {
    case Command.Generate(configPath, clearFlag) =>
      if (clearFlag) {
        clear()
      }

      val genConfig = ConfigLoader.getGeneratorConfig(configPath)
      logger.info(s"Generating a graph...")
      val exitCode =
        Process(
          Seq(
            "java",
            s"-Xms${genConfig.minMemory}G",
            s"-Xmx${genConfig.maxMemory}G",
            s"-Dconfig.file=${genConfig.NGSConfigPath}",
            "-jar",
            s"${genConfig.jarPath}",
            s"${genConfig.outputFileName}"
          )
        ).!

      if (exitCode != 0) {
        logger.error(s"Generator failed with exit code $exitCode")
      }

    case Command.Enrich(configPath, clearFlag) =>
      val enricherConfig = ConfigLoader.getEnricherConfig(configPath)
      if (clearFlag) {
        clear()
      }
      Enricher.run(enricherConfig.genOutputFilePath, enricherConfig.nodes, enricherConfig.edges,
        enricherConfig.enrichedOutputFilePath)

    case Command.Simulate(algorithm) =>
      val translatorConfig = ConfigLoader.getTranslatorConfig(None)
      Translator.run(translatorConfig.enrichedOutputFilePath, algorithm)
  }

  private def clear(): Unit = {
    val pathStr = "output"
    val path = Paths.get(pathStr).toAbsolutePath.normalize()

    require(pathStr.nonEmpty && pathStr != "/" && pathStr != ".",
      s"Refusing to delete unsafe path: $pathStr")

    if (Files.exists(path)) {
      val stream = Files.walk(path)
      try {
        stream.iterator()
          .asScala
          .filter(Files.isRegularFile(_))
          .foreach(Files.delete)
      } finally {
        stream.close()
      }

      logger.info(s"Cleared directory: $path")
    }
  }
}