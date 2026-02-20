import java.util.ArrayList;
import java.util.HashSet;

public class TxHandler {

    private UTXOPool utxoPool;

    /**
     * Creates a public ledger whose current UTXOPool (collection of unspent
     * transaction outputs) is utxoPool. Makes a defensive copy of utxoPool.
     */
    public TxHandler(UTXOPool utxoPool) {
        this.utxoPool = new UTXOPool(utxoPool);
    }

    /**
     * Returns true if:
     * (1) all outputs claimed by tx are in the current UTXO pool,
     * (2) the signatures on each input of tx are valid,
     * (3) no UTXO is claimed multiple times by tx,
     * (4) all of tx's output values are non-negative, and
     * (5) the sum of tx's input values >= sum of tx's output values.
     */
    public boolean isValidTx(Transaction tx) {
        if (tx == null) return false;

        HashSet<UTXO> seenUTXOs = new HashSet<>();
        double inputSum = 0;
        double outputSum = 0;

        // Validate each input
        for (int i = 0; i < tx.numInputs(); i++) {
            Transaction.Input input = tx.getInput(i);

            if (input == null) return false;
            if (input.prevTxHash == null) return false;

            UTXO utxo = new UTXO(input.prevTxHash, input.outputIndex);

            // (1) The UTXO must exist in the pool
            if (!utxoPool.contains(utxo)) return false;

            Transaction.Output prevOutput = utxoPool.getTxOutput(utxo);
            if (prevOutput == null) return false;

            // (2) Signature must be valid
            byte[] rawData = tx.getRawDataToSign(i);
            if (rawData == null) return false;
            if (input.signature == null) return false;
            if (!prevOutput.address.verifySignature(rawData, input.signature)) return false;

            // (3) No double-spend within this transaction
            if (seenUTXOs.contains(utxo)) return false;
            seenUTXOs.add(utxo);

            inputSum += prevOutput.value;
        }

        // Validate each output
        for (int i = 0; i < tx.numOutputs(); i++) {
            Transaction.Output output = tx.getOutput(i);
            if (output == null) return false;

            // (4) Output values must be non-negative
            if (output.value < 0) return false;

            outputSum += output.value;
        }

        // (5) Input sum must be >= output sum
        if (inputSum < outputSum) return false;

        return true;
    }

    /**
     * Handles each epoch by receiving an unordered array of proposed transactions,
     * checking each transaction for correctness, returning a mutually valid array
     * of accepted transactions, and updating the current UTXO pool as appropriate.
     */
    public Transaction[] handleTxs(Transaction[] possibleTxs) {
        if (possibleTxs == null) return new Transaction[0];

        ArrayList<Transaction> accepted = new ArrayList<>();
        boolean[] processed = new boolean[possibleTxs.length];

        // Keep looping until no new transactions can be accepted.
        // This handles chains where tx B spends an output of tx A in the same block.
        boolean changed = true;
        while (changed) {
            changed = false;
            for (int i = 0; i < possibleTxs.length; i++) {
                if (processed[i]) continue;
                if (possibleTxs[i] == null) { processed[i] = true; continue; }

                if (isValidTx(possibleTxs[i])) {
                    Transaction tx = possibleTxs[i];
                    accepted.add(tx);
                    processed[i] = true;
                    changed = true;

                    // Remove spent UTXOs from the pool
                    for (Transaction.Input input : tx.getInputs()) {
                        if (input.prevTxHash == null) continue;
                        UTXO spent = new UTXO(input.prevTxHash, input.outputIndex);
                        utxoPool.removeUTXO(spent);
                    }

                    // Add new UTXOs created by this transaction's outputs
                    byte[] txHash = tx.getHash();
                    if (txHash != null) {
                        for (int j = 0; j < tx.numOutputs(); j++) {
                            UTXO newUTXO = new UTXO(txHash, j);
                            utxoPool.addUTXO(newUTXO, tx.getOutput(j));
                        }
                    }
                }
            }
        }

        return accepted.toArray(new Transaction[0]);
    }
}
