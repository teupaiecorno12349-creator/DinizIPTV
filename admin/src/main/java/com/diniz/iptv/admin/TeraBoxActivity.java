package com.diniz.iptv.admin;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.*;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TeraBoxActivity extends Activity {
    private final ExecutorService io=Executors.newSingleThreadExecutor();
    private TeraBoxStore store;
    private DbHelper db;
    private EditText clientId,clientSecret,privateSecret,rootDir;
    private Spinner quality;
    private TextView status,progress;
    private Button authorize,sync;
    private static final Pattern EP=Pattern.compile("(?i)(?:^|[ ._\\-])S(\\d{1,2})E(\\d{1,3})(?:[ ._\\-]|$)");

    private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}
    private GradientDrawable bg(int c){GradientDrawable g=new GradientDrawable();g.setColor(c);g.setCornerRadius(dp(12));return g;}
    private TextView txt(String s,int sp){TextView t=new TextView(this);t.setText(s);t.setTextColor(Color.WHITE);t.setTextSize(sp);return t;}

    @Override public void onCreate(Bundle b){
        super.onCreate(b);store=new TeraBoxStore(this);db=new DbHelper(this);build();handleCallback(getIntent());refreshStatus();
    }
    @Override protected void onNewIntent(Intent intent){super.onNewIntent(intent);setIntent(intent);handleCallback(intent);}

    private void build(){
        ScrollView sv=new ScrollView(this);sv.setFillViewport(true);
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(16),dp(18),dp(16),dp(40));root.setBackgroundColor(Color.rgb(11,14,20));sv.addView(root);
        TextView h=txt("TERABOX — DINIZ IPTV",25);h.setTypeface(null,1);root.addView(h);
        TextView info=muted("Integração oficial. As credenciais ficam protegidas no celular Admin; a TV Box nunca recebe client_secret, private_secret ou refresh_token.");root.addView(info);

        section(root,"Credenciais Open Platform");
        clientId=field(root,"client_id");clientId.setText(store.clientId());
        clientSecret=field(root,"client_secret");clientSecret.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);clientSecret.setText(store.clientSecret());
        privateSecret=field(root,"private_secret");privateSecret.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);privateSecret.setText(store.privateSecret());
        Button save=button("SALVAR CREDENCIAIS",true);root.addView(save,lp());
        save.setOnClickListener(v->{saveSettings();refreshStatus();toast("Credenciais protegidas no Android Keystore.");});

        status=txt("",16);root.addView(status,lpWrap());
        authorize=button("AUTORIZAR MINHA CONTA TERABOX",true);root.addView(authorize,lp());
        authorize.setOnClickListener(v->{
            try{saveSettings();String u=new TeraBoxClient(this).authorizationUrl();startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(u)));}
            catch(Exception e){toast(e.getMessage());}
        });

        section(root,"Sincronização");
        root.addView(muted("A API oficial só permite trabalhar no espaço/pasta concedido ao aplicativo TeraBox. Use / se o seu aplicativo recebeu a raiz lógica do espaço autorizado."));
        rootDir=field(root,"Pasta raiz TeraBox");rootDir.setText(store.rootDir());
        quality=new Spinner(this);quality.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"720p","1080p","480p"}));
        String q=store.quality();quality.setSelection("M3U8_AUTO_1080".equals(q)?1:("M3U8_AUTO_480".equals(q)?2:0));root.addView(quality,new LinearLayout.LayoutParams(-1,dp(54)));
        sync=button("SINCRONIZAR FILMES E SÉRIES",true);root.addView(sync,lp());
        progress=muted("");root.addView(progress,lpWrap());
        sync.setOnClickListener(v->syncCatalog());

        TextView note=muted("Organização recomendada no TeraBox:\n/Filmes/Nome do Filme (2024).mp4\n/Series/Nome da Série/S01E01 - Episódio.mp4\n\nCapas, ano, gêneros e sinopse são preenchidos pelo TMDb quando o token já estiver configurado no Admin.");
        LinearLayout.LayoutParams np=lpWrap();np.setMargins(0,dp(18),0,0);root.addView(note,np);
        setContentView(sv);
    }

    private void saveSettings(){
        store.setClientId(clientId.getText().toString().trim());store.setClientSecret(clientSecret.getText().toString().trim());store.setPrivateSecret(privateSecret.getText().toString().trim());
        if(rootDir!=null)store.setRootDir(rootDir.getText().toString().trim());
        if(quality!=null)store.setQuality(quality.getSelectedItemPosition()==1?"M3U8_AUTO_1080":quality.getSelectedItemPosition()==2?"M3U8_AUTO_480":"M3U8_AUTO_720");
    }

    private void refreshStatus(){
        if(status==null)return;
        if(!store.configured()){status.setText("● Credenciais ainda não configuradas");status.setTextColor(Color.rgb(255,180,150));return;}
        if(store.authorized()){status.setText("● TeraBox autorizado — API: "+store.apiDomain());status.setTextColor(Color.rgb(120,230,160));}
        else if(!store.refreshToken().isEmpty()){status.setText("● Token expirado; será renovado automaticamente na próxima operação");status.setTextColor(Color.rgb(255,210,130));}
        else{status.setText("● Credenciais salvas. Falta autorizar a conta TeraBox.");status.setTextColor(Color.rgb(255,210,130));}
    }

    private void handleCallback(Intent intent){
        Uri data=intent==null?null:intent.getData();if(data==null)return;
        String code=data.getQueryParameter("code");if(code==null||code.isEmpty())code=data.getQueryParameter("authorization_code");
        if(code==null||code.isEmpty()){toast("Retorno TeraBox recebido sem código de autorização.");return;}
        String finalCode=code;setBusy(true,"Concluindo autorização TeraBox...");
        io.execute(()->{try{new TeraBoxClient(this).exchangeAuthorizationCode(finalCode);runOnUiThread(()->{setBusy(false,"");refreshStatus();toast("Conta TeraBox autorizada com sucesso.");});}catch(Exception e){runOnUiThread(()->{setBusy(false,"");toast("TeraBox: "+e.getMessage());});}});
    }

    private void syncCatalog(){
        saveSettings();setBusy(true,"Listando vídeos no TeraBox...");
        io.execute(()->{
            try{
                TeraBoxClient client=new TeraBoxClient(this);List<TeraBoxClient.CloudFile> files=client.listVideosRecursive(store.rootDir());
                String tmdb=getSharedPreferences("admin",MODE_PRIVATE).getString("tmdb","").trim();
                Map<String,TmdbClient.Meta> metaCache=new HashMap<>();int added=0,existing=0,index=0;
                for(TeraBoxClient.CloudFile f:files){
                    index++;String stream="terabox:"+f.path;if(db.hasStreamUrl(stream)){existing++;continue;}
                    Parsed p=parse(f);TmdbClient.Meta m=null;
                    if(!tmdb.isEmpty()){
                        String mk=(p.series?"S|":"M|")+p.title.toLowerCase(Locale.ROOT);
                        if(metaCache.containsKey(mk))m=metaCache.get(mk);else{try{m=TmdbClient.fetch(tmdb,p.title,p.series);metaCache.put(mk,m);}catch(Exception ignored){metaCache.put(mk,null);}}
                    }
                    String title=m!=null&&!m.title.isEmpty()?m.title:p.title;
                    String year=m!=null?m.year:p.year;String poster=m!=null?m.posterUrl:"";String genres=m!=null?m.genres:"";String desc=m!=null?m.description:"";
                    if(db.addTeraBoxContent(p.series?"series":"movie",title,p.episodeTitle,year,desc,poster,genres,f.path,p.season,p.episode)>0)added++;
                    if(index%10==0){int fi=index,fa=added;runOnUiThread(()->progress.setText("Processando "+fi+"/"+files.size()+" • novos "+fa));}
                }
                int a=added,e=existing,total=files.size();runOnUiThread(()->{setBusy(false,"Sincronização concluída: "+a+" novos, "+e+" já existentes, "+total+" vídeos encontrados.");refreshStatus();});
            }catch(Exception e){runOnUiThread(()->setBusy(false,"Erro: "+e.getMessage()));}
        });
    }

    private Parsed parse(TeraBoxClient.CloudFile f){
        String name=f.name;int dot=name.lastIndexOf('.');String base=dot>0?name.substring(0,dot):name;Matcher m=EP.matcher(base);Parsed p=new Parsed();
        if(m.find()){
            p.series=true;p.season=Integer.parseInt(m.group(1));p.episode=Integer.parseInt(m.group(2));
            String parent=parentName(f.path);p.title=cleanTitle(parent.isEmpty()?base.substring(0,m.start()):parent);String ep=base.substring(m.end()).replaceFirst("^[ ._\\-]+","");p.episodeTitle=cleanTitle(ep);
        }else{
            String low=f.path.toLowerCase(Locale.ROOT);p.series=low.contains("/series/")||low.contains("/serie/")||low.contains("/séries/")||low.contains("/seriados/");
            p.title=cleanTitle(p.series?parentName(f.path):base);p.episodeTitle="";
        }
        Matcher y=Pattern.compile("(?:^|[ (._-])((?:19|20)\\d{2})(?:[ ) ._-]|$)").matcher(base);if(y.find())p.year=y.group(1);
        if(p.title.isEmpty())p.title=cleanTitle(base);return p;
    }

    private String parentName(String path){if(path==null)return "";int last=path.lastIndexOf('/');if(last<=0)return "";String parent=path.substring(0,last);int x=parent.lastIndexOf('/');return x>=0?parent.substring(x+1):parent;}
    private String cleanTitle(String s){if(s==null)return "";String x=s.replace('_',' ').replace('.',' ').replaceAll("(?i)\\b(?:19|20)\\d{2}\\b"," ").replaceAll("\\s+"," ").trim();return x.replaceAll("^[\\- ]+|[\\- ]+$","");}
    private static class Parsed{boolean series;String title="",episodeTitle="",year="";int season,episode;}

    private void setBusy(boolean busy,String msg){authorize.setEnabled(!busy);sync.setEnabled(!busy);if(progress!=null)progress.setText(msg);}
    private EditText field(LinearLayout root,String hint){EditText e=new EditText(this);e.setHint(hint);e.setHintTextColor(Color.GRAY);e.setTextColor(Color.WHITE);e.setSingleLine(true);e.setPadding(dp(14),0,dp(14),0);e.setBackground(bg(Color.rgb(21,26,35)));root.addView(e,lp());return e;}
    private Button button(String s,boolean primary){Button b=new Button(this);b.setText(s);b.setTextColor(Color.WHITE);b.setBackground(bg(primary?Color.rgb(124,77,255):Color.rgb(35,43,57)));return b;}
    private TextView muted(String s){TextView t=txt(s,14);t.setTextColor(Color.rgb(165,174,190));t.setLineSpacing(0,1.12f);return t;}
    private void section(LinearLayout root,String s){TextView t=txt(s,20);t.setTypeface(null,1);LinearLayout.LayoutParams p=lpWrap();p.setMargins(0,dp(22),0,dp(8));root.addView(t,p);}
    private LinearLayout.LayoutParams lp(){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(54));p.setMargins(0,dp(5),0,dp(5));return p;}
    private LinearLayout.LayoutParams lpWrap(){return new LinearLayout.LayoutParams(-1,-2);}
    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
    @Override protected void onDestroy(){io.shutdownNow();db.close();super.onDestroy();}
}
