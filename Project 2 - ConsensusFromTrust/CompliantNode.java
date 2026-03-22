import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

public class CompliantNode implements Node {

    private int numRounds;
    private int roundCount;
    private boolean[] followees;
    private Set<Transaction> pendingTransactions;
    private HashSet<Integer> seenTxs;
    private HashSet<Integer> responsiveFollowees;
    private HashMap<Integer, HashSet<Integer>> txToSenders;

    public CompliantNode(double p_graph, double p_malicious, double p_txDistribution, int numRounds) {
        this.numRounds = numRounds;
        this.roundCount = 0;
        this.seenTxs = new HashSet<Integer>();
        this.responsiveFollowees = new HashSet<Integer>();
        this.txToSenders = new HashMap<Integer, HashSet<Integer>>();
    }

    public void setFollowees(boolean[] followees) {
        this.followees = followees;
    }

    public void setPendingTransaction(Set<Transaction> pendingTransactions) {
        this.pendingTransactions = pendingTransactions;
        for (Transaction tx : pendingTransactions) {
            seenTxs.add(tx.id);
            if (!txToSenders.containsKey(tx.id))
                txToSenders.put(tx.id, new HashSet<Integer>());
            txToSenders.get(tx.id).add(-1); // -1 means it came from us
        }
    }

    public Set<Transaction> getProposals() {
        roundCount++;

        // after all rounds, return final consensus
        if (roundCount > numRounds) {
            return getFinalConsensus();
        }

        // otherwise just broadcast everything we know about
        HashSet<Transaction> toSend = new HashSet<Transaction>();
        for (int id : seenTxs) {
            toSend.add(new Transaction(id));
        }
        return toSend;
    }

    public void receiveCandidates(ArrayList<Integer[]> candidates) {
        for (Integer[] c : candidates) {
            int txId = c[0];
            int sender = c[1];

            if (sender < 0 || sender >= followees.length || !followees[sender])
                continue;

            responsiveFollowees.add(sender);
            seenTxs.add(txId);

            if (!txToSenders.containsKey(txId))
                txToSenders.put(txId, new HashSet<Integer>());
            txToSenders.get(txId).add(sender);
        }
    }

    private Set<Transaction> getFinalConsensus() {
        HashSet<Transaction> result = new HashSet<Transaction>();

        for (int txId : seenTxs) {
            HashSet<Integer> senders = txToSenders.get(txId);
            if (senders == null) continue;

            // always keep our own transactions
            if (senders.contains(-1)) {
                result.add(new Transaction(txId));
                continue;
            }

            // keep if at least one responsive followee sent it
            for (int s : senders) {
                if (responsiveFollowees.contains(s)) {
                    result.add(new Transaction(txId));
                    break;
                }
            }
        }

        return result;
    }
}