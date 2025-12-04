package messenger; //package name for organizing classes in the messenger project

import org.junit.jupiter.api.*; //import junit 5 annotations and testing utilities
import protocol.Error; //import custom error codes defined in protocol
import protocol.Method; //import enum of protocol methods like REGISTER, LOGIN, WHOIS
import protocol.Packet; //import packet class for sending/receiving structured requests

import java.io.IOException; //import for handling io exceptions
import java.net.InetAddress; //import for working with ip addresses
import java.util.Map; //import for creating maps of headers

import static org.junit.jupiter.api.Assertions.*; //import static assertion methods for tests

//to enforce test execution order based on @Order annotations
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ServerTest {
    //constant port number where server listens
    static final int SERVER_PORT = 6789;
    //constant port number for user client
    static final int USER_PORT = 1234;
    //server object shared across tests
    static Server server;

    //method that runs once before all tests
    @BeforeAll
    static void setUp() throws IOException {
        //create server object bound to server port
        server = new Server(SERVER_PORT);
    }

    //method that runs once after all tests (cleanup if needed)
    @AfterAll
    static void tearDown() {}

    //test method for registering a new user
    @Test
    @Order(1) //run first
    void testRegister() {
        //create a REGISTER packet with username, password, and listen port
        var request = new Packet(Method.REGISTER,
                Map.of(
                        "username", "testuser",
                        "password", "hunter2",
                        "listenPort", String.valueOf(USER_PORT)
                ), "", InetAddress.getLoopbackAddress()
        );
        //send request to server handler
        var response = server.onRequestREGISTER(request);
        //assert that response is a SUCCESS packet
        assertEquals(new Packet(Method.SUCCESS), response);
    }

    //test method for successful login
    @Test
    @Order(2) //run second
    void testLoginSuccess() {
        //create a LOGIN packet with correct username and password
        var request = new Packet(Method.LOGIN,
                Map.of(
                        "username", "testuser",
                        "password", "hunter2",
                        "listenPort", String.valueOf(USER_PORT)
                ), "", InetAddress.getLoopbackAddress()
        );
        //send request to server handler
        var response = server.onRequestLOGIN(request);
        //assert that response is a SUCCESS packet
        assertEquals(new Packet(Method.SUCCESS), response);
    }

    //test method for login with wrong username
    @Test
    @Order(3) //run third
    void testLoginWrongUsername() {
        //create a LOGIN packet with non-existent username
        var request = new Packet(Method.LOGIN,
                Map.of(
                        "username", "nonexistantuser",
                        "password", "hunter2",
                        "listenPort", String.valueOf(USER_PORT)
                ), "", InetAddress.getLoopbackAddress()
        );
        //send request to server handler
        var response = server.onRequestLOGIN(request);
        //assert that response is WRONG_CREDENTIALS error
        assertEquals(new Packet(Error.WRONG_CREDENTIALS), response);
    }

    //test method for login with wrong password
    @Test
    @Order(3) //same order as previous, but still runs after
    void testLoginWrongPassword() {
        //create a LOGIN packet with correct username but wrong password
        var request = new Packet(Method.LOGIN,
                Map.of(
                        "username", "testuser",
                        "password", "wrongpassword",
                        "listenPort", "1234"
                ), "", InetAddress.getLoopbackAddress()
        );
        //send request to server handler
        var response = server.onRequestLOGIN(request);
        //assert that response is WRONG_CREDENTIALS error
        assertEquals(new Packet(Error.WRONG_CREDENTIALS), response);
    }

    //test method for successful WHOIS lookup
    @Test
    @Order(4) //run fourth
    void testWhoisSuccess() {
        //create a WHOIS packet with target username
        var request = new Packet(Method.WHOIS, Map.of("username", "testuser"));
        //send request to server handler
        var response = server.onRequestWHOIS(request);
        //create expected SUCCESS packet with user info headers
        var target = new Packet(Method.SUCCESS,
                Map.of(
                        "address", InetAddress.getLoopbackAddress().getHostAddress(),
                        "port", String.valueOf(USER_PORT),
                        "status", "ONLINE"
                ), "", InetAddress.getLoopbackAddress()
        );
        //assert that response matches expected packet
        assertEquals(target, response);
    }
}