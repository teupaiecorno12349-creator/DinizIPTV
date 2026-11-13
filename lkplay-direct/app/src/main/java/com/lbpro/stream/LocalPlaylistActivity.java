package com.lbpro.stream;

import android.app.Activity;
import android.os.Bundle;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class LocalPlaylistActivity extends Activity {
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private LinearLayout root, items;
    private EditText name, server, username, password, m3uUrl;
    private Spinner kind;
    private TextView status;
    private Button save;
    private SharedPreferences prefs;
    private LocalPanelServer panelServer;
    private String panelKey, panelDeviceId;
    private JSONArray playlists = new JSONArray();
    private boolean formVisible = false;
    private int editing = -1;

    private int dp(float v) { return (int)(v * getResources().getDisplayMetrics().density + 0.5f); }
    private SharedPreferences prefs() { return getSharedPreferences("PREF", Context.MODE_PRIVATE); }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().getDecorView().setBackgroundColor(Color.rgb(13,20,33));
        prefs = prefs();
        panelDeviceId = LocalPanelServer.deviceId(this);
        panelKey = LocalPanelServer.deviceKey(this);
        panelServer = new LocalPanelServer(this);
        try {
            panelServer.start(5000, false);
        } catch (Exception e) {
            Toast.makeText(this, "Painel web indisponível: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
        loadExisting();
        showManager();
    }

    private void loadExisting() {
        try {
            JSONObject app = new JSONObject(prefs.getString("APP_INFO_MODEL", "{}"));
            JSONArray a = app.optJSONArray("result");
            playlists = a == null ? new JSONArray() : a;
        } catch (Exception e) { playlists = new JSONArray(); }
    }

    private TextView label(String s, int size, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s); t.setTextSize(size); t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        t.setGravity(Gravity.CENTER_VERTICAL);
        return t;
    }

    private EditText field(String hint, int type) {
        EditText e = new EditText(this);
        e.setSingleLine(true); e.setTextSize(16); e.setHint(hint);
        e.setTextColor(Color.WHITE); e.setHintTextColor(0xff899bb2);
        e.setPadding(dp(12),0,dp(12),0); e.setInputType(type);
        e.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xff58708e));
        e.setMinHeight(dp(48));
        return e;
    }

    private Button button(String text) {
        Button b = new Button(this); b.setText(text); b.setTextSize(13);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setTextColor(Color.WHITE);
        b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xff16877d));
        return b;
    }

    private LinearLayout column() {
        LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(dp(22),dp(18),dp(22),dp(18)); return l;
    }

    private void addField(LinearLayout parent, String caption, EditText e) {
        TextView t=label(caption,12,0xffaebed2,true);
        LinearLayout.LayoutParams tl=new LinearLayout.LayoutParams(-1,-2); tl.topMargin=dp(12); parent.addView(t,tl);
        parent.addView(e,new LinearLayout.LayoutParams(-1,dp(50)));
    }

    private void showManager() {
        loadExisting();
        formVisible=false; root=column();
        root.addView(label("LK PLAY",30,Color.WHITE,true));
        root.addView(label("PAINEL WEB • GERENCIE PELO CELULAR",12,0xff8fa5c0,true));
        root.addView(label("Endereço: http://" + LocalPanelServer.localIp() + ":8765/",13,0xff72d6c8,true));
        root.addView(label("ID do dispositivo: " + panelDeviceId,11,0xffaebed2,false));
        root.addView(label("Chave de acesso: " + panelKey,11,0xffaebed2,false));
        TextView info=label("Cadastre sua lista diretamente neste aparelho. A conexão será feita somente com o servidor do seu provedor IPTV.",14,0xffdbe5f2,false);
        LinearLayout.LayoutParams ip=new LinearLayout.LayoutParams(-1,-2); ip.topMargin=dp(18); root.addView(info,ip);
        Button add=button("+ ADICIONAR LISTA"); LinearLayout.LayoutParams ap=new LinearLayout.LayoutParams(-1,dp(52)); ap.topMargin=dp(18); root.addView(add,ap);
        add.setOnClickListener(v->showForm(-1));
        TextView heading=label("SUAS LISTAS",16,Color.WHITE,true);
        LinearLayout.LayoutParams hp=new LinearLayout.LayoutParams(-1,-2);hp.topMargin=dp(22);root.addView(heading,hp);
        items=new LinearLayout(this);items.setOrientation(LinearLayout.VERTICAL);root.addView(items,new LinearLayout.LayoutParams(-1,-2));
        for(int i=0;i<playlists.length();i++) {
            final int idx=i;
            JSONObject p=playlists.optJSONObject(i); if(p==null)continue;
            LinearLayout card=new LinearLayout(this);card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(dp(12),dp(10),dp(12),dp(10));card.setBackgroundColor(0xff1a2a40);
            LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,-2);cp.topMargin=dp(10);items.addView(card,cp);
            card.addView(label(p.optString("name","Minha lista"),17,Color.WHITE,true));
            card.addView(label(p.optString("type","").toUpperCase(Locale.ROOT),11,0xff72d6c8,true));
            LinearLayout actions=new LinearLayout(this);actions.setOrientation(LinearLayout.HORIZONTAL);card.addView(actions);
            Button open=button("ABRIR NO PLAYER");actions.addView(open,new LinearLayout.LayoutParams(0,dp(46),1));
            Button edit=button("EDITAR");edit.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xff405778));LinearLayout.LayoutParams ep=new LinearLayout.LayoutParams(0,dp(46),1);ep.leftMargin=dp(6);actions.addView(edit,ep);
            Button del=button("EXCLUIR");del.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xff813e4a));LinearLayout.LayoutParams dpv=new LinearLayout.LayoutParams(0,dp(46),1);dpv.leftMargin=dp(6);actions.addView(del,dpv);
            open.setOnClickListener(v->openPlayer(idx));
            edit.setOnClickListener(v->showForm(idx));
            del.setOnClickListener(v->delete(idx));
        }
        if(playlists.length()==0) {
            TextView empty=label("Nenhuma lista cadastrada. Use “Adicionar lista” para começar.",14,0xffaebed2,false);
            LinearLayout.LayoutParams ep=new LinearLayout.LayoutParams(-1,-2);ep.topMargin=dp(12);items.addView(empty,ep);
        }
        ScrollView scroll=new ScrollView(this);
        scroll.addView(root);
        setContentView(scroll);
    }

    private void showForm(int index) {
        formVisible=true; editing=index;
        root=column();
        root.addView(label(index<0?"ADICIONAR LISTA":"EDITAR LISTA",24,Color.WHITE,true));
        root.addView(label("Escolha o formato e preencha os dados fornecidos pelo seu provedor.",14,0xffaebed2,false));
        kind=new Spinner(this);
        String[] types={"Xtream Codes","Lista M3U / M3U8"};
        kind.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,types));
        root.addView(kind,new LinearLayout.LayoutParams(-1,dp(50)));
        name=field("Ex.: Minha TV",InputType.TYPE_CLASS_TEXT);
        addField(root,"NOME DA LISTA",name);
        server=field("https://servidor:porta",InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_URI);
        username=field("Usuário",InputType.TYPE_CLASS_TEXT);
        password=field("Senha",InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);
        m3uUrl=field("https://.../playlist.m3u",InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_URI);
        LinearLayout xtream=new LinearLayout(this);xtream.setOrientation(LinearLayout.VERTICAL);
        addField(xtream,"ENDEREÇO DO SERVIDOR",server);addField(xtream,"USUÁRIO",username);addField(xtream,"SENHA",password);
        root.addView(xtream);
        LinearLayout m3u=new LinearLayout(this);m3u.setOrientation(LinearLayout.VERTICAL);addField(m3u,"URL DA LISTA M3U",m3uUrl);root.addView(m3u);
        Runnable update=()->{boolean isM3u=kind.getSelectedItemPosition()==1;xtream.setVisibility(isM3u?View.GONE:View.VISIBLE);m3u.setVisibility(isM3u?View.VISIBLE:View.GONE);};
        kind.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){
            public void onItemSelected(android.widget.AdapterView<?> p,View v,int pos,long id){update.run();}
            public void onNothingSelected(android.widget.AdapterView<?> p){}
        });
        status=label("",14,0xffffcf8a,false);LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(-1,-2);sp.topMargin=dp(10);root.addView(status,sp);
        LinearLayout buttons=new LinearLayout(this);buttons.setOrientation(LinearLayout.HORIZONTAL);
        save=button("SALVAR E VALIDAR");buttons.addView(save,new LinearLayout.LayoutParams(0,dp(50),1));
        Button cancel=button("VOLTAR");cancel.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xff405778));LinearLayout.LayoutParams bc=new LinearLayout.LayoutParams(0,dp(50),1);bc.leftMargin=dp(8);buttons.addView(cancel,bc);
        LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(-1,-2);bp.topMargin=dp(18);root.addView(buttons,bp);
        save.setOnClickListener(v->validateAndSave());cancel.setOnClickListener(v->showManager());
        if(index>=0&&index<playlists.length()) {
            JSONObject p=playlists.optJSONObject(index);
            if(p!=null) {
                name.setText(p.optString("name",""));
                String type=p.optString("type","m3u");
                boolean m3uType=type.equalsIgnoreCase("m3u");
                kind.setSelection(m3uType?1:0);
                if(m3uType)m3uUrl.setText(p.optString("url",""));
                else {
                    String[] parsed=parseXtream(p.optString("url",""));
                    server.setText(parsed[0]);username.setText(parsed[1]);password.setText(parsed[2]);
                }
                update.run();
            }
        }
        ScrollView scroll=new ScrollView(this);scroll.addView(root);setContentView(scroll);
    }

    private String[] parseXtream(String url) {
        try {
            URL u=new URL(url);
            String user=u.getQuery()==null?"":queryParam(u.getQuery(),"username");
            String pass=u.getQuery()==null?"":queryParam(u.getQuery(),"password");
            return new String[]{u.getProtocol()+"://"+u.getAuthority(),user,pass};
        } catch(Exception e){return new String[]{"","",""};}
    }

    private String queryParam(String query,String key) {
        for(String part:query.split("&")) {
            int i=part.indexOf('=');
            if(i>0&&part.substring(0,i).equals(key)) try{return java.net.URLDecoder.decode(part.substring(i+1),"UTF-8");}catch(Exception ignored){}
        }
        return "";
    }

    private String buildXtreamUrl(String host,String user,String pass) throws Exception {
        String base=host.trim();
        if(base.endsWith("/"))base=base.substring(0,base.length()-1);
        if(base.endsWith("/player_api.php"))base=base.substring(0,base.length()-15);
        if(base.endsWith("/get.php"))base=base.substring(0,base.length()-8);
        return base+"/get.php?username="+URLEncoder.encode(user,"UTF-8")+"&password="+URLEncoder.encode(pass,"UTF-8")+"&type=m3u_plus&output=ts";
    }

    private void validateAndSave() {
        final String n=name.getText().toString().trim();
        final boolean isM3u=kind.getSelectedItemPosition()==1;
        final String u;
        try {
            if(n.isEmpty()){name.setError("Informe um nome");return;}
            if(isM3u) {
                u=m3uUrl.getText().toString().trim();
                if(!(u.startsWith("http://")||u.startsWith("https://"))){m3uUrl.setError("URL inválida");return;}
            } else {
                String h=server.getText().toString().trim(), user=username.getText().toString().trim(), pass=password.getText().toString();
                if(user.isEmpty()||pass.isEmpty()){username.setError("Preencha usuário e senha");return;}
                URL parsed=new URL(h);
                if(!parsed.getProtocol().equals("http")&&!parsed.getProtocol().equals("https"))throw new Exception("Use HTTP ou HTTPS");
                u=buildXtreamUrl(h,user,pass);
            }
        } catch(Exception e){status.setText(e.getMessage()==null?"Dados inválidos":e.getMessage());return;}
        save.setEnabled(false);status.setText("Testando conexão diretamente com o provedor...");
        io.execute(()->{
            String result=validateProvider(u,isM3u);
            runOnUiThread(()->{
                save.setEnabled(true);
                if(result!=null){status.setText(result);return;}
                try {
                    JSONObject item=new JSONObject().put("id",editing>=0?playlists.optJSONObject(editing).optString("id"):String.valueOf(System.currentTimeMillis()))
                        .put("name",n).put("type",isM3u?"m3u":"xtream").put("url",u).put("is_protected","0");
                    if(editing>=0&&editing<playlists.length())playlists.put(editing,item);else playlists.put(item);
                    saveAppInfo();
                    Toast.makeText(this,"Lista salva neste aparelho.",Toast.LENGTH_LONG).show();
                    showManager();
                } catch(Exception e){status.setText("Não foi possível salvar: "+e.getMessage());}
            });
        });
    }

    private String validateProvider(String link, boolean m3u) {
        HttpURLConnection c=null;
        try {
            String check=link;
            if(!m3u) {
                int q=check.indexOf("/get.php?");
                if(q>=0)check=check.substring(0,q)+"/player_api.php"+check.substring(q+8);
            }
            c=(HttpURLConnection)new URL(check).openConnection();
            c.setConnectTimeout(12000);c.setReadTimeout(12000);c.setRequestProperty("User-Agent","LKPlay/4.4");
            c.setInstanceFollowRedirects(true);
            int code=c.getResponseCode();
            if(code<200||code>=400)return "O provedor respondeu HTTP "+code+". Confira o endereço.";
            InputStream in=c.getInputStream();
            byte[] buf=new byte[8192];int count=in.read(buf);in.close();
            String body=count>0?new String(buf,0,count,StandardCharsets.UTF_8):"";
            if(m3u) {
                if(!body.toUpperCase(Locale.ROOT).contains("#EXTM3U"))return "A URL respondeu, mas não parece ser uma lista M3U.";
            } else {
                try {
                    JSONObject json=new JSONObject(body);
                    JSONObject user=json.optJSONObject("user_info");
                    if(user!=null&&user.has("auth")&&user.optInt("auth",0)==0)return "Usuário ou senha rejeitados pelo provedor.";
                    if(user!=null&&user.has("status")&&"Disabled".equalsIgnoreCase(user.optString("status")))return "A conta está desativada no provedor.";
                } catch(Exception ignored) {
                    if(body.trim().isEmpty())return "O provedor não retornou dados de autenticação.";
                }
            }
            return null;
        } catch(Exception e) {return "Falha ao conectar ao provedor: "+(e.getMessage()==null?"verifique a rede":e.getMessage());}
        finally {if(c!=null)c.disconnect();}
    }

    private void saveAppInfo() throws Exception {
        String id=LocalPanelServer.deviceId(this);
        JSONObject app;
        try{app=new JSONObject(prefs.getString("APP_INFO_MODEL","{}"));}catch(Exception e){app=new JSONObject();}
        app.put("result",playlists).put("success",true).put("theme","theme_d")
           .put("app_version","4.4").put("mac_address",id).put("device_key","")
           .put("home_url1","").put("qr_url","").put("qr_url_short","")
           .put("expiredDate","2099-12-31").put("is_trial",0).put("is_google_pay",false)
           .put("lock",0).put("price","Free");
        prefs.edit().putString("APP_INFO_MODEL",app.toString())
            .putString("mac_address",id).putString("device_type","tv")
            .putString("device_key","").putLong("last_playlist_date",System.currentTimeMillis())
            .putString("user_id","local-"+id).apply();
    }

    private void delete(int index) {
        JSONArray next=new JSONArray();
        for(int i=0;i<playlists.length();i++)if(i!=index)next.put(playlists.opt(i));
        playlists=next;
        try{saveAppInfo();showManager();}catch(Exception e){Toast.makeText(this,"Falha ao excluir: "+e.getMessage(),Toast.LENGTH_LONG).show();}
    }

    private void openPlayer(int index) {
        try {
            JSONObject p=playlists.getJSONObject(index);
            boolean m3u=p.optString("type","").equalsIgnoreCase("m3u");
            String url=p.optString("url","");
            String user="",pass="",host="";
            if(!m3u) {
                String[] v=parseXtream(url);host=v[0];user=v[1];pass=v[2];
            }
            saveAppInfo();
            JSONObject login=new JSONObject().put("username",user).put("password",pass).put("status","Active")
                .put("auth","1").put("message","Autenticado diretamente no provedor")
                .put("max_connections","1").put("active_cons","1")
                .put("created_at",System.currentTimeMillis()/1000L).put("exp_date",4102444800L);
            prefs.edit().putString("login_models",login.toString()).putString("user_name",user)
                .putString("password",pass).putString("server_url",host).putBoolean("is_m3u",m3u)
                .putInt("playlist_position",index).apply();
            Intent intent=new Intent();
            intent.setClassName(getPackageName(),"com.lbpro.stream.activities.HomeActivity");
            startActivity(intent);
        } catch(Exception e){Toast.makeText(this,"Não foi possível abrir o player: "+e.getMessage(),Toast.LENGTH_LONG).show();}
    }

    @Override protected void onResume() {
        super.onResume();
        if(root!=null&&!formVisible) showManager();
    }

    @Override public void onBackPressed() {
        if(formVisible)showManager();else super.onBackPressed();
    }
    @Override protected void onDestroy(){
        if(panelServer!=null) panelServer.stop();
        super.onDestroy();
        io.shutdownNow();
    }
}
