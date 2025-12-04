package messenger; //package name for organizing classes

import org.junit.jupiter.api.*;//import junit 5 annotations and testing utilities
import protocol.Error;//import custom error codes defined in protocol
import java.io.IOException;//import for handling io exceptions
import java.net.InetAddress;//import for working with ip addresses

import static org.junit.jupiter.api.Assertions.*; //import static assertion methods for tests

//to enforce test execution order based on @Order annotations
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ClientTest {
    //static client object shared across tests
    static Client client;
    //static thread to run the server in background
    static Thread serverThread;
    //constant port number for client
    static final int CLIENT_PORT = 1234;

    //method that runs once before all tests
    @BeforeAll
    static void setUp() {
        //create a new thread to run the server
        serverThread = new Thread(() -> {
            try {
                //create server object listening on configured port
                var server = new Server(Config.SERVER_PORT);
                //start server loop to accept connections
                server.listenLoop();
            } catch (IOException e) {
                //wrap io exception into runtime exception if server fails
                throw new RuntimeException(e);
            }
        });
        //start the server thread
        serverThread.start();

        //create client object with client port, server ip, and server port
        client = new Client(CLIENT_PORT, Config.SERVER_IP, Config.SERVER_PORT);
    }

    //test method for user registration
    @Test
    @Order(1) //run first
    void register() {
        //assert multiple conditions together
        assertAll(
                //first registration should succeed
                () -> assertEquals(Error.OK, client.register("testuser", "letmein")),
                //second registration with same username should fail with USER_ALREADY_EXISTS
                () -> assertEquals(Error.USER_ALREADY_EXISTS, client.register("testuser", "letmein"))
        );
    }

    //test method for user login
    @Test
    @Order(2) //run second
    void login() {
        assertAll(
                //login with non-existent user should fail
                () -> assertEquals(Error.WRONG_CREDENTIALS, client.login("nonexistantuser", "letmein")),
                //login with wrong password should fail
                () -> assertEquals(Error.WRONG_CREDENTIALS, client.login("testuser", "password1")),
                //login with correct credentials should succeed
                () -> assertEquals(Error.OK, client.login("testuser", "letmein"))
        );
    }

    //test method for whois lookup
    @Test
    @Order(3) //run third
    void whois() {
        assertAll(
                //lookup for non-existent user should return null
                () -> assertNull(client.whois("nonexistantuser")),
                //lookup for existing user should return a Peer object with username, ip, and port
                () -> assertEquals(new Peer(
                        "testuser", //username
                        InetAddress.getLoopbackAddress(), //loopback ip (127.0.0.1)
                        CLIENT_PORT //client port number
                ), client.whois("testuser"))
        );
    }
}