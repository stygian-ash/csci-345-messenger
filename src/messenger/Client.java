package messenger;

import protocol.Error;
import protocol.Method;
import protocol.Packet;
import server.HandlesMethod;
import server.PacketHandler;

import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.Locale;
import java.util.Map;

public class Client {
    public int port;
    public String serverIP;
    public int serverPort;
    public Socket socket;
    public BufferedReader in;
    public PrintWriter out;
    public InputStream rawIn;
    public OutputStream rawOut;
    public Thread readerThread;
    public String username;
    public String password;
    public Socket peerSocket = null;
    public Thread incomingThread = null;
    public ServerSocket serverSocket;
    public Thread serverThread;

    private static class IncomingPacketHandler extends PacketHandler implements Runnable {
        private Socket socket;

        public IncomingPacketHandler(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            while (!socket.isClosed()) {
                Packet response = null;
                try {
                    var request = Packet.readPacket(socket);
                    response = switch (request.method()) {
                        case HELLO ->  handleHELLO(request);
                        case MESSAGE ->  handleMESSAGE(request);
                        case GOODBYE ->  handlesGOODBYE(request);
                        case FILE ->  handleFILE(request);
                        default -> new Packet(Error.UNSUPPORTED_METHOD);
                    };
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }

                try {
                    Packet.sendPacket(socket, response);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        }

        @HandlesMethod(Method.HELLO)
        public Packet handleHELLO(Packet hello) throws IOException {
            return new Packet(Method.SUCCESS);
        }

        @HandlesMethod(Method.MESSAGE)
        public Packet handleMESSAGE(Packet message) {
            IO.println(message.content());
            return new Packet(Method.SUCCESS);
        }

        @HandlesMethod(Method.FILE)
        public Packet handleFILE(Packet message) {
            System.out.printf("Received file %s\n", message.headers().get("filename"));
            try (var output = new BufferedWriter(new FileWriter(message.headers().get("filename")))) {
                output.write(message.content());
            } catch (IOException e) {
                IO.println("Failed to download file!");
                e.printStackTrace();
            }
            return new Packet(Method.SUCCESS);
        }

        @HandlesMethod(Method.GOODBYE)
        public Packet handlesGOODBYE(Packet message) throws IOException {
            socket.close();
            return new Packet(Method.SUCCESS);
        }
    }

    public static void main(String[] args) throws Exception {
        Client client = new Client(Integer.parseInt(args[1]), Config.SERVER_IP, Config.SERVER_PORT);
        client.runConsole();
    }

    public Client(int port, String serverIP, int serverPort) throws IOException {
        this.port = port;
        this.serverIP = serverIP;
        this.serverPort = serverPort;
        this.serverSocket = new ServerSocket(port);
        this.serverThread = new Thread(() -> {
            while (true) {
                try (var connection = serverSocket.accept()) {
                    setStatus(Status.CHATTING);
                    synchronized (Client.this) {
                        peerSocket = connection;
                        incomingThread = new Thread(new IncomingPacketHandler(connection));
                        incomingThread.start();
                    }
                    incomingThread.join();
                } catch (IOException | InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        });
        this.serverThread.start();
    }

    public Packet makeServerRequest(Packet request) throws IOException {
        try (var socket = new Socket(serverIP, serverPort)) {
            Packet.sendPacket(socket, request);
            return Packet.readPacket(socket);
        }
    }

    public Error register(String username, String password) throws IOException {
        this.username = username;
        this.password = password;
        var request = new Packet(Method.REGISTER,
                Map.of(
                        "username", username,
                        "password", password,
                        "listenPort", String.valueOf(port)
                )
        );

        var response = makeServerRequest(request);
        return response.getError();
    }

    public Error login(String username, String password) throws IOException {
        this.username = username;
        this.password = password;
        var request = new Packet(Method.LOGIN,
                Map.of(
                        "username", username,
                        "password", password,
                        "listenPort", String.valueOf(port)
                )
        );

        var response = makeServerRequest(request);
        return response.getError();
    }

    public Peer whois(String username) throws IOException {
        var request = new Packet(Method.WHOIS, Map.of("username", username));
        var response = makeServerRequest(request);
        if (response.method() != Method.SUCCESS)
            return null;
        return new Peer(
                username,
                InetAddress.getByName(response.headers().get("address")),
                Integer.parseInt(response.headers().get("port")),
                Status.valueOf(response.headers().get("status"))
        );
    }

    public Error setStatus(Status status) throws IOException {
        var request = new Packet(Method.STATUS,
                Map.of(
                        "username", username,
                        "password", password,
                        "status", status.toString()
                )
        );
        var response = makeServerRequest(request);
        return response.getError();
    }

    public void connectToPeer(String username) throws IOException {
        var peer = whois(username);
        if (peer == null || peer.status() != Status.READY)
            return;
        setStatus(Status.CHATTING);
        peerSocket = new Socket(peer.address(), peer.port());
        incomingThread = new Thread(new IncomingPacketHandler(peerSocket));
        incomingThread.start();
    }

   public Error sendMessage(String message) throws IOException {
        var packet = new Packet(Method.MESSAGE, message);
        Packet.sendPacket(peerSocket, packet);
        var response = Packet.readPacket(peerSocket);
        return response.getError();
   }

    public synchronized void destroySession() throws IOException {
        if (!peerSocket.isClosed()) {
            Packet.sendPacket(peerSocket, new Packet(Method.GOODBYE));
            var response = Packet.readPacket(socket);
            peerSocket.close();
        }
        incomingThread = null;
        peerSocket = null;
    }

    public void runConsole() throws IOException {
        BufferedReader stdin = new BufferedReader(new InputStreamReader(System.in));
        System.out.println("Client ready. Type 'login <host> <port> <username> <password>' to begin.");

        String line;
        while ((line = stdin.readLine()) != null) {
            if (line.trim().isEmpty()) continue;
            String[] parts = line.trim().split("\\s+");
            String cmd = parts[0].toLowerCase(Locale.ROOT);

            switch (cmd) {
                case "login":
                    if (parts.length != 5) {
                        System.out.println("Usage: login <host> <port> <username> <password>");
                        break;
                    }
                    String host = parts[1];
                    int port = Integer.parseInt(parts[2]);
                    String user = parts[3];
                    String pass = parts[4];
                    throw new UnsupportedOperationException("TODO");

                case "status":
                    if (!isConnected()) { System.out.println("Not connected."); break; }
                    if (parts.length != 2) {
                        System.out.println("Usage: status <Online|Away|Busy|Offline>");
                        break;
                    }
                    out.println("STATUS " + parts[1]);
                    break;

                case "msg":
                    if (!isConnected()) { System.out.println("Not connected."); break; }
                    if (parts.length < 3) {
                        System.out.println("Usage: msg <recipient> <message...>");
                        break;
                    }
                    String recipient = parts[1];
                    String message = line.substring(line.indexOf(recipient) + recipient.length()).trim();
                    out.println("MSG " + recipient + " " + message);
                    break;

                case "sendfile":
                    if (!isConnected()) { System.out.println("Not connected."); break; }
                    if (parts.length != 3) {
                        System.out.println("Usage: sendfile <recipient> <path_to_file>");
                        break;
                    }
                    String toUser = parts[1];
                    Path path = Paths.get(parts[2]);
                    sendFile(toUser, path);
                    break;

                case "quit":
                    close();
                    System.out.println("Bye.");
                    return;

                default:
                    System.out.println("Unknown command.");
            }
        }
    }

    private void readerLoop() {
        try {
            String line;
            while ((line = in.readLine()) != null) {
                if (line.startsWith("MSG_FROM ")) {
                    String[] parts = line.split("\\s+", 3);
                    String from = parts.length >= 2 ? parts[1] : "?";
                    String msg = parts.length == 3 ? parts[2] : "";
                    System.out.println("[" + from + "] " + msg);
                } else if (line.startsWith("FILE_FROM ")) {
                    // Format: FILE_FROM <sender> <filename> <size>
                    String[] parts = line.split("\\s+");
                    if (parts.length != 4) {
                        System.out.println("Bad FILE_FROM header: " + line);
                        continue;
                    }
                    String sender = parts[1];
                    String filename = parts[2];
                    long size = Long.parseLong(parts[3]);

                    Path savePath = Paths.get("downloads").resolve(filename);
                    Files.createDirectories(savePath.getParent());
                    receiveFile(savePath, size);
                    System.out.println("File received from " + sender + ": " + savePath.toAbsolutePath());
                } else if (line.startsWith("PRESENCE ")) {
                    System.out.println("Online: " + line.substring("PRESENCE ".length()));
                } else if (line.startsWith("ERROR FILE_TRANSFER_FAILED ")){
                    // Handling the new file failure message from the server
                    System.err.println("The server failed to relay the file. Recipient may have been disconnected");
                } else if (line.startsWith("ERROR ")) {
                    System.out.println("Server error: " + line.substring("ERROR ".length()));
                } else if (line.startsWith("STATUS_OK")) {
                    System.out.println("Status updated.");
                } else if (line.startsWith("MSG_SENT ")) {
                    System.out.println("Message sent to " + line.substring("MSG_SENT ".length()));
                } else if (line.startsWith("FILE_OK")) {
                    System.out.println("File transfer starting...");
                } else if (line.startsWith("FILE_SENT ")) {
                    System.out.println("File sent: " + line.substring("FILE_SENT ".length()));
                } else {
                    // Other lines
                    System.out.println(line);
                }
            }
        } catch(SocketException e) {
            // Log message if server shuts down or connection is broken
            System.out.println("Disconnected from server: " + e.getMessage());
        } catch (IOException e) {
            System.out.println("Disconnected (I/O Errror): " + e.getMessage());
        } catch (NumberFormatException e) {
            System.err.println("Protocol Error: Received invalid number (e.g., file size)" );
        } finally {
            close();
        }
    }

    public void sendFile(String recipient, Path path) {
        try {
            if (!Files.exists(path)) {
                System.out.println("File not found: " + path);
                return;
            }
            long size = Files.size(path);
            String filename = path.getFileName().toString();
            // Send header
            out.println("FILE " + recipient + " " + filename + " " + size);

            // Send bytes
            try (InputStream fis = Files.newInputStream(path)) {
                byte[] buf = new byte[8192];
                long remaining = size;
                while (remaining > 0) {
                    int read = fis.read(buf, 0, (int)Math.min(buf.length, remaining));
                    if (read == -1) throw new EOFException("Unexpected EOF");
                    rawOut.write(buf, 0, read);
                    remaining -= read;
                }
                rawOut.flush();
            }
        } catch (IOException e) {
            System.out.println("sendFile error: " + e.getMessage());
        }
    }

    public void receiveFile(Path savePath, long size) throws IOException {
        try (OutputStream outFile = Files.newOutputStream(savePath, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
            byte[] buf = new byte[8192];
            long remaining = size;
            while (remaining > 0) {
                int read = rawIn.read(buf, 0, (int)Math.min(buf.length, remaining));
                if (read == -1) throw new EOFException("Unexpected EOF during file receive");
                outFile.write(buf, 0, read);
                remaining -= read;
            }
        }
    }

    public boolean isConnected() {
        return socket != null && socket.isConnected() && !socket.isClosed();
    }

    public void close() {
        try { if (readerThread != null) readerThread.interrupt(); } catch (Exception ignored) {}
        try { if (socket != null) socket.close(); } catch (IOException ignored) {}
        socket = null; in = null; out = null; rawIn = null; rawOut = null;
    }
}
