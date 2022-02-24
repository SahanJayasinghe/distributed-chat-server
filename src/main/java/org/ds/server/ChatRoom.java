package org.ds.server;

import org.json.simple.JSONObject;

import java.util.ArrayList;

public class ChatRoom {
    private String roomId;
    private ClientHandler owner;
    private ArrayList<ClientHandler> members;

    public ChatRoom(String roomId) {
        this.roomId = roomId;
        this.owner = null;
        this.members = new ArrayList<>();
    }

    public ChatRoom(String roomId, ClientHandler owner) {
        this.roomId = roomId;
        this.owner = owner;
        this.members = new ArrayList<>();
    }

    public String getRoomId() {
        return roomId;
    }

    public String getOwnerId() {
        if (owner == null)
            return "";
        return owner.getClientId();
    }

    public void addMember(ClientHandler client) {
        members.add(client);
        JSONObject msg = client.changeRoom(roomId);
        broadcast(msg);
    }

    public void broadcast(JSONObject msg) {
        this.members.forEach(clientHandler -> {clientHandler.sendMessage(msg);});
    }

    public void broadcast(JSONObject msg, String exceptId) {
        this.members.forEach(clientHandler -> {
            if (clientHandler.getClientId().equals(exceptId))
                clientHandler.sendMessage(msg);
        });
    }

    public ArrayList<String> getMemberIds() {
        ArrayList<String> memberIds = new ArrayList<>();
        members.forEach(clientHandler -> {
            memberIds.add(clientHandler.getClientId());
        });
        return memberIds;
    }
}
