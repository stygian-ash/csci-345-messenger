package protocol;

/**
 * Indicates the reading of a packet failed because it was malformed.
 */
public class PacketMalformedException extends RuntimeException {
    public PacketMalformedException(String message) {
        super(message);
    }
}
