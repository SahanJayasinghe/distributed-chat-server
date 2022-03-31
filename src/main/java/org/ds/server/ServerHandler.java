package org.ds.server;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.*;


public class ServerHandler extends Thread {
    private Socket clientSocket;
    private DataOutputStream out;
    private BufferedReader in;
    private JSONParser jsonParser;
    private boolean alive;
    public static String DELETE_SERVER_ROOMS = "DELETE_SERVER_ROOMS";
    private static final Object newIdentityLock = new Object();
    private static final Object newRoomLock = new Object();

    public ServerHandler(Socket s) {
        clientSocket = s;
        jsonParser = new JSONParser();
        alive = true;
    }

    @Override
    public void run() {
        try {
            out = new DataOutputStream(clientSocket.getOutputStream());
            in = new BufferedReader(
                    new InputStreamReader(clientSocket.getInputStream(), StandardCharsets.UTF_8));
//            System.out.printf("new connection from server running on %s\n",
//                    clientSocket.getRemoteSocketAddress().toString());

            String inputLine;
            JSONObject req;

            while (alive) {
                inputLine = in.readLine();
//                System.out.println("Received: " + inputLine);
                if (inputLine != null) {
                    req = (JSONObject) jsonParser.parse(inputLine);
                    handleRequest(req);
                } else {
                    break;
                }
            }
            in.close();
            out.close();
            clientSocket.close();
        } catch (IOException | ParseException e) {
            e.printStackTrace();
        }
    }

    public void handleRequest(JSONObject request) {
        String msgType = (String) request.get("type");
        if (!msgType.equals(HeartBeatScheduler.HEARTBEAT)) System.out.println(">>> Server Request: " + request.toJSONString());
        if (ServerConnectionManager.isLeader()) {
            if (msgType.equals("newclient")) {
                String id = (String) request.get("id");
                synchronized (newIdentityLock) {
                    boolean approved = ServerState.isClientIdUnique(id);
                    System.out.printf("clientId: %s approved by leader\n", id);
                    if (approved) {
                        String sId = (String) request.get("server");
                        ServerState.addClientId(id, sId);
                    }
                    JSONObject response = new JSONObject();
                    response.put("type", msgType);
                    response.put("approved", String.valueOf(approved));
                    sendMessage(response);
                }
            }
            else if (msgType.equals("newroom")) {
                String roomId = (String) request.get("id");
                synchronized (newRoomLock) {
                    boolean approved = ServerState.isRoomIdUnique(roomId);
                    String sId = (String) request.get("server");
                    JSONObject res = new JSONObject();
                    res.put("type", "roomidapproval");
                    res.put("roomid", roomId);
                    res.put("server", sId);
                    res.put("approved", String.valueOf(approved));
                    if (approved) {
                        ServerState.addRoomId(roomId, sId);
                        sendMessage(res);
                        ServerConnectionManager.broadcast(res, sId);
                    } else {
                        sendMessage(res);
                    }
                }
            }
            else if (msgType.equals("serverswitch")) {
                ServerState.switchServer(
                        (String) request.get("clientid"),
                        (String) request.get("formerserver"),
                        (String) request.get("newserver"));
            }
            else if (msgType.equals("deleteclient")) {
                ServerState.removeClientId(
                        (String) request.get("clientid"),
                        (String) request.get("serverid"));
            }
        }
        if (msgType.equals("roomidapproval")) {
            boolean approved = Boolean.parseBoolean((String) request.get("approved"));
            if (approved) {
                String sId = (String) request.get("server");
                String roomId = (String) request.get("roomid");
                ServerState.addRoomId(roomId, sId);
            }
        }
        if (msgType.equals("roomswitch")) {
            String formerRoomId = (String) request.get("formerroom");
            String newRoomId = (String) request.get("newroom");
            JSONObject msg = new JSONObject();
            msg.put("type", "roomchange");
            msg.put("identity", request.get("clientid"));
            msg.put("former", formerRoomId);
            msg.put("roomid", newRoomId);
            ChatRoom former = ServerState.getRoom(formerRoomId);
            former.broadcast(msg);
        }
        if (msgType.equals("deleteroom")) {
            ServerState.removeRoomId(
                    (String) request.get("roomid"),
                    (String) request.get("serverid"));
        }
        if(msgType.equals(DELETE_SERVER_ROOMS)){
            ServerState.removeServerRoomIds((String) request.get("serverId"));
        }
        if (msgType.equals("IamUp")) {
            String sender = (String) request.get("sender");
            ServerConnectionManager.addServerToReceivedView(sender);
            ServerConnectionManager.addServerToOnlineServers(sender);
            ServerState.updateStateOnServerUp(sender);
            String view = ServerConnectionManager.getOnlineServers().toString();
            JSONObject response = new JSONObject();
            response.put("type", "view");
            response.put("serverList", view);
            ServerConnectionManager.sendToServer(response, sender);
        }
        if (msgType.equals("view")) {
            ServerConnectionManager.setisViewReceived(true);
            String sList = (String) request.get("serverList");
            String[] ids = sList.substring(1, sList.length() - 1).split("\\s*,\\s*");
            Set<String> view = new HashSet<>(Arrays.asList(ids));
//            ServerConnectionManager.setReceivedView(view);
            ServerConnectionManager.concatReceivedView(view);
            ServerConnectionManager.concatOnlineServers(view);
        }
        if (msgType.equals("coordinator")) {
            String newLeader = (String) request.get("leader");
            int serverNum = Integer.parseInt(newLeader.substring(1));
            String serverId = ServerConnectionManager.getServerId();
            int myServer = Integer.parseInt(serverId.substring(1));
            if (myServer < serverNum) {
                ServerConnectionManager.setLeader(newLeader);
            }
        }
        if (msgType.equals("election")) {
            String sender = (String) request.get("sender");
            int senderNum = Integer.parseInt(sender.substring(1));
            String serverId = ServerConnectionManager.getServerId();
            int myServer = Integer.parseInt(serverId.substring(1));
            if (senderNum < myServer) {
                JSONObject response = new JSONObject();
                response.put("type", "answer");
                response.put("sender", serverId);
                ServerConnectionManager.sendToServer(response, sender);
                Instant startTime = Instant.now();
                boolean received = false;
                while (Duration.between(startTime, Instant.now()).getNano()/1000/1000 < ServerConnectionManager.getT4()) {
                    if (ServerConnectionManager.getisNomReceived() || ServerConnectionManager.getisCordReceived()) {
                        received = true;
                        ServerConnectionManager.setisNomReceived(false);
                        break;
                    }
                }
                if (!received) {
                    ServerConnectionManager.electLeaderFailure();
                }
            }
        }
        if (msgType.equals("nomination")) {
            String sender = (String) request.get("sender");
            int senderNum = Integer.parseInt(sender.substring(1));
            String serverId = ServerConnectionManager.getServerId();
            int myServer = Integer.parseInt(serverId.substring(1));
            if (senderNum < myServer) {
                JSONObject coordMsg = new JSONObject();
                coordMsg.put("type", "coordinator");
                coordMsg.put("leader", serverId);
                ServerConnectionManager.broadcast(coordMsg);
            }
        }
        if (msgType.equals("answer")) {
            String sender = (String) request.get("sender");
            ServerConnectionManager.addToReceivedAnswers(sender);
        }
        if (msgType.equals(HeartBeatScheduler.HEARTBEAT)) {
            String server_id = (String) request.get("server");
            HeartBeatScheduler.updateHeartbeatReceivedTimes(server_id);
        }
        if (msgType.equals(HeartBeatScheduler.SERVER_DOWN)) {
            String server_id = (String) request.get("server");
            System.out.println("Recieved the failure msg - failed server id : " + server_id);
//            ServerConnectionManager.removeServerFromOnlineServers(server_id);
//            ServerState.updateStateOnServerDown(server_id);
//            if (ServerConnectionManager.getLeader().equals(server_id)) {
//                ServerConnectionManager.setIsElectionRunning(true);
//            }

//            JSONObject deleteRoomsResponse = new JSONObject();
//            deleteRoomsResponse.put("type", DELETE_SERVER_ROOMS);
//            deleteRoomsResponse.put("serverId", server_id);
//            ServerConnectionManager.broadcast(deleteRoomsResponse, server_id);
//            ServerConnectionManager.removeServerFromOnlineServers(server_id);
//            if (ServerConnectionManager.isLeader() || ServerConnectionManager.getLeader().equals(server_id)) {
//                JSONObject response = new JSONObject();
//                response.put("type", HeartBeatScheduler.SERVER_DOWN);
//                response.put("server", server_id);
//                ServerConnectionManager.broadcast(response, server_id);
//            }
//            if (ServerConnectionManager.getLeader().equals(server_id)) {
//                ServerConnectionManager.electLeaderFailure();
//                System.out.println("Leader Failed! new election started");
//            }
        }
        if (msgType.equals("areYouThere")) {
            String sender = (String) request.get("sender");
            System.out.println("Recieved AreYouThere msg from : " + sender);
            ServerConnectionManager.addServerToOnlineServers(sender);
            JSONObject response = new JSONObject();
//            response.put("type", "IamHere");
            response.put("server", ServerConnectionManager.getServerId());
            sendMessage(response);
//            ServerConnectionManager.sendToServer(response, sender);
        }
        if (msgType.equals("IamHere")) {
            String sender = (String) request.get("server");
            System.out.println("Recieved IamHere msg from : " + sender);
            ServerConnectionManager.addServerToOnlineServers(sender);
        }
        if (msgType.equals("statusRequest")) {
            JSONObject res = new JSONObject();
            res.put("serverId", ServerConnectionManager.getServerId());
            res.put("clientIds", ServerState.getClientIds().toString());
            res.put("roomIds", ServerState.getRoomIds().toString());
            sendMessage(res);
        }
        if (msgType.equals("statusUpdated")) {
            ServerConnectionManager.setStatusUpdated(true);
        }
        alive = false;
    }

    public void sendMessage(JSONObject msg) {
        try {
            out.write((msg.toJSONString() + "\n").getBytes(StandardCharsets.UTF_8));
            out.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
