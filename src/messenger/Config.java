package messenger; //package name for organizing classes in the messenger project

//config class to store constant configuration values for server connection
public class Config {
    //constant string for server ip address
    //127.0.0.1 is the loopback address, meaning "localhost" (the same machine)
    public static final String SERVER_IP = "127.0.0.1";

    //constant integer for server port number
    //6789 is the port where the server listens for incoming client connections
    public static final int SERVER_PORT = 6789;
}