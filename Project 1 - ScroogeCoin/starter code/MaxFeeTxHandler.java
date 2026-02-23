import java.util.ArrayList;
import java.util.HashSet;

public class MaxFeeTxHandler {

    private UTXOPool utxoPool;

    /**
     * Creates a public ledger whose current UTXOPool (collection of unspent
     * transaction outputs) is utxoPool. Makes a defensive copy of utxoPool.
     */
    public MaxFeeTxHandler(UTXOPool utxoPool) {
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

        for (int i = 0; i < tx.numInputs(); i++) {
            Transaction.Input input = tx.getInput(i);

            if (input == null || input.prevTxHash == null) return false;

            UTXO utxo = new UTXO(input.prevTxHash, input.outputIndex);

            if (!utxoPool.contains(utxo)) return false;

            Transaction.Output prevOutput = utxoPool.getTxOutput(utxo);
            if (prevOutput == null) return false;

            byte[] rawData = tx.getRawDataToSign(i);
            if (rawData == null || input.signature == null) return false;
            if (!prevOutput.address.verifySignature(rawData, input.signature)) return false;

            if (seenUTXOs.contains(utxo)) return false;
            seenUTXOs.add(utxo);

            inputSum += prevOutput.value;
        }

        for (int i = 0; i < tx.numOutputs(); i++) {
            Transaction.Output output = tx.getOutput(i);
            if (output == null || output.value < 0) return false;

            outputSum += output.value;
        }

        if (inputSum < outputSum) return false;

        return true;
    }

    /**
     * Calculates the transaction fee (sum of inputs - sum of outputs).
     */
    private double calculateFee(Transaction tx) {
        double inputSum = 0;
        for (Transaction.Input input : tx.getInputs()) {
            UTXO utxo = new UTXO(input.prevTxHash, input.outputIndex);
            inputSum += utxoPool.getTxOutput(utxo).value;
        }

        double outputSum = 0;
        for (Transaction.Output output : tx.getOutputs()) {
            outputSum += output.value;
        }

        return inputSum - outputSum;
    }

    /**
     * Handles each epoch by receiving an unordered array of proposed transactions,
     * checking each transaction for correctness, returning a mutually valid array
     * of accepted transactions, and updating the current UTXO pool as appropriate.
     * * This implementation maximizes total transaction fees.
     */
    public Transaction[] handleTxs(Transaction[] possibleTxs) {
        if (possibleTxs == null) return new Transaction[0];

        ArrayList<Transaction> accepted = new ArrayList<>();

        while (true) {
            Transaction maxFeeTx = null;
            double maxFee = -1;

            // Find the valid transaction with the highest fee in the current pool state
            for (Transaction tx : possibleTxs) {
                if (tx != null && isValidTx(tx)) {
                    double currentFee = calculateFee(tx);
                    if (currentFee > maxFee) {
                        maxFee = currentFee;
                        maxFeeTx = tx;
                    }
                }
            }

            // If no valid transactions are left to process, break out
            if (maxFeeTx == null) {
                break;
            }

            // Accept the transaction with the highest fee
            accepted.add(maxFeeTx);

            // Update the UTXOPool: remove spent inputs
            for (Transaction.Input input : maxFeeTx.getInputs()) {
                UTXO spent = new UTXO(input.prevTxHash, input.outputIndex);
                utxoPool.removeUTXO(spent);
            }

            // Update the UTXOPool: add new outputs
            byte[] txHash = maxFeeTx.getHash();
            for (int j = 0; j < maxFeeTx.numOutputs(); j++) {
                UTXO newUTXO = new UTXO(txHash, j);
                utxoPool.addUTXO(newUTXO, maxFeeTx.getOutput(j));
            }
        }

        return accepted.toArray(new Transaction[0]);
    }
}