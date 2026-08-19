package com.diniz.iptv.player;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.widget.*;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private final ExecutorService io=Executors.newSingleThreadExecutor();
    private SharedPreferences prefs;
    private LinearLayout root;

    private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}
    private GradientDrawable bg(int color,int radius){GradientDrawable g=new GradientDrawable();g.setColor(color);g.setCornerRadius(dp(radius));return g;}
    private TextView txt(String s,int sp){TextView t=new TextView(this);t.setText(s);t.setTextColor(Color.WHITE);t.setTextSize(sp);return t;}

    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        immersive();
        prefs=getSharedPreferences("player",MODE_PRIVATE);
        showLogin(null);
    }

    private void showLogin(String message){
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);
        root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setGravity(Gravity.CENTER_HORIZONTAL);root.setPadding(dp(56),dp(36),dp(56),dp(30));root.setBackgroundColor(Color.rgb(11,14,20));scroll.addView(root);
        TextView logo=txt("DINIZ IPTV",34);logo.setTypeface(null,1);logo.setGravity(Gravity.CENTER);root.addView(logo,new LinearLayout.LayoutParams(-1,dp(64)));
        TextView sub=txt("PLAYER TV BOX",17);sub.setTextColor(Color.rgb(170,178,192));sub.setGravity(Gravity.CENTER);root.addView(sub,new LinearLayout.LayoutParams(-1,dp(38)));

        EditText server=field("Servidor do Admin");server.setText(prefs.getString("server",""));
        EditText user=field("Usuário");user.setText(prefs.getString("user",""));
        EditText pass=field("Senha");pass.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);
        Button discover=button("LOCALIZAR ADMIN NA REDE",false);
        Button login=button("ENTRAR",true);
        ProgressBar progress=new ProgressBar(this);progress.setVisibility(View.GONE);root.addView(progress);
        TextView status=txt(message==null?"":message,15);status.setGravity(Gravity.CENTER);status.setTextColor(Color.rgb(255,185,185));root.addView(status,new LinearLayout.LayoutParams(-1,dp(62)));

        discover.setOnClickListener(v->{
            discover.setEnabled(false);login.setEnabled(false);progress.setVisibility(View.VISIBLE);status.setText("Procurando Admin na mesma rede...");
            io.execute(()->{try{String found=AdminDiscovery.discover(4500);runOnUiThread(()->{discover.setEnabled(true);login.setEnabled(true);progress.setVisibility(View.GONE);if(found!=null){server.setText(found);prefs.edit().putString("server",found).apply();status.setText("Admin localizado: "+found);}else status.setText("Admin não encontrado. Confira a rede e o servidor do celular.");});}catch(Exception e){runOnUiThread(()->{discover.setEnabled(true);login.setEnabled(true);progress.setVisibility(View.GONE);status.setText("Falha: "+e.getMessage());});}});
        });

        login.setOnClickListener(v->{
            String base=normalize(server.getText().toString()),u=user.getText().toString().trim(),p=pass.getText().toString();
            if(base.isEmpty()||u.isEmpty()||p.isEmpty()){status.setText("Preencha servidor, usuário e senha.");return;}
            login.setEnabled(false);discover.setEnabled(false);progress.setVisibility(View.VISIBLE);status.setText("Conectando...");
            io.execute(()->{try{JSONObject req=new JSONObject();req.put("username",u);req.put("password",p);JSONObject r=Net.postJson(base+"/api/login",req);String token=r.getString("token");prefs.edit().putString("server",base).putString("token",token).putString("user",u).apply();loadCatalog(base,token);}catch(Exception e){runOnUiThread(()->{login.setEnabled(true);discover.setEnabled(true);progress.setVisibility(View.GONE);status.setText(e.getMessage());});}});
        });
        pass.setOnEditorActionListener((v,a,e)->{login.performClick();return true;});
        setContentView(scroll);immersive();
        if(server.getText().toString().trim().isEmpty())discover.requestFocus();else user.requestFocus();
    }

    private EditText field(String hint){
        EditText e=new EditText(this);e.setHint(hint);e.setHintTextColor(Color.rgb(145,153,168));e.setTextColor(Color.WHITE);e.setSingleLine(true);e.setPadding(dp(18),0,dp(18),0);e.setBackground(bg(Color.rgb(22,27,36),12));e.setFocusable(true);e.setFocusableInTouchMode(true);
        int w=Math.min(dp(650),getResources().getDisplayMetrics().widthPixels-dp(180));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(w,dp(58));p.setMargins(0,dp(7),0,dp(7));root.addView(e,p);return e;
    }

    private Button button(String label,boolean primary){
        Button b=new Button(this);b.setText(label);b.setTextColor(Color.WHITE);b.setTextSize(15);b.setBackground(bg(primary?Color.rgb(124,77,255):Color.rgb(35,43,57),12));b.setFocusable(true);b.setFocusableInTouchMode(true);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(dp(360),dp(58));p.setMargins(0,dp(8),0,dp(6));root.addView(b,p);focusScale(b);return b;
    }

    private String normalize(String s){s=s.trim();while(s.endsWith("/"))s=s.substring(0,s.length()-1);if(!s.isEmpty()&&!s.startsWith("http://")&&!s.startsWith("https://"))s="http://"+s;return s;}

    private void loadCatalog(String base,String token)throws Exception{
        JSONObject r=Net.getJson(base+"/api/catalog?token="+java.net.URLEncoder.encode(token,"UTF-8"));
        JSONArray a=r.getJSONArray("items");CatalogStore.ITEMS.clear();for(int i=0;i<a.length();i++)CatalogStore.ITEMS.add(ContentItem.fromJson(a.getJSONObject(i)));runOnUiThread(this::showHome);
    }

    private void showHome(){
        LinearLayout page=new LinearLayout(this);page.setOrientation(LinearLayout.VERTICAL);page.setPadding(dp(32),dp(18),dp(32),dp(18));page.setBackgroundColor(Color.rgb(11,14,20));
        LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);page.addView(top,new LinearLayout.LayoutParams(-1,dp(70)));
        TextView brand=txt("DINIZ IPTV",25);brand.setTypeface(null,1);top.addView(brand,new LinearLayout.LayoutParams(0,-2,1));
        EditText search=new EditText(this);search.setHint("Pesquisar filmes e séries");search.setHintTextColor(Color.rgb(145,153,168));search.setTextColor(Color.WHITE);search.setSingleLine(true);search.setPadding(dp(16),0,dp(16),0);search.setBackground(bg(Color.rgb(22,27,36),12));search.setFocusable(true);top.addView(search,new LinearLayout.LayoutParams(dp(430),dp(54)));
        Button refresh=new Button(this);refresh.setText("↻");refresh.setTextSize(22);refresh.setTextColor(Color.WHITE);refresh.setBackground(bg(Color.rgb(35,43,57),12));refresh.setFocusable(true);LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(dp(64),dp(54));rp.setMargins(dp(12),0,0,0);top.addView(refresh,rp);focusScale(refresh);

        ScrollView sv=new ScrollView(this);LinearLayout rows=new LinearLayout(this);rows.setOrientation(LinearLayout.VERTICAL);rows.setPadding(0,dp(8),0,dp(40));sv.addView(rows);page.addView(sv,new LinearLayout.LayoutParams(-1,0,1));
        Runnable render=()->render(rows,search.getText().toString());render.run();
        search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int c,int a){}public void onTextChanged(CharSequence s,int st,int b,int c){render.run();}public void afterTextChanged(Editable e){}});
        refresh.setOnClickListener(v->{Toast.makeText(this,"Atualizando catálogo...",Toast.LENGTH_SHORT).show();io.execute(()->{try{loadCatalog(prefs.getString("server",""),prefs.getString("token",""));}catch(Exception e){runOnUiThread(()->Toast.makeText(this,e.getMessage(),Toast.LENGTH_LONG).show());}});});
        setContentView(page);immersive();
    }

    private void render(LinearLayout rows,String query){
        rows.removeAllViews();String q=query==null?"":query.trim().toLowerCase(Locale.ROOT);List<ContentItem> filtered=new ArrayList<>();
        for(ContentItem x:CatalogStore.ITEMS){String h=(x.title+" "+x.episodeTitle+" "+x.genres).toLowerCase(Locale.ROOT);if(q.isEmpty()||h.contains(q))filtered.add(x);}addSection(rows,"Filmes",unique(filtered,"movie"));addSection(rows,"Séries",unique(filtered,"series"));
        if(filtered.isEmpty()){TextView e=txt("Nenhum título encontrado.",19);e.setTextColor(Color.rgb(170,178,192));rows.addView(e);}
    }

    private List<ContentItem> unique(List<ContentItem> src,String type){LinkedHashMap<String,ContentItem> m=new LinkedHashMap<>();for(ContentItem x:src){if(!type.equals(x.type))continue;String k="series".equals(type)?x.title.toLowerCase(Locale.ROOT):String.valueOf(x.id);m.putIfAbsent(k,x);}return new ArrayList<>(m.values());}

    private void addSection(LinearLayout rows,String title,List<ContentItem> items){
        if(items.isEmpty())return;TextView h=txt(title,22);h.setTypeface(null,1);LinearLayout.LayoutParams hp=new LinearLayout.LayoutParams(-1,dp(48));hp.setMargins(0,dp(12),0,0);rows.addView(h,hp);
        HorizontalScrollView hsv=new HorizontalScrollView(this);hsv.setHorizontalScrollBarEnabled(false);hsv.setFocusable(false);LinearLayout line=new LinearLayout(this);line.setOrientation(LinearLayout.HORIZONTAL);hsv.addView(line);rows.addView(hsv,new LinearLayout.LayoutParams(-1,dp(330)));for(ContentItem x:items)line.addView(card(x));
    }

    private View card(ContentItem x){
        LinearLayout c=new LinearLayout(this);c.setOrientation(LinearLayout.VERTICAL);c.setFocusable(true);c.setFocusableInTouchMode(true);c.setClickable(true);c.setPadding(dp(7),dp(7),dp(7),dp(7));c.setBackground(bg(Color.rgb(22,27,36),12));LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(dp(188),dp(306));cp.setMargins(0,0,dp(14),0);c.setLayoutParams(cp);
        ImageView p=new ImageView(this);p.setScaleType(ImageView.ScaleType.CENTER_CROP);p.setBackgroundColor(Color.rgb(35,41,52));c.addView(p,new LinearLayout.LayoutParams(-1,dp(246)));ImageLoader.load(p,x.posterUrl);
        TextView t=txt(x.displayTitle(),15);t.setMaxLines(2);t.setGravity(Gravity.CENTER_VERTICAL);t.setPadding(dp(4),dp(4),dp(4),0);c.addView(t,new LinearLayout.LayoutParams(-1,0,1));
        focusScale(c);c.setOnClickListener(v->{Intent i=new Intent(this,DetailActivity.class);i.putExtra("id",x.id);i.putExtra("series","series".equals(x.type));i.putExtra("title",x.title);startActivity(i);});return c;
    }

    private void focusScale(View v){v.setOnFocusChangeListener((x,has)->x.animate().scaleX(has?1.06f:1f).scaleY(has?1.06f:1f).setDuration(120).start());}

    private void immersive(){
        if(Build.VERSION.SDK_INT>=30){getWindow().setDecorFitsSystemWindows(false);WindowInsetsController c=getWindow().getInsetsController();if(c!=null){c.hide(WindowInsets.Type.statusBars()|WindowInsets.Type.navigationBars());c.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);}}
        else getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY|View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN|View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
    }

    @Override public void onWindowFocusChanged(boolean has){super.onWindowFocusChanged(has);if(has)immersive();}
    @Override protected void onDestroy(){io.shutdownNow();super.onDestroy();}
}
