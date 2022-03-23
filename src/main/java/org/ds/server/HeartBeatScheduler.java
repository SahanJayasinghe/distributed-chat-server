package org.ds.server;

import java.util.HashMap;
import java.util.Set;
import org.json.simple.JSONObject;


public class HeartBeatScheduler {

    private static HashMap<String, Long> heartbeatReceivedTimes;
    private static Long HEARTBEAT_INTERVEL = 1000L;
    private static Long TIMEOUT = HEARTBEAT_INTERVEL * 3;
    private String serverId;
    public static String HEARTBEAT = "HEARTBEAT";
    public static String SERVER_DOWN = "SERVER_DOWN";
    // public static String SERVER_UP = "SERVER_UP";
    HashMap<String, HashMap<String, String>> serverConfig;

    public static void updateHeartbeatReceivedTimes(String server_id) {
//        System.out.println("Heartbeat recieved from : " + server_id);
        heartbeatReceivedTimes.put(server_id, System.nanoTime());
        if(!ServerConnectionManager.getOnlineServers().contains(server_id)){
            ServerConnectionManager.addServerToOnlineServers(server_id);
            ServerState.updateStateOnServerUp(server_id);
        }

    }

    public HeartBeatScheduler(HashMap<String, HashMap<String, String>> _serverConfig, String _serverId) {
        serverId = _serverId;
        System.out.println("Heartbeat scheduler initialized - server ID : " + serverId);
        heartbeatReceivedTimes = new HashMap<>();
        serverConfig = _serverConfig;
        for (String server_Id : serverConfig.keySet()) {
            heartbeatReceivedTimes.put(server_Id, 0L);
        }

    }

    class HeartBeatValidator extends Thread {
        public void run() {
            while (true) {
                try {
                    Thread.sleep(TIMEOUT);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                Long now = System.nanoTime();
//                String leader = ServerConnectionManager.getLeader();
                Set<String> serverIds = heartbeatReceivedTimes.keySet();
                for (String server_Id : serverIds) {
                    if (!serverId.equals(server_Id) && ServerConnectionManager.getOnlineServers().contains(server_Id)) {
                        Long lastHeartbeatReceivedTime = (Long) heartbeatReceivedTimes.get(server_Id);
                        Long timeSinceLastHeartbeat = now - lastHeartbeatReceivedTime;
                        if (timeSinceLastHeartbeat >= HEARTBEAT_INTERVEL * 1000000) {
                            if (server_Id.equals(ServerConnectionManager.getLeader())) {
                                System.out.println("leader failed");
                                ServerConnectionManager.removeServerFromOnlineServers(server_Id);
                                ServerState.updateStateOnServerDown(server_Id);
                                ServerConnectionManager.electLeaderFailure(); // send leader elect msg
                            } else {
                                System.out.println("Server failed - " + server_Id);
                                ServerConnectionManager.removeServerFromOnlineServers(server_Id);
                                ServerState.updateStateOnServerDown(server_Id);
                                // notify leader
//                                System.out.println("Server failure notified to leader");
                            }
                            JSONObject msg = new JSONObject();
                            msg.put("type", SERVER_DOWN);
                            msg.put("server", server_Id);
//                          ServerConnectionManager.sendToLeader(msg);
                            ServerConnectionManager.broadcast(msg, server_Id);
                        }
                    }

                }
            }
        }
    }


    class HeartBeatSender extends Thread {
        public void run() {
            while (true) {
                // Set<String> serverIds = heartbeatReceivedTimes.keySet();
                JSONObject msg = new JSONObject();
                msg.put("type", HEARTBEAT);
                msg.put("server", serverId);
                ServerConnectionManager.broadcast(msg);
//                    Set<String> serverIds = ServerConnectionManager.getOnlineServers();
//                System.out.println("Leader : " + ServerConnectionManager.getLeader());
//                System.out.println("online servers " + serverIds);
//                    for (String server_Id : serverIds) {
//                        if (!server_Id.equals(serverId)) {
//
//                            ServerConnectionManager.sendToServer(msg, server_Id);
////                            System.out.println("Heartbeat sent! to :" + server_Id);
//                        }
//                    }
                try {
                    Thread.sleep(HEARTBEAT_INTERVEL);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }

    }

    public void start() {
        HeartBeatSender sender = new HeartBeatSender();
        HeartBeatValidator validator = new HeartBeatValidator();
        sender.start();
        validator.start();
    }
}
