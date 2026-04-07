import com.monovore.decline.*
import scala.sys.process.*
import cats.syntax.all.*

sealed trait Command
object Command {
  final case class Generate(configPath: String, jarPath: String) extends Command
}

object Main extends CommandApp(
  name = "simulator",
  header = "Distributed Algorithms Simulator",
  main = {
    val generatorConfig: Opts[String] =
      Opts.option[String](
        "generator-config",
        help = "Path to generator config"
      ).withDefault("configs/generator.conf")

    val generatorJarPath: Opts[String] =
      Opts.option[String](
        "generator-jar",
        help = "Path to NetGameSim jar file"
      ).withDefault("core/generator/target/scala-3.2.2/netmodelsim.jar")

    val generate: Opts[Command] =
      Opts.subcommand("generate", "Generate a graph using NetGameSim") {
        (generatorConfig, generatorJarPath).mapN(Command.Generate.apply)
      }

    val command: Opts[Command] =
      generate

    command.map {
      case Command.Generate(configPath, jarPath) =>
        val root = sys.props("user.dir")
        val exitCode =
          Process(
            Seq(
              "java",
              "-Xms2G",
              "-Xmx4G",
              s"-Dconfig.file=$configPath",
              "-jar",
              s"$jarPath",
              "generated"
            )
          ).!

        if (exitCode != 0) {
          System.err.println(s"Generator failed with exit code $exitCode")
        }
    }
  }
)