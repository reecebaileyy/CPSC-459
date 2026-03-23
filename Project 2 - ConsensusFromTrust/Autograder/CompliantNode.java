import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class CompliantNode implements Node {

    private boolean[] followees;
    private int numFollowees;
    private final int numRounds;
    private int proposalCalls;
    private boolean receivedInPriorRound;

    /** All transaction IDs we gossip during simulation rounds. */
    private final HashSet<Integer> pending;

    /** IDs that had strict majority among followees in the latest receiveCandidates (final output). */
    private final HashSet<Integer> lastRoundConsensus;

    public CompliantNode(double p_graph, double p_malicious, double p_txDistribution, int numRounds) {
        this.numRounds = numRounds;
        this.pending = new HashSet<Integer>();
        this.lastRoundConsensus = new HashSet<Integer>();
    }

    public void setFollowees(boolean[] followees) {
        this.followees = followees;
        this.numFollowees = 0;
        for (boolean f : followees) {
            if (f) {
                numFollowees++;
            }
        }
    }

    public void setPendingTransaction(Set<Transaction> pendingTransactions) {
        for (Transaction tx : pendingTransactions) {
            pending.add(tx.id);
        }
    }

    public Set<Transaction> getProposals() {
        if (proposalCalls > 0 && proposalCalls < numRounds && !receivedInPriorRound) {
            lastRoundConsensus.clear();
        }
        proposalCalls++;
        HashSet<Transaction> out = new HashSet<Transaction>();
        if (proposalCalls > numRounds) {
            for (int id : lastRoundConsensus) {
                out.add(new Transaction(id));
            }
            return out;
        }
        for (int id : pending) {
            out.add(new Transaction(id));
        }
        receivedInPriorRound = false;
        return out;
    }

    public void receiveCandidates(ArrayList<Integer[]> candidates) {
        receivedInPriorRound = true;
        lastRoundConsensus.clear();

        if (numFollowees == 0) {
            lastRoundConsensus.addAll(pending);
            return;
        }

        HashMap<Integer, HashSet<Integer>> txToSenders = new HashMap<Integer, HashSet<Integer>>();
        for (Integer[] candidate : candidates) {
            int id = candidate[0];
            int whoSent = candidate[1];
            if (!followees[whoSent]) {
                continue;
            }
            pending.add(id);
            if (!txToSenders.containsKey(id)) {
                txToSenders.put(id, new HashSet<Integer>());
            }
            txToSenders.get(id).add(whoSent);
        }

        for (Map.Entry<Integer, HashSet<Integer>> e : txToSenders.entrySet()) {
            if (e.getValue().size() * 2 > numFollowees) {
                lastRoundConsensus.add(e.getKey());
            }
        }
    }
}
