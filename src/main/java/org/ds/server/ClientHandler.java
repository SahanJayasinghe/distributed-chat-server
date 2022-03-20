package org.ds.server;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

//import java.io.*;
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.regex.Pattern;

@SuppressWarnings("unchecked")
public class ClientHandler extends Thread {
    private String clientId = null;
    private String joinedRoomId = "";
    private String ownedRoomId = null;
    private Socket clientSocket;
    private DataOutputStream out;
    private BufferedReader in;
    private JSONParser jsonParser;

    public ClientHandler(Socket s) {
        this.clientSocket = s;
        jsonParser = new JSONParser();
    }

    @Override
    public void run() {
        try {
            out = new DataOutputStream(clientSocket.getOutputStream());
            in = new BufferedReader(
                    new InputStreamReader(clientSocket.getInputStream(), StandardCharsets.UTF_8)
            );
            String inputLine;
            JSONObject req;

            while (!clientSocket.isClosed()) {
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
//            e.printStackTrace();
            quit();
        }
    }

    public void handleRequest(JSONObject request) {
        String msgType = (String) request.get("type");
        JSONObject response = new JSONObject();
        if (msgType.equals("newidentity")) {
            String clientId = (String) request.get("identity");
            response.put("type", "newidentity");
            synchronized (this) {
                if (ServerState.isClientIdUnique(clientId)) {
                    response.put("approved", "true");
                    this.clientId = clientId;
                    sendMessage(response);
                    ServerState.addClientId(this.clientId, ServerConnectionManager.getServerId());
                    ServerState.addMemberToRoom(this, ServerState.getMainHallId());
                } else {
                    response.put("approved", "false");
                    sendMessage(response);
                }
            }
        }
        else if (msgType.equals("list")) {
            JSONArray roomList = new JSONArray();
            roomList.addAll(ServerState.getRoomList());
            response.put("type", "roomlist");
            response.put("rooms", roomList);
            sendMessage(response);
        }
        else if (msgType.equals("who")) {
            JSONArray members = new JSONArray();
            members.addAll(ServerState.getRoomMembers(joinedRoomId));
            response.put("type", "roomcontents");
            response.put("roomid", joinedRoomId);
            response.put("identities", members);
            response.put("owner", ServerState.getRoomOwner(joinedRoomId));
            sendMessage(response);
        }
        else if (msgType.equals("quit")) {
            response.put("type", "roomchange");
            response.put("roomid", "");
            response.put("identity", clientId);
            response.put("former", joinedRoomId);
            sendMessage(response);
            quit();
        }
        else if (msgType.equals("createroom")) {
            String roomId = (String) request.get("roomid");
            response.put("type", "createroom");
            response.put("roomid", roomId);
            createRoom(roomId, response);
        }
        else if (msgType.equals("joinroom")) {
            String roomId = (String) request.get("roomid");
            joinRoom(roomId);
        }
        else if (msgType.equals("movejoin")) {
            String formerRoom = (String) request.get("former");
            String joiningRoomId = (String) request.get("roomid");
            clientId = (String) request.get("identity");

            joinedRoomId = formerRoom;
            String formerServer = ServerState.findServerContainingRoom(formerRoom);
            ServerState.addClientId(clientId, ServerConnectionManager.getServerId());
            JSONObject serverChangeMsg = new JSONObject();
            serverChangeMsg.put("type", "serverchange");
            serverChangeMsg.put("approved", "true");
            serverChangeMsg.put("serverid", ServerConnectionManager.getServerId());
            sendMessage(serverChangeMsg);
            if (ServerState.isRoomInThisServer(joiningRoomId)) {
                if (ServerState.getRoom(joiningRoomId).isDeleting()) {
                    joiningRoomId = ServerState.getMainHallId();
                }
                ServerState.addMemberToRoom(this, joiningRoomId);
            }

            // inform leader about server change -> switch client id to relevant hashset
            JSONObject leaderMsg = new JSONObject();
            leaderMsg.put("type", "serverswitch");
            leaderMsg.put("clientid", clientId);
            leaderMsg.put("formerserver", formerServer);
            leaderMsg.put("newserver", ServerConnectionManager.getServerId());
            ServerConnectionManager.sendToLeader(leaderMsg);

            // broadcast roomchange message in the room where client previously joined in (previous server)
            JSONObject msg = new JSONObject();
            msg.put("type", "roomswitch");
            msg.put("clientid", clientId);
            msg.put("formerroom", formerRoom);
            msg.put("newroom", joinedRoomId);
            ServerConnectionManager.sendToServer(msg, formerServer);
        }
        else if (msgType.equals("deleteroom")) {
            String roomId = (String) request.get("roomid");
            response.put("type", "deleteroom");
            response.put("roomid", roomId);
            if (ownedRoomId != null && ownedRoomId.equals(roomId)) {
                ServerState.moveMembers(roomId, ServerState.getMainHallId());
                ownedRoomId = null;
                JSONObject serverMsg = new JSONObject(response);
                serverMsg.put("serverid", ServerConnectionManager.getServerId());
                ServerConnectionManager.broadcast(serverMsg);
                response.put("approved", "true");
                sendMessage(response);
            }  else {
                response.put("approved", "false");
                sendMessage(response);
            }
        }
        else if (msgType.equals("message")) {
            String content = (String) request.get("content");
            response.put("type", "message");
            response.put("identity", clientId);
            response.put("content", content);
            ChatRoom joined = ServerState.getRoom(joinedRoomId);
            joined.broadcast(response, clientId);
        }
    }

    public void setJoinedRoomId(String roomId) {
        joinedRoomId = roomId;
    }

    public JSONObject getChangeRoomMsg(String newRoomId) {
        JSONObject response = new JSONObject();
        response.put("type", "roomchange");
        response.put("identity", clientId);
        response.put("former", joinedRoomId);
        response.put("roomid", newRoomId);
//        joinedRoomId = newRoomId;
//        out.write((response.toJSONString() + "\n").getBytes(StandardCharsets.UTF_8));
//        out.flush();
        return response;
    }

    private void createRoom(String roomId, JSONObject response) {
        String regex = "^[a-zA-Z]{1}[a-zA-Z0-9]{2,15}$";
        boolean isMatched = Pattern.matches(regex, roomId);
        synchronized (this) {
            if (isMatched && this.ownedRoomId == null && ServerState.isRoomIdUnique(roomId)) {
                ServerState.addRoomId(roomId, ServerConnectionManager.getServerId());
                if (ServerConnectionManager.isLeader()) {
                    JSONObject res = new JSONObject();
                    res.put("type", "roomidapproval");
                    res.put("roomid", roomId);
                    res.put("server", ServerConnectionManager.getServerId());
                    res.put("approved", "true");
                    ServerConnectionManager.broadcast(res);
                }
                response.put("approved", "true");
                sendMessage(response);
                ChatRoom newChatRoom = new ChatRoom(roomId, this);
                this.ownedRoomId = roomId;
                ServerState.addRoom(newChatRoom);

                JSONObject broadcastMsg = new JSONObject();
                broadcastMsg.put("type", "roomchange");
                broadcastMsg.put("identity", this.clientId);
                broadcastMsg.put("former", this.joinedRoomId);
                broadcastMsg.put("roomid", this.ownedRoomId);
                ChatRoom formerRoom = ServerState.getRoom(this.joinedRoomId);
                formerRoom.removeMember(this);
                formerRoom.broadcast(broadcastMsg);
//                    ServerState.addMemberToRoom(this, ownedRoomId);
                newChatRoom.addMember(this);
                joinedRoomId = ownedRoomId;
            } else {
                response.put("approved", "false");
                sendMessage(response);
            }
        }
    }

    private void joinRoom(String roomId) {
        boolean alreadyIn = roomId.equals(joinedRoomId);
        boolean roomInThisServer = ServerState.isRoomInThisServer(roomId);
        if (!alreadyIn && this.ownedRoomId == null && roomInThisServer) {
            if (ServerState.getRoom(roomId).isDeleting()) {
                sendMessage(getChangeRoomMsg(joinedRoomId));
            }
            else {
                try {
                    ChatRoom formerRoom = ServerState.getRoom(joinedRoomId);
                    formerRoom.removeMember(this);
                    formerRoom.broadcast(getChangeRoomMsg(roomId));
                    ServerState.addMemberToRoom(this, roomId);
                } catch (NullPointerException e) {
                    sendMessage(getChangeRoomMsg(joinedRoomId));
                }
            }
        }
        else if (!roomInThisServer) {
            String sId = ServerState.findServerContainingRoom(roomId);
            if (sId == null) {
                sendMessage(getChangeRoomMsg(joinedRoomId));
            }
            else {
                HashMap<String, String> config = ServerConnectionManager.getServerConfig(sId);
                ChatRoom formerRoom = ServerState.getRoom(joinedRoomId);
                formerRoom.removeMember(this);
                joinedRoomId = "";
                JSONObject res = new JSONObject();
                res.put("type", "route");
                res.put("roomid", roomId);
                res.put("host", config.get("address"));
                res.put("port", config.get("clientsPort"));
                sendMessage(res);
            }
        }
        else {
            sendMessage(getChangeRoomMsg(joinedRoomId));
        }
    }

    private void quit() {
        if (clientId != null && !joinedRoomId.equals("")) {
            ChatRoom currentRoom = ServerState.getRoom(joinedRoomId);
            currentRoom.removeMember(this);
//            ServerState.removeMemberFromRoom(this, joinedRoomId);
            ServerState.removeClientId(this.clientId, ServerConnectionManager.getServerId());

            JSONObject broadcastMsg = new JSONObject();
            broadcastMsg.put("type", "roomchange");
            broadcastMsg.put("roomid", "");
            broadcastMsg.put("identity", clientId);
            broadcastMsg.put("former", joinedRoomId);
            currentRoom.broadcast(broadcastMsg);

            if (!ServerConnectionManager.isLeader()) {
                JSONObject clientIdRemoveMsg = new JSONObject();
                clientIdRemoveMsg.put("type", "deleteclient");
                clientIdRemoveMsg.put("clientid", clientId);
                clientIdRemoveMsg.put("serverid", ServerConnectionManager.getServerId());
                ServerConnectionManager.sendToLeader(clientIdRemoveMsg);
            }
        }

        if (ownedRoomId != null) { // delete owned room
            JSONObject delRoomMsg = new JSONObject();
            delRoomMsg.put("type", "deleteroom");
            delRoomMsg.put("roomid", ownedRoomId);
            ServerState.moveMembers(ownedRoomId, ServerState.getMainHallId());
            ownedRoomId = null;
            JSONObject roomIdRemoveMsg = new JSONObject();
            roomIdRemoveMsg.put("type", "deleteroom");
            roomIdRemoveMsg.put("clientid", clientId);
            roomIdRemoveMsg.put("serverid", ServerConnectionManager.getServerId());
            ServerConnectionManager.broadcast(roomIdRemoveMsg);
            delRoomMsg.put("approved", "true");
            sendMessage(delRoomMsg);
        }
    }

    public void sendMessage(JSONObject msg) {
        try {
            out.write((msg.toJSONString() + "\n").getBytes(StandardCharsets.UTF_8));
            out.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public String getClientId() {
        return clientId;
    }
}
