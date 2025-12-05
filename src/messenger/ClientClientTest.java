package messenger;

import org.junit.jupiter.api.*;
import protocol.Error;

import java.io.IOException;
import java.net.InetAddress;

import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ClientClientTest {
    static Client client;
    static Client peer;
    static Thread serverThread;
    static final int CLIENT_PORT = 1234;
    static final int PEER_PORT = 1235;
    static final String PEER_USERNAME = "bob";

    @BeforeAll
    static void setUp() throws IOException, InterruptedException {
        serverThread = new Thread(() -> {
            try {
                var server = new Server(Config.SERVER_PORT);
                server.listenLoop();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
        serverThread.start();
        Thread.sleep(1000);

        client = new Client(CLIENT_PORT, Config.SERVER_IP, Config.SERVER_PORT);
        peer = new Client(PEER_PORT, Config.SERVER_IP, Config.SERVER_PORT);

        peer.register("bob", "password123");
        peer.setStatus(Status.READY);

        client.register("alice", "hunter2");
    }

    @Test
    @Order(1)
    void testMessage() throws IOException {
        var peerInfo = client.whois("bob");
        assertEquals(
                new Peer("bob", InetAddress.getLoopbackAddress(), PEER_PORT, Status.READY), peerInfo
        );
        assertNotNull(client.connectToPeer("bob"));
        client.sendMessage("Hi bob!");
        peer.sendMessage("Hi alice!");
        client.sendFile("src/messenger/Client.java", "/dev/null");
    }
}