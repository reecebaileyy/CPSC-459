import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

public class CompliantNode implements Node {

    // List of bools so we can keep track of who we follow, you only want to follow trusted transaction
    private boolean[] followees;
    // every transaction id is so they can be returned
    private HashSet<Integer> transactionsID;

    public CompliantNode(double p_graph, double p_malicious, double p_txDistribution, int numRounds) {
        this.transactionsID = new HashSet<Integer>();
    }

    public void setFollowees(boolean[] followees) {
        // initial followess added
        this.followees = followees;
    }

    public void setPendingTransaction(Set<Transaction> pendingTransactions) {
        // start by getting every pending transaction and adding it to the transactionsID list
        for (Transaction tx : pendingTransactions) {
            transactionsID.add(tx.id);
        }
    }

    public Set<Transaction> getProposals() {
        // Get every id in the transactionsID set and return them
        HashSet<Transaction> ids = new HashSet<Transaction>();
        for (int id : transactionsID) {
            ids.add(new Transaction(id));
        }
        return ids;
    }

    public void receiveCandidates(ArrayList<Integer[]> candidates) {
        for (Integer[] candidate : candidates) {
            int id = candidate[0];
            int whoSent = candidate[1];
            // make sure whoever sent it is in the followees list by checking if its false or true
            if (!followees[whoSent]){
                continue;
            }
            // add the id of the candidate to the transactionsID set if its trusted so it can be returned
            transactionsID.add(id);
        }
    }
}
