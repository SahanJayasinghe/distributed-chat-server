package org.ds.server;

import org.json.simple.JSONObject;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ServerState {
    private static final String MAINHALL_PREFIX = "MainHall-";
    private static String mainHallId;
    private static ConcurrentHashMap<String, HashSet<String>> clientIds;
    private static ConcurrentHashMap<String, HashSet<String>> roomIds;
    private static Map<String, ChatRoom> rooms;
    private static final Object clientIdsLock = new Object();
    private static final Object roomIdsLock = new Object();
//    private static String serverId;

    public static void init() {
        String serverId = ServerConnectionManager.getServerId();
        mainHallId = MAINHALL_PREFIX + serverId;
        clientIds = new ConcurrentHashMap<>();
        clientIds.put(serverId, new HashSet<>());
        roomIds = new ConcurrentHashMap<>();
        HashSet<String> defaultRoomIds = new HashSet<>();
        defaultRoomIds.add(MAINHALL_PREFIX + serverId);
        roomIds.put(serverId, defaultRoomIds);
//        ServerConnectionManager.getServerIds().forEach(sId -> {
////            if (ServerConnectionManager.isLeader()) {
////                clientIds.put(sId, new HashSet<>());
////            }
//            clientIds.put(sId, new HashSet<>());
//            HashSet<String> currServerRooms = new HashSet<>();
//            currServerRooms.add(MAINHALL_PREFIX + sId);
//            roomIds.put(sId, currServerRooms);
//        });

        rooms = new ConcurrentHashMap<>();
        rooms.put(mainHallId, new ChatRoom(mainHallId));
    }

    public static String getMainHallId() {
        return mainHallId;
    }

    @SuppressWarnings("unchecked")
    public static synchronized boolean isClientIdUnique(String id) {
        if (ServerConnectionManager.isLeader()) {
            return checkClientIdByLeader(id);
        }
        JSONObject msg = new JSONObject();
        msg.put("type", "newclient");
        msg.put("id", id);
        msg.put("server", ServerConnectionManager.getServerId());
        JSONObject res = ServerConnectionManager.contactLeader(msg);
        System.out.println("asking leader to check uniqueness of client id");
        if (res != null) {
            System.out.println(res.toJSONString());
        }
        else {
            System.out.println("null");
        }
        return res != null && Boolean.parseBoolean((String) res.get("approved"));
    }

    private static synchronized boolean checkClientIdByLeader(String id) {
        boolean isUnique = true;
        for (Map.Entry<String, HashSet<String>> entry : clientIds.entrySet()) {
            if (entry.getValue().contains(id)) {
                isUnique = false;
                break;
            }
        }
        return isUnique;
    }

    public static synchronized void addClientId(String id, String sId) {
        updateClientIds(id, sId, true);
    }

    public static synchronized void removeClientId(String id, String sId) {
        updateClientIds(id, sId, false);
    }

    private static synchronized void updateClientIds(String id, String sId, boolean add) {
        synchronized (clientIdsLock) {
            if (add) {
                clientIds.get(sId).add(id);
            }
            else {
                clientIds.get(sId).remove(id);
            }
        }
    }

    public static Set<String> getClientIds() {
        return clientIds.get(ServerConnectionManager.getServerId());
    }

    public static synchronized void switchServer(String clientId, String formerServer, String newServer) {
        addClientId(clientId, newServer);
        removeClientId(clientId, formerServer);
    }

    public static synchronized boolean isRoomIdUnique(String id) {
        if (ServerConnectionManager.isLeader()) {
            return checkRoomIdByLeader(id);
        }
        // contact leader to check room id uniqueness
        JSONObject msg = new JSONObject();
        msg.put("type", "newroom");
        msg.put("id", id);
        msg.put("server", ServerConnectionManager.getServerId());
        System.out.println("asking leader to check roomid");
        JSONObject res = ServerConnectionManager.contactLeader(msg);
        if (res!=null) System.out.println(res.toJSONString());
        return res != null && Boolean.parseBoolean((String) res.get("approved"));
//        return !rooms.containsKey(id);
    }

    private static synchronized boolean checkRoomIdByLeader(String id) {
        boolean isUnique = true;
        for (HashSet<String> values : roomIds.values()) {
            if (values.contains(id)) {
                isUnique = false;
                break;
            }
        }
        return isUnique;
    }


    public static synchronized void addServerClientIds(String sId, HashSet<String> ids) {
        synchronized (clientIdsLock) {
            clientIds.put(sId, ids);
        }
    }

    public static synchronized void removeServerClientIds(String serverId) {
        synchronized (clientIdsLock) {
            clientIds.remove(serverId);
        }
    }

    public static synchronized void addRoomId(String roomId, String sId) {
        synchronized (roomIdsLock) {
            updateRoomIds(roomId, sId, true);
        }
    }

    public static synchronized void removeRoomId(String roomId, String sId) {
        synchronized (roomIdsLock) {
            updateRoomIds(roomId, sId, false);
        }
    }

    private static synchronized void updateRoomIds(String roomId, String sId, boolean add) {
        synchronized (roomIdsLock) {
            if (add) {
                roomIds.get(sId).add(roomId);
            }
            else {
                roomIds.get(sId).remove(roomId);
            }
        }
    }

    public static synchronized void addServerRoomIds(String sId, HashSet<String> ids) {
        synchronized (roomIdsLock) {
            if (roomIds.contains(sId)) {
                roomIds.get(sId).addAll(ids);
            }
            roomIds.put(sId, ids);
        }
    }

    public static synchronized void removeServerRoomIds(String serverId){
        synchronized (roomIdsLock) {
            roomIds.remove(serverId);
        }
    }

    public static Set<String> getRoomIds() {
        return rooms.keySet();
    }

    public static String findServerContainingRoom(String roomId) {
        String sId = null;
        for (Map.Entry<String, HashSet<String>> entry : roomIds.entrySet()) {
            if (entry.getValue().contains(roomId)) {
                sId = entry.getKey();
                break;
            }
        }
        return sId;
    }

    public static synchronized boolean isRoomInThisServer(String roomId) {
        return rooms.containsKey(roomId);
    }

    public static synchronized void addRoom(ChatRoom room) {
        rooms.put(room.getRoomId(), room);
    }

    public static synchronized void addMemberToRoom(ClientHandler client, String roomId) {
        ChatRoom joiningRoom = rooms.get(roomId);
        joiningRoom.addMember(client);
    }

    public static synchronized void removeMemberFromRoom(ClientHandler client, String roomId) {
        if (rooms.containsKey(roomId)) {
            ChatRoom currentRoom = rooms.get(roomId);
            currentRoom.removeMember(client);
        }
    }

    public static void moveMembers(String fromRoomId, String toRoomId) {
        ChatRoom from = rooms.get(fromRoomId);
        from.setDeleting();
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
        Set<String> allRoomIds = new HashSet<>();
        for (HashSet<String> values : roomIds.values()) {
            allRoomIds.addAll(values);
        }
//        return rooms.keySet();
        return allRoomIds;
    }

    public static String getRoomOwner(String roomId) {
        return rooms.get(roomId).getOwnerId();
    }

    public static synchronized void updateStateOnServerUp(String sId) {
        HashSet<String> defaultRooms = new HashSet<>();
        defaultRooms.add(MAINHALL_PREFIX + sId);
        addServerRoomIds(sId, defaultRooms);
        if (ServerConnectionManager.isLeader()) {
            addServerClientIds(sId, new HashSet<>());
        }
    }

    public static synchronized void updateStateOnServerDown(String sId) {
        removeServerRoomIds(sId);
        if (ServerConnectionManager.isLeader()) {
            removeServerClientIds(sId);
        }
    }
}
