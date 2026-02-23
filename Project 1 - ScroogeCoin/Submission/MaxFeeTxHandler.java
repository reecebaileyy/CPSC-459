import java.util.ArrayList;
import java.util.HashSet;

public class MaxFeeTxHandler {

    private UTXOPool utxoPool;
    private double bestFee;
    private ArrayList<Transaction> bestSet;

    public MaxFeeTxHandler(UTXOPool utxoPool) {
        this.utxoPool = new UTXOPool(utxoPool);
    }

    public boolean isValidTx(Transaction tx) {
        return isValidTx(tx, utxoPool);
    }

    private boolean isValidTx(Transaction tx, UTXOPool pool) {
        if (tx == null) return false;

        HashSet<UTXO> seenUTXOs = new HashSet<>();
        double inputSum = 0;
        double outputSum = 0;

        for (int i = 0; i < tx.numInputs(); i++) {
            Transaction.Input input = tx.getInput(i);
            if (input == null || input.prevTxHash == null) return false;

            UTXO utxo = new UTXO(input.prevTxHash, input.outputIndex);
            if (!pool.contains(utxo)) return false;

            Transaction.Output prevOutput = pool.getTxOutput(utxo);
            if (prevOutput == null) return false;

            byte[] rawData = tx.getRawDataToSign(i);
            if (rawData == null || input.signature == null) return false;
            if (!prevOutput.address.verifySignature(rawData, input.signature)) return false;

            if (!seenUTXOs.add(utxo)) return false;
            inputSum += prevOutput.value;
        }

        for (int i = 0; i < tx.numOutputs(); i++) {
            Transaction.Output output = tx.getOutput(i);
            if (output == null || output.value < 0) return false;
            outputSum += output.value;
        }

        return inputSum >= outputSum;
    }

    private double calculateFee(Transaction tx, UTXOPool pool) {
        double inputSum = 0;
        for (int i = 0; i < tx.numInputs(); i++) {
            Transaction.Input input = tx.getInput(i);
            UTXO utxo = new UTXO(input.prevTxHash, input.outputIndex);
            inputSum += pool.getTxOutput(utxo).value;
        }
        double outputSum = 0;
        for (int i = 0; i < tx.numOutputs(); i++) {
            outputSum += tx.getOutput(i).value;
        }
        return inputSum - outputSum;
    }

    private void applyTx(Transaction tx, UTXOPool pool) {
        for (int i = 0; i < tx.numInputs(); i++) {
            Transaction.Input input = tx.getInput(i);
            pool.removeUTXO(new UTXO(input.prevTxHash, input.outputIndex));
        }
        byte[] hash = tx.getHash();
        for (int j = 0; j < tx.numOutputs(); j++) {
            pool.addUTXO(new UTXO(hash, j), tx.getOutput(j));
        }
    }

    public Transaction[] handleTxs(Transaction[] possibleTxs) {
        if (possibleTxs == null) return new Transaction[0];

        bestFee = -1;
        bestSet = new ArrayList<>();

        search(possibleTxs, new boolean[possibleTxs.length],
               new UTXOPool(utxoPool), new ArrayList<>(), 0);

        for (Transaction tx : bestSet) {
            applyTx(tx, utxoPool);
        }

        return bestSet.toArray(new Transaction[0]);
    }

    /**
     * Backtracking search over all valid subsets. At each step, finds the
     * first currently-valid unused transaction and branches: include it or
     * permanently skip it. This naturally handles both UTXO conflicts (including
     * one invalidates the other) and dependencies (including one may unlock others).
     */
    private void search(Transaction[] txs, boolean[] used, UTXOPool pool,
                        ArrayList<Transaction> current, double currentFee) {
        int firstValid = -1;
        for (int i = 0; i < txs.length; i++) {
            if (!used[i] && txs[i] != null && isValidTx(txs[i], pool)) {
                firstValid = i;
                break;
            }
        }

        if (firstValid == -1) {
            if (currentFee > bestFee) {
                bestFee = currentFee;
                bestSet = new ArrayList<>(current);
            }
            return;
        }

        // Branch 1: skip this transaction entirely
        used[firstValid] = true;
        search(txs, used, pool, current, currentFee);
        used[firstValid] = false;

        // Branch 2: include this transaction
        double fee = calculateFee(txs[firstValid], pool);
        UTXOPool newPool = new UTXOPool(pool);
        applyTx(txs[firstValid], newPool);
        used[firstValid] = true;
        current.add(txs[firstValid]);
        search(txs, used, newPool, current, currentFee + fee);
        current.remove(current.size() - 1);
        used[firstValid] = false;
    }
}
