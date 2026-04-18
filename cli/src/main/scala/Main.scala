import com.monovore.decline.*
import cats.syntax.all.*
import com.typesafe.scalalogging.LazyLogging
import scala.sys.process.*

import java.nio.file.{Files, Paths}
import scala.jdk.CollectionConverters.*
import enricher.Enricher
import core.{ConfigLoader, Constants}
import translator.Translator

sealed trait Command
object Command {
  final case class Generate(configPath: Option[String], clearFlag: Boolean) extends Command
  final case class Enrich(configPath: Option[String], clearFlag: Boolean) extends Command
  final case class Simulate(configPath: Option[String], algorithm: String, injectionMode: String, duration: Int) extends Command
  final case class Pipeline(generatorConfigPath: Option[String], enricherConfigPath: Option[String], 
                            translatorConfig: Option[String], algorithm: String, clearFlag: Boolean, injectionMode: String, duration: Int) extends Command
}

object Main extends CommandApp(
  name = "simulator",
  header = "Distributed Algorithms Simulator",
  main = CommandLineInterface.run
)

object CommandLineInterface extends LazyLogging {
  private val clearFlag: Opts[Boolean] =
    Opts.flag("clear", help = "Clear the output directory").orFalse

  private val generatorConfig: Opts[Option[String]] =
    Opts.option[String]("config", help = "Path to generator config").orNone

  private val enricherConfig: Opts[Option[String]] =
    Opts.option[String]("config", help = "Path to enricher config").orNone

  private val translatorConfig: Opts[Option[String]] =
    Opts.option[String]("config", help = "Path to translator config").orNone

  private val algorithm: Opts[String] =
    Opts.option[String]("algorithm", help = "Algorithm to simulate")
  
  private val injectionMode: Opts[String] =
    Opts.option[String]("inject", help = "Choose injection mode for input nodes")
    
  private val duration: Opts[Int] =
    Opts.option[Int]("duration", help = "The duration after which the simulator will shutdown")

  private val pipelineGeneratorConfig: Opts[Option[String]] =
    Opts.option[String]("generator-config", help = "Path to generator config").orNone

  private val pipelineEnricherConfig: Opts[Option[String]] =
    Opts.option[String]("enricher-config", help = "Path to enricher config").orNone

  private val generate: Opts[Command] =
    Opts.subcommand("generate", "Generate a graph using NetGameSim") {
      (generatorConfig, clearFlag).mapN(Command.Generate.apply)
    }

  private val enrich: Opts[Command] =
    Opts.subcommand("enrich", "Enrich the graph by adding edge labels and a probability distribution" +
      "function for each node") {
      (enricherConfig, clearFlag).mapN(Command.Enrich.apply)
    }

  private val simulate: Opts[Command] =
    Opts.subcommand("simulate", "Simulate an algorithm") {
      (translatorConfig, algorithm, injectionMode, duration).mapN(Command.Simulate.apply)
    }

  private val pipeline: Opts[Command] =
    Opts.subcommand("pipeline", "Run generate, enrich, and simulate in sequence") {
      (pipelineGeneratorConfig, pipelineEnricherConfig, translatorConfig, algorithm, clearFlag, injectionMode, duration)
        .mapN(Command.Pipeline.apply)
    }

  private val command: Opts[Command] =
    generate.orElse(enrich).orElse(simulate).orElse(pipeline)

  def run: Opts[Unit] =
    command.map {
      case Command.Generate(configPath, clearFlag) =>
        runGenerate(configPath, clearFlag)

      case Command.Enrich(configPath, clearFlag) =>
        runEnrich(configPath, clearFlag)

      case Command.Simulate(configPath, algorithm, injectionMode, duration) =>
        runSimulate(configPath, algorithm, injectionMode, duration)

      case Command.Pipeline(generatorConfigPath, enricherConfigPath, translatorConfig,
        algorithm, clearFlag, injectionMode, duration) =>
        if (clearFlag) clear()
        runGenerate(generatorConfigPath, clearFlag = false)
        runEnrich(enricherConfigPath, clearFlag = false)
        runSimulate(translatorConfig, algorithm, injectionMode, duration)
    }

  private def runGenerate(configPath: Option[String], clearFlag: Boolean): Unit = {
    if (clearFlag) clear()

    val genConfig = ConfigLoader.getGeneratorConfig(configPath)
    logger.info("Generating a graph...")

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
  }

  private def runEnrich(configPath: Option[String], clearFlag: Boolean): Unit = {
    if (clearFlag) clear()

    val enricherConfig = ConfigLoader.getEnricherConfig(configPath)
    Enricher.run(
      enricherConfig.genOutputFilePath,
      enricherConfig.nodes,
      enricherConfig.edges,
      enricherConfig.topology,
      enricherConfig.enrichedOutputFilePath
    )
  }

  private def runSimulate(configPath: Option[String], algorithm: String, injectionMode: String, duration: Int): Unit = {
    val translatorConfig = ConfigLoader.getTranslatorConfig(configPath)
    Translator.run(translatorConfig.enrichedOutputFilePath, algorithm, 
      injectionMode, translatorConfig.injectFilePath, duration)
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