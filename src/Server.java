import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class Server {

    private static final int PORT = 6789;

    // In-memory user store (replace with DB in production)
    private static final Map<String, String> USER_DB = new HashMap<>();

    // Online clients: username -> ClientSession
    private static final ConcurrentHashMap<String, ClientSession> ONLINE = new ConcurrentHashMap<>();

    public static void main(String[] args) throws IOException {
        // Demo users
        USER_DB.put("alice", "alice123");
        USER_DB.put("bob", "bob123");
        USER_DB.put("leysha", "strongpass");

        ServerSocket serverSocket = new ServerSocket(PORT);
        System.out.println("IMServer listening on port " + PORT);

        while (true) {
            Socket socket = serverSocket.accept();
            new Thread(new ClientHandler(socket)).start();
        }
    }

    static class ClientSession {
        final Socket socket;
        final BufferedReader in;
        final PrintWriter out;
        final InputStream rawIn;
        final OutputStream rawOut;
        volatile String status = "Online";

        ClientSession(Socket socket) throws IOException {
            this.socket = socket;
            this.in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            this.out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()), true);
            this.rawIn = socket.getInputStream();
            this.rawOut = socket.getOutputStream();
        }

        void sendLine(String line) {
            out.println(line);
        }

        void sendBytes(byte[] buf, int off, int len) throws IOException {
            rawOut.write(buf, off, len);
            rawOut.flush();
        }

        void close() {
            try { socket.close(); } catch (IOException ignored) {}
        }
    }

    static class ClientHandler implements Runnable {
        private final Socket socket;
        private ClientSession session;
        private String username;

        ClientHandler(Socket socket) { this.socket = socket; }

        @Override
        public void run() {
            try {
                session = new ClientSession(socket);
                if (!handleAuth()) {
                    session.sendLine("AUTH_FAIL Invalid credentials");
                    session.close();
                    return;
                }
                session.sendLine("AUTH_OK");
                System.out.println("User authenticated: " + username + " from " + socket.getRemoteSocketAddress());

                // Default status
                ONLINE.get(username).status = "Online";
                broadcastPresence();

                // Command loop
                String line;
                while ((line = session.in.readLine()) != null) {
                    if (line.trim().isEmpty()) continue;
                    handleCommand(line);
                }
            } catch (IOException e) {
                // Connection closed or IO error
            } finally {
                if (username != null) {
                    ONLINE.remove(username);
                    broadcastPresence();
                    System.out.println("User disconnected: " + username);
                }
                if (session != null) session.close();
            }
        }

        private boolean handleAuth() throws IOException {
            // Expect AUTH <username> <password>
            String line = session.in.readLine();
            if (line == null) return false;
            String[] parts = line.trim().split("\\s+");
            if (parts.length != 3 || !parts[0].equalsIgnoreCase("AUTH")) return false;

            String user = parts[1];
            String pass = parts[2];
            String expected = USER_DB.get(user);

            if (expected == null || !expected.equals(pass)) return false;
            // Prevent duplicate login
            if (ONLINE.containsKey(user)) {
                session.sendLine("AUTH_FAIL User already logged in");
                return false;
            }
            username = user;
            ONLINE.put(username, session);
            return true;
        }

        private void handleCommand(String line) throws IOException {
            String[] parts = line.split("\\s+");
            String cmd = parts[0].toUpperCase(Locale.ROOT);

            switch (cmd) {
                case "STATUS":
                    handleStatus(parts);
                    break;
                case "MSG":
                    handleMsg(parts, line);
                    break;
                case "FILE":
                    handleFile(parts);
                    break;
                default:
                    session.sendLine("ERROR Unknown command");
            }
        }

        private void handleStatus(String[] parts) {
            if (parts.length != 2) {
                session.sendLine("ERROR STATUS requires one argument");
                return;
            }
            String newStatus = parts[1];
            // Only updates status if user is in ONLINE map
            if (ONLINE.containsKey(username)) {
                ONLINE.get(username).status = newStatus;
                session.sendLine("STATUS OK");
                broadcastPresence();
                System.out.println("Status update: " + username + " ->" + newStatus);
            } else {
                session.sendLine("ERROR Not Logged In.");
            }
        }

        private void handleMsg(String[] parts, String rawLine) {
            if (parts.length < 3) {
                session.sendLine("ERROR MSG <recipient> <message>");
                return;
            }
            String recipient = parts[1];

            ClientSession dest = ONLINE.get(recipient);
            if (dest == null) {
                session.sendLine("ERROR Recipient not online");
                return;
            }

            // Extract message tail: after "MSG recipient "
            int idx = rawLine.indexOf(recipient);
            String message = rawLine.substring(idx + recipient.length()).trim();

            dest.sendLine("MSG_FROM " + username + " " + message);
            session.sendLine("MSG_SENT " + recipient);
            System.out.println("Message " + username + " -> " + recipient + ": " + message);
        }

        private void handleFile(String[] parts) throws IOException {
            if (parts.length != 4) {
                session.sendLine("ERROR FILE <recipient> <filename> <size>");
                return;
            }
            String recipient = parts[1];
            String filename = parts[2];
            long size;
            try {
                size = Long.parseLong(parts[3]);
            } catch (NumberFormatException e) {
                session.sendLine("ERROR Invalid file size");
                return;
            }
            ClientSession dest = ONLINE.get(recipient);
            if (dest == null) {
                session.sendLine("ERROR Recipient not online");
                skipBytes(session.rawIn, size); // Drain to keep stream sane
                return;
            }

            // Notify recipient and sender before relaying bytes
            dest.sendLine("FILE_FROM " + username + " " + filename + " " + size);
            session.sendLine("FILE_OK Sending");

            boolean success = false;
            // Notifies original sender if a transfer failed
            try {
                relayBytes(session.rawIn, dest.rawOut, size);
                success = true;
                session.sendLine("FILE_SENT " + recipient + " " + filename);
            } catch (IOException e) {
                // Notify original sender that the relay failed
                session.sendLine("ERROR FILE_TRANSFER_FAILED to " + recipient);
                // Log the error on the server side
                System.err.println("File relay failed: " + e.getMessage());
            }

            if (success) {
                System.out.println("File " + filename + " (" + size + " bytes) successfully relayed " + username + " -> " + recipient);
            }
        }

        private void relayBytes(InputStream in, OutputStream out, long size) throws IOException {
            byte[] buf = new byte[8192];
            long remaining = size;
            while (remaining > 0) {
                int read = in.read(buf, 0, (int)Math.min(buf.length, remaining));
                if (read == -1) throw new EOFException("Unexpected EOF during file relay");
                out.write(buf, 0, read);
                remaining -= read;
            }
            out.flush();
        }

        private void skipBytes(InputStream in, long size) throws IOException {
            byte[] buf = new byte[8192];
            long remaining = size;
            while (remaining > 0) {
                int read = in.read(buf, 0, (int)Math.min(buf.length, remaining));
                if (read == -1) break;
                remaining -= read;
            }
        }

        private void broadcastPresence() {
            StringBuilder sb = new StringBuilder("PRESENCE ");
            boolean first = true;
            for (Map.Entry<String, ClientSession> e : ONLINE.entrySet()) {
                if (!first) sb.append(",");
                sb.append(e.getKey()).append(":").append(e.getValue().status);
                first = false;
            }
            String msg = sb.toString();
            ONLINE.values().forEach(s -> s.sendLine(msg));
            System.out.println("Presence: " + msg);
        }
    }
}