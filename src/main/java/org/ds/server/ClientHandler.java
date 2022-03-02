package org.ds.server;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

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
//            String msgType;
            JSONObject req;
            while (!clientSocket.isClosed()) {
                inputLine = in.readLine();
                System.out.println("Received: " + inputLine);
                req = (JSONObject) jsonParser.parse(inputLine);
//                msgType = (String) obj.get("type");
//                if ("newidentity".equals(msgType)) {
//                    JSONObject response = new JSONObject();
//                    response.put("type", "newidentity");
//                    response.put("approved", "false");
//                    out.write((response.toJSONString() + "\n").getBytes(StandardCharsets.UTF_8));
//                    out.flush();
//                }
                handleRequest(req);
            }

            in.close();
            out.close();
            clientSocket.close();
        } catch (IOException | ParseException e) {
            e.printStackTrace();
        }
    }

    public JSONObject readRequest(JSONObject request) {
        String msgType = (String) request.get("type");
        JSONObject response = new JSONObject();
        if ("newidentity".equals(msgType)) {
            String clientId = (String) request.get("identity");
            response.put("type", "newidentity");
            if (ServerState.isClientIdUnique(clientId)) {
                response.put("approved", "true");
                this.clientId = clientId;
            }
            else
                response.put("approved", "false");
        }
        return response;
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
                    ServerState.addClientId(this.clientId);
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

        }
        else if (msgType.equals("createroom")) {
            String roomId = (String) request.get("roomid");
            response.put("type", "createroom");
            response.put("roomid", roomId);
            synchronized (this) {
                if (this.ownedRoomId == null && ServerState.isRoomIdUnique(roomId)) {
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
                }
                else {
                    response.put("approved", "false");
                    sendMessage(response);
                }
            }
        }
    }

    public JSONObject changeRoom(String newRoomId) {
        JSONObject response = new JSONObject();
        response.put("type", "roomchange");
        response.put("identity", clientId);
        response.put("former", joinedRoomId);
        response.put("roomid", newRoomId);
        joinedRoomId = newRoomId;
//        out.write((response.toJSONString() + "\n").getBytes(StandardCharsets.UTF_8));
//        out.flush();
        return response;
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
