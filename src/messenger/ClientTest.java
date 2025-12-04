package messenger;

import org.junit.jupiter.api.*;
import protocol.Error;

import java.io.IOException;
import java.net.InetAddress;

import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ClientTest {
    static Client client;
    static Thread serverThread;
    static final int CLIENT_PORT = 1234;

    @BeforeAll
    static void setUp() {
        serverThread = new Thread(() -> {
            try {
                var server = new Server(Config.SERVER_PORT);
                server.listenLoop();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
        serverThread.start();

        client = new Client(CLIENT_PORT, Config.SERVER_IP, Config.SERVER_PORT);
    }

    @Test
    @Order(1)
    void register() {
        assertAll(
                () -> assertEquals(Error.OK, client.register("testuser", "letmein")),
                () -> assertEquals(Error.USER_ALREADY_EXISTS, client.register("testuser", "letmein"))
        );
    }

    @Test
    @Order(2)
    void login() {
        assertAll(
                () -> assertEquals(Error.WRONG_CREDENTIALS, client.login("nonexistantuser", "letmein")),
                () -> assertEquals(Error.WRONG_CREDENTIALS, client.login("testuser", "password1")),
                () -> assertEquals(Error.OK, client.login("testuser", "letmein"))
        );
    }

    @Test
    @Order(3)
    void whois() {
        assertAll(
                () -> assertNull(client.whois("nonexistantuser")),
                () -> assertEquals(new Peer(
                        "testuser",
                        InetAddress.getLoopbackAddress(),
                        CLIENT_PORT
                ), client.whois("testuser"))
        );
    }
}