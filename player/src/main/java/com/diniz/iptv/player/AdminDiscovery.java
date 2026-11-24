package com.diniz.iptv.player;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

public final class AdminDiscovery {
    private static final int DISCOVERY_PORT = 8788;
    private static final String REQUEST = "DINIZ_IPTV_DISCOVER_V1";
    private static final String REPLY = "DINIZ_IPTV_ADMIN_V1";

    private AdminDiscovery() {}

    public static String discover(int timeoutMs) throws Exception {
        long deadline = System.currentTimeMillis() + timeoutMs;
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setBroadcast(true);
            socket.setSoTimeout(Math.min(timeoutMs, 800));
            byte[] request = REQUEST.getBytes(StandardCharsets.UTF_8);

            Set<InetAddress> targets = new LinkedHashSet<>();
            targets.add(InetAddress.getByName("255.255.255.255"));
            for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!ni.isUp() || ni.isLoopback()) continue;
                for (InterfaceAddress ia : ni.getInterfaceAddresses()) if (ia.getBroadcast() != null) targets.add(ia.getBroadcast());
            }
            for (InetAddress target : targets) {
                try { socket.send(new DatagramPacket(request, request.length, target, DISCOVERY_PORT)); } catch (Exception ignored) {}
            }

            byte[] buf = new byte[256];
            while (System.currentTimeMillis() < deadline) {
                int remaining = (int)Math.max(100, deadline - System.currentTimeMillis());
                socket.setSoTimeout(Math.min(remaining, 800));
                DatagramPacket packet = new DatagramPacket(buf, buf.length);
                try { socket.receive(packet); }
                catch (SocketTimeoutException e) { continue; }
                String msg = new String(packet.getData(), packet.getOffset(), packet.getLength(), StandardCharsets.UTF_8).trim();
                if (!msg.startsWith(REPLY + "|")) continue;
                String[] parts = msg.split("\\|", 2);
                int port = 8787;
                try { port = Integer.parseInt(parts[1]); } catch (Exception ignored) {}
                return "http://" + packet.getAddress().getHostAddress() + ":" + port;
            }
        }
        return null;
    }
}
