package messenger;

import protocol.Error;
import protocol.Method;
import protocol.Packet;
import server.HandlesMethod;
import server.PacketHandler;

import java.io.*;
import java.net.*;
import java.util.*;

public class Server extends PacketHandler {
    private static class UserSession {
        public String username;
        public InetAddress address;
        public int port;
        public Status status = Status.ONLINE;

        public UserSession(String username, InetAddress address, int port) {
            this.username = username;
            this.address = address;
            this.port = port;
        }
    }

    private int port;
    private ServerSocket socket;
    private final Map<String, String> userPasswords = new HashMap<>();
    private final Map<String, UserSession> userSessions = new HashMap<>();

    public Server(int port) throws IOException {
        this.port = port;
        this.socket = new ServerSocket(port);
    }

    public void listen() throws IOException {
        try (var connection = socket.accept()) {
            var request = Packet.readPacket(connection);
            Packet response;
            try {
                response = this.runRequestHandler(request);
            } catch (IllegalArgumentException _) {
                response = new Packet(Error.UNSUPPORTED_METHOD);
            }
            Packet.sendPacket(connection, response);
        }
    }

    public void listenLoop() {
        while (true) {
            try {
                listen();
            } catch (IOException e) {
                e.printStackTrace();
                return;
            }
        }
    }

    static void main() throws IOException {
        var server = new Server(Config.SERVER_PORT);
        server.listenLoop();
    }

    @HandlesMethod(Method.REGISTER)
    public Packet onRequestREGISTER(Packet request) {
        var username = request.headers().get("username");
        var password = request.headers().get("password");
        if (userPasswords.containsKey(username))
            return new Packet(Error.USER_ALREADY_EXISTS);
        userPasswords.put(username, password);
        var session = new UserSession(username, request.address(),
                Integer.parseInt(request.headers().get("listenPort")));
        userSessions.put(username, session);
        return new Packet(Method.SUCCESS);
    }

    @HandlesMethod(Method.LOGIN)
    public Packet onRequestLOGIN(Packet request) {
        var username = request.headers().get("username");
        var password = request.headers().get("password");
        if (username == null || password == null)
            return new Packet(Error.MALFORMED_REQUEST);
        var target = userPasswords.get(username);
        if (!password.equals(target))
            return new Packet(Error.WRONG_CREDENTIALS);
        var session = new UserSession(username, request.address(),
                Integer.parseInt(request.headers().get("listenPort")));
        userSessions.put(username, session);
        return new Packet(Method.SUCCESS);
    }

    @HandlesMethod(Method.WHOIS)
    public Packet onRequestWHOIS(Packet request) {
        var username = request.headers().get("username");
        if (username == null)
            return new Packet(Error.MALFORMED_REQUEST);
        if (!userPasswords.containsKey(username))
            return new Packet(Error.NO_SUCH_USER);
        var session = userSessions.get(username);
        if (session == null || session.status == Status.OFFLINE)
            return new Packet(Error.USER_NOT_ONLINE);
        return new Packet(Method.SUCCESS, Map.of(
                "address", session.address.toString(),
                "port", String.valueOf(session.port),
                "status", session.status.toString()
        ));
    }
}