package core

final case class GeneratorConfig (
   jarPath: String,
   minMemory: Int,
   maxMemory: Int,
   NGSConfigPath: String,
   outputFileName: String,
)