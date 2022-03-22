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
import java.util.*;

public class ServerHandler extends Thread {
    private Socket clientSocket;
    private DataOutputStream out;
    private BufferedReader in;
    private JSONParser jsonParser;
    private boolean alive;

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
            System.out.printf("new connection from server running on %s\n",
                    clientSocket.getRemoteSocketAddress().toString());

            String inputLine;
            JSONObject req;

            while (alive) {
                inputLine = in.readLine();
                System.out.println("Received: " + inputLine);
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
        if (ServerConnectionManager.isLeader()) {
            if (msgType.equals("newclient")) {
                String id = (String) request.get("id");
                synchronized (this) {
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
            } else if (msgType.equals("newroom")) {
                String roomId = (String) request.get("id");
                synchronized (this) {
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
            } else if (msgType.equals("switchserver")) {
                ServerState.switchServer(
                        (String) request.get("clientid"),
                        (String) request.get("formerserver"),
                        (String) request.get("newserver"));
            } else if (msgType.equals("deleteclient")) {
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
        if (msgType.equals("IamUp")) {
            String sender = (String) request.get("sender");
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
            ServerConnectionManager.setReceivedView(view);
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
        if (msgType.equals(HeartBeatScheduler.HEARTBEAT)) {
            String server_id = (String) request.get("server");
            HeartBeatScheduler.updateHeartbeatReceivedTimes(server_id);
        }
        if (msgType.equals(HeartBeatScheduler.SERVER_DOWN)) {
            String server_id = (String) request.get("server");
            System.out.println("server recieved the failure msg - failed server id : " + server_id);
            // This server has failed.
            // TODO :: take actions
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
