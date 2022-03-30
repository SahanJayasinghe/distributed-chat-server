package org.ds.server;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.SocketAddress;
import java.util.HashMap;
import java.util.Scanner;


public class ChatServer {
    private String serverId = null;
    private int clientPort;
    private ServerSocket clientConnectionSocket;
    private HashMap<String, HashMap<String, String>> serverConfig;

    public ChatServer() {
        serverConfig = new HashMap<>();
    }

    public void start(String[] args) {
        try {
            serverId = args[0].strip();
            readConfigFile(args[1].strip());
            clientConnectionSocket = new ServerSocket();
            SocketAddress clientEndPoint = new InetSocketAddress("0.0.0.0", clientPort);
            clientConnectionSocket.bind(clientEndPoint);
            System.out.println("Server is listening client connections on port " + clientPort);
            ServerConnectionManager.init(serverId, serverConfig);
            ServerState.init();
            ServerConnectionManager.updateOnlineServers();
            HeartBeatScheduler heartBeatScheduler = new HeartBeatScheduler(serverConfig, serverId);
            new ServerConnectionManager().start();
            ServerConnectionManager.electLeader();

            heartBeatScheduler.start();
            while (!clientConnectionSocket.isClosed()){
//                if(ServerConnectionManager.getIsElectionRunning()){
//                    CustomLock.customWait();
//                }
//                while(ServerConnectionManager.getIsElectionRunning() || !ServerConnectionManager.getLeaderStatusUpdated()){
////                    System.out.println(">>>>>>>>>>>>>>>>>>>>>>>>>loopserver");
////                    System.out.println(ServerConnectionManager.getIsElectionRunning());
//                }
//                System.out.println(">>>>>>>>>>>>>>>>>>>>>>>>>endloopserver");

                if (!ServerConnectionManager.getIsElectionRunning() && ServerConnectionManager.getStatusUpdated())
                    new ClientHandler(clientConnectionSocket.accept()).start();
            }

        } catch (IOException ex) {
            System.out.println("Error in the server: " + ex.getMessage());
            ex.printStackTrace();
        }
//        catch (InterruptedException e) {
//            e.printStackTrace();
//        }
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
                // System.out.println(data[0]);
                currentConfig.put("address", data[1]);
                currentConfig.put("clientsPort", data[2]);
                currentConfig.put("coordPort", data[3]);
                serverConfig.put(data[0], currentConfig);
                if (serverId.equals(data[0])) {
                    clientPort = Integer.parseInt(data[2]);
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
            clientConnectionSocket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        if (args.length < 2) {
            System.out.println("Syntax: java Server <server-id> <\"config-path\">");
            System.exit(0);
        }
        ChatServer server = new ChatServer();
        server.start(args);
    }
}
