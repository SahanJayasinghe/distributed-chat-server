package org.ds.server;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.ServerSocket;
//import java.nio.file.Path;
import java.util.HashMap;
import java.util.Scanner;

public class ChatServer {
    private String serverId = null;
    private int port;
    private ServerSocket serverSocket;
    private HashMap<String, HashMap<String, String>> serverConfig;

    public ChatServer() {
        serverConfig = new HashMap<>();
    }

    public void start(String[] args) {
        try {
            serverId = args[0].strip();
            readConfigFile(args[1].strip());
            serverSocket = new ServerSocket(this.port);
            System.out.println("Server is listening on port " + port);
            ServerState.init("MainHall-" + serverId);
            while (!serverSocket.isClosed())
                new ClientHandler(serverSocket.accept()).start();
        }
        catch (IOException ex) {
            System.out.println("Error in the server: " + ex.getMessage());
            ex.printStackTrace();
        }
    }

    private void readConfigFile(String configPath) {
        File configFile = new File(configPath);
        boolean validServerId = false;
        try {
            Scanner fileReader = new Scanner(configFile);
            while (fileReader.hasNextLine()) {
                String line = fileReader.nextLine();
                String[] data = line.strip().split("\\t");
                HashMap<String, String> currentConfig = new HashMap<>();
                currentConfig.put("address", data[1]);
                currentConfig.put("clientsPort", data[2]);
                currentConfig.put("coordPort", data[3]);
                serverConfig.put(data[0], currentConfig);
                if (serverId.equals(data[0])) {
                    port = Integer.parseInt(data[2]);
                    validServerId = true;
                }
            }
            if (!validServerId) {
                System.out.printf("\nServer ID '%s' not found in config file %s", serverId, configPath);
                System.exit(0);
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            System.exit(0);
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
        if (args.length < 2) {
            System.out.println("Syntax: java Server <server-id> <\"config-path\">");
            System.exit(0);
        }

//        int port = Integer.parseInt(args[0]);
//        System.out.println(args.length);

        ChatServer server = new ChatServer();
        server.start(args);
    }
}
