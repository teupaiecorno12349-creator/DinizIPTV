package com.diniz.orsayinstaller;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.storage.StorageManager;
import android.os.storage.StorageVolume;
import android.provider.DocumentsContract;
import android.view.Gravity;
import android.view.View;
import android.webkit.URLUtil;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.Inet4Address;
import java.net.NetworkInterface;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends Activity implements OrsayServer.Listener {
    private static final int REQ_TREE = 1001;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final List<WidgetEntry> widgets = new ArrayList<>();

    private TextView tvUsb;
    private TextView tvFolder;
    private TextView tvIp;
    private TextView tvServer;
    private TextView tvStatus;
    private TextView tvWidgets;
    private Spinner portSpinner;
    private Button btnChoose;
    private Button btnScan;
    private Button btnStart;
    private Button btnStop;
    private OrsayServer server;
    private Uri treeUri;
    private boolean redirect80;
    private SharedPreferences prefs;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = getSharedPreferences("state", MODE_PRIVATE);
        if (prefs.contains("treeUri")) {
            try { treeUri = Uri.parse(prefs.getString("treeUri", null)); } catch (Exception ignored) {}
        }
        buildUi();
        refreshUsbStatus();
        refreshIp();
        if (treeUri != null) scanWidgets();
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(18), dp(18), dp(18));
        scroll.addView(root);

        TextView title = text("Samsung Orsay Widget Installer", 24, true);
        root.addView(title);

        TextView subtitle = text("Pendrive → telefone → rede → Samsung Orsay", 15, false);
        root.addView(subtitle);

        tvUsb = text("", 16, true); root.addView(tvUsb);
        tvFolder = text("Pasta: não selecionada", 14, false); root.addView(tvFolder);
        tvIp = text("IP do celular: procurando…", 16, true); root.addView(tvIp);
        tvServer = text("Servidor: parado", 16, true); root.addView(tvServer);
        tvStatus = text("Pronto.", 14, false); root.addView(tvStatus);
        tvWidgets = text("Widgets encontrados: 0", 14, true); root.addView(tvWidgets);

        btnChoose = button("1. Escolher pendrive / pasta dos widgets");
        root.addView(btnChoose);
        btnChoose.setOnClickListener(v -> chooseFolder());

        btnScan = button("2. Procurar widgets novamente");
        root.addView(btnScan);
        btnScan.setOnClickListener(v -> scanWidgets());

        root.addView(text("Porta para o servidor", 15, true));
        portSpinner = new Spinner(this);
        ArrayAdapter<String> ad = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item,
                new String[]{"80 (Orsay)", "8080 (teste do telefone)"});
        ad.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        portSpinner.setAdapter(ad);
        root.addView(portSpinner);

        btnStart = button("3. Iniciar servidor");
        btnStop = button("Parar servidor");
        btnStop.setEnabled(false);
        root.addView(btnStart);
        root.addView(btnStop);

        btnStart.setOnClickListener(v -> startServer());
        btnStop.setOnClickListener(v -> stopServer());

        Button btnTest = button("Abrir widgetlist.xml no telefone");
        root.addView(btnTest);
        btnTest.setOnClickListener(v -> testInBrowser());

        TextView help = text(
                "Na TV Orsay: entre como develop, abra o menu de desenvolvimento, coloque o IP deste celular em “Setting Server IP” e execute “User Application Synchronization”. " +
                "O Orsay normalmente busca o servidor HTTP na porta 80. Em Android 10 sem root, a porta 80 é bloqueada pelo sistema; o app tentará usar root/redirect quando disponível. " +
                "Mantenha o telefone e a TV na mesma rede Wi‑Fi.",
                14, false);
        help.setPadding(0, dp(18), 0, dp(18));
        root.addView(help);

        setContentView(scroll);
    }

    private void chooseFolder() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
        startActivityForResult(intent, REQ_TREE);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_TREE || resultCode != RESULT_OK || data == null) return;
        Uri uri = data.getData();
        if (uri == null) return;
        try {
            getContentResolver().takePersistableUriPermission(uri, data.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (Exception ignored) {}
        treeUri = uri;
        prefs.edit().putString("treeUri", uri.toString()).apply();
        tvFolder.setText("Pasta selecionada: " + uri);
        scanWidgets();
    }

    private void refreshUsbStatus() {
        boolean removable = false;
        String label = "Nenhum volume removível detectado";
        try {
            StorageManager sm = (StorageManager) getSystemService(STORAGE_SERVICE);
            for (StorageVolume v : sm.getStorageVolumes()) {
                if (v.isRemovable() && Environment.MEDIA_MOUNTED.equals(v.getState())) {
                    removable = true;
                    label = "Pendrive/armazenamento removível detectado: " + v.getDescription(this);
                    break;
                }
            }
        } catch (Exception ignored) {}
        tvUsb.setText(removable ? "USB: " + label : "USB: " + label);
    }

    private void refreshIp() {
        worker.execute(() -> {
            final String ip = getLocalIpv4();
            runOnUiThread(() -> tvIp.setText("IP do celular: " + (ip == null ? "não encontrado" : ip)));
        });
    }

    private String getLocalIpv4() {
        try {
            Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces();
            String fallback = null;
            while (en != null && en.hasMoreElements()) {
                NetworkInterface ni = en.nextElement();
                if (!ni.isUp() || ni.isLoopback()) continue;
                String n = ni.getName() == null ? "" : ni.getName().toLowerCase(Locale.US);
                Enumeration<java.net.InetAddress> addrs = ni.getInetAddresses();
                while (addrs.hasMoreElements()) {
                    java.net.InetAddress a = addrs.nextElement();
                    if (!(a instanceof Inet4Address) || a.isLoopbackAddress() || a.isLinkLocalAddress()) continue;
                    String s = a.getHostAddress();
                    if (s == null) continue;
                    if (n.contains("wlan") || n.contains("wifi") || n.contains("ap")) return s;
                    if (fallback == null) fallback = s;
                }
            }
            return fallback;
        } catch (Exception e) {
            return null;
        }
    }

    private void scanWidgets() {
        if (treeUri == null) {
            toast("Escolha primeiro a pasta do pendrive.");
            return;
        }
        tvStatus.setText("Procurando .zip/.wgt no pendrive…");
        worker.execute(() -> {
            List<WidgetEntry> found = new ArrayList<>();
            try {
                scanDocumentTree(treeUri, found, new HashSet<String>());
                Collections.sort(found, (a,b) -> a.name.compareToIgnoreCase(b.name));
                synchronized (widgets) {
                    widgets.clear();
                    widgets.addAll(found);
                }
                runOnUiThread(() -> {
                    tvFolder.setText("Pasta selecionada: " + treeUri);
                    tvWidgets.setText("Widgets encontrados: " + found.size());
                    tvStatus.setText(found.isEmpty()
                            ? "Nenhum .zip/.wgt encontrado."
                            : "Pronto para publicar os widgets.");
                });
            } catch (Exception e) {
                runOnUiThread(() -> tvStatus.setText("Erro ao ler o pendrive: " + e.getMessage()));
            }
        });
    }

    private void scanDocumentTree(Uri root, List<WidgetEntry> out, Set<String> visited) {
        String docId = DocumentsContract.getTreeDocumentId(root);
        scanDocument(root, docId, out, visited, 0);
    }

    private void scanDocument(Uri tree, String docId, List<WidgetEntry> out, Set<String> visited, int depth) {
        if (depth > 8 || docId == null || !visited.add(docId)) return;
        Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, docId);
        String[] cols = new String[]{
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
                DocumentsContract.Document.COLUMN_SIZE
        };
        try (android.database.Cursor c = getContentResolver().query(children, cols, null, null, null)) {
            if (c == null) return;
            int idCol = c.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID);
            int nameCol = c.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME);
            int mimeCol = c.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE);
            int sizeCol = c.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE);
            while (c.moveToNext()) {
                String childId = idCol >= 0 ? c.getString(idCol) : null;
                String name = nameCol >= 0 ? c.getString(nameCol) : "";
                String mime = mimeCol >= 0 ? c.getString(mimeCol) : "";
                long size = sizeCol >= 0 && !c.isNull(sizeCol) ? c.getLong(sizeCol) : 0L;
                if (DocumentsContract.Document.MIME_TYPE_DIR.equals(mime)) {
                    scanDocument(tree, childId, out, visited, depth + 1);
                } else if (isWidget(name)) {
                    Uri fileUri = DocumentsContract.buildDocumentUriUsingTree(tree, childId);
                    out.add(new WidgetEntry(fileUri, name, size));
                }
            }
        }
    }

    private boolean isWidget(String name) {
        String n = name == null ? "" : name.toLowerCase(Locale.US);
        return n.endsWith(".zip") || n.endsWith(".wgt");
    }

    private void startServer() {
        if (server != null && server.isRunning()) {
            toast("O servidor já está ligado.");
            return;
        }
        List<WidgetEntry> copy;
        synchronized (widgets) { copy = new ArrayList<>(widgets); }
        if (copy.isEmpty()) {
            toast("Nenhum widget foi encontrado.");
            return;
        }
        final String ip = getLocalIpv4();
        if (ip == null) {
            tvStatus.setText("Sem IPv4 local. Conecte o celular à mesma rede da TV.");
            return;
        }
        final int requested = portSpinner.getSelectedItemPosition() == 0 ? 80 : 8080;
        server = new OrsayServer(getContentResolver(), this);

        if (requested == 80) {
            worker.execute(() -> startRequested80(ip, copy));
        } else {
            worker.execute(() -> startOn8080(ip, copy, false));
        }
    }

    private void startRequested80(String ip, List<WidgetEntry> copy) {
        try {
            server.start(80, "http://" + ip, copy);
            runOnUiThread(() -> markServerRunning("http://" + ip, "Porta 80 aberta diretamente."));
            return;
        } catch (Exception directFail) {
            runOnUiThread(() -> tvStatus.setText("Android bloqueou a porta 80 diretamente. Tentando root/redirect…"));
        }

        if (hasRoot()) {
            try {
                server.start(8080, "http://" + ip, copy);
                if (enableRedirect80()) {
                    redirect80 = true;
                    runOnUiThread(() -> markServerRunning("http://" + ip,
                            "Servidor em 8080 + redirect 80→8080 com root."));
                    return;
                }
                server.stop();
            } catch (Exception e) {
                // Continue to non-root diagnostic below.
            }
        }

        try {
            server.start(8080, "http://" + ip + ":8080", copy);
            runOnUiThread(() -> {
                markServerRunning("http://" + ip + ":8080", "Porta 80 não disponível sem root. 8080 funciona para teste, mas o User Sync do Orsay normalmente exige porta 80.");
            });
        } catch (Exception e) {
            runOnUiThread(() -> tvStatus.setText("Não foi possível iniciar o servidor: " + e.getMessage()));
        }
    }

    private void startOn8080(String ip, List<WidgetEntry> copy, boolean unused) {
        try {
            server.start(8080, "http://" + ip + ":8080", copy);
            runOnUiThread(() -> markServerRunning("http://" + ip + ":8080", "Modo de teste."));
        } catch (Exception e) {
            runOnUiThread(() -> tvStatus.setText("Não foi possível iniciar o servidor: " + e.getMessage()));
        }
    }

    private void markServerRunning(String url, String detail) {
        tvServer.setText("Servidor: " + url);
        tvStatus.setText(detail);
        btnStart.setEnabled(false);
        btnStop.setEnabled(true);
    }

    private void stopServer() {
        if (server != null) server.stop();
        worker.execute(() -> {
            if (redirect80) {
                disableRedirect80();
                redirect80 = false;
            }
        });
        btnStart.setEnabled(true);
        btnStop.setEnabled(false);
        tvServer.setText("Servidor: parado");
        tvStatus.setText("Servidor encerrado.");
    }

    private void testInBrowser() {
        String ip = getLocalIpv4();
        if (ip == null) { toast("Não encontrei o IP do celular."); return; }
        int port = server != null && server.isRunning() ? server.getPort() : 8080;
        String url = "http://" + ip + (port == 80 ? "" : ":" + port) + "/widgetlist.xml";
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception e) {
            toast(url);
        }
    }

    private boolean hasRoot() {
        try {
            Process p = Runtime.getRuntime().exec(new String[]{"su", "-c", "id"});
            int code = p.waitFor();
            return code == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean enableRedirect80() {
        return runRoot("iptables -t nat -D PREROUTING -p tcp --dport 80 -j REDIRECT --to-ports 8080 >/dev/null 2>&1 || true; " +
                "iptables -t nat -A PREROUTING -p tcp --dport 80 -j REDIRECT --to-ports 8080");
    }

    private boolean disableRedirect80() {
        return runRoot("iptables -t nat -D PREROUTING -p tcp --dport 80 -j REDIRECT --to-ports 8080 >/dev/null 2>&1 || true");
    }

    private boolean runRoot(String command) {
        try {
            Process p = Runtime.getRuntime().exec(new String[]{"su", "-c", command});
            return p.waitFor() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    @Override public void onLog(String line) {
        runOnUiThread(() -> tvStatus.setText(line));
    }

    @Override public void onStopped() {
        runOnUiThread(() -> {
            if (server != null && !server.isRunning()) {
                tvServer.setText("Servidor: parado");
                btnStart.setEnabled(true);
                btnStop.setEnabled(false);
            }
        });
    }

    @Override protected void onResume() {
        super.onResume();
        refreshUsbStatus();
        refreshIp();
    }

    @Override protected void onDestroy() {
        try {
            if (server != null) server.stop();
            if (redirect80) disableRedirect80();
            worker.shutdownNow();
        } catch (Exception ignored) {}
        super.onDestroy();
    }

    private Button button(String label) {
        Button b = new Button(this);
        b.setText(label);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        p.topMargin = dp(8); p.bottomMargin = dp(4);
        b.setLayoutParams(p);
        return b;
    }

    private TextView text(String value, int sp, boolean bold) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(sp);
        t.setTextColor(0xFF111111);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        t.setPadding(0, dp(6), 0, dp(6));
        return t;
    }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_LONG).show(); }
}
