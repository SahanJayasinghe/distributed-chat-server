package org.ds.server;

import java.util.HashMap;
import java.util.Set;
import java.net.ServerSocket;
import java.net.Socket;
import java.io.InputStreamReader;
import java.io.BufferedReader;
import java.nio.charset.StandardCharsets;
import java.io.DataOutputStream;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

public class HeartBeatScheduler {

    private HashMap<String, Long> heartbeatReceivedTimes;
    private static Long HEARTBEAT_INTERVEL = 500L;
    private static Long TIMEOUT = HEARTBEAT_INTERVEL * 3;
    private String serverId = null;
    private Integer serverPort = null;
    public String HEARTBEAT = "HEARTBEAT";
    public String SERVER_DOWN = "SERVER_DOWN";
    HashMap<String, HashMap<String, String>> serverConfig;
    private JSONParser jsonParser;
    private static String leader = null;

    public HeartBeatScheduler(HashMap<String, HashMap<String, String>> serverConfig, String _serverId, String _leader) {
        serverId = _serverId;
        jsonParser = new JSONParser();
        for (String server_Id : serverConfig.keySet()) {
            heartbeatReceivedTimes.put(server_Id, 0L);
        }
        serverPort = Integer.parseInt(serverConfig.get(serverId).get("coordPort"));
        leader = _leader;
    }

    class HeartBeatListner extends Thread {
        public void run() {
            while (true) {
                ServerSocket serverSocket;
                try {
                    serverSocket = new ServerSocket(serverPort);
                    Socket socket = serverSocket.accept();
                    BufferedReader in = new BufferedReader(
                            new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                    String inputLine = in.readLine();
                    System.out.println("Received: " + inputLine);
                    if (inputLine != null) {

                        JSONObject request = (JSONObject) jsonParser.parse(inputLine);
                        String msgType = (String) request.get("type");
                        if (msgType.equals(HEARTBEAT)) {
                            String server_id = (String) request.get("server");

                            heartbeatReceivedTimes.put(server_id, System.nanoTime());
                        }
                    }
                    in.close();
                    socket.close();
                } catch (Exception e) {
                    e.printStackTrace();
                }

            }
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
                Set<String> serverIds = heartbeatReceivedTimes.keySet();
                for (String server_Id : serverIds) {
                    Long lastHeartbeatReceivedTime = (Long) heartbeatReceivedTimes.get(server_Id);
                    Long timeSinceLastHeartbeat = now - lastHeartbeatReceivedTime;
                    if (timeSinceLastHeartbeat >= HEARTBEAT_INTERVEL) {
                        // InformLeader(serverId);
                        if (server_Id == leader) {
                            // send leader elect msg
                        } else {
                            int port = Integer.parseInt(serverConfig.get(leader).get("coordPort"));
                            String address = serverConfig.get(leader).get("address");
                            Socket socket;
                            try {
                                socket = new Socket(address, port);
                                DataOutputStream out = new DataOutputStream(socket.getOutputStream());
                                JSONObject msg = new JSONObject();
                                msg.put("type", SERVER_DOWN);
                                msg.put("server", server_Id);
                                out.write((msg.toJSONString() + "\n").getBytes(StandardCharsets.UTF_8));
                                out.flush();
                                out.close();
                                socket.close();
                            } catch (Exception e) {
                                e.printStackTrace();
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
                Set<String> serverIds = heartbeatReceivedTimes.keySet();
                for (String server_Id : serverIds) {
                    if (server_Id != serverId) {
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
        HeartBeatListner listner = new HeartBeatListner();
        HeartBeatSender sender = new HeartBeatSender();
        HeartBeatValidator validator = new HeartBeatValidator();
        listner.start();
        sender.start();
        validator.start();
    }
}
