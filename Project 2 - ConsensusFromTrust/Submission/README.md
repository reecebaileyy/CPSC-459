# CPSC 459-01 - Project 2: ConsensusFromTrust

**Group**

- Name: Christian Huerta
- Email: christianhuerta@csu.fullerton.edu

- Name: Joshua Andrada
- Email: AndradaJ@csu.fullerton.edu

- Name: Reece Bailey
- Email: Reece.bailey8857@csu.fullerton.edu

**Extra Credit:**

- Implemented `CompliantNode` with two rules: while simulating, any transaction proposed by a followed node is added to the gossip set so multi-hop propagation works (requiring a majority of *all* followees to add would block almost every first hop). After `numRounds` proposal phases, the extra `getProposals()` call returns only transaction IDs that a strict majority of followees proposed in the **last** `receiveCandidates` batch (or all locally known IDs when the node has no followees). This matches the `Node` contract and behaves sensibly against `MalOne`, `MalTwo`, and `MalThree` in `TestCompliantNode`.
