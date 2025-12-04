package messenger; //package name for organizing classes in the messenger project

import protocol.Error; //import custom error codes defined in protocol
import protocol.Method; //import enum of protocol methods like REGISTER, LOGIN, WHOIS
import protocol.Packet; //import packet class for sending/receiving structured requests
import server.HandlesMethod; //import annotation to mark handler methods for specific protocol methods
import server.PacketHandler; //import base class that dispatches packets to handler methods

import java.io.*; //for input/output streams and exceptions
import java.net.*; //for tcp sockets and inetaddress
import java.util.*; //for collections like map and hashMap

//server class extends PacketHandler to process incoming packets
public class Server extends PacketHandler {
    //inner class to represent a user session (active connection info)
    private static class UserSession {
        public String username; //username of the client
        public InetAddress address; //ip address of the client
        public int port; //port number where client listens
        public Status status = Status.ONLINE; //default status is online

        //constructor to initialize user session with username, address, and port
        public UserSession(String username, InetAddress address, int port) {
            this.username = username;
            this.address = address;
            this.port = port;
        }
    }

    //server port number
    private int port;
    //server socket to accept incoming connections
    private ServerSocket socket;
    //map to store usernames and their passwords
    private final Map<String, String> userPasswords = new HashMap<>();
    //map to store active user sessions
    private final Map<String, UserSession> userSessions = new HashMap<>();

    //constructor to initialize server with given port
    public Server(int port) throws IOException {
        this.port = port;
        this.socket = new ServerSocket(port); //bind server socket to port
    }

    //method to accept a single connection and process one request
    public void listen() throws IOException {
        //accept incoming connection (blocks until client connects)
        try (var connection = socket.accept()) {
            //print connection info (ip and port of client)
            IO.println("Received connection from %s:%d".formatted(connection.getInetAddress(), connection.getPort()));
            //read packet sent by client
            var request = Packet.readPacket(connection);
            IO.println("\t" + request); //print packet for debugging
            Packet response;
            try {
                //dispatch request to appropriate handler method
                response = this.runRequestHandler(request);
            } catch (IllegalArgumentException _) {
                //if method not supported, return error packet
                response = new Packet(Error.UNSUPPORTED_METHOD);
            }
            //send response packet back to client
            Packet.sendPacket(connection, response);
        }
    }

    //method to continuously listen for connections in a loop
    public void listenLoop() {
        IO.println("Listening on port %d".formatted(port)); //print listening port
        while (true) {
            try {
                //accept and process one connection
                listen();
            } catch (IOException e) {
                //print stack trace if error occurs and exit loop
                e.printStackTrace();
                return;
            }
        }
    }

    //main method to start server
    static void main() throws IOException {
        var server = new Server(Config.SERVER_PORT); //create server on configured port
        server.listenLoop(); //start listening loop
    }

    //handler for REGISTER method
    @HandlesMethod(Method.REGISTER)
    public Packet onRequestREGISTER(Packet request) {
        //extract username and password from packet headers
        var username = request.headers().get("username");
        var password = request.headers().get("password");
        //if username already exists, return error
        if (userPasswords.containsKey(username))
            return new Packet(Error.USER_ALREADY_EXISTS);
        //store new username and password
        userPasswords.put(username, password);
        //create new user session with username, client address, and listen port
        var session = new UserSession(username, request.address(),
                Integer.parseInt(request.headers().get("listenPort")));
        //add session to active sessions map
        userSessions.put(username, session);
        //return success packet
        return new Packet(Method.SUCCESS);
    }

    //handler for LOGIN method
    @HandlesMethod(Method.LOGIN)
    public Packet onRequestLOGIN(Packet request) {
        //extract username and password from packet headers
        var username = request.headers().get("username");
        var password = request.headers().get("password");
        //if either is missing, return malformed request error
        if (username == null || password == null)
            return new Packet(Error.MALFORMED_REQUEST);
        //get stored password for username
        var target = userPasswords.get(username);
        //if password does not match, return wrong credentials error
        if (!password.equals(target))
            return new Packet(Error.WRONG_CREDENTIALS);
        //create new user session with username, client address, and listen port
        var session = new UserSession(username, request.address(),
                Integer.parseInt(request.headers().get("listenPort")));
        //add session to active sessions map
        userSessions.put(username, session);
        //return success packet
        return new Packet(Method.SUCCESS);
    }

    //handler for WHOIS method
    @HandlesMethod(Method.WHOIS)
    public Packet onRequestWHOIS(Packet request) {
        //extract username from packet headers
        var username = request.headers().get("username");
        //if username missing, return malformed request error
        if (username == null)
            return new Packet(Error.MALFORMED_REQUEST);
        //if username not registered, return no such user error
        if (!userPasswords.containsKey(username))
            return new Packet(Error.NO_SUCH_USER);
        //get active session for username
        var session = userSessions.get(username);
        //if no session or user is offline, return user not online error
        if (session == null || session.status == Status.OFFLINE)
            return new Packet(Error.USER_NOT_ONLINE);
        //otherwise return success packet with user info headers
        return new Packet(Method.SUCCESS, Map.of(
                "address", session.address.getHostAddress(), //ip address of user
                "port", String.valueOf(session.port), //port number of user
                "status", session.status.toString() //current status of user
        ));
    }
}