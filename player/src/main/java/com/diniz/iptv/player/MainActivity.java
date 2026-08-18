package com.diniz.iptv.player;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.*;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.TreeSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private LinearLayout root;
    private SharedPreferences prefs;
    private EditText search;

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        prefs = getSharedPreferences("player", MODE_PRIVATE);
        showLogin(null);
    }

    private int dp(int v){ return Math.round(v * getResources().getDisplayMetrics().density); }
    private TextView text(String s, int sp) { TextView t = new TextView(this); t.setText(s); t.setTextColor(Color.WHITE); t.setTextSize(sp); return t; }
    private GradientDrawable bg(int color, int radius) { GradientDrawable g=new GradientDrawable(); g.setColor(color); g.setCornerRadius(dp(radius)); return g; }

    private void showLogin(String initialStatus) {
        ScrollView scroll = new ScrollView(this);
        root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(dp(48),dp(42),dp(48),dp(30)); root.setBackgroundColor(Color.rgb(11,14,20)); scroll.addView(root);
        TextView logo = text("DINIZ IPTV", 30); logo.setGravity(Gravity.CENTER); logo.setTypeface(null,1); root.addView(logo,new LinearLayout.LayoutParams(-1,dp(64)));
        TextView sub = text("Player para TV Box",18); sub.setTextColor(Color.rgb(170,178,192)); sub.setGravity(Gravity.CENTER); root.addView(sub,new LinearLayout.LayoutParams(-1,dp(42)));

        EditText server = field("Servidor do Admin");
        server.setText(prefs.getString("server", ""));
        EditText user = field("Usuário"); user.setText(prefs.getString("user", ""));
        EditText pass = field("Senha"); pass.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);
        Button discover = secondaryButton("LOCALIZAR ADMIN NA REDE");
        Button login = button("ENTRAR");
        ProgressBar progress = new ProgressBar(this); progress.setVisibility(android.view.View.GONE); root.addView(progress);
        TextView status = text(initialStatus == null ? "" : initialStatus,15); status.setTextColor(Color.rgb(255,190,190)); status.setGravity(Gravity.CENTER); root.addView(status,new LinearLayout.LayoutParams(-1,dp(54)));

        discover.setOnClickListener(v -> {
            discover.setEnabled(false); login.setEnabled(false); progress.setVisibility(android.view.View.VISIBLE); status.setText("Procurando o Diniz IPTV Admin na rede...");
            io.execute(() -> {
                try {
                    String found = AdminDiscovery.discover(4500);
                    runOnUiThread(() -> {
                        discover.setEnabled(true); login.setEnabled(true); progress.setVisibility(android.view.View.GONE);
                        if (found != null) { server.setText(found); prefs.edit().putString("server",found).apply(); status.setText("Admin localizado: " + found); }
                        else status.setText("Admin não encontrado. Confirme que o servidor está ativo e os dois aparelhos estão na mesma rede.");
                    });
                } catch (Exception e) { runOnUiThread(() -> { discover.setEnabled(true); login.setEnabled(true); progress.setVisibility(android.view.View.GONE); status.setText("Falha na busca: " + e.getMessage()); }); }
            });
        });

        login.setOnClickListener(v -> {
            String base = normalize(server.getText().toString());
            String u = user.getText().toString().trim(), p = pass.getText().toString();
            if (base.isEmpty() || u.isEmpty() || p.isEmpty()) { status.setText("Preencha servidor, usuário e senha."); return; }
            login.setEnabled(false); discover.setEnabled(false); progress.setVisibility(android.view.View.VISIBLE); status.setText("Conectando...");
            io.execute(() -> {
                try {
                    JSONObject req=new JSONObject(); req.put("username",u); req.put("password",p);
                    JSONObject r=Net.postJson(base+"/api/login",req);
                    String token=r.getString("token");
                    prefs.edit().putString("server",base).putString("token",token).putString("user",u).apply();
                    loadCatalog(base, token);
                } catch(Exception e){ runOnUiThread(() -> {login.setEnabled(true);discover.setEnabled(true);progress.setVisibility(android.view.View.GONE);status.setText(e.getMessage());}); }
            });
        });
        pass.setOnEditorActionListener((v,action,event) -> { login.performClick(); return true; });
        setContentView(scroll);
        if (server.getText().toString().trim().isEmpty()) discover.requestFocus(); else user.requestFocus();
    }

    private EditText field(String hint) {
        EditText e=new EditText(this); e.setHint(hint); e.setHintTextColor(Color.rgb(150,158,172)); e.setTextColor(Color.WHITE);
        e.setSingleLine(true); e.setPadding(dp(18),0,dp(18),0); e.setBackground(bg(Color.rgb(21,26,35),10)); e.setFocusable(true);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(Math.min(dp(650), getResources().getDisplayMetrics().widthPixels-dp(140)),dp(62)); lp.setMargins(0,dp(8),0,dp(8)); root.addView(e,lp); return e;
    }
    private Button button(String label){ Button b=new Button(this); b.setText(label); b.setTextColor(Color.WHITE); b.setBackground(bg(Color.rgb(124,77,255),10)); b.setFocusable(true); LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(dp(340),dp(60)); lp.setMargins(0,dp(12),0,dp(7)); root.addView(b,lp); return b; }
    private Button secondaryButton(String label){ Button b=button(label); b.setBackground(bg(Color.rgb(35,43,57),10)); return b; }
    private String normalize(String s){ s=s.trim(); while(s.endsWith("/")) s=s.substring(0,s.length()-1); if(!s.isEmpty()&&!s.startsWith("http://")&&!s.startsWith("https://")) s="http://"+s; return s; }

    private void loadCatalog(String base,String token) throws Exception {
        JSONObject r=Net.getJson(base+"/api/catalog?token="+java.net.URLEncoder.encode(token,"UTF-8"));
        JSONArray a=r.getJSONArray("items"); CatalogStore.ITEMS.clear();
        for(int i=0;i<a.length();i++) CatalogStore.ITEMS.add(ContentItem.fromJson(a.getJSONObject(i)));
        runOnUiThread(this::showHome);
    }

    private void showHome() {
        LinearLayout page=new LinearLayout(this); page.setOrientation(LinearLayout.VERTICAL); page.setBackgroundColor(Color.rgb(11,14,20)); page.setPadding(dp(30),dp(18),dp(30),dp(18));
        LinearLayout top=new LinearLayout(this); top.setGravity(Gravity.CENTER_VERTICAL); page.addView(top,new LinearLayout.LayoutParams(-1,dp(70)));
        TextView brand=text("DINIZ IPTV",24); brand.setTypeface(null,1); top.addView(brand,new LinearLayout.LayoutParams(0,-2,1));
        search=new EditText(this); search.setHint("Pesquisar filmes e séries"); search.setHintTextColor(Color.rgb(150,158,172)); search.setTextColor(Color.WHITE); search.setSingleLine(true); search.setCompoundDrawablesWithIntrinsicBounds(android.R.drawable.ic_menu_search,0,0,0); search.setCompoundDrawablePadding(dp(12)); search.setBackground(bg(Color.rgb(21,26,35),12)); search.setPadding(dp(16),0,dp(16),0); search.setFocusable(true); top.addView(search,new LinearLayout.LayoutParams(dp(440),dp(54)));
        Button refresh=new Button(this); refresh.setText("↻"); refresh.setContentDescription("Atualizar catálogo"); refresh.setTextSize(22); refresh.setTextColor(Color.WHITE); refresh.setFocusable(true); refresh.setBackground(bg(Color.rgb(21,26,35),12)); LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(dp(64),dp(54)); rp.setMargins(dp(12),0,0,0); top.addView(refresh,rp);
        ScrollView scroll=new ScrollView(this); LinearLayout rows=new LinearLayout(this); rows.setOrientation(LinearLayout.VERTICAL); rows.setPadding(0,dp(10),0,dp(40)); scroll.addView(rows); page.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        Runnable render=()-> renderRows(rows, search.getText().toString()); render.run();
        search.addTextChangedListener(new TextWatcher(){@Override public void beforeTextChanged(CharSequence s,int st,int c,int a){}@Override public void onTextChanged(CharSequence s,int st,int b,int c){render.run();}@Override public void afterTextChanged(Editable e){}});
        refresh.setOnClickListener(v->{ String base=prefs.getString("server","");String token=prefs.getString("token",""); Toast.makeText(this,"Atualizando catálogo...",Toast.LENGTH_SHORT).show(); io.execute(()->{try{loadCatalog(base,token);}catch(Exception e){runOnUiThread(()->{Toast.makeText(this,e.getMessage(),Toast.LENGTH_LONG).show(); if(e.getMessage()!=null && e.getMessage().toLowerCase(Locale.ROOT).contains("sessão")) showLogin("Faça login novamente.");});}}); });
        setContentView(page);
    }

    private void renderRows(LinearLayout rows, String query) {
        rows.removeAllViews();
        String q=query==null?"":query.trim().toLowerCase(Locale.ROOT);
        List<ContentItem> filtered=new ArrayList<>();
        for(ContentItem x:CatalogStore.ITEMS){ String hay=(x.title+" "+x.episodeTitle+" "+x.genres).toLowerCase(Locale.ROOT); if(q.isEmpty()||hay.contains(q)) filtered.add(x); }
        addRow(rows,"Filmes",uniqueCards(filtered,"movie",null));
        addRow(rows,"Séries",uniqueCards(filtered,"series",null));
        TreeSet<String> genres=new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for(ContentItem x:filtered) if(x.genres!=null) for(String g:x.genres.split(",")) if(!g.trim().isEmpty()) genres.add(g.trim());
        for(String g:genres) addRow(rows,g,uniqueCards(filtered,null,g));
        if(filtered.isEmpty()){ TextView e=text("Nenhum título encontrado.",20); e.setTextColor(Color.rgb(170,178,192)); rows.addView(e); }
    }

    private List<ContentItem> uniqueCards(List<ContentItem> in,String type,String genre){
        LinkedHashMap<String,ContentItem> map=new LinkedHashMap<>();
        for(ContentItem x:in){
            if(type!=null&&!type.equals(x.type)) continue;
            if(genre!=null && (x.genres==null || !Arrays.asList(x.genres.split("\\s*,\\s*")).contains(genre))) continue;
            String key=x.type.equals("series")?"series:"+x.title.toLowerCase(Locale.ROOT):"movie:"+x.id;
            map.putIfAbsent(key,x);
        } return new ArrayList<>(map.values());
    }

    private void addRow(LinearLayout rows,String title,List<ContentItem> items){
        if(items.isEmpty()) return;
        TextView h=text(title,22); h.setTypeface(null,1); LinearLayout.LayoutParams hp=new LinearLayout.LayoutParams(-1,dp(48)); hp.setMargins(0,dp(12),0,0); rows.addView(h,hp);
        HorizontalScrollView hsv=new HorizontalScrollView(this); hsv.setHorizontalScrollBarEnabled(false); LinearLayout line=new LinearLayout(this); line.setOrientation(LinearLayout.HORIZONTAL); hsv.addView(line); rows.addView(hsv,new LinearLayout.LayoutParams(-1,dp(330)));
        for(ContentItem x:items) line.addView(card(x));
    }

    private android.view.View card(ContentItem x){
        LinearLayout c=new LinearLayout(this); c.setOrientation(LinearLayout.VERTICAL); c.setFocusable(true); c.setClickable(true); c.setPadding(dp(7),dp(7),dp(7),dp(7)); c.setBackground(bg(Color.rgb(21,26,35),12));
        LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(dp(188),dp(306)); cp.setMargins(0,0,dp(14),0); c.setLayoutParams(cp);
        ImageView poster=new ImageView(this); poster.setScaleType(ImageView.ScaleType.CENTER_CROP); poster.setBackgroundColor(Color.rgb(32,38,48)); c.addView(poster,new LinearLayout.LayoutParams(-1,dp(246))); ImageLoader.load(poster,x.posterUrl);
        TextView t=text(x.displayTitle(),15); t.setMaxLines(2); t.setGravity(Gravity.CENTER_VERTICAL); t.setPadding(dp(4),dp(4),dp(4),0); c.addView(t,new LinearLayout.LayoutParams(-1,0,1));
        c.setOnFocusChangeListener((v,has)->v.animate().scaleX(has?1.06f:1f).scaleY(has?1.06f:1f).setDuration(120).start());
        c.setOnClickListener(v->{ Intent i=new Intent(this,DetailActivity.class); i.putExtra("id",x.id); i.putExtra("series",x.type.equals("series")); i.putExtra("title",x.title); startActivity(i); });
        return c;
    }
}
