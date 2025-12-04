package messenger;

import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.Locale;

public class Client {
    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;
    private InputStream rawIn;
    private OutputStream rawOut;
    private Thread readerThread;

    public static void main(String[] args) throws Exception {
        Client client = new Client();
        client.runConsole();
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
                    doLogin(host, port, user, pass);
                    break;

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

    public void doLogin(String host, int port, String username, String password) {
        try {
            close(); // ensure clean state
            socket = new Socket(host, port);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()), true);
            rawIn = socket.getInputStream();
            rawOut = socket.getOutputStream();

            // Send AUTH
            out.println("AUTH " + username + " " + password);

            // Read response
            String resp = in.readLine();
            if (resp == null || !resp.startsWith("AUTH_OK")) {
                System.out.println("Login failed: " + (resp == null ? "no response" : resp));
                close();
                return;
            }
            System.out.println("Logged in as " + username);

            // Start reader thread for events
            readerThread = new Thread(this::readerLoop, "ServerReader");
            readerThread.setDaemon(true);
            readerThread.start();
        } catch (IOException e) {
            System.out.println("Login error: " + e.getMessage());
            close();
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
