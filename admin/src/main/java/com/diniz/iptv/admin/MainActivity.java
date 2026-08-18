package com.diniz.iptv.admin;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.*;
import android.text.InputType;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.net.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;

public class MainActivity extends Activity {
    private DbHelper db;
    private LinearLayout root, accounts, catalog;
    private ScrollView scroll;
    private EditText tmdb, title, epTitle, year, desc, poster, genres, url, season, episode;
    private Spinner type;
    private TextView serverStatus, serverAddr;
    private Button saveItem, cancelEdit;
    private long editing = -1;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd", Locale.US);

    private int dp(int v){ return Math.round(v * getResources().getDisplayMetrics().density); }
    private GradientDrawable bg(int c){ GradientDrawable g=new GradientDrawable(); g.setColor(c); g.setCornerRadius(dp(10)); return g; }
    private TextView txt(String s,int sp){ TextView t=new TextView(this); t.setText(s); t.setTextSize(sp); t.setTextColor(Color.WHITE); return t; }

    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        db=new DbHelper(this);
        df.setLenient(false);
        if(Build.VERSION.SDK_INT>=33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},7);
        build();
        refresh();
        startServer();
    }

    @Override protected void onResume(){ super.onResume(); updateServer(); }

    private void build(){
        scroll=new ScrollView(this);
        root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16),dp(16),dp(16),dp(40));
        root.setBackgroundColor(Color.rgb(11,14,20));
        scroll.addView(root);

        TextView h=txt("DINIZ IPTV — ADMIN MOBILE",26);
        h.setTypeface(null,1);
        root.addView(h);
        root.addView(muted("Controle completo pelo celular. Player e Admin devem estar na mesma rede para conexão local."));

        section("Servidor local");
        serverStatus=txt("",16); root.addView(serverStatus);
        serverAddr=muted(""); serverAddr.setTextIsSelectable(true); root.addView(serverAddr);
        Button st=btn("INICIAR / REINICIAR SERVIDOR"), sp=btn2("PARAR SERVIDOR");
        root.addView(st,lp()); root.addView(sp,lp());
        st.setOnClickListener(v->startServer());
        sp.setOnClickListener(v->{ stopService(new Intent(this,HttpServerService.class)); updateServer(); });

        section("Capas e metadados automáticos");
        root.addView(muted("Configure o TMDb uma única vez. Depois, ao digitar o título e sair do campo ou ao salvar, o Admin busca automaticamente capa, ano, gêneros e sinopse."));
        tmdb=field("TMDb Read Access Token");
        tmdb.setText(getSharedPreferences("admin",MODE_PRIVATE).getString("tmdb",""));
        Button saveKey=btn("SALVAR TOKEN TMDB"); root.addView(saveKey,lp());
        saveKey.setOnClickListener(v->{
            getSharedPreferences("admin",MODE_PRIVATE).edit().putString("tmdb",tmdb.getText().toString().trim()).apply();
            toast("Token salvo. Capas automáticas ativadas.");
        });

        section("Criar conta");
        EditText user=field("Usuário"), pass=field("Senha (mínimo 4 caracteres)"), exp=dateField("Vencimento");
        pass.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);
        Button add=btn("CRIAR CONTA"); root.addView(add,lp());
        add.setOnClickListener(v->{
            try{
                String u=user.getText().toString().trim(), p=pass.getText().toString();
                if(u.isEmpty()||p.length()<4) throw new Exception("Usuário e senha inválidos.");
                db.createAccount(u,p,expiry(exp.getText().toString()));
                user.setText(""); pass.setText(""); exp.setText(""); refresh();
            }catch(Exception e){ toast(e.getMessage()); }
        });
        accounts=new LinearLayout(this); accounts.setOrientation(LinearLayout.VERTICAL); root.addView(accounts);

        section("Filmes e séries");
        type=new Spinner(this);
        type.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"Filme","Série"}));
        root.addView(type,new LinearLayout.LayoutParams(-1,dp(54)));

        title=field("Título do filme ou série");
        epTitle=field("Nome do episódio (série)");
        year=field("Ano — automático");
        season=field("Temporada");
        episode=field("Episódio");
        url=field("MP4/HLS/DASH, OK.ru ou TeraBox");
        poster=field("URL da capa — automática");
        genres=field("Gêneros — automáticos");
        desc=multi("Sinopse — automática");

        title.setOnFocusChangeListener((v,hasFocus)->{
            if(!hasFocus && shouldAutoMetadata()) metadata(false);
        });

        Button meta=btn2("ATUALIZAR CAPA / METADADOS AGORA"); root.addView(meta,lp());
        saveItem=btn("ADICIONAR AO CATÁLOGO");
        cancelEdit=btn2("CANCELAR EDIÇÃO"); cancelEdit.setVisibility(View.GONE);
        root.addView(saveItem,lp()); root.addView(cancelEdit,lp());
        meta.setOnClickListener(v->metadata(false));
        saveItem.setOnClickListener(v->saveOrMeta());
        cancelEdit.setOnClickListener(v->clearForm());

        catalog=new LinearLayout(this); catalog.setOrientation(LinearLayout.VERTICAL); root.addView(catalog);
        setContentView(scroll);
    }

    private void startServer(){
        try{
            Intent i=new Intent(this,HttpServerService.class);
            if(Build.VERSION.SDK_INT>=26) startForegroundService(i); else startService(i);
            serverStatus.postDelayed(this::updateServer,400);
        }catch(Exception e){ toast("Servidor: "+e.getMessage()); }
    }

    private void updateServer(){
        if(serverStatus==null)return;
        boolean r=HttpServerService.isRunning();
        serverStatus.setText(r?"● Servidor ativo":"● Servidor parado");
        serverStatus.setTextColor(r?Color.rgb(120,230,160):Color.rgb(255,150,150));
        serverAddr.setText("Endereço: http://"+localIp()+":"+HttpServerService.PORT);
    }

    private boolean shouldAutoMetadata(){
        String token=tmdb==null?"":tmdb.getText().toString().trim();
        String q=title==null?"":title.getText().toString().trim();
        if(token.isEmpty()||q.isEmpty()) return false;
        return poster.getText().toString().trim().isEmpty() ||
               year.getText().toString().trim().isEmpty() ||
               genres.getText().toString().trim().isEmpty() ||
               desc.getText().toString().trim().isEmpty();
    }

    private void metadata(boolean saveAfter){
        String q=title.getText().toString().trim();
        String token=tmdb.getText().toString().trim();
        if(q.isEmpty()){ toast("Digite o título."); return; }
        if(token.isEmpty()){
            if(saveAfter) saveNow();
            else toast("Configure o TMDb Read Access Token uma vez para usar capas automáticas.");
            return;
        }

        saveItem.setEnabled(false);
        io.execute(()->{
            try{
                TmdbClient.Meta m=TmdbClient.fetch(token,q,type.getSelectedItemPosition()==1);
                runOnUiThread(()->{
                    if(!m.title.isEmpty()) title.setText(m.title);
                    if(year.getText().toString().trim().isEmpty()) year.setText(m.year);
                    if(poster.getText().toString().trim().isEmpty()) poster.setText(m.posterUrl);
                    if(genres.getText().toString().trim().isEmpty()) genres.setText(m.genres);
                    if(desc.getText().toString().trim().isEmpty()) desc.setText(m.description);
                    saveItem.setEnabled(true);
                    if(saveAfter) saveNow(); else toast("Capa e metadados carregados automaticamente.");
                });
            }catch(Exception e){
                runOnUiThread(()->{
                    saveItem.setEnabled(true);
                    if(saveAfter) saveNow();
                    else toast("TMDb: "+e.getMessage());
                });
            }
        });
    }

    private void saveOrMeta(){
        if(shouldAutoMetadata()) metadata(true);
        else saveNow();
    }

    private void saveNow(){
        try{
            String u=url.getText().toString().trim(), t=title.getText().toString().trim();
            if(t.isEmpty()||u.isEmpty()) throw new Exception("Título e link são obrigatórios.");
            Uri x=Uri.parse(u);
            if(x.getScheme()==null||(!"http".equalsIgnoreCase(x.getScheme())&&!"https".equalsIgnoreCase(x.getScheme())))
                throw new Exception("Use um link HTTP/HTTPS.");
            if(u.contains("ok.ru/")&&!u.matches("(?i).*ok\\.ru/(?:video|videoembed)/\\d+.*"))
                throw new Exception("Link OK.ru inválido.");

            ContentValues v=new ContentValues();
            v.put("type",type.getSelectedItemPosition()==1?"series":"movie");
            v.put("title",t);
            v.put("episodeTitle",epTitle.getText().toString().trim());
            v.put("year",year.getText().toString().trim());
            v.put("description",desc.getText().toString().trim());
            v.put("posterUrl",poster.getText().toString().trim());
            v.put("genres",genres.getText().toString().trim());
            v.put("streamUrl",u);
            v.put("season",num(season));
            v.put("episode",num(episode));
            if(editing>0) db.updateContent(editing,v); else db.addContent(v);
            clearForm(); refresh(); toast("Catálogo atualizado.");
        }catch(Exception e){ saveItem.setEnabled(true); toast(e.getMessage()); }
    }

    private int num(EditText e){ try{return Integer.parseInt(e.getText().toString().trim());}catch(Exception x){return 0;} }

    private void clearForm(){
        editing=-1;
        for(EditText e:new EditText[]{title,epTitle,year,desc,poster,genres,url,season,episode}) e.setText("");
        type.setSelection(0);
        saveItem.setText("ADICIONAR AO CATÁLOGO");
        saveItem.setEnabled(true);
        cancelEdit.setVisibility(View.GONE);
    }

    private void edit(JSONObject o){
        editing=o.optLong("id",-1);
        type.setSelection("series".equals(o.optString("type"))?1:0);
        title.setText(o.optString("title"));
        epTitle.setText(o.optString("episodeTitle"));
        year.setText(o.optString("year"));
        desc.setText(o.optString("description"));
        poster.setText(o.optString("posterUrl"));
        genres.setText(o.optString("genres"));
        url.setText(o.optString("streamUrl"));
        season.setText(String.valueOf(o.optInt("season")));
        episode.setText(String.valueOf(o.optInt("episode")));
        saveItem.setText("SALVAR ALTERAÇÕES");
        cancelEdit.setVisibility(View.VISIBLE);
        toast("Editando "+o.optString("title"));
    }

    private void refresh(){
        if(accounts==null||catalog==null)return;
        accounts.removeAllViews(); catalog.removeAllViews();
        try{
            JSONArray a=db.listAccounts();
            for(int i=0;i<a.length();i++){
                JSONObject o=a.getJSONObject(i); long id=o.getLong("id"), ex=o.getLong("expiresAt");
                LinearLayout c=card(); c.addView(txt(o.getString("username"),17)); c.addView(muted("Vence em "+df.format(new Date(ex))));
                Button r=btn2("RENOVAR"), p=btn2("TROCAR SENHA"), d=danger("EXCLUIR");
                c.addView(r,small()); c.addView(p,small()); c.addView(d,small());
                r.setOnClickListener(v->renew(id,ex)); p.setOnClickListener(v->password(id)); d.setOnClickListener(v->{db.deleteAccount(id);refresh();});
                accounts.addView(c,cardLp());
            }

            JSONArray items=db.listContent();
            for(int i=0;i<items.length();i++){
                JSONObject o=items.getJSONObject(i); long id=o.getLong("id");
                LinearLayout c=card();
                c.addView(txt(o.optString("title")+(o.optString("year").isEmpty()?"":" ("+o.optString("year")+")"),17));
                String stream=o.optString("streamUrl");
                String source=stream.contains("ok.ru/")?"OK.ru":(stream.toLowerCase(Locale.ROOT).contains("terabox")?"TeraBox":"Link direto");
                c.addView(muted(source+(o.optString("posterUrl").isEmpty()?" • sem capa":" • capa automática")));
                Button e=btn2("EDITAR"), d=danger("EXCLUIR"); c.addView(e,small()); c.addView(d,small());
                e.setOnClickListener(v->edit(o)); d.setOnClickListener(v->{db.deleteContent(id);refresh();});
                catalog.addView(c,cardLp());
            }
        }catch(Exception e){ toast("Banco: "+e.getMessage()); }
    }

    private void renew(long id,long current){
        Calendar c=Calendar.getInstance(); c.setTimeInMillis(current);
        new DatePickerDialog(this,(v,y,m,d)->{
            Calendar n=Calendar.getInstance(); n.set(y,m,d,23,59,59);
            db.updateAccountExpiry(id,n.getTimeInMillis()); refresh();
        },c.get(Calendar.YEAR),c.get(Calendar.MONTH),c.get(Calendar.DAY_OF_MONTH)).show();
    }

    private void password(long id){
        EditText p=new EditText(this); p.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);
        new AlertDialog.Builder(this).setTitle("Nova senha").setView(p).setNegativeButton("Cancelar",null).setPositiveButton("Salvar",(d,w)->{
            try{ if(p.length()<4)throw new Exception("Mínimo 4 caracteres."); db.updateAccountPassword(id,p.getText().toString()); toast("Senha alterada."); }
            catch(Exception e){ toast(e.getMessage()); }
        }).show();
    }

    private long expiry(String s)throws Exception{
        Date d=df.parse(s); if(d==null)throw new Exception("Data inválida.");
        Calendar c=Calendar.getInstance(); c.setTime(d); c.set(Calendar.HOUR_OF_DAY,23); c.set(Calendar.MINUTE,59); c.set(Calendar.SECOND,59);
        return c.getTimeInMillis();
    }

    private EditText dateField(String h){
        EditText e=field(h); e.setFocusable(false);
        e.setOnClickListener(v->{ Calendar c=Calendar.getInstance(); new DatePickerDialog(this,(x,y,m,d)->e.setText(String.format(Locale.US,"%04d-%02d-%02d",y,m+1,d)),c.get(Calendar.YEAR),c.get(Calendar.MONTH),c.get(Calendar.DAY_OF_MONTH)).show(); });
        return e;
    }

    private void section(String s){ TextView t=txt(s,21); t.setTypeface(null,1); LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2); p.setMargins(0,dp(22),0,dp(8)); root.addView(t,p); }
    private EditText field(String h){ EditText e=new EditText(this); e.setHint(h); e.setHintTextColor(Color.GRAY); e.setTextColor(Color.WHITE); e.setSingleLine(true); e.setPadding(dp(12),0,dp(12),0); e.setBackground(bg(Color.rgb(21,26,35))); LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(52)); p.setMargins(0,dp(4),0,dp(4)); root.addView(e,p); return e; }
    private EditText multi(String h){ EditText e=field(h); e.setSingleLine(false); e.setMinLines(3); LinearLayout.LayoutParams p=(LinearLayout.LayoutParams)e.getLayoutParams(); p.height=dp(110); e.setLayoutParams(p); return e; }
    private Button btn(String s){ Button b=new Button(this); b.setText(s); b.setTextColor(Color.WHITE); b.setBackground(bg(Color.rgb(124,77,255))); return b; }
    private Button btn2(String s){ Button b=btn(s); b.setBackground(bg(Color.rgb(35,43,57))); return b; }
    private Button danger(String s){ Button b=btn(s); b.setBackground(bg(Color.rgb(105,42,51))); return b; }
    private LinearLayout.LayoutParams lp(){ LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(52)); p.setMargins(0,dp(5),0,dp(5)); return p; }
    private LinearLayout.LayoutParams small(){ return lp(); }
    private LinearLayout card(){ LinearLayout c=new LinearLayout(this); c.setOrientation(LinearLayout.VERTICAL); c.setPadding(dp(12),dp(12),dp(12),dp(12)); c.setBackground(bg(Color.rgb(21,26,35))); return c; }
    private LinearLayout.LayoutParams cardLp(){ LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2); p.setMargins(0,dp(6),0,dp(6)); return p; }
    private TextView muted(String s){ TextView t=txt(s,14); t.setTextColor(Color.rgb(165,174,190)); return t; }
    private void toast(String s){ Toast.makeText(this,s,Toast.LENGTH_LONG).show(); }

    private String localIp(){
        try{
            for(NetworkInterface n:Collections.list(NetworkInterface.getNetworkInterfaces()))
                for(InetAddress a:Collections.list(n.getInetAddresses()))
                    if(a instanceof Inet4Address&&!a.isLoopbackAddress()){
                        String ip=a.getHostAddress();
                        if(ip.startsWith("192.168.")||ip.startsWith("10.")||ip.startsWith("172."))return ip;
                    }
        }catch(Exception ignored){}
        return "IP-DO-CELULAR";
    }

    @Override protected void onDestroy(){ io.shutdownNow(); super.onDestroy(); }
}
