package com.diniz.dnschanger;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.VpnService;
import android.os.Build;
import android.os.ParcelFileDescriptor;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.util.Arrays;

public final class DnsVpnService extends VpnService {
    public static final String ACTION_START = "com.diniz.dnschanger.START";
    private static final String PREFS = "diniz_dns";
    private static final String CHANNEL_ID = "diniz_dns_active";
    private static final int NOTIFICATION_ID = 7142;

    private volatile boolean running;
    private volatile ParcelFileDescriptor tunnel;
    private SharedPreferences preferences;

    @Override
    public void onCreate() {
        super.onCreate();
        preferences = getSharedPreferences(PREFS, MODE_PRIVATE);
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null || !ACTION_START.equals(intent.getAction())) {
            stopTunnel();
            stopForeground(true);
            stopSelf(startId);
            return START_NOT_STICKY;
        }

        String name = intent.getStringExtra("dns_name");
        String primary = intent.getStringExtra("primary");
        String secondary = intent.getStringExtra("secondary");
        if (name == null || primary == null || secondary == null) {
            stopSelf(startId);
            return START_NOT_STICKY;
        }

        startForeground(NOTIFICATION_ID, buildNotification(name));
        try {
            establishTunnel(name, primary, secondary);
        } catch (Exception error) {
            stopTunnel();
            preferences.edit().putBoolean("active", false).putString("last_error", error.getMessage()).apply();
            stopForeground(true);
            stopSelf(startId);
        }
        return START_NOT_STICKY;
    }

    private void establishTunnel(String name, String primary, String secondary) throws IOException {
        stopTunnel();

        Builder builder = new Builder()
                .setSession("Diniz DNS - " + name)
                .setMtu(1500)
                .addAddress("10.77.0.1", 32)
                .addDnsServer(primary)
                .addDnsServer(secondary)
                .addRoute(primary, 32)
                .addRoute(secondary, 32);

        ParcelFileDescriptor descriptor = builder.establish();
        if (descriptor == null) {
            throw new IOException("O Android não permitiu criar a interface DNS.");
        }

        tunnel = descriptor;
        running = true;
        preferences.edit()
                .putBoolean("active", true)
                .putString("dns_name", name)
                .putString("primary", primary)
                .putString("secondary", secondary)
                .remove("last_error")
                .apply();

        Thread worker = new Thread(() -> processPackets(descriptor), "DinizDnsForwarder");
        worker.start();
    }

    private void processPackets(ParcelFileDescriptor descriptor) {
        byte[] buffer = new byte[32767];
        try (FileInputStream input = new FileInputStream(descriptor.getFileDescriptor());
             FileOutputStream output = new FileOutputStream(descriptor.getFileDescriptor())) {
            while (running && tunnel == descriptor) {
                int length = input.read(buffer);
                if (length <= 0) continue;

                byte[] packet = Arrays.copyOf(buffer, length);
                byte[] response = handleDnsPacket(packet);
                if (response != null && running && tunnel == descriptor) {
                    output.write(response);
                    output.flush();
                }
            }
        } catch (IOException ignored) {
            // Closing the TUN descriptor is the normal way to stop this loop.
        } finally {
            if (tunnel == descriptor) {
                tunnel = null;
                running = false;
                preferences.edit().putBoolean("active", false).apply();
                stopForeground(true);
                stopSelf();
            }
        }
    }

    private byte[] handleDnsPacket(byte[] packet) {
        if (packet.length < 28 || (packet[0] >>> 4 & 0x0F) != 4) return null;

        int ipHeaderLength = (packet[0] & 0x0F) * 4;
        int totalLength = readU16(packet, 2);
        if (ipHeaderLength < 20 || totalLength > packet.length || totalLength < ipHeaderLength + 8) return null;
        if ((packet[9] & 0xFF) != 17) return null; // UDP only

        int fragment = readU16(packet, 6);
        if ((fragment & 0x3FFF) != 0) return null; // Ignore fragmented datagrams.
        int udpOffset = ipHeaderLength;
        int sourcePort = readU16(packet, udpOffset);
        int destinationPort = readU16(packet, udpOffset + 2);
        int udpLength = readU16(packet, udpOffset + 4);
        if (destinationPort != 53 || udpLength < 8 || udpOffset + udpLength > totalLength) return null;

        int dnsLength = udpLength - 8;
        if (dnsLength < 12) return null;
        byte[] query = Arrays.copyOfRange(packet, udpOffset + 8, udpOffset + 8 + dnsLength);
        byte[] resolverAddress = Arrays.copyOfRange(packet, 16, 20);

        byte[] dnsResponse = askRealDnsServer(resolverAddress, query);
        if (dnsResponse == null || dnsResponse.length < 12) return null;
        return buildUdpResponse(packet, ipHeaderLength, sourcePort, dnsResponse);
    }

    private byte[] askRealDnsServer(byte[] address, byte[] query) {
        try {
            InetAddress resolver = InetAddress.getByAddress(address);
            try (DatagramSocket socket = new DatagramSocket()) {
                if (!protect(socket)) return null;
                socket.setSoTimeout(3500);
                DatagramPacket outgoing = new DatagramPacket(query, query.length, resolver, 53);
                socket.send(outgoing);

                byte[] responseBuffer = new byte[8192];
                DatagramPacket incoming = new DatagramPacket(responseBuffer, responseBuffer.length);
                socket.receive(incoming);
                if (incoming.getLength() < 12) return null;
                return Arrays.copyOf(incoming.getData(), incoming.getLength());
            }
        } catch (SocketTimeoutException timeout) {
            return null;
        } catch (Exception error) {
            return null;
        }
    }

    private byte[] buildUdpResponse(byte[] request, int ipHeaderLength, int clientPort, byte[] dnsResponse) {
        int responseLength = 20 + 8 + dnsResponse.length;
        if (responseLength > 65507) return null;

        byte[] response = new byte[responseLength];
        response[0] = 0x45;
        response[1] = request[1];
        writeU16(response, 2, responseLength);
        response[4] = request[4];
        response[5] = request[5];
        writeU16(response, 6, 0x4000);
        response[8] = 64;
        response[9] = 17;

        // Reverse IPv4 addresses: the DNS server becomes the reply source.
        System.arraycopy(request, 16, response, 12, 4);
        System.arraycopy(request, 12, response, 16, 4);

        writeU16(response, 10, 0);
        writeU16(response, 10, checksum(response, 0, 20));

        int udpOffset = 20;
        writeU16(response, udpOffset, readU16(request, ipHeaderLength + 2));
        writeU16(response, udpOffset + 2, clientPort);
        writeU16(response, udpOffset + 4, 8 + dnsResponse.length);
        writeU16(response, udpOffset + 6, 0); // IPv4 permits a zero UDP checksum.
        System.arraycopy(dnsResponse, 0, response, udpOffset + 8, dnsResponse.length);
        return response;
    }

    private static int checksum(byte[] data, int offset, int length) {
        long sum = 0;
        int end = offset + length;
        for (int i = offset; i + 1 < end; i += 2) {
            sum += ((data[i] & 0xFF) << 8) | (data[i + 1] & 0xFF);
        }
        if ((length & 1) != 0) sum += (data[end - 1] & 0xFF) << 8;
        while ((sum >>> 16) != 0) sum = (sum & 0xFFFF) + (sum >>> 16);
        return (int) (~sum) & 0xFFFF;
    }

    private static int readU16(byte[] data, int offset) {
        return ((data[offset] & 0xFF) << 8) | (data[offset + 1] & 0xFF);
    }

    private static void writeU16(byte[] data, int offset, int value) {
        data[offset] = (byte) ((value >>> 8) & 0xFF);
        data[offset + 1] = (byte) (value & 0xFF);
    }

    private Notification buildNotification(String name) {
        Intent openApp = new Intent(this, MainActivity.class);
        openApp.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, openApp,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                        ? PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
                        : PendingIntent.FLAG_UPDATE_CURRENT
        );

        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);
        return builder
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle("Diniz DNS ativo")
                .setContentText(name + " • consultas enviadas ao DNS escolhido")
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .build();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "Diniz DNS", NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("Mostra quando a seleção de DNS está ativa.");
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(channel);
        }
    }

    private void stopTunnel() {
        running = false;
        ParcelFileDescriptor current = tunnel;
        tunnel = null;
        if (current != null) {
            try {
                current.close();
            } catch (IOException ignored) {
            }
        }
        if (preferences != null) preferences.edit().putBoolean("active", false).apply();
    }

    @Override
    public void onRevoke() {
        stopTunnel();
        stopForeground(true);
        stopSelf();
        super.onRevoke();
    }

    @Override
    public void onDestroy() {
        stopTunnel();
        stopForeground(true);
        super.onDestroy();
    }
}
