package core

import com.typesafe.config.{ConfigFactory, Config}
import com.typesafe.scalalogging.LazyLogging

import Constants.*

object ConfigReader extends LazyLogging {
  private val config = ConfigFactory.load()

  private def key(parts: String*): String = parts.mkString(".")

  private def load(path: Option[String] = None): Config =
    path match
      case Some(p) =>
        logger.info(s"Overriding default generator config with values in $p")
        ConfigFactory.parseFile(new java.io.File(p)).withFallback(config)
      case None =>
        config

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

}