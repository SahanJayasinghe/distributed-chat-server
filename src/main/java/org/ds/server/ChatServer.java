package org.ds.server;

import java.io.IOException;
import java.net.ServerSocket;

public class ChatServer {
    int port;
    private ServerSocket serverSocket;

    public ChatServer(int port) {
        this.port = port;
    }

    public void start() {
        try {
            serverSocket = new ServerSocket(this.port);
            System.out.println("Server is listening on port " + port);
            ServerState.init("MainHall-s1");
            while (!serverSocket.isClosed())
                new ClientHandler(serverSocket.accept()).start();
        }
        catch (IOException ex) {
            System.out.println("Error in the server: " + ex.getMessage());
            ex.printStackTrace();
        }
    }

    public void stop() {
        try {
            serverSocket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("Syntax: java Server <port-number>");
            System.exit(0);
        }

        int port = Integer.parseInt(args[0]);

        ChatServer server = new ChatServer(port);
        server.start();
    }
}
