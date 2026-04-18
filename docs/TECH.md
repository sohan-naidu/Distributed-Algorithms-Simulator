# CLI
Internally uses the `decline` to parse CLI args and autogenerate `help` docs. Uses `PureConfig` for HOCON parsing.
Ran out of time but will add PureConfig for generator later since it's not an immediate problem or a problem for that matter

# Enricher
Probably the most technical part of the project. Reads json, converts to in-memory representation by first parsing only 
the fields we need like `id`, `fromNode`, `toNode`, etc. This raw graph is then enriched by adding the corresponding configs
for each node and edge with validations at each step. Implements uniform and zipf distribution for sampling messages.

> I'd like to highlight two key parts about this implementation. For Hirschberg-Sinclair, since a bidirectional ring structure
> is a must, the enricher expects the input to be a chain for this particular algorithm. This is due to NetGameSim generating random
> graphs, it is probabilistically difficult to consistently generate a bidirectional ring. As such, I circumvent this by 
> generating a chain graph. It then checks for exactly two nodes with degree one and connects the two by adding an edge between
> them, essentially converting it into a ring.
> 
> Secondly, generating a proper tree deterministically was also difficult. Instead of fighting the generator, I generate a 
> random graph with typical configurations. A minimum spanning tree is extracted using Kruskal's algorithm, which always guarantees
> a tree for the tree election algorithm.

# Algorithms
Based on the `BaseDistributedNode` class, I extend a generic base for leader election with common functions that both algorithms
utilize. All val's defined are read-only and almost never modified, except to avoid the chicken-and-egg problem. The algorithm
subclasses implement state-switching for algorithm-specific messages. Each message between actor is wrapped in a `NetworkMessage`
and case matching is used to define actor behavior on receiving a message. The main states are `idle`, `running` and `done`
where `idle` means an election is not in process, `running` is when an election is active, and `done` when the leader is elected

## Hirschberg-Sinclair
For the HS algorithm, each actor focuses on three four message types - `ELECTION, PROBE, REPLY, LEADER`. `ELECTION` is a
message that is used to start an election in the ring. Each node sends a `PROBE` to its two neighbors and waits for `REPLY`s at
each phase. If it receives a single negative reply, it drops out of the election, else it advances to the next phase and sends
`PROBE`s again with an increased hop distance. Once it receives its own `PROBE`, it guarantees that this particular node has
the highest ID and declares itself as the leader by broadcasting `LEADER`.

## Tree Election
When any node receives an `ELECTION` message, if it is not the root node, i.e., the parent actor reference is not null, it
sends the message upwards without changing its state. Once root sees `ELECTION`, it sends `START` message down and switches from `idle` to 
`running`. This message is specifically for leaf nodes to start a convergecast. All other intermediary nodes forward the message and 
switch to `running` anticipating their subtrees to send the local maximum ID back up. Once leaf nodes receive `START`, 
they send `VOTE` along with their own ID back up and essentially `idle` again. The parent node waits for all their chidlren
to send a `VOTE` after which a maximum ID is chose from among the subtree and self, and sent back up. After the root node receives
`VOTE` from all its children, a global maximum is selected and broadcast back down through `LEADER`.



