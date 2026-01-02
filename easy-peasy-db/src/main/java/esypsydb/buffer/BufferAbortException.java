package esypsydb.buffer;

/**
 * A runtime exception indicating that the transaction
 * needs to abort because a buffer request could not be satisfied.
 */
@SuppressWarnings("serial")
public class BufferAbortException extends RuntimeException {}

