package org.ds.server;

import java.util.HashMap;
import java.util.Set;
import java.net.ServerSocket;
import java.net.Socket;
import java.io.InputStreamReader;
import java.io.BufferedReader;
import java.nio.charset.StandardCharsets;
import java.io.DataOutputStream;
import java.io.IOException;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

public class HeartBeatScheduler {

    private static HashMap<String, Long> heartbeatReceivedTimes;
    private static Long HEARTBEAT_INTERVEL = 1000L;
    private static Long TIMEOUT = HEARTBEAT_INTERVEL * 3;
    private String serverId = null;
    public static String HEARTBEAT = "HEARTBEAT";
    public String SERVER_DOWN = "SERVER_DOWN";
    HashMap<String, HashMap<String, String>> serverConfig;

    public static void updateHeartbeatReceivedTimes(String server_id) {
        System.out.println("Heartbeat recieved from : " + server_id);
        heartbeatReceivedTimes.put(server_id, System.nanoTime());
        System.out.println(heartbeatReceivedTimes);
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
                String leader = ServerConnectionManager.getLeader();
                Set<String> serverIds = heartbeatReceivedTimes.keySet();
                for (String server_Id : serverIds) {
                    if (!serverId.equals(server_Id)) {
                        Long lastHeartbeatReceivedTime = (Long) heartbeatReceivedTimes.get(server_Id);
                        Long timeSinceLastHeartbeat = now - lastHeartbeatReceivedTime;
                        if (timeSinceLastHeartbeat >= HEARTBEAT_INTERVEL * 1000000) {
                            if (server_Id.equals(leader)) {
                                System.out.println("leader failed");
                                // send leader elect msg
                            } else {
                                System.out.println("server failed - " + server_Id);
                                // notify leader
                            }
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
                Set<String> serverIds = ServerConnectionManager.getOnlineServers();
                System.out.println("online servers " + serverIds);
                for (String server_Id : serverIds) {
                    if (!server_Id.equals(serverId)) {
                        int port = Integer.parseInt(serverConfig.get(server_Id).get("coordPort"));
                        String address = serverConfig.get(server_Id).get("address");
                        Socket socket;
                        try {
                            socket = new Socket(address, port);
                            DataOutputStream out = new DataOutputStream(socket.getOutputStream());
                            JSONObject msg = new JSONObject();
                            msg.put("type", HEARTBEAT);
                            msg.put("server", serverId);
                            out.write((msg.toJSONString() + "\n").getBytes(StandardCharsets.UTF_8));
                            System.out.println("Heartbeat sent! to :" + server_Id);
                            out.flush();
                            out.close();
                            socket.close();
                        } catch (Exception e) {
                            e.printStackTrace();
                        }

                    }
                }

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
