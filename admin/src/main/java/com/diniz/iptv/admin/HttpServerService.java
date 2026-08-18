package com.diniz.iptv.admin;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class HttpServerService extends Service {
    public static final int PORT = 8787;
    public static final int DISCOVERY_PORT = 8788;
    public static final String DISCOVER = "DINIZ_IPTV_DISCOVER_V1";
    public static final String DISCOVER_REPLY = "DINIZ_IPTV_ADMIN_V1";
    private static volatile boolean appRunning = false;

    private volatile boolean running;
    private ServerSocket server;
    private DatagramSocket discoverySocket;
    private ExecutorService pool;
    private DbHelper db;

    public static boolean isRunning() { return appRunning; }

    @Override public void onCreate() {
        super.onCreate();
        db = new DbHelper(this);
        pool = Executors.newFixedThreadPool(8);
        createChannel();
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        startForeground(77, notification("Servidor IPTV ativo na rede local"));
        if (!running) {
            running = true;
            appRunning = true;
            new Thread(this::serveLoop, "DinizIptvServer").start();
            new Thread(this::discoveryLoop, "DinizIptvDiscovery").start();
        }
        return START_STICKY;
    }

    private void serveLoop() {
        try {
            server = new ServerSocket();
            server.setReuseAddress(true);
            server.bind(new InetSocketAddress("0.0.0.0", PORT));
            while (running) {
                Socket s = server.accept();
                pool.execute(() -> handle(s));
            }
        } catch (Exception e) {
            if (running) stopSelf();
        }
    }

    private void discoveryLoop() {
        try {
            discoverySocket = new DatagramSocket(null);
            discoverySocket.setReuseAddress(true);
            discoverySocket.setBroadcast(true);
            discoverySocket.bind(new InetSocketAddress("0.0.0.0", DISCOVERY_PORT));
            byte[] buf = new byte[256];
            while (running) {
                DatagramPacket packet = new DatagramPacket(buf, buf.length);
                discoverySocket.receive(packet);
                String msg = new String(packet.getData(), packet.getOffset(), packet.getLength(), StandardCharsets.UTF_8).trim();
                if (!DISCOVER.equals(msg)) continue;
                byte[] reply = (DISCOVER_REPLY + "|" + PORT).getBytes(StandardCharsets.UTF_8);
                DatagramPacket out = new DatagramPacket(reply, reply.length, packet.getAddress(), packet.getPort());
                discoverySocket.send(out);
            }
        } catch (Exception ignored) {
        }
    }

    private void handle(Socket socket) {
        try (Socket s = socket) {
            s.setSoTimeout(15000);
            BufferedInputStream in = new BufferedInputStream(s.getInputStream());
            String requestLine = readLine(in);
            if (requestLine == null || requestLine.isEmpty()) return;
            String[] first = requestLine.split(" ");
            if (first.length < 2) { send(s, 400, jsonError("Requisição inválida.")); return; }
            String method = first[0], target = first[1];
            Map<String,String> headers = new HashMap<>();
            String line;
            while ((line = readLine(in)) != null && !line.isEmpty()) {
                int p = line.indexOf(':');
                if (p > 0) headers.put(line.substring(0,p).trim().toLowerCase(Locale.ROOT), line.substring(p+1).trim());
            }
            int len = 0;
            try { len = Integer.parseInt(headers.getOrDefault("content-length","0")); } catch (Exception ignored) {}
            if (len < 0 || len > 1024 * 1024) { send(s, 413, jsonError("Requisição grande demais.")); return; }
            byte[] body = new byte[len]; int off=0;
            while(off<body.length){int n=in.read(body,off,body.length-off);if(n<0)break;off+=n;}
            route(s, method, target, new String(body,0,off,StandardCharsets.UTF_8));
        } catch (Exception ignored) {}
    }

    private void route(Socket s, String method, String target, String body) throws Exception {
        URI uri = new URI("http://localhost" + target);
        String path = uri.getPath();
        if ("GET".equals(method) && "/api/health".equals(path)) {
            JSONObject o = new JSONObject(); o.put("ok", true); o.put("service", "Diniz IPTV Admin"); o.put("port", PORT); o.put("version", 2); send(s,200,o); return;
        }
        if ("POST".equals(method) && "/api/login".equals(path)) {
            JSONObject q = new JSONObject(body);
            String user=q.optString("username").trim(), pass=q.optString("password");
            long exp = db.authenticate(user, pass);
            if (exp == -2) { send(s,403,jsonError("Conta expirada.")); return; }
            if (exp < 0) { send(s,401,jsonError("Usuário ou senha inválidos.")); return; }
            JSONObject o=new JSONObject();o.put("token",Auth.issue(this,user,exp));o.put("expiresAt",exp);send(s,200,o);return;
        }
        if ("GET".equals(method) && "/api/catalog".equals(path)) {
            Map<String,String> q=parseQuery(uri.getRawQuery()); String token=q.get("token"); String user=Auth.validate(this,token);
            if(user==null){send(s,401,jsonError("Sessão inválida ou expirada."));return;}
            JSONObject o=new JSONObject();o.put("items",db.listContent());o.put("user",user);o.put("generatedAt",System.currentTimeMillis());send(s,200,o);return;
        }
        send(s,404,jsonError("Rota não encontrada."));
    }

    private Map<String,String> parseQuery(String raw) throws Exception {Map<String,String> m=new HashMap<>();if(raw==null)return m;for(String part:raw.split("&")){String[] kv=part.split("=",2);String k=URLDecoder.decode(kv[0],"UTF-8");String v=kv.length>1?URLDecoder.decode(kv[1],"UTF-8"):"";m.put(k,v);}return m;}
    private JSONObject jsonError(String msg) throws Exception {JSONObject o=new JSONObject();o.put("error",msg);return o;}
    private String readLine(InputStream in) throws IOException {ByteArrayOutputStream b=new ByteArrayOutputStream();int c;boolean seen=false;while((c=in.read())!=-1){seen=true;if(c=='\n')break;if(c!='\r')b.write(c);}return !seen&&b.size()==0?null:b.toString(StandardCharsets.UTF_8.name());}
    private void send(Socket s,int code,JSONObject obj) throws IOException {byte[] data=obj.toString().getBytes(StandardCharsets.UTF_8);String status=code==200?"OK":code==400?"Bad Request":code==401?"Unauthorized":code==403?"Forbidden":code==404?"Not Found":code==413?"Payload Too Large":"Error";String h="HTTP/1.1 "+code+" "+status+"\r\nContent-Type: application/json; charset=utf-8\r\nContent-Length: "+data.length+"\r\nConnection: close\r\nCache-Control: no-store\r\n\r\n";OutputStream out=s.getOutputStream();out.write(h.getBytes(StandardCharsets.UTF_8));out.write(data);out.flush();}

    private void createChannel(){if(android.os.Build.VERSION.SDK_INT>=26){NotificationChannel c=new NotificationChannel("iptv_server","Servidor IPTV",NotificationManager.IMPORTANCE_LOW);getSystemService(NotificationManager.class).createNotificationChannel(c);}}
    private Notification notification(String text){Notification.Builder b=android.os.Build.VERSION.SDK_INT>=26?new Notification.Builder(this,"iptv_server"):new Notification.Builder(this);Intent i=new Intent(this,MainActivity.class);PendingIntent pi=PendingIntent.getActivity(this,0,i,PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);return b.setContentTitle("Diniz IPTV Admin").setContentText(text).setSmallIcon(android.R.drawable.stat_sys_upload_done).setContentIntent(pi).setOngoing(true).build();}

    @Override public void onDestroy(){running=false;appRunning=false;try{if(server!=null)server.close();}catch(Exception ignored){}try{if(discoverySocket!=null)discoverySocket.close();}catch(Exception ignored){}if(pool!=null)pool.shutdownNow();super.onDestroy();}
    @Override public IBinder onBind(Intent intent){return null;}
}
