package translator

enum InjectionMode:
  case File, Interactive

object InjectionMode:
  def parse(input: String): Option[InjectionMode] =
    input.trim.toLowerCase match
      case "file" | "f" =>
        Some(InjectionMode.File)

      case "interactive" | "i" | "int" =>
        Some(InjectionMode.Interactive)

      case _ => None

