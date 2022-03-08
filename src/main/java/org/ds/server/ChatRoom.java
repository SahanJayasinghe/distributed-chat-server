package org.ds.server;

import org.json.simple.JSONObject;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class ChatRoom {
    private String roomId;
    private ClientHandler owner;
    private HashMap<String, ClientHandler> members;

    public ChatRoom(String roomId) {
        this.roomId = roomId;
        this.owner = null;
        this.members = new HashMap<>();
    }

    public ChatRoom(String roomId, ClientHandler owner) {
        this.roomId = roomId;
        this.owner = owner;
        this.members = new HashMap<>();
    }

    public String getRoomId() {
        return roomId;
    }

    public String getOwnerId() {
        if (owner == null)
            return "";
        return owner.getClientId();
    }

    public synchronized void addMember(ClientHandler client) {
        members.put(client.getClientId(), client);
        JSONObject msg = client.getChangeRoomMsg(roomId);
        client.setJoinedRoomId(roomId);
        broadcast(msg);
    }

    public synchronized void removeMember(ClientHandler client) {
        members.remove(client.getClientId());
    }

    public void broadcast(JSONObject msg) {
        for (Map.Entry<String, ClientHandler> entry : members.entrySet()) {
            entry.getValue().sendMessage(msg);
        }
    }

    public void broadcast(JSONObject msg, String exceptId) {
        for (Map.Entry<String, ClientHandler> entry : members.entrySet()) {
            if (!entry.getKey().equals(exceptId)) {
                entry.getValue().sendMessage(msg);
            }
        }
    }

    public void delete() {
        owner = null;
        members = null;
        roomId = null;
    }

    public Collection<ClientHandler> getMembers() {
        return members.values();
    }

    public Set<String> getMemberIds() {
        return members.keySet();
    }
}
