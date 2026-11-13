package com.mgplay.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;

import fi.iki.elonen.NanoHTTPD;

public final class PanelService extends Service {
    private static final String CHANNEL = "mgplay_panel";
    private PanelServer server;

    @Override public void onCreate() {
        super.onCreate();
        createChannel();
        Intent open = new Intent(this, MainActivity.class);
        PendingIntent pending = PendingIntent.getActivity(this, 0, open,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder builder = Build.VERSION.SDK_INT >= 26
            ? new Notification.Builder(this, CHANNEL) : new Notification.Builder(this);
        startForeground(5101, builder.setContentTitle("MG Play")
            .setContentText("Painel de gerenciamento ativo na rede local")
            .setSmallIcon(android.R.drawable.stat_sys_upload_done)
            .setContentIntent(pending).setOngoing(true).build());
        try {
            server = new PanelServer(this);
            server.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false);
        } catch (Exception e) {
            stopSelf();
        }
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(CHANNEL, "MG Play Painel", NotificationManager.IMPORTANCE_LOW);
            getSystemService(NotificationManager.class).createNotificationChannel(channel);
        }
    }

    @Override public void onDestroy() {
        if (server != null) server.stop();
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}