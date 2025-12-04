package server;

import protocol.Packet;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

/**
 * This class represents a generalized server component, i.e. one that maps an incoming request packet to a response packet.
 * For each request packet it receives, it sends exactly one response packet.
 * Requests are handled by different functions depending on their {@link protocol.Packet#method()} field.
 * You specify these handlers by using the {@link HandlesMethod} annotation:
 * <pre>{@code
 *     @HandlesMethod(Method.LOGIN)
 *     public Packet onRequestLogin(Packet request) { /* ... *\/ }
 * }</pre>
 * These handlers are discovered automatically at runtime, and can be executed
 * on a packet using {@link PacketHandler#runRequestHandler(Packet)}.
 * Note that each handler must take a packet and return a packet.
 * @see PacketHandler#runRequestHandler(Packet)
 */
public abstract class PacketHandler {
    private final Map<protocol.Method, Method> requestHandlers;

    /**
     * Instantiate the server.
     * Performs discovery of handlers.
     * @throws IllegalArgumentException if we discover a handler with an incorrect type signature.
     * @see PacketHandler#enumerateRequestHandlers(Class)
     */
    public PacketHandler() {
        this.requestHandlers = PacketHandler.enumerateRequestHandlers(this.getClass());
    }

    /**
     * Search for all request handlers in a class.
     * Discovers all methods with an {@code @}{@link HandlesMethod} annotation, including in superclasses.
     * @param kind The class to search.
     * @return A mapping from protocol methods to their handler methods.
     * @throws IllegalArgumentException if any of the handlers are not of type {@code Packet -> Packet}.
     * @see HandlesMethod
     * @see protocol.Packet
     */
    public static Map<protocol.Method, Method> enumerateRequestHandlers(Class<? extends PacketHandler> kind) {
        var requestHandlers = new HashMap<protocol.Method, Method>();
        Class<?> type = kind;
        while (type != Object.class) {
            for (var method: type.getDeclaredMethods()) {
                HandlesMethod annotation = method.getAnnotation(HandlesMethod.class);
                if (annotation == null || requestHandlers.containsKey(annotation.value()))
                    continue;
                // XXX: what happens if one of these is private? should we check for that?
                if (method.getParameterTypes().length != 1
                        || method.getParameterTypes()[0] != Packet.class
                        || method.getReturnType() != Packet.class)
                    throw new IllegalArgumentException("Method %s with annotation @HandlesMethod(%s) must have type Packet -> Packet."
                            .formatted(method, annotation.value())
                    );
                requestHandlers.put(annotation.value(), method);
            }
            type = type.getSuperclass();
        }
        return requestHandlers;
    }

    /**
     * Execute the request handler corresponding to a particular packet.
     * @param request A request packet with a method we have a handler for.
     * @return The response packet as produced by the handler.
     * @see PacketHandler#enumerateRequestHandlers(Class)
     * @throws IllegalArgumentException if there is no handler for the packet.
     */
    public Packet runRequestHandler(Packet request) {
        var method = request.method();
        var handler = requestHandlers.get(method);
        if (handler == null)
            throw new IllegalArgumentException("Server lacks a handler for method '%s'".formatted(method));
        Packet response;
        try {
            response = (Packet) handler.invoke(this, request);
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException(e);
        }
        return response;
    }
}