package org.ds.server;

import java.util.HashMap;
import java.util.HashSet;

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

    public static boolean isClientIdUnique(String id) {
        return !clientIds.contains(id);
    }

    public static void addClientId(String id) {
        clientIds.add(id);
    }

    public static boolean isRoomIdUnique(String id) {
        return !rooms.containsKey(id);
    }

    public static void addRoom(ChatRoom room) {
        rooms.put(room.getRoomId(), room);
    }

    public static void addMemberToRoom(ClientHandler client, String roomId) {
        if (rooms.containsKey(roomId)) {
            ChatRoom joiningRoom = rooms.get(roomId);
            joiningRoom.addMember(client);
        }
    }
}
