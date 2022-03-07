package org.ds.server;

import java.util.*;

public class ServerState {
    private static String mainHallId;
    private static HashSet<String> clientIds;
    private static HashMap<String, ChatRoom> rooms;

    public static void init(String _mainHallId) {
        clientIds = new HashSet<>();
        mainHallId = _mainHallId;
        rooms = new HashMap<>();
        rooms.put(mainHallId, new ChatRoom(mainHallId));
    }

    public static String getMainHallId() {
        return mainHallId;
    }

    public static synchronized boolean isClientIdUnique(String id) {
        return !clientIds.contains(id);
    }

    public static synchronized void addClientId(String id) {
        clientIds.add(id);
    }

    public static synchronized void removeClientId(String id) {
        clientIds.remove(id);
    }

    public static synchronized boolean isRoomIdUnique(String id) {
        return !rooms.containsKey(id);
    }

    public static synchronized void addRoom(ChatRoom room) {
        rooms.put(room.getRoomId(), room);
    }

    public static synchronized void addMemberToRoom(ClientHandler client, String roomId) {
        if (rooms.containsKey(roomId)) {
            ChatRoom joiningRoom = rooms.get(roomId);
            joiningRoom.addMember(client);
        }
    }

    public static synchronized void removeMemberFromRoom(ClientHandler client, String roomId) {
        if (rooms.containsKey(roomId)) {
            ChatRoom currentRoom = rooms.get(roomId);
            currentRoom.removeMember(client);
        }
    }

    public static void moveMembers(String fromRoomId, String toRoomId) {
        ChatRoom from = rooms.get(fromRoomId);
        ChatRoom to = rooms.get(toRoomId);
        ArrayList<ClientHandler> toBeMovedMembers = new ArrayList<>(from.getMembers());
        for (ClientHandler client : toBeMovedMembers) {
            from.removeMember(client);
            from.broadcast(client.getChangeRoomMsg(toRoomId));
            to.addMember(client);
        }
        rooms.remove(fromRoomId);
    }

    public static ChatRoom getRoom(String roomId) {
        return rooms.get(roomId);
    }

    public static Set<String> getRoomMembers(String roomId) {
        return rooms.get(roomId).getMemberIds();
    }

    public static Set<String> getRoomList() {
        return rooms.keySet();
    }

    public static String getRoomOwner(String roomId) {
        return rooms.get(roomId).getOwnerId();
    }
}
