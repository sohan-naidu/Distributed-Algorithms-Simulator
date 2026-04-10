import com.monovore.decline.*

import scala.sys.process.*
import cats.syntax.all.*
import com.typesafe.scalalogging.LazyLogging

import java.nio.file.{Files, Path, Paths}
import scala.jdk.CollectionConverters.*

import enricher.Enricher

sealed trait Command
object Command {
  final case class Generate(configPath: String, jarPath: String,
                            minimumMemory: Int, maximumMemory: Int, clearFlag: Boolean) extends Command
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

  private val generatorConfig: Opts[String] =
    Opts.option[String](
      "config",
      help = "Path to generator config"
    ).withDefault("configs/generator.conf")

  private val generatorJarPath: Opts[String] =
    Opts.option[String](
      "jar",
      help = "Path to NetGameSim jar file"
    ).withDefault("core/generator/target/scala-3.2.2/netmodelsim.jar")

  private val minimumMemory: Opts[Int] =
    Opts.option[Int](
      "min",
      help = "Minimum memory allocation (in GB)"
    ).withDefault(2)

  private val maximumMemory: Opts[Int] =
    Opts.option[Int](
      "max",
      help = "Maximum memory allocation (in GB)"
    ).withDefault(4)

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
      (generatorConfig, generatorJarPath, minimumMemory, maximumMemory, clearFlag)
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
    case Command.Generate(configPath, jarPath, minimumMemory, maximumMemory, clearFlag) =>
      logger.info(s"Running generator with params config=$configPath, jar=$jarPath, memory alloc between " +
        s"${minimumMemory}GB and ${maximumMemory}GB")
      val root = sys.props("user.dir")
      val exitCode =
        Process(
          Seq(
            "java",
            s"-Xms${minimumMemory}G",
            s"-Xmx${maximumMemory}G",
            s"-Dconfig.file=$configPath",
            "-jar",
            s"$jarPath",
            "generated"
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