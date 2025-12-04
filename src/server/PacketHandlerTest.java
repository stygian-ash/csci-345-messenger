package server;

import org.junit.jupiter.api.Test;
import protocol.Packet;
import protocol.Method;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PacketHandlerTest {
    static class Server extends PacketHandler {
        @HandlesMethod(Method.REGISTER)
        public Packet onRegisterRequest(Packet request) {
            return new Packet(Method.SUCCESS, "%s:%s".formatted(
                    request.headers().get("username"),
                    request.headers().get("password")
            ));
        }

        @HandlesMethod(Method.STATUS)
        public Packet onStatusRequest(Packet request) {
            return new Packet(Method.SUCCESS, "status="+ request.content());
        }
    }

    @Test
    void enumerateRequestHandlers() {
        var handlers = PacketHandler.enumerateRequestHandlers(Server.class);
        assertAll(
                () -> assertEquals(2, handlers.size()),
                () -> assertEquals(Server.class.getMethod("onRegisterRequest", Packet.class),
                        handlers.get(Method.REGISTER)),
                () -> assertEquals(Server.class.getMethod("onStatusRequest", Packet.class),
                        handlers.get(Method.STATUS))
        );
    }

    @Test
    void runRequestHandler() {
        var server = new Server();
        var registerRequest = new Packet(Method.REGISTER, Map.of(
                "username", "admin",
                "password", "hunter2"
        ));
        var registerResponse = server.runRequestHandler(registerRequest);
        var statusRequest = new Packet(Method.STATUS, "online");
        var statusResponse = server.runRequestHandler(statusRequest);
        var messageRequest = new Packet(Method.HELLO, Map.of("from", "alice"));

        assertAll(
                () -> assertEquals("admin:hunter2", registerResponse.content()),
                () -> assertEquals("status=online", statusResponse.content()),
                () -> assertThrows(IllegalArgumentException.class,
                () -> server.runRequestHandler(messageRequest))
        );
    }
}