package com.diniz.iptv.player;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.*;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.*;
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
        showLogin();
    }

    private int dp(int v){ return Math.round(v * getResources().getDisplayMetrics().density); }
    private TextView text(String s, int sp) {
        TextView t = new TextView(this); t.setText(s); t.setTextColor(Color.WHITE); t.setTextSize(sp); return t;
    }
    private GradientDrawable bg(int color, int radius) { GradientDrawable g=new GradientDrawable(); g.setColor(color); g.setCornerRadius(dp(radius)); return g; }

    private void showLogin() {
        ScrollView scroll = new ScrollView(this);
        root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(dp(48),dp(50),dp(48),dp(30)); root.setBackgroundColor(Color.rgb(11,14,20)); scroll.addView(root);
        TextView logo = text("DINIZ IPTV", 30); logo.setGravity(Gravity.CENTER); root.addView(logo,new LinearLayout.LayoutParams(-1,dp(70)));
        TextView sub = text("Player para TV Box",18); sub.setTextColor(Color.rgb(170,178,192)); sub.setGravity(Gravity.CENTER); root.addView(sub,new LinearLayout.LayoutParams(-1,dp(48)));

        EditText server = field("Servidor (ex.: http://192.168.1.20:8787)");
        server.setText(prefs.getString("server", "http://192.168.1.20:8787"));
        EditText user = field("Usuário");
        EditText pass = field("Senha"); pass.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);
        Button login = button("ENTRAR");
        ProgressBar progress = new ProgressBar(this); progress.setVisibility(View.GONE); root.addView(progress);
        TextView status = text("",15); status.setTextColor(Color.rgb(255,180,180)); status.setGravity(Gravity.CENTER); root.addView(status,new LinearLayout.LayoutParams(-1,dp(45)));
        login.setOnClickListener(v -> {
            String base = normalize(server.getText().toString());
            String u = user.getText().toString().trim(), p = pass.getText().toString();
            if (base.isEmpty() || u.isEmpty() || p.isEmpty()) { status.setText("Preencha servidor, usuário e senha."); return; }
            login.setEnabled(false); progress.setVisibility(View.VISIBLE); status.setText("Conectando...");
            io.execute(() -> {
                try {
                    JSONObject req=new JSONObject(); req.put("username",u); req.put("password",p);
                    JSONObject r=Net.postJson(base+"/api/login",req);
                    String token=r.getString("token");
                    prefs.edit().putString("server",base).putString("token",token).apply();
                    loadCatalog(base, token);
                } catch(Exception e){ runOnUiThread(() -> {login.setEnabled(true);progress.setVisibility(View.GONE);status.setText(e.getMessage());}); }
            });
        });
        setContentView(scroll);
    }

    private EditText field(String hint) {
        EditText e=new EditText(this); e.setHint(hint); e.setHintTextColor(Color.rgb(150,158,172)); e.setTextColor(Color.WHITE);
        e.setSingleLine(true); e.setPadding(dp(18),0,dp(18),0); e.setBackground(bg(Color.rgb(21,26,35),10));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(Math.min(dp(650), getResources().getDisplayMetrics().widthPixels-dp(140)),dp(62)); lp.setMargins(0,dp(9),0,dp(9)); root.addView(e,lp); return e;
    }
    private Button button(String label){ Button b=new Button(this); b.setText(label); b.setTextColor(Color.WHITE); b.setBackground(bg(Color.rgb(124,77,255),10)); b.setFocusable(true); LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(dp(300),dp(60)); lp.setMargins(0,dp(14),0,dp(8)); root.addView(b,lp); return b; }
    private String normalize(String s){ s=s.trim(); while(s.endsWith("/")) s=s.substring(0,s.length()-1); return s; }

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
        search=new EditText(this); search.setHint("Pesquisar filmes e séries"); search.setHintTextColor(Color.rgb(150,158,172)); search.setTextColor(Color.WHITE); search.setSingleLine(true); search.setCompoundDrawablesWithIntrinsicBounds(android.R.drawable.ic_menu_search,0,0,0); search.setCompoundDrawablePadding(dp(12)); search.setBackground(bg(Color.rgb(21,26,35),12)); search.setPadding(dp(16),0,dp(16),0); top.addView(search,new LinearLayout.LayoutParams(dp(440),dp(54)));
        Button refresh=new Button(this); refresh.setText("↻"); refresh.setTextSize(22); refresh.setTextColor(Color.WHITE); refresh.setBackground(bg(Color.rgb(21,26,35),12)); LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(dp(64),dp(54)); rp.setMargins(dp(12),0,0,0); top.addView(refresh,rp);
        ScrollView scroll=new ScrollView(this); LinearLayout rows=new LinearLayout(this); rows.setOrientation(LinearLayout.VERTICAL); rows.setPadding(0,dp(10),0,dp(40)); scroll.addView(rows); page.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        Runnable render=()-> renderRows(rows, search.getText().toString()); render.run();
        search.setOnEditorActionListener((v,action,event)->{ render.run(); return true; });
        search.setOnKeyListener((v,key,event)->{ if(event.getAction()==1){ render.run(); } return false; });
        refresh.setOnClickListener(v->{ String base=prefs.getString("server","");String token=prefs.getString("token",""); io.execute(()->{try{loadCatalog(base,token);}catch(Exception e){runOnUiThread(()->Toast.makeText(this,e.getMessage(),Toast.LENGTH_LONG).show());}}); });
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

    private View card(ContentItem x){
        LinearLayout c=new LinearLayout(this); c.setOrientation(LinearLayout.VERTICAL); c.setFocusable(true); c.setClickable(true); c.setPadding(dp(7),dp(7),dp(7),dp(7)); c.setBackground(bg(Color.rgb(21,26,35),12));
        LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(dp(188),dp(306)); cp.setMargins(0,0,dp(14),0); c.setLayoutParams(cp);
        ImageView poster=new ImageView(this); poster.setScaleType(ImageView.ScaleType.CENTER_CROP); poster.setBackgroundColor(Color.rgb(32,38,48)); c.addView(poster,new LinearLayout.LayoutParams(-1,dp(246))); ImageLoader.load(poster,x.posterUrl);
        TextView t=text(x.displayTitle(),15); t.setMaxLines(2); t.setGravity(Gravity.CENTER_VERTICAL); t.setPadding(dp(4),dp(4),dp(4),0); c.addView(t,new LinearLayout.LayoutParams(-1,0,1));
        c.setOnFocusChangeListener((v,has)->v.animate().scaleX(has?1.06f:1f).scaleY(has?1.06f:1f).setDuration(120).start());
        c.setOnClickListener(v->{ Intent i=new Intent(this,DetailActivity.class); i.putExtra("id",x.id); i.putExtra("series",x.type.equals("series")); i.putExtra("title",x.title); startActivity(i); });
        return c;
    }
}
