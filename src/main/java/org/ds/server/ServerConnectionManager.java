package org.ds.server;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class ServerConnectionManager extends Thread{
    private static String serverId = null;
    private static String leader = null;
    /**
    serverConfig = {
        "s1" : {"address": "localhost", "clientsPort": 4444, "coordPort": 5555},
        "s2" : {"address": "123.68.129.4", "clientsPort": 4445, "coordPort": 5556}
     }
     **/
    private static HashMap<String, HashMap<String, String>> serverConfig;
    private static ServerSocket serverSocket;
    private static JSONParser jsonParser;

    // need to move
    private static final int T2 = 500;
    private static final int T3 = 500;

    public static void init(String _serverId, HashMap<String, HashMap<String, String>> _serverConfig) {
        try {
            serverId = _serverId;
            serverConfig = _serverConfig;
            int serverPort = Integer.parseInt(serverConfig.get(serverId).get("coordPort"));
            serverSocket = new ServerSocket(serverPort);
            jsonParser = new JSONParser();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void run(){
        try {
            while (!serverSocket.isClosed()) {
                new ServerHandler(serverSocket.accept()).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void electLeader() {
//        leader = "s1";
        JSONObject iamup = new JSONObject();
        iamup.put("type", "IamUp");
        broadcast(iamup);
    }

    public static JSONObject contactLeader(JSONObject msg) {
        try {
            HashMap<String, String> leaderConfig = serverConfig.get(leader);
            String host = leaderConfig.get("address");
            int port = Integer.parseInt(leaderConfig.get("coordPort"));
            Socket socket = new Socket(host, port);
            DataOutputStream out = new DataOutputStream(socket.getOutputStream());
            out.write((msg.toJSONString() + "\n").getBytes(StandardCharsets.UTF_8));
            out.flush();
            BufferedReader in = new BufferedReader(
                    new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8)
            );
            JSONObject res = (JSONObject) jsonParser.parse(in.readLine());
            out.close();
            in.close();
            socket.close();
            return res;
        } catch (IOException | ParseException e) {
            e.printStackTrace();
            return null;
        }
    }

    public static String getServerId() {
        return serverId;
    }

    public static boolean isLeader() {
        return serverId.equals(leader);
    }

    public static Set<String> getServerIds() {
        return serverConfig.keySet();
    }

    public static void broadcast(JSONObject msg) {
        for (Map.Entry<String, HashMap<String, String>> entry : serverConfig.entrySet()) {
            String currentServerId = entry.getKey();
            if (!currentServerId.equals(serverId)) {
                try {
                    Socket s = new Socket(
                            entry.getValue().get("address"),
                            Integer.parseInt(entry.getValue().get("coordPort"))
                    );
                    DataOutputStream out = new DataOutputStream(s.getOutputStream());
                    out.write((msg.toJSONString() + "\n").getBytes(StandardCharsets.UTF_8));
                    out.flush();
                }
                catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public static void broadcast(JSONObject msg, String exceptServerId) {
        for (Map.Entry<String, HashMap<String, String>> entry : serverConfig.entrySet()) {
            String currentServerId = entry.getKey();
            if (!currentServerId.equals(serverId) && !currentServerId.equals(exceptServerId)) {
                try {
                    System.out.println(currentServerId);
                    Socket s = new Socket(
                            entry.getValue().get("address"),
                            Integer.parseInt(entry.getValue().get("coordPort"))
                    );
                    DataOutputStream out = new DataOutputStream(s.getOutputStream());
                    out.write((msg.toJSONString() + "\n").getBytes(StandardCharsets.UTF_8));
                    out.flush();
                }
                catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}
