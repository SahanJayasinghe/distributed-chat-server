package org.ds.server;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;

public class ServerConnectionManager extends Thread {
    private static String serverId = null;
    private static String leader = null;
    /**
     * serverConfig = {
     * "s1" : {"address": "localhost", "clientsPort": 4444, "coordPort": 5555},
     * "s2" : {"address": "123.68.129.4", "clientsPort": 4445, "coordPort": 5556}
     * }
     **/
    private static HashMap<String, HashMap<String, String>> serverConfigMap;
    private static ServerSocket serverSocket;
    private static JSONParser jsonParser;

    // fast bully
    private static boolean isViewReceived = false;
    private static boolean isCordReceived = false;
    private static boolean isNomReceived = false;
    private static ConcurrentHashMap<String, String> receivedAnswersMap = new ConcurrentHashMap<>();
    private static Set<String> receivedView;
    private static Set<String> onlineServers;
    private static boolean isElectionRunning = false;
    private static boolean statusUpdated = false;
    public static Semaphore s = new Semaphore(1);

    // time constants in ms
    private static final int T2 = 500;
    private static final int T3 = 500;
    private static int T4;

    public static void init(String _serverId, HashMap<String, HashMap<String, String>> _serverConfigMap) {
        try {
            serverId = _serverId;
            serverConfigMap = _serverConfigMap;
            T4 = 2000 / Integer.parseInt(serverId.substring(1));
            onlineServers = new HashSet<>();
            onlineServers.add(serverId);
            receivedView = new HashSet<>();
            int serverPort = Integer.parseInt(serverConfigMap.get(serverId).get("coordPort"));
            serverSocket = new ServerSocket();
            SocketAddress serverEndPoint = new InetSocketAddress("0.0.0.0", serverPort);
            serverSocket.bind(serverEndPoint);
            System.out.println("Server is listening server connections on port " + serverPort);
            jsonParser = new JSONParser();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void run() {
        try {
            while (!serverSocket.isClosed()) {
                new ServerHandler(serverSocket.accept()).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void electLeaderFailure() {
        setIsElectionRunning(true);
        int myServerNum = Integer.parseInt(getServerId().substring(1));
        boolean ansReceived = false;
        clearReceivedAnswers();

        // coordination message
        JSONObject coordMsg = new JSONObject();
        coordMsg.put("type", "coordinator");
        coordMsg.put("leader", serverId);

        for (String server : serverConfigMap.keySet()) {
            int serverNum = Integer.parseInt(server.substring(1));
            if (serverNum > myServerNum) {
                JSONObject electionMsg = new JSONObject();
                electionMsg.put("type", "election");
                electionMsg.put("sender", getServerId());
                sendToServer(electionMsg, server);
            }
        }
        Instant sTime = Instant.now();
        while (Duration.between(sTime, Instant.now()).getNano() / 1000 / 1000 < T2) {
            if (!getReceivedAnswers().isEmpty()) {
                ansReceived = true;
                // break;
            }
        }
        if (ansReceived) {
            while (!getReceivedAnswers().isEmpty()) {
                String maxAnsweredServer = getMaxReceivedAnswer();
                removeFromReceivedAnswers(maxAnsweredServer);
                boolean cordReceived = false;
                setisCordReceived(false);
                JSONObject nomResponse = new JSONObject();
                nomResponse.put("type", "nomination");
                nomResponse.put("sender", getServerId());
                sendToServer(nomResponse, maxAnsweredServer);
                Instant startTime = Instant.now();
                while (Duration.between(startTime, Instant.now()).getNano() / 1000 / 1000 < T3) {
                    if (getisCordReceived()) {
                        cordReceived = true;
                        setisCordReceived(false);
                        break;
                    }
                }
                if (cordReceived) {
                    return;
                }
            }
        }
        broadcast(coordMsg);
        setLeader(serverId);
    }

    public static void updateOnlineServers() {
        JSONObject response = new JSONObject();
        response.put("type", "areYouThere");
        response.put("sender", serverId);
//        broadcast(response);
        for (Map.Entry<String, HashMap<String, String>> entry : serverConfigMap.entrySet()) {
            String currentServerId = entry.getKey();
            if (!currentServerId.equals(serverId)) {
                try {
                    Socket s = new Socket(
                            entry.getValue().get("address"),
                            Integer.parseInt(entry.getValue().get("coordPort")));
                    DataOutputStream out = new DataOutputStream(s.getOutputStream());
                    out.write((response.toJSONString() + "\n").getBytes(StandardCharsets.UTF_8));
                    out.flush();
                    BufferedReader in = new BufferedReader(
                            new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8));
                    JSONObject res = (JSONObject) jsonParser.parse(in.readLine());
                    System.out.println("Response for areYouThere: " + res.toJSONString());
                    // res: {"server": "id"}
                    addServerToOnlineServers((String) res.get("server"));
                    out.close();
                    in.close();
                    s.close();
                } catch (IOException e) {
                    System.out.printf("AreYouThere message to %s failed\n", currentServerId);
                    // e.printStackTrace();
                } catch (ParseException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public static void electLeader() {
        setIsElectionRunning(true);
        isViewReceived = false;
        JSONObject response = new JSONObject();
        response.put("type", "IamUp");
        response.put("sender", serverId);
        broadcast(response);
        Instant startTime = Instant.now();
        boolean received = false;
        while (Duration.between(startTime, Instant.now()).getNano() / 1000 / 1000 < T2) {
            if (isViewReceived && !receivedView.isEmpty()) {
                received = true;
//                break;
            }
        }
        if (!received) {
            setLeader(serverId);

        } else {
            setisViewReceived(false);
            Set<String> ids = getOnlineServers();
            boolean isViewSame = ids.equals(receivedView);
            if (!isViewSame) {
                concatOnlineServers(receivedView);
            }
            int myServerNum = Integer.parseInt(serverId.substring(1));
            int max = 0;
            for (String id : onlineServers) {
                int serverNum = Integer.parseInt(id.substring(1));
                if (serverNum > max) {
                    max = serverNum;
                }
            }
            if (max > myServerNum) {
                setLeader("s" + max);
            } else {
                JSONObject coordResponse = new JSONObject();
                coordResponse.put("type", "coordinator");
                coordResponse.put("leader", serverId);
                broadcast(coordResponse);
                setLeader(serverId);
            }
        }
    }

    public static JSONObject contactLeader(JSONObject msg) {
        try {
            HashMap<String, String> leaderConfig = serverConfigMap.get(leader);
            String host = leaderConfig.get("address");
            int port = Integer.parseInt(leaderConfig.get("coordPort"));
            Socket socket = new Socket(host, port);
            DataOutputStream out = new DataOutputStream(socket.getOutputStream());
            out.write((msg.toJSONString() + "\n").getBytes(StandardCharsets.UTF_8));
            out.flush();
            BufferedReader in = new BufferedReader(
                    new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            JSONObject res = (JSONObject) jsonParser.parse(in.readLine());
            out.close();
            in.close();
            socket.close();
            return res;
        } catch (IOException | ParseException | NullPointerException e) {
            e.printStackTrace();
            return null;
        }
    }

    public static void sendToLeader(JSONObject msg) {
        try {
            HashMap<String, String> leaderConfig = serverConfigMap.get(leader);
            String host = leaderConfig.get("address");
            int port = Integer.parseInt(leaderConfig.get("coordPort"));
            Socket socket = new Socket(host, port);
            DataOutputStream out = new DataOutputStream(socket.getOutputStream());
            out.write((msg.toJSONString() + "\n").getBytes(StandardCharsets.UTF_8));
            out.flush();
            out.close();
            socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void sendToServer(JSONObject msg, String serverId) {
        if (onlineServers.contains(serverId)) {
            try {
                HashMap<String, String> serverConfig = serverConfigMap.get(serverId);
                String host = serverConfig.get("address");
                int port = Integer.parseInt(serverConfig.get("coordPort"));
                Socket socket = new Socket(host, port);
                DataOutputStream out = new DataOutputStream(socket.getOutputStream());
                out.write((msg.toJSONString() + "\n").getBytes(StandardCharsets.UTF_8));
                out.flush();
                out.close();
                socket.close();
            } catch (IOException e) {
                System.out.println("Could not connect to " + serverId);
                // e.printStackTrace();
            }
        }
    }

    public static String getServerId() {
        return serverId;
    }

    public static void setisViewReceived(boolean val) {
        isViewReceived = val;
    }

    public static void setisNomReceived(boolean val) {
        isNomReceived = val;
    }

    public static boolean getisNomReceived() {
        return isNomReceived;
    }

    public static void setisCordReceived(boolean val) {
        isCordReceived = val;
    }

    public static boolean getisCordReceived() {
        return isCordReceived;
    }

    public static void addToReceivedAnswers(String val) {
        receivedAnswersMap.put(val, val);
        // receivedAnswers = receivedAnswersMap.keySet();
    }

    public static void removeFromReceivedAnswers(String val) {
        receivedAnswersMap.remove(val);
        // receivedAnswers = receivedAnswersMap.keySet();

    }

    public static void clearReceivedAnswers() {
        receivedAnswersMap.clear();
        // receivedAnswers = receivedAnswersMap.keySet();
    }

    public static boolean getIsElectionRunning(){
        return isElectionRunning;
    }

    public static void setIsElectionRunning(boolean _isElectionRunning){
        isElectionRunning = _isElectionRunning;
//        CustomLock.customNotifyAll();
    }

    public static boolean getStatusUpdated() {
        return statusUpdated;
    }

    public static void setStatusUpdated(boolean b) {
        if (b) System.out.println("Status updated! Ready to serve client requests.");
        statusUpdated = b;
    }

    public static String getMaxReceivedAnswer() {
        int max = 0;
        for (String server : getReceivedAnswers()) {
            int serverNum = Integer.parseInt(server.substring(1));
            if (serverNum > max) {
                max = serverNum;
            }
        }
        return ("s" + max);
    }

    public static Set<String> getReceivedAnswers() {
        return receivedAnswersMap.keySet();
    }

    public static Set<String> getReceivedView() {
        return receivedView;
    }

    public static void setReceivedView(Set<String> view) {
        receivedView = view;
    }

    public static void concatReceivedView(Set<String> view) {
        if (view != null) {
            if (receivedView == null) {
                receivedView = new HashSet<>();
            }
            receivedView.addAll(view);
        }
    }

    public static void addServerToReceivedView(String s) {
        if (receivedView == null) {
            receivedView = new HashSet<>();
        }
        receivedView.add(s);
    }

    public static boolean isLeader() {
        return serverId.equals(leader);
    }

    public static void setLeader(String newLeader) {
        leader = newLeader;
        System.out.printf("%s is the new leader!\n", leader);
//        try {
//            Thread.sleep(10000);
//        } catch (InterruptedException e) {
//            e.printStackTrace();
//        }
        setStatusUpdated(false);
        setIsElectionRunning(false);

        JSONObject msg = new JSONObject();
        msg.put("type", "statusRequest");
        for (Map.Entry<String, HashMap<String, String>> entry : serverConfigMap.entrySet()) {
            String currentServerId = entry.getKey();
            if (!currentServerId.equals(serverId) && onlineServers.contains(currentServerId)) {
                try {
                    Socket s = new Socket(
                            entry.getValue().get("address"),
                            Integer.parseInt(entry.getValue().get("coordPort")));
                    DataOutputStream out = new DataOutputStream(s.getOutputStream());
                    out.write((msg.toJSONString() + "\n").getBytes(StandardCharsets.UTF_8));
                    out.flush();
                    BufferedReader in = new BufferedReader(
                            new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8));
                    JSONObject res = (JSONObject) jsonParser.parse(in.readLine());
                    // res = {"serverId": "s1", "clientIds": "(john, adel)", "roomIds": "(room1, room2)"}
                    System.out.println("Server Status: " + res.toJSONString());
                    String sId = (String) res.get("serverId");
                    if (isLeader()) {
                        String clientIdsList = (String) res.get("clientIds");
                        String[] cIds = clientIdsList.substring(1, clientIdsList.length() - 1).split("\\s*,\\s*");
                        HashSet<String> cIdsSet = new HashSet<>(Arrays.asList(cIds));
                        ServerState.addServerClientIds(sId, cIdsSet);
                    }

                    String roomIdsList = (String) res.get("roomIds");
                    String[] rIds = roomIdsList.substring(1, roomIdsList.length() - 1).split("\\s*,\\s*");
                    HashSet<String> rIdsSet = new HashSet<>(Arrays.asList(rIds));
                    ServerState.addServerRoomIds(sId, rIdsSet);

                    out.close();
                    in.close();
                    s.close();
                } catch (IOException e) {
                    System.out.printf("statusRequest message to %s failed\n", currentServerId);
                    // e.printStackTrace();
                } catch (ParseException e) {
                    e.printStackTrace();
                }
            }
        }
        setStatusUpdated(true);
        JSONObject ack = new JSONObject();
        ack.put("type", "statusUpdated");
        broadcast(ack);
    }

    public static Set<String> getServerIds() {
        return serverConfigMap.keySet();
    }

    public static Set<String> getOnlineServers() {
        return onlineServers;
    }

    public static void setOnlineServers(Set<String> val) {
        onlineServers = val;
    }

    public static HashMap<String, String> getServerConfig(String sId) {
        return serverConfigMap.get(sId);
    }

    public static int getT4() {
        return T4;
    }

    public static void broadcast(JSONObject msg) {
        for (Map.Entry<String, HashMap<String, String>> entry : serverConfigMap.entrySet()) {
            String currentServerId = entry.getKey();
            if (!currentServerId.equals(serverId)) {
                if (onlineServers.contains(currentServerId)) {
                    try {
                        Socket s = new Socket(
                                entry.getValue().get("address"),
                                Integer.parseInt(entry.getValue().get("coordPort")));
                        DataOutputStream out = new DataOutputStream(s.getOutputStream());
                        out.write((msg.toJSONString() + "\n").getBytes(StandardCharsets.UTF_8));
                        out.flush();
                    } catch (IOException e) {
                        // System.out.printf("Broadcast message to %s failed\n", currentServerId);
                        // e.printStackTrace();
                    }
                }
            }
        }
    }

    public static void broadcast(JSONObject msg, String exceptServerId) {
        for (Map.Entry<String, HashMap<String, String>> entry : serverConfigMap.entrySet()) {
            String currentServerId = entry.getKey();
            if (!currentServerId.equals(serverId) && !currentServerId.equals(exceptServerId)) {
                if (onlineServers.contains(currentServerId)) {
                    try {
                        Socket s = new Socket(
                                entry.getValue().get("address"),
                                Integer.parseInt(entry.getValue().get("coordPort")));
                        DataOutputStream out = new DataOutputStream(s.getOutputStream());
                        out.write((msg.toJSONString() + "\n").getBytes(StandardCharsets.UTF_8));
                        out.flush();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    public static String getLeader() {
        return leader;
    }

    public static void removeServerFromOnlineServers(String server_id) {
        System.out.println("removed from online servers - server id : " + server_id);
        onlineServers.remove(server_id);
    }

    public static void addServerToOnlineServers(String server_id) {
//        System.out.println("onlineServers : " + onlineServers);
        onlineServers.add(server_id);
//        System.out.println("added to online servers - server id : " + server_id);
//        System.out.println("onlineServers : " + onlineServers);
    }

    public static void concatOnlineServers(Set<String> servers) {
        if (servers != null)
            onlineServers.addAll(servers);
    }
}
