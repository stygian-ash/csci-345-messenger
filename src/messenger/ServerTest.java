package messenger;

import org.junit.jupiter.api.*;
import protocol.Error;
import protocol.Method;
import protocol.Packet;

import java.io.IOException;
import java.net.InetAddress;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ServerTest {
    static final int SERVER_PORT = 6789;
    static final int USER_PORT = 1234;
    static Server server;

    @BeforeAll
    static void setUp() throws IOException {
        server = new Server(SERVER_PORT);
    }

    @AfterAll
    static void tearDown() {}

    @Test
    @Order(1)
    void testRegister() {
        var request = new Packet(Method.REGISTER,
                Map.of(
                        "username", "testuser",
                        "password", "hunter2",
                        "listenPort", String.valueOf(USER_PORT)
                ), "", InetAddress.getLoopbackAddress()
        );
        var response = server.onRequestREGISTER(request);
        assertEquals(new Packet(Method.SUCCESS), response);
    }

    @Test
    @Order(2)
    void testLoginSuccess() {
        var request = new Packet(Method.LOGIN,
                Map.of(
                        "username", "testuser",
                        "password", "hunter2",
                        "listenPort", String.valueOf(USER_PORT)
                ), "", InetAddress.getLoopbackAddress()
        );
        var response = server.onRequestLOGIN(request);
        assertEquals(new Packet(Method.SUCCESS), response);
    }

    @Test
    @Order(3)
    void testLoginWrongUsername() {
        var request = new Packet(Method.LOGIN,
                Map.of(
                        "username", "nonexistantuser",
                        "password", "hunter2",
                        "listenPort", String.valueOf(USER_PORT)
                ), "", InetAddress.getLoopbackAddress()
        );
        var response = server.onRequestLOGIN(request);
        assertEquals(new Packet(Error.WRONG_CREDENTIALS), response);
    }

    @Test
    @Order(3)
    void testLoginWrongPassword() {
        var request = new Packet(Method.LOGIN,
                Map.of(
                        "username", "testuser",
                        "password", "wrongpassword",
                        "listenPort", "1234"
                ), "", InetAddress.getLoopbackAddress()
        );
        var response = server.onRequestLOGIN(request);
        assertEquals(new Packet(Error.WRONG_CREDENTIALS), response);
    }

    @Test
    @Order(4)
    void testWhoisSuccess() {
        var request = new Packet(Method.WHOIS, Map.of("username", "testuser"));
        var response = server.onRequestWHOIS(request);
        var target = new Packet(Method.SUCCESS,
                Map.of(
                        "address", InetAddress.getLoopbackAddress().getHostAddress(),
                        "port", String.valueOf(USER_PORT),
                        "status", "ONLINE"
                ), "", InetAddress.getLoopbackAddress()
        );
        assertEquals(target, response);
    }
}