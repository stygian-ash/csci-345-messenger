package messenger; //package name for organizing classes

import protocol.Error; //import custom error codes defined in protocol
import protocol.Method; //import enum of protocol methods like REGISTER, LOGIN, WHOIS
import protocol.Packet; //import packet class for sending/receiving structured requests

import java.io.*; //for input/output streams and exceptions
import java.net.*; //for tcp socket and inetaddress
import java.nio.file.*; //for file path and file operations
import java.util.Locale; //for locale when converting strings to lowercase
import java.util.Map; //for creating maps of headers

//client class that connects to server and handles commands
public class Client {
    //port number where client listens
    private int port;
    //server ip address
    private String serverIP;
    //server port number
    private int serverPort;
    //tcp socket connection to server
    private Socket socket;
    //reader for line-based input from server
    private BufferedReader in;
    //writer for line-based output to server
    private PrintWriter out;
    //raw input stream for binary data
    private InputStream rawIn;
    //raw output stream for binary data
    private OutputStream rawOut;
    //thread to continuously read server messages
    private Thread readerThread;

    //main method, entry point of client program
    public static void main(String[] args) throws Exception {
        //create client object with port from args and server config
        Client client = new Client(Integer.parseInt(args[1]), Config.SERVER_IP, Config.SERVER_PORT);
        //run console interface
        client.runConsole();
    }

    //constructor to initialize client with port, server ip, and server port
    public Client(int port, String serverIP, int serverPort) {
        this.port = port;
        this.serverIP = serverIP;
        this.serverPort = serverPort;
    }

    //method to send a packet to server and receive response
    public Packet makeServerRequest(Packet request) throws IOException {
        //open temporary socket connection to server
        try (var socket = new Socket(serverIP, serverPort)) {
            //send packet to server
            Packet.sendPacket(socket, request);
            //read response packet from server
            return Packet.readPacket(socket);
        }
    }

    //method to register a new user
    public Error register(String username, String password) throws IOException {
        //create packet with REGISTER method and headers
        var request = new Packet(Method.REGISTER,
                Map.of(
                        "username", username,
                        "password", password,
                        "listenPort", String.valueOf(port)
                )
        );
        //send request and get response
        var response = makeServerRequest(request);
        //return error code from response
        return response.getError();
    }

    //method to login existing user
    public Error login(String username, String password) throws IOException {
        //create packet with LOGIN method and headers
        var request = new Packet(Method.LOGIN,
                Map.of(
                        "username", username,
                        "password", password,
                        "listenPort", String.valueOf(port)
                )
        );
        //send request and get response
        var response = makeServerRequest(request);
        //return error code from response
        return response.getError();
    }

    //method to look up user info
    public Peer whois(String username) throws IOException {
        //create packet with WHOIS method and username header
        var request = new Packet(Method.WHOIS, Map.of("username", username));
        //send request and get response
        var response = makeServerRequest(request);
        //if response is not success, return null
        if (response.method() != Method.SUCCESS)
            return null;
        //otherwise create peer object with username, address, and port from headers
        return new Peer(
                username,
                InetAddress.getByName(response.headers().get("address")),
                Integer.parseInt(response.headers().get("port"))
        );
    }

    //method to run console interface for user commands
    public void runConsole() throws IOException {
        //reader for user input from keyboard
        BufferedReader stdin = new BufferedReader(new InputStreamReader(System.in));
        //print instructions
        System.out.println("Client ready. Type 'login <host> <port> <username> <password>' to begin.");

        String line; //variable to hold user input
        //loop to continuously read user input
        while ((line = stdin.readLine()) != null) {
            //ignore empty lines
            if (line.trim().isEmpty()) continue;
            //split line into parts
            String[] parts = line.trim().split("\\s+");
            //get command word in lowercase
            String cmd = parts[0].toLowerCase(Locale.ROOT);

            //switch to handle commands
            switch (cmd) {
                case "login":
                    //login requires 5 arguments
                    if (parts.length != 5) {
                        System.out.println("Usage: login <host> <port> <username> <password>");
                        break;
                    }
                    //extract host, port, username, password
                    String host = parts[1];
                    int port = Integer.parseInt(parts[2]);
                    String user = parts[3];
                    String pass = parts[4];
                    //not yet implemented
                    throw new UnsupportedOperationException("TODO");

                case "status":
                    //status requires connection
                    if (!isConnected()) { System.out.println("Not connected."); break; }
                    //status requires exactly 2 arguments
                    if (parts.length != 2) {
                        System.out.println("Usage: status <Online|Away|Busy|Offline>");
                        break;
                    }
                    //send status command to server
                    out.println("STATUS " + parts[1]);
                    break;

                case "msg":
                    //msg requires connection
                    if (!isConnected()) { System.out.println("Not connected."); break; }
                    //msg requires at least 3 arguments
                    if (parts.length < 3) {
                        System.out.println("Usage: msg <recipient> <message...>");
                        break;
                    }
                    //extract recipient
                    String recipient = parts[1];
                    //extract message text after recipient
                    String message = line.substring(line.indexOf(recipient) + recipient.length()).trim();
                    //send msg command to server
                    out.println("MSG " + recipient + " " + message);
                    break;

                case "sendfile":
                    //sendfile requires connection
                    if (!isConnected()) { System.out.println("Not connected."); break; }
                    //sendfile requires exactly 3 arguments
                    if (parts.length != 3) {
                        System.out.println("Usage: sendfile <recipient> <path_to_file>");
                        break;
                    }
                    //extract recipient and file path
                    String toUser = parts[1];
                    Path path = Paths.get(parts[2]);
                    //call sendFile method
                    sendFile(toUser, path);
                    break;

                case "quit":
                    //close connection and exit
                    close();
                    System.out.println("Bye.");
                    return;

                default:
                    //unknown command
                    System.out.println("Unknown command.");
            }
        }
    }

    //method to continuously read server messages
    private void readerLoop() {
        try {
            String line;
            //loop to read lines from server
            while ((line = in.readLine()) != null) {
                if (line.startsWith("MSG_FROM ")) {
                    //parse message from another user
                    String[] parts = line.split("\\s+", 3);
                    String from = parts.length >= 2 ? parts[1] : "?";
                    String msg = parts.length == 3 ? parts[2] : "";
                    System.out.println("[" + from + "] " + msg);
                } else if (line.startsWith("FILE_FROM ")) {
                    //format: FILE_FROM <sender> <filename> <size>
                    String[] parts = line.split("\\s+");
                    if (parts.length != 4) {
                        System.out.println("Bad FILE_FROM header: " + line);
                        continue;
                    }
                    String sender = parts[1];
                    String filename = parts[2];
                    long size = Long.parseLong(parts[3]);

                    //create downloads directory and save path
                    Path savePath = Paths.get("downloads").resolve(filename);
                    Files.createDirectories(savePath.getParent());
                    //receive file from server
                    receiveFile(savePath, size);
                    System.out.println("File received from " + sender + ": " + savePath.toAbsolutePath());
                } else if (line.startsWith("PRESENCE ")) {
                    //presence update from server
                    System.out.println("Online: " + line.substring("PRESENCE ".length()));
                } else if (line.startsWith("ERROR FILE_TRANSFER_FAILED ")){
                    //handling file transfer failure
                    System.err.println("The server failed to relay the file. Recipient may have been disconnected");
                } else if (line.startsWith("ERROR ")) {
                    //general error message
                    System.out.println("Server error: " + line.substring("ERROR ".length()));
                } else if (line.startsWith("STATUS_OK")) {
                    //status update confirmation
                    System.out.println("Status updated.");
                } else if (line.startsWith("MSG_SENT ")) {
                    //message sent confirmation
                    System.out.println("Message sent to " + line.substring("MSG_SENT ".length()));
                } else if (line.startsWith("FILE_OK")) {
                    //file transfer starting
                    System.out.println("File transfer starting...");
                } else if (line.startsWith("FILE_SENT ")) {
                    //confirmation that file was fully relayed to recipient
                    System.out.println("File sent: " + line.substring("FILE_SENT ".length()));
                } else {
                    //print any other lines for visibility (fallback)
                    System.out.println(line);
                }
            }
        } catch(SocketException e) {
            //print message if server shuts down or connection drops unexpectedly
            System.out.println("Disconnected from server: " + e.getMessage());
        } catch (IOException e) {
            //print i/o error message for read failures
            System.out.println("Disconnected (I/O Errror): " + e.getMessage());
        } catch (NumberFormatException e) {
            //handle parsing issues like invalid file size in protocol
            System.err.println("Protocol Error: Received invalid number (e.g., file size)" );
        } finally {
            //always close connection and cleanup when reader loop exits
            close();
        }
    }

    //method to send a file to another user via the server
    public void sendFile(String recipient, Path path) {
        try {
            //validate that the file exists at the given path
            if (!Files.exists(path)) {
                System.out.println("File not found: " + path);
                return;
            }
            //get total file size in bytes to inform the server
            long size = Files.size(path);
            //extract filename only (without directories) for display and protocol header
            String filename = path.getFileName().toString();
            //send file transfer header line to server with recipient, filename, and size
            out.println("FILE " + recipient + " " + filename + " " + size);

            //open input stream to read file contents from disk
            try (InputStream fis = Files.newInputStream(path)) {
                byte[] buf = new byte[8192]; //buffer for chunked reads
                long remaining = size; //track bytes left to send
                //loop until we send all declared bytes
                while (remaining > 0) {
                    //read up to buffer length or remaining bytes, whichever is smaller
                    int read = fis.read(buf, 0, (int)Math.min(buf.length, remaining));
                    //if end-of-file occurs unexpectedly, throw exception to indicate protocol mismatch
                    if (read == -1) throw new EOFException("Unexpected EOF");
                    //write read bytes to server via raw output stream
                    rawOut.write(buf, 0, read);
                    //subtract the number of bytes just sent from remaining
                    remaining -= read;
                }
                //flush output to ensure all bytes are pushed to the server
                rawOut.flush();
            }
        } catch (IOException e) {
            //print error message if any issue occurs during file send
            System.out.println("sendFile error: " + e.getMessage());
        }
    }

    //method to receive a file of a known size from the server and save to disk
    public void receiveFile(Path savePath, long size) throws IOException {
        //open output stream to write file, create or overwrite existing file
        try (OutputStream outFile = Files.newOutputStream(savePath, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
            byte[] buf = new byte[8192]; //buffer for chunked reads
            long remaining = size; //track how many bytes we still need to read
            //loop until we read exactly the declared number of bytes
            while (remaining > 0) {
                //read up to buffer length or remaining bytes from raw input stream
                int read = rawIn.read(buf, 0, (int)Math.min(buf.length, remaining));
                //if stream ends early, throw exception to indicate protocol/connection error
                if (read == -1) throw new EOFException("Unexpected EOF during file receive");
                //write the read bytes into the output file
                outFile.write(buf, 0, read);
                //subtract the number of bytes just read from remaining
                remaining -= read;
            }
        }
    }

    //method to check if the interactive console client is currently connected
    public boolean isConnected() {
        //socket must be non-null, connected, and not closed
        return socket != null && socket.isConnected() && !socket.isClosed();
    }

    //method to close the interactive connection and clean up streams and thread
    public void close() {
        //try to stop the reader thread gracefully
        try { if (readerThread != null) readerThread.interrupt(); } catch (Exception ignored) {}
        //close the socket if it exists
        try { if (socket != null) socket.close(); } catch (IOException ignored) {}
        //reset all connection-related fields to null for clean state
        socket = null; in = null; out = null; rawIn = null; rawOut = null;
    }
}

