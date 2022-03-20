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
import java.util.HashMap;
import java.util.Set;

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
                    new InputStreamReader(clientSocket.getInputStream(), StandardCharsets.UTF_8)
            );
            System.out.printf("new connection from server running on %s\n", clientSocket.getRemoteSocketAddress().toString());

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
                synchronized (this) {
                    boolean approved = ServerState.isRoomIdUnique(roomId);
                    if (approved) {
                        String sId = (String) request.get("server");
                        ServerState.addRoomId(roomId, sId);
                        JSONObject res = new JSONObject();
                        res.put("type", "roomidapproval");
                        res.put("roomid", roomId);
                        res.put("server", sId);
                        res.put("approved", "true");
                        sendMessage(res);
                        ServerConnectionManager.broadcast(res, sId);
                    }
                }
            }
            else if (msgType.equals("switchserver")) {
                ServerState.switchServer(
                        (String) request.get("clientid"),
                        (String) request.get("formerserver"),
                        (String) request.get("newserver")
                );
            }
            else if (msgType.equals("deleteclient")) {
                ServerState.removeClientId(
                        (String) request.get("clientid"),
                        (String) request.get("serverid")
                );
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
            msg.put("identity", (String) request.get("clientid"));
            msg.put("former", formerRoomId);
            msg.put("roomid", newRoomId);
            ChatRoom former = ServerState.getRoom(formerRoomId);
            former.broadcast(msg);
        }
        if (msgType.equals("deleteroom")) {
            ServerState.removeRoomId(
                    (String) request.get("roomid"),
                    (String) request.get("serverid")
            );
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
