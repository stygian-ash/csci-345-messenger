package messenger; //package name for organizing classes in the messenger project

import java.net.InetAddress; //import inetaddress class to represent ip addresses

//peer record to store information about a user in the network
//records in java automatically create constructor, getters, equals, hashcode, and toString
public record Peer(
        String username, //the username of the client (unique identifier for user)
        InetAddress address, //the ip address of the client (where they are located on the network)
        int port //the port number the client is listening on (used for direct communication)
) {}