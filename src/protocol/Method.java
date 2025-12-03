package protocol;

/**
 * The method attached to a packet.
 * May be attached to a request or to a response.
 * @see protocol.Packet
 */
public enum Method {
    /* Server Request Methods */
    /** Register an account with the server for the first time. */
    REGISTER,
    /** Log in to the server. */
    LOGIN,
    /** Log out of your session. */
    LOGOUT,
    /** Look up the IP address and port of another user. */
    WHOIS,
    /** Change your status with the server (available, busy, away, etc.) */
    STATUS,

    /* Client Request Methods */
    /** Request to initialize a chat session. */
    HELLO,
    /** Politely end a chat session. */
    GOODBYE,
    /** Send a message in a chat session. */
    MESSAGE,
    /** Initiate file transfer. */
    FILE,

    /* Response Methods */
    /** The requested operation was a success.
     * May have headers or content dependent on the method and result of the request. */
    SUCCESS,
    /** The requested operation failed. Body contains error message. */
    FAILURE
}