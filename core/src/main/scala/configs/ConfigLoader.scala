package core

import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.scalalogging.LazyLogging
import Constants.*
import pureconfig.{ConfigReader, ConfigSource}
import java.nio.file.{Files, Paths}
import pureconfig.configurable.genericMapReader
import pureconfig.error.CannotConvert

object ConfigLoader extends LazyLogging {
  private val config = ConfigFactory.load()

  private def key(parts: String*): String = parts.mkString(".")

  private def load(path: Option[String] = None): Config =
    path match
      case Some(p) =>
        logger.info(s"Overriding default generator config with values in $p")
        ConfigFactory.parseFile(new java.io.File(p)).withFallback(config)
      case None =>
        config

  private def validateEnricherConfig(config: EnricherConfig): Unit =
    if Files.notExists(Paths.get(config.genOutputFilePath)) then
      throw new java.io.FileNotFoundException(
        s"Generated graph file does not exist at ${config.genOutputFilePath}. Please generate a new graph" +
          s"using the generate command first."
      )
    require(
      Math.abs(config.nodes.defaultPdf.values.sum - 1.0) < 1e-9,
      "Default PDF values for nodes do not sum up to 1"
    )

  def getGeneratorConfig(configPath: Option[String] = None): GeneratorConfig = {
    val cfg = load(configPath)
    GeneratorConfig(
      jarPath = cfg.getString(key(SIMULATOR, GENERATOR, JAR_PATH)),
      minMemory = cfg.getInt(key(SIMULATOR, GENERATOR, MIN_MEMORY)),
      maxMemory = cfg.getInt(key(SIMULATOR, GENERATOR, MAX_MEMORY)),
      NGSConfigPath = cfg.getString(key(SIMULATOR, GENERATOR, NGS_CONFIG_PATH)),
      outputFileName = cfg.getString(key(SIMULATOR, GENERATOR, OUTPUT_FILENAME))
    )
  }

  def getEnricherConfig(configPath: Option[String] = None): EnricherConfig = {
    given ConfigReader[Map[Message, Double]] =
      genericMapReader[Message, Double] { s =>
        Message.fromString(s).toRight(
          CannotConvert(s, "Message", s"Expected one of: ${Message.values.mkString(", ")}")
        )
      }
    given ConfigReader[NodeOverride] = ConfigReader.derived
    given ConfigReader[NodesConfig] = ConfigReader.derived
    given ConfigReader[EdgeOverride] = ConfigReader.derived
    given ConfigReader[EdgesConfig] = ConfigReader.derived
    given ConfigReader[EnricherConfig] = ConfigReader.derived

    val config = ConfigSource.fromConfig(load(configPath)).at(key(SIMULATOR, ENRICHER)).loadOrThrow[EnricherConfig]
    validateEnricherConfig(config)
    config
  }
  
  def getTranslatorConfig(configPath: Option[String]): TranslatorConfig = {
    given ConfigReader[TranslatorConfig] = ConfigReader.derived

    val config = ConfigSource.fromConfig(load(configPath)).at(key(SIMULATOR, TRANSLATOR)).loadOrThrow[TranslatorConfig]
    config
  }
}