import java.util.ArrayList;
import java.util.HashSet;

public class TxHandler {

    // storing the UTXOPool so we can have access to it
    private UTXOPool utxoPool;

	/* Creates a public ledger whose current UTXOPool (collection of unspent 
	 * transaction outputs) is utxoPool. This should make a defensive copy of 
	 * utxoPool by using the UTXOPool(UTXOPool uPool) constructor.
	 */
    public TxHandler(UTXOPool utxoPool) {
        this.utxoPool = new UTXOPool(utxoPool);
    }

	/* Returns true if 
	 * (1) all outputs claimed by tx are in the current UTXO pool, 
	 * (2) the signatures on each input of tx are valid, 
	 * (3) no UTXO is claimed multiple times by tx, 
	 * (4) all of tx’s output values are non-negative, and
	 * (5) the sum of tx’s input values is greater than or equal to the sum of   
	        its output values;
	   and false otherwise.
	 */
    public boolean isValidTx(Transaction tx) {

        // make a hash set to keep all UTXOs weve seen and make sure no double spends
        HashSet<UTXO> prevousUTXOs = new HashSet<>();

        // track the totals for output and inputs to make sure no new money is created
        double inputTotalAmount = 0;
        double outputTotalAmount = 0;

        // loops over and check all UTXOs inputs
        for (int i = 0; i < tx.numInputs(); i++) {

            // First get the input of the transaction
            Transaction.Input input = tx.getInput(i);

            // create the UTXO based on the input
            UTXO utxo = new UTXO(input.prevTxHash, input.outputIndex);

            // (1) Make sure the UTXO that I just created is in the pool
            if (!utxoPool.contains(utxo)) {
                return false;
            }

            // here I get the before output to see if its valid
            Transaction.Output prevOutput = utxoPool.getTxOutput(utxo);

            // (2) Make sure the it has a valid signature
            // get the raw data so that I can verify the signature
            byte[] rawData = tx.getRawDataToSign(i);

            // use rsa to check the public key with the signature to make sure its legit
            if (!prevOutput.address.verifySignature(rawData, input.signature)) {
                return false;
            }

            // (3) No double spend so check if this UTXO has already been used then add it to prevous
            if (prevousUTXOs.contains(utxo)) {
                return false;
            }
            prevousUTXOs.add(utxo);

            // finally add the value of the UTXO to the transaction to check later for step 5
            inputTotalAmount += prevOutput.value;
        }

        // now check all outputs are legit
        for (int i = 0; i < tx.numOutputs(); i++) {
            // get the output
            Transaction.Output output = tx.getOutput(i);

            // (4) Make sure its not negative 
            if (output.value < 0) {
                return false;
            }

            // add the value of the output to the transaction to check later for step 5
            outputTotalAmount += output.value;
        }

        // (5) Make sure they didnt spend more money then they had
        if (inputTotalAmount < outputTotalAmount) {
            return false;
        }

        return true;
    }

	/* Handles each epoch by receiving an unordered array of proposed 
	 * transactions, checking each transaction for correctness, 
	 * returning a mutually valid array of accepted transactions, 
	 * and updating the current UTXO pool as appropriate.
	 */
    public Transaction[] handleTxs(Transaction[] possibleTxs) {

        // keep track of all the accepted transactions
        ArrayList<Transaction> accepted = new ArrayList<>();
        // also keep track of processed transactions just to not check again or double spend
        boolean[] processed = new boolean[possibleTxs.length];

        // loop through all the transactions and see which ones are valid and do this until no new transactions are valid because some might rely on others
        boolean keepGoing = true;
        while (keepGoing) {
            keepGoing = false;
            // loop through all the transactions
            for (int i = 0; i < possibleTxs.length; i++) {
                // if we see this already and did it we skip it
                if (processed[i] == true) {
                    continue;
                }

                // make sure its valid
                if (isValidTx(possibleTxs[i])) {
                    // save it, accept it, make sure its valid, and keep going now
                    Transaction tx = possibleTxs[i];
                    accepted.add(tx);
                    processed[i] = true;
                    keepGoing = true;

                    // loop through all the input so they can be removed now since valid
                   for (int b = 0; b < tx.numInputs(); b++) {
                        // get the input
                        Transaction.Input input = tx.getInput(b);
                        // create a UTXO so it can be removed
                        UTXO spent = new UTXO(input.prevTxHash, input.outputIndex);
                        utxoPool.removeUTXO(spent);
                    }

                    // now get the outputs and put them into the pool
                    byte[] transactionHash = tx.getHash();
                    for (int j = 0; j < tx.numOutputs(); j++) {
                        UTXO newUTXO = new UTXO(transactionHash, j);
                        utxoPool.addUTXO(newUTXO, tx.getOutput(j));
                    }
                }
            }
        }

        return accepted.toArray(new Transaction[0]);
    }
}
