package translator

enum Algorithm:
  case HirschbergSinclair, TreeElection

object Algorithm:
  def parse(input: String): Option[Algorithm] =
    input.trim.toLowerCase match
      case "hirschbergsinclair" | "hirschberg" | "hs" | "sinclair" =>
        Some(Algorithm.HirschbergSinclair)

      case "treeelection" | "tree" | "te" =>
        Some(Algorithm.TreeElection)

      case _ => None