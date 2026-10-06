package com.diniz.orsayinstaller;

import android.content.ContentResolver;
import android.net.Uri;
import android.text.TextUtils;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class OrsayServer {
    public interface Listener {
        void onLog(String line);
        void onStopped();
    }

    private final ContentResolver resolver;
    private final Listener listener;
    private final ExecutorService clients = Executors.newCachedThreadPool();
    private final List<WidgetEntry> widgets = new ArrayList<>();
    private final Object lock = new Object();
    private volatile boolean running;
    private volatile ServerSocket serverSocket;
    private Thread acceptThread;
    private int port;
    private String publicBaseUrl = "";

    public OrsayServer(ContentResolver resolver, Listener listener) {
        this.resolver = resolver;
        this.listener = listener;
    }

    public synchronized void start(int port, String baseUrl, List<WidgetEntry> entries) throws IOException {
        if (running) throw new IllegalStateException("Servidor já está em execução.");
        this.port = port;
        this.publicBaseUrl = baseUrl;
        synchronized (lock) {
            widgets.clear();
            widgets.addAll(entries);
        }
        ServerSocket ss = new ServerSocket();
        ss.setReuseAddress(true);
        ss.bind(new java.net.InetSocketAddress(port));
        serverSocket = ss;
        running = true;
        acceptThread = new Thread(() -> acceptLoop(), "OrsayAccept");
        acceptThread.start();
        log("Servidor iniciado em " + baseUrl);
        log("Widgets publicados: " + entries.size());
    }

    public synchronized void stop() {
        running = false;
        ServerSocket ss = serverSocket;
        serverSocket = null;
        if (ss != null) {
            try { ss.close(); } catch (IOException ignored) {}
        }
        if (listener != null) listener.onStopped();
    }

    public boolean isRunning() { return running; }
    public int getPort() { return port; }

    private void acceptLoop() {
        try {
            while (running) {
                final Socket socket = serverSocket.accept();
                clients.submit(() -> handle(socket));
            }
        } catch (IOException ignored) {
        } finally {
            running = false;
            if (listener != null) listener.onStopped();
        }
    }

    private void handle(Socket socket) {
        try (Socket s = socket) {
            s.setSoTimeout(15000);
            BufferedInputStream in = new BufferedInputStream(s.getInputStream());
            BufferedOutputStream out = new BufferedOutputStream(s.getOutputStream());

            String requestLine = readLine(in);
            if (TextUtils.isEmpty(requestLine)) return;
            String[] parts = requestLine.split(" ");
            if (parts.length < 2) return;
            String method = parts[0].toUpperCase(Locale.US);
            String rawPath = parts[1];

            // Consume request headers.
            String line;
            do { line = readLine(in); } while (line != null && line.length() > 0);

            String path = rawPath;
            int q = path.indexOf('?');
            if (q >= 0) path = path.substring(0, q);
            String decoded = URLDecoder.decode(path, StandardCharsets.UTF_8.name());

            if ("/".equals(decoded) || "/index.html".equals(decoded)) {
                byte[] body = htmlPage().getBytes(StandardCharsets.UTF_8);
                writeHeaders(out, 200, "text/html; charset=utf-8", body.length);
                if (!"HEAD".equals(method)) out.write(body);
                out.flush();
                return;
            }

            if ("/widgetlist.xml".equalsIgnoreCase(decoded)) {
                byte[] body = buildWidgetList().getBytes(StandardCharsets.UTF_8);
                writeHeaders(out, 200, "text/xml; charset=utf-8", body.length);
                if (!"HEAD".equals(method)) out.write(body);
                out.flush();
                return;
            }

            if (decoded.startsWith("/Widget/")) {
                String name = decoded.substring("/Widget/".length());
                WidgetEntry entry = findByName(name);
                if (entry == null) {
                    writeHeaders(out, 404, "text/plain; charset=utf-8", 9);
                    if (!"HEAD".equals(method)) out.write("Not found".getBytes(StandardCharsets.UTF_8));
                    out.flush();
                    return;
                }
                writeHeaders(out, 200, "application/zip", entry.size);
                if (!"HEAD".equals(method)) stream(entry, out);
                out.flush();
                return;
            }

            writeHeaders(out, 404, "text/plain; charset=utf-8", 9);
            if (!"HEAD".equals(method)) out.write("Not found".getBytes(StandardCharsets.UTF_8));
            out.flush();
        } catch (Exception e) {
            log("Falha HTTP: " + e.getMessage());
        }
    }

    private WidgetEntry findByName(String name) {
        synchronized (lock) {
            for (WidgetEntry e : widgets) if (e.name.equals(name)) return e;
        }
        return null;
    }

    private void stream(WidgetEntry entry, OutputStream out) throws IOException {
        try (InputStream raw = resolver.openInputStream(entry.uri)) {
            if (raw == null) throw new IOException("Não foi possível abrir o arquivo " + entry.name);
            byte[] buffer = new byte[32 * 1024];
            int n;
            while ((n = raw.read(buffer)) != -1) out.write(buffer, 0, n);
        }
    }

    private String buildWidgetList() {
        StringBuilder x = new StringBuilder(512);
        x.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
        x.append("<rsp stat=\"ok\"><list>");
        List<WidgetEntry> copy;
        synchronized (lock) { copy = new ArrayList<>(widgets); }
        for (WidgetEntry e : copy) {
            x.append("<widget id=\"").append(xml(e.id())).append("\">");
            x.append("<title>").append(xml(e.name)).append("</title>");
            x.append("<compression type=\"zip\" size=\"").append(e.size).append("\"/>");
            x.append("<description>Samsung Orsay - instalação local</description>");
            x.append("<download>").append(xml(publicBaseUrl + "/Widget/" + Uri.encode(e.name))).append("</download>");
            x.append("</widget>");
        }
        x.append("</list></rsp>");
        return x.toString();
    }

    private String htmlPage() {
        List<WidgetEntry> copy;
        synchronized (lock) { copy = new ArrayList<>(widgets); }
        StringBuilder h = new StringBuilder();
        h.append("<html><head><meta charset=\"utf-8\"><title>Orsay Installer</title></head><body>");
        h.append("<h2>Samsung Orsay Widget Installer</h2>");
        h.append("<p>Servidor: ").append(xml(publicBaseUrl)).append("</p>");
        h.append("<p>widgetlist.xml: <a href=\"/widgetlist.xml\">abrir</a></p><ul>");
        for (WidgetEntry e : copy) {
            h.append("<li>").append(xml(e.name)).append(" (").append(e.size).append(" bytes)</li>");
        }
        h.append("</ul></body></html>");
        return h.toString();
    }

    private static String xml(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    private static String readLine(InputStream in) throws IOException {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        int c;
        while ((c = in.read()) != -1) {
            if (c == '\n') break;
            if (c != '\r') b.write(c);
            if (b.size() > 8192) throw new IOException("Header too large");
        }
        if (c == -1 && b.size() == 0) return null;
        return b.toString(StandardCharsets.ISO_8859_1.name());
    }

    private static void writeHeaders(OutputStream out, int status, String type, long length) throws IOException {
        String reason = status == 200 ? "OK" : status == 404 ? "Not Found" : "Error";
        String headers = "HTTP/1.1 " + status + " " + reason + "\r\n"
                + "Content-Type: " + type + "\r\n"
                + "Content-Length: " + length + "\r\n"
                + "Connection: close\r\n"
                + "Cache-Control: no-store\r\n\r\n";
        out.write(headers.getBytes(StandardCharsets.ISO_8859_1));
    }

    private void log(String s) {
        if (listener != null) listener.onLog(s);
    }
}
