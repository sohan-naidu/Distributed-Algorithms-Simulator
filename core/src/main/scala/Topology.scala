package core

enum Topology:
  case Ring, Tree

object Topology:
  def parse(input: String): Option[Topology] =
    input.trim.toLowerCase match
      case "ring" =>
        Some(Topology.Ring)

      case "tree" =>
        Some(Topology.Tree)

      case _ => None