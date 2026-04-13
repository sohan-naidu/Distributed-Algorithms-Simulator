import com.monovore.decline.*

import scala.sys.process.*
import cats.syntax.all.*
import com.typesafe.scalalogging.LazyLogging

import java.nio.file.{Files, Path, Paths}
import scala.jdk.CollectionConverters.*
import enricher.Enricher
import core.ConfigReader

sealed trait Command
object Command {
  final case class Generate(configPath: Option[String], clearFlag: Boolean) extends Command
  final case class Enrich(inputPath:String, outputPath: String, clearFlag: Boolean) extends Command
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

  private val enricherInputPath: Opts[String] =
    Opts.option[String](
      "input",
      help = "Input path for the enricher"
    ).withDefault("output/generated/generated.ngs")

  private val enricherOutputPath: Opts[String] =
    Opts.option[String](
      "output",
      help = "Output path for the enricher"
    ).withDefault("output/enriched/enriched.sim")

  private val generate: Opts[Command] =
    Opts.subcommand("generate", "Generate a graph using NetGameSim") {
      (generatorConfig, clearFlag)
        .mapN(Command.Generate.apply)
    }

  private val enrich: Opts[Command] =
    Opts.subcommand("enrich", "Enrich the graph by adding edge labels and a probability distribution" +
      "function for each node") {
      (enricherInputPath, enricherOutputPath, clearFlag).mapN(Command.Enrich.apply)
    }

  private val command: Opts[Command] =
    generate.orElse(enrich)

  def run: Opts[Unit] = command.map {
    case Command.Generate(configPath, clearFlag) =>
      val genConfig = ConfigReader.getGeneratorConfig(configPath)
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

    case Command.Enrich(inputPath, outputPath, clearFlag) =>
      if (clearFlag) {
        clear()
      }
      Enricher.run(inputPath, outputPath)
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