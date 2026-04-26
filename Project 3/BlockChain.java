import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;

/* Block Chain should maintain only limited block nodes to satisfy the functions
   You should not have the all the blocks added to the block chain in memory 
   as it would overflow memory
 */

public class BlockChain {
   public static final int CUT_OFF_AGE = 10;

   // all information required in handling a block in block chain
   private class BlockNode {
      public Block b;
      public BlockNode parent;
      public ArrayList<BlockNode> children;
      public int height;
      // utxo pool for making a new block on top of this block
      private UTXOPool uPool;

      public BlockNode(Block b, BlockNode parent, UTXOPool uPool) {
         this.b = b;
         this.parent = parent;
         children = new ArrayList<BlockNode>();
         this.uPool = uPool;
         if (parent != null) {
            height = parent.height + 1;
            parent.children.add(this);
         } else {
            height = 1;
         }
      }

      public UTXOPool getUTXOPoolCopy() {
         return new UTXOPool(uPool);
      }
   }

   // Map from block hash (wrapped) to BlockNode
   private HashMap<ByteArrayWrapper, BlockNode> blockMap;
   // The current max-height node (oldest if tie)
   private BlockNode maxHeightNode;
   // Global transaction pool
   private TransactionPool txPool;

   /* create an empty block chain with just a genesis block.
    * Assume genesis block is a valid block
    */
   public BlockChain(Block genesisBlock) {
      blockMap = new HashMap<>();
      txPool = new TransactionPool();

      // Build initial UTXO pool: add the coinbase transaction output
      UTXOPool genesisPool = new UTXOPool();
      Transaction coinbase = genesisBlock.getCoinbase();
      for (int i = 0; i < coinbase.getOutputs().size(); i++) {
         UTXO utxo = new UTXO(coinbase.getHash(), i);
         genesisPool.addUTXO(utxo, coinbase.getOutput(i));
      }

      BlockNode genesisNode = new BlockNode(genesisBlock, null, genesisPool);
      blockMap.put(wrap(genesisBlock.getHash()), genesisNode);
      maxHeightNode = genesisNode;
   }

   /* Get the maximum height block
    */
   public Block getMaxHeightBlock() {
      return maxHeightNode.b;
   }
   
   /* Get the UTXOPool for mining a new block on top of 
    * max height block
    */
   public UTXOPool getMaxHeightUTXOPool() {
      return maxHeightNode.getUTXOPoolCopy();
   }
   
   /* Get the transaction pool to mine a new block
    */
   public TransactionPool getTransactionPool() {
      return txPool;
   }

   /* Add a block to block chain if it is valid.
    * For validity, all transactions should be valid
    * and block should be at height > (maxHeight - CUT_OFF_AGE).
    * Return true if block is successfully added
    */
   public boolean addBlock(Block b) {
      byte[] prevHash = b.getPrevBlockHash();
      
      // A new genesis block (null parent hash) is rejected
      if (prevHash == null) return false;

      // Find parent node
      BlockNode parentNode = blockMap.get(wrap(prevHash));
      if (parentNode == null) return false;

      int newHeight = parentNode.height + 1;

      // Height check: must be > maxHeight - CUT_OFF_AGE
      if (newHeight <= maxHeightNode.height - CUT_OFF_AGE) return false;

      // Validate all transactions using parent's UTXO pool
      UTXOPool parentPool = parentNode.getUTXOPoolCopy();
      TxHandler handler = new TxHandler(parentPool);

      ArrayList<Transaction> txs = b.getTransactions();
      Transaction[] txArray = txs.toArray(new Transaction[0]);
      Transaction[] validTxs = handler.handleTxs(txArray);

      // All transactions in the block must be valid
      if (validTxs.length != txArray.length) return false;

      // Build UTXO pool for this new block:
      // Start from the handler's updated pool, then add the coinbase
      UTXOPool newPool = handler.getUTXOPool();
      Transaction coinbase = b.getCoinbase();
      for (int i = 0; i < coinbase.getOutputs().size(); i++) {
         UTXO utxo = new UTXO(coinbase.getHash(), i);
         newPool.addUTXO(utxo, coinbase.getOutput(i));
      }

      // Create new node
      BlockNode newNode = new BlockNode(b, parentNode, newPool);
      blockMap.put(wrap(b.getHash()), newNode);

      // Update max height node (keep oldest if same height)
      if (newHeight > maxHeightNode.height) {
         maxHeightNode = newNode;
      }

      // Remove validated transactions from global pool
      for (Transaction tx : validTxs) {
         txPool.removeTransaction(tx.getHash());
      }

      // Prune old nodes that are beyond CUT_OFF_AGE
      pruneOldNodes();

      return true;
   }

   /* Add a transaction in transaction pool
    */
   public void addTransaction(Transaction tx) {
      txPool.addTransaction(tx);
   }

   // Helper: wrap a byte array for use as HashMap key
   private ByteArrayWrapper wrap(byte[] bytes) {
      return new ByteArrayWrapper(bytes);
   }

   // Remove nodes that are too old to ever be built upon.
   // We keep nodes at height >= (maxHeight - CUT_OFF_AGE) so they can still
   // serve as parents for new blocks at height > (maxHeight - CUT_OFF_AGE).
   private void pruneOldNodes() {
      int cutoff = maxHeightNode.height - CUT_OFF_AGE;
      ArrayList<ByteArrayWrapper> toRemove = new ArrayList<>();
      for (ByteArrayWrapper key : blockMap.keySet()) {
         if (blockMap.get(key).height < cutoff) {
            toRemove.add(key);
         }
      }
      for (ByteArrayWrapper key : toRemove) {
         blockMap.remove(key);
      }
   }
}
