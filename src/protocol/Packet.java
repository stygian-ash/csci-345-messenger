package protocol;

import java.io.*;
import java.net.Socket;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * A packet sent or received by a client.
 * The format of these packets is inspired by HTTP with a method, a collection of headers and an
 * optional message body.
 * Here is an example packet as it is sent over TCP:
 * <pre>
 *     FILE\n
 *     contentLength: 12\n
 *     fileName: hello.txt\n
 *     \n
 *     Hello world!
 * </pre>
 * <p>Note the mandatory {@code contentLength} header, which gives the number of Java (UTF-16) characters in the
 * message body. It is the same as calling {@code content().length()}.</p>
 *
 * <p>Packets can also have an empty body, in which case the {@code contentLength} header is not required.</p>
 * <pre>
 *     LOGIN\n
 *     username: admin\n
 *     password: hunter2\n
 *     \n
 * </pre>
 * Note that the blank line separating the headers from the body is still required. This is how we know when we are
 * done reading the headers.
 *
 * @param method Somewhat like HTTP, describes what the packet is intended to do (send a message, log in, etc.)
 * @param headers A Map of header names to values.
 * @param content The (possibly empty) message body of the packet.
 */
public record Packet(Method method, Map<String, String> headers, String content) {
    /**
     * Instantiate a packet.
     * @param method The packet method.
     * @param headers The packet headers. The {@code contentLength} header is automatically computed.
     * @param content The body of the packet.
     */
    public Packet(Method method, Map<String, String> headers, String content) {
        var modifiedHeaders = new HashMap<>(headers);
        if (!content.isEmpty())
            modifiedHeaders.put("contentLength", String.valueOf(content.length()));
        this.method = method;
        this.headers = Collections.unmodifiableMap(modifiedHeaders);
        this.content = content;
    }

    /**
     * Instantiate a packet with an empty body.
     * @param method The packet method.
     * @param headers The packet headers. The {@code contentLength} header is automatically computed.
     */
    public Packet(Method method, Map<String, String> headers) {
        this(method, headers, "");
    }

    /**
     * Instantiate a packet with no headers.
     * @param method The packet method.
     * @param content The packet body text.
     */
    public Packet(Method method, String content) {
        this(method, Map.of(), content);
    }

    /**
     * Send a packet over a TCP socket.
     * @param socket The socket to use.
     * @throws IOException if the transfer fails.
     */
    public static void sendPacket(Socket socket, Packet packet) throws IOException {
        var output = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
        output.write(packet.method() + "\n");
        for (var header: packet.headers().entrySet())
            output.write("%s: %s\n".formatted(header.getKey(), header.getValue()));
        output.write("\n");
        output.write(packet.content());
        output.flush();
    }

    /**
     * Read a packet from a TCP socket.
     * @param socket The socket to read from.
     * @return A single packet read from the socket.
     * @throws PacketMalformedException if the packet is malformed (wrong {@code contentLength} header,
     * invalid {@code method}, etc.)
     */
    public static Packet readPacket(Socket socket)
            throws IOException, PacketMalformedException {
        var input = new BufferedReader(new InputStreamReader(socket.getInputStream()));

        Method method;
        var methodLine = input.readLine();
        try {
            method = Method.valueOf(methodLine);
        } catch (IllegalArgumentException _) {
            throw new PacketMalformedException("Invalid method '%s'".formatted(methodLine));
        }

        var headers = new HashMap<String, String>();
        String line;
        while (true) {
            line = input.readLine();
            if (line == null || line.isEmpty())
                break;
            var split = line.split(": ", 2);
            headers.put(split[0], split[1]);
        }

        var content = "";
        if (headers.containsKey("contentLength")) {
            var length = Integer.parseInt(headers.get("contentLength"));
            if (length < 0)
                throw new PacketMalformedException("The contentLength header cannot not be negative.");
            var buffer = new char[length];
            var read = input.read(buffer);
            if (read < length)
                throw new PacketMalformedException("Stream ended before we could finish reading packet body.");
            content = String.valueOf(buffer);
        }
        return new Packet(method, Collections.unmodifiableMap(headers), content);
    }
}