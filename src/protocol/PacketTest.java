package protocol;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PacketTest {
    public static final int PORT = 1234;

    @Test
    @DisplayName("Create packet")
    void createPacket() {
        var messagePacket = new Packet(Method.MESSAGE, Map.of(), "Hello world!");

        var loginPacket = new Packet(Method.LOGIN, Map.of(
                "username", "admin",
                "password", "hunter2"
        ));

        assertAll(
                () -> assertEquals("12", messagePacket.headers().get("contentLength")),
                () -> assertEquals(2, loginPacket.headers().size()),
                () -> assertEquals("admin", loginPacket.headers().get("username")),
                () -> assertEquals("hunter2", loginPacket.headers().get("password")),
                () -> assertFalse(loginPacket.headers().containsKey("contentLength"))
        );
    }

    @Test
    void createErrorPacket() {
        var packet = new Packet(Error.WRONG_CREDENTIALS);
        var target = new Packet(Method.FAILURE,
                Map.of("error", "WRONG_CREDENTIALS")
        );
        assertEquals(target, packet);
    }

    @Test
    @DisplayName("Send packet over TCP")
    void sendPacket() throws IOException {
        var messagePacket = new Packet(Method.MESSAGE, Map.of("header1", "value1"), "Hello world!");

        var senderThread = new Thread(() -> {
            try (var socket = new Socket("127.0.0.1", PORT)) {
                Packet.sendPacket(socket, messagePacket);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });

        try (var socket = new ServerSocket(1234)) {
            // Is this a race condition?
            senderThread.start();
            var connection = socket.accept();
            var input = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            var received = input.readAllAsString();
            assertEquals("""
                    MESSAGE
                    contentLength: 12
                    header1: value1
                    
                    Hello world!""", received);
        }
    }

    @Test
    @DisplayName("Send empty-body packet over TCP")
    void sendHeaderOnlyPacket() throws IOException {
        var messagePacket = new Packet(Method.LOGIN, Map.of(
                "username", "admin",
                "password", "hunter2"
        ));

        var senderThread = new Thread(() -> {
            try (var socket = new Socket("127.0.0.1", PORT)) {
                Packet.sendPacket(socket, messagePacket);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });

        try (var socket = new ServerSocket(1234)) {
            // Is this a race condition?
            senderThread.start();
            var connection = socket.accept();
            var input = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            var received = input.readAllAsString();
            // XXX: the output order of headers is dependent on the traversal order of the header map implementation
            // should we enforce that packets are outputted in sorted order?
            // that would be pretty useless, but on the other hand this test might fail randomly
            assertEquals("""
                    LOGIN
                    password: hunter2
                    username: admin
                    
                    """, received);
        }
    }

    @Test
    @DisplayName("Send + Receive packet over TCP")
    void receivePacket() throws IOException {
        var messagePacket = new Packet(Method.MESSAGE, Map.of("header1", "value1"),
        "Hello world!", InetAddress.getLoopbackAddress());
        var senderThread = new Thread(() -> {
            try (var socket = new Socket("127.0.0.1", PORT)) {
                Packet.sendPacket(socket, messagePacket);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });

        try (var socket = new ServerSocket(PORT)) {
            senderThread.start();
            var connection = socket.accept();
            var received = Packet.readPacket(connection);
            assertEquals(messagePacket, received);
        }
    }

    @Test
    @DisplayName("Send + Receive empty-body packet over TCP")
    void receiveHeaderOnlyPacket() throws IOException {
        var messagePacket = new Packet(Method.LOGIN, Map.of(
                "username", "admin",
                "password", "hunter2"
        ), "", InetAddress.getLocalHost());
        var senderThread = new Thread(() -> {
            try (var socket = new Socket("127.0.0.1", PORT)) {
                Packet.sendPacket(socket, messagePacket);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });

        try (var socket = new ServerSocket(PORT)) {
            senderThread.start();
            var connection = socket.accept();
            var received = Packet.readPacket(connection);
            assertEquals(messagePacket, received);
        }
    }
}