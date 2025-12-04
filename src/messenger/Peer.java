package messenger;

import java.net.InetAddress;

public record Peer(String username, InetAddress address, int port) {}
