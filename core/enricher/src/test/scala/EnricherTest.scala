package enricher

import core.*
import org.scalatest.PrivateMethodTester
import org.scalatest.funsuite.AnyFunSuite

class EnricherTest extends AnyFunSuite with PrivateMethodTester {

  private val validateNodesConfigOverrides =
    PrivateMethod[Unit](Symbol("validateNodesConfigOverrides"))

  private val validateEdgesConfigOverrides =
    PrivateMethod[Unit](Symbol("validateEdgesConfigOverrides"))

  private val enrichMethod =
    PrivateMethod[EnrichedGraph](Symbol("enrich"))

  private val applyNodeConfigsMethod =
    PrivateMethod[List[EnrichedNode]](Symbol("applyNodeConfigs"))

  private val applyEdgeConfigsMethod =
    PrivateMethod[List[EnrichedEdge]](Symbol("applyEdgeConfigs"))

  private val constructRingMethod =
    PrivateMethod[List[RawEdge]](Symbol("constructRing"))

  private val makeBidirectionalMethod =
    PrivateMethod[List[RawEdge]](Symbol("makeBidirectional"))

  private val extractMSTMethod =
    PrivateMethod[List[RawEdge]](Symbol("extractMST"))

  private val rawNodes = List(
    RawNode(1),
    RawNode(2),
    RawNode(3)
  )

  private val rawEdges = List(
    RawEdge(1, 2, 1.0),
    RawEdge(2, 3, 2.0)
  )

  private val defaultPdf = Map(
    Message.Ping -> 0.3,
    Message.Work -> 0.7
  )

  private val nodesConfig = NodesConfig(
    defaultPdf = defaultPdf,
    distribution = Distribution.Uniform,
    seed = 42,
    overrides = Nil
  )

  private val edgesConfig = EdgesConfig(
    defaultAllowedMessages = Set(Message.Ping, Message.Work),
    overrides = Nil
  )

  test("validateNodesConfigOverrides accepts valid override ids and normalized pdfs") {
    val overrides = List(
      NodeOverride(
        id = 1,
        pdf = Some(Map(Message.Ping -> 0.5, Message.Work -> 0.5)),
        tickIntervalMs = Some(1000),
        isInput = Some(true)
      )
    )
  }

  test("validateNodeConfigOverrides accepts valid node overrides") {
    val overrides = List(
      NodeOverride(
        id = 1,
        pdf = Some(Map(Message.Ping -> 0.5, Message.Work -> 0.5)),
        tickIntervalMs = Some(1000),
        isInput = Some(true)
      )
    )
    try {
      Enricher invokePrivate validateNodesConfigOverrides(rawNodes, overrides)
      succeed
    } catch {
      case e: Throwable => fail(s"Unexpected exception: $e")
    }
  }

  test("validateNodesConfigOverrides throws when override id is missing from graph") {
    val overrides = List(
      NodeOverride(
        id = 99,
        pdf = Some(Map(Message.Ping -> 0.5, Message.Work -> 0.5)),
        tickIntervalMs = None,
        isInput = Some(false)
      )
    )

    assertThrows[IllegalArgumentException] {
      Enricher invokePrivate validateNodesConfigOverrides(rawNodes, overrides)
    }
  }

  test("validateNodesConfigOverrides throws when override pdf does not sum to 1") {
    val overrides = List(
      NodeOverride(
        id = 1,
        pdf = Some(Map(Message.Ping -> 0.2, Message.Work -> 0.2)),
        tickIntervalMs = None,
        isInput = Some(false)
      )
    )

    assertThrows[IllegalArgumentException] {
      Enricher invokePrivate validateNodesConfigOverrides(rawNodes, overrides)
    }
  }

  test("validateEdgesConfigOverrides accepts valid edge overrides") {
    val overrides = List(
      EdgeOverride(from = 1, to = 2, allow = Set(Message.Ping))
    )
  }

  test("validateEdgesConfigOverrides throws when override edge does not exist") {
    val overrides = List(
      EdgeOverride(from = 3, to = 1, allow = Set(Message.Ping))
    )

    assertThrows[IllegalArgumentException] {
      Enricher invokePrivate validateEdgesConfigOverrides(rawEdges, overrides)
    }
  }

  test("applyNodeConfigs uses default pdf when node has no override") {
    val result =
      Enricher invokePrivate applyNodeConfigsMethod(rawNodes, nodesConfig)

    assert(result.size == 3)
    assert(result.forall(_.pdf == defaultPdf))
    assert(result.forall(_.tickIntervalMs.isEmpty))
    assert(result.forall(!_.isInput))
  }

  test("applyNodeConfigs applies override tickInterval and isInput") {
    val config = NodesConfig(
      defaultPdf = defaultPdf,
      distribution = Distribution.Uniform,
      seed = 42,
      overrides = List(
        NodeOverride(
          id = 2,
          pdf = Some(Map(Message.Ping -> 1.0)),
          tickIntervalMs = Some(500),
          isInput = Some(true)
        )
      )
    )

    val result =
      Enricher invokePrivate applyNodeConfigsMethod(rawNodes, config)

    val node2 = result.find(_.id == 2).get
    val node1 = result.find(_.id == 1).get

    assert(node2.pdf == Map(Message.Ping -> 1.0))
    assert(node2.tickIntervalMs.contains(500))
    assert(node2.isInput)

    assert(node1.pdf == defaultPdf)
    assert(node1.tickIntervalMs.isEmpty)
    assert(!node1.isInput)
  }

  test("applyEdgeConfigs uses defaults when no overrides exist") {
    val result =
      Enricher invokePrivate applyEdgeConfigsMethod(rawEdges, edgesConfig)

    assert(result == List(
      EnrichedEdge(1, 2, Set(Message.Ping, Message.Work)),
      EnrichedEdge(2, 3, Set(Message.Ping, Message.Work))
    ))
  }

  test("applyEdgeConfigs applies override allowed messages for matching edge") {
    val config = EdgesConfig(
      defaultAllowedMessages = Set(Message.Ping, Message.Work),
      overrides = List(
        EdgeOverride(from = 1, to = 2, allow = Set(Message.Work))
      )
    )

    val result =
      Enricher invokePrivate applyEdgeConfigsMethod(rawEdges, config)

    assert(result.contains(EnrichedEdge(1, 2, Set(Message.Work))))
    assert(result.contains(EnrichedEdge(2, 3, Set(Message.Ping, Message.Work))))
  }

  test("constructRing connects the two endpoints of a path") {
    val edges = List(
      RawEdge(1, 2, 1.0),
      RawEdge(2, 3, 1.0),
      RawEdge(3, 4, 1.0)
    )

    val result =
      Enricher invokePrivate constructRingMethod(edges)

    assert(result.size == 4)
    assert(result.exists(e =>
      (e.fromNode == 1 && e.toNode == 4) || (e.fromNode == 4 && e.toNode == 1)
    ))
  }

  test("constructRing throws when graph does not have exactly two endpoints") {
    val cycle = List(
      RawEdge(1, 2, 1.0),
      RawEdge(2, 3, 1.0),
      RawEdge(3, 1, 1.0)
    )

    assertThrows[IllegalArgumentException] {
      Enricher invokePrivate constructRingMethod(cycle)
    }
  }

  test("makeBidirectional adds reverse edges and removes duplicates") {
    val edges = List(
      RawEdge(1, 2, 1.0),
      RawEdge(2, 1, 1.0),
      RawEdge(2, 3, 2.0)
    )

    val result =
      Enricher invokePrivate makeBidirectionalMethod(edges)

    assert(result.contains(RawEdge(1, 2, 1.0)))
    assert(result.contains(RawEdge(2, 1, 1.0)))
    assert(result.contains(RawEdge(2, 3, 2.0)))
    assert(result.contains(RawEdge(3, 2, 2.0)))
    assert(result.distinct.size == result.size)
  }

  test("extractMST returns minimum spanning tree edges") {
    val edges = List(
      RawEdge(1, 2, 1.0),
      RawEdge(2, 3, 2.0),
      RawEdge(1, 3, 10.0),
      RawEdge(3, 4, 3.0)
    )

    val result =
      Enricher invokePrivate extractMSTMethod(edges)

    assert(result.size == 3)
    assert(result.contains(RawEdge(1, 2, 1.0)))
    assert(result.contains(RawEdge(2, 3, 2.0)))
    assert(result.contains(RawEdge(3, 4, 3.0)))
    assert(!result.contains(RawEdge(1, 3, 10.0)))
  }

  test("enrich applies ring topology and returns bidirectional enriched graph") {
    val graph = RawGraph(
      nodes = List(RawNode(1), RawNode(2), RawNode(3)),
      edges = List(
        RawEdge(1, 2, 1.0),
        RawEdge(2, 3, 1.0)
      )
    )

    val result =
      Enricher invokePrivate enrichMethod(graph, nodesConfig, edgesConfig, "ring")

    assert(result.nodes.size == 3)
    assert(result.edges.exists(e => e.fromId == 1 && e.toId == 3))
    assert(result.edges.exists(e => e.fromId == 3 && e.toId == 1))
  }

  test("enrich applies tree topology using MST") {
    val graph = RawGraph(
      nodes = List(RawNode(1), RawNode(2), RawNode(3), RawNode(4)),
      edges = List(
        RawEdge(1, 2, 1.0),
        RawEdge(2, 3, 2.0),
        RawEdge(1, 3, 10.0),
        RawEdge(3, 4, 3.0)
      )
    )

    val result =
      Enricher invokePrivate enrichMethod(graph, nodesConfig, edgesConfig, "tree")

    assert(result.nodes.size == 4)
    assert(result.edges.exists(e => e.fromId == 1 && e.toId == 2))
    assert(result.edges.exists(e => e.fromId == 2 && e.toId == 1))
    assert(result.edges.exists(e => e.fromId == 2 && e.toId == 3))
    assert(result.edges.exists(e => e.fromId == 3 && e.toId == 2))
    assert(!result.edges.exists(e => e.fromId == 1 && e.toId == 3))
  }

  test("enrich throws on unknown topology") {
    val graph = RawGraph(rawNodes, rawEdges)

    assertThrows[IllegalArgumentException] {
      Enricher invokePrivate enrichMethod(graph, nodesConfig, edgesConfig, "mesh")
    }
  }
}