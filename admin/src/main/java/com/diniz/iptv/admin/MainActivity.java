package com.diniz.iptv.admin;

import android.app.Activity;
import android.content.*;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.*;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.*;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.Inet4Address;
import java.net.NetworkInterface;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private DbHelper db; private LinearLayout root, accountsList, contentList; private final ExecutorService io=Executors.newSingleThreadExecutor();
    private EditText tmdb, title, episodeTitle, year, description, poster, genres, stream, season, episode; private Spinner type;
    private final SimpleDateFormat df=new SimpleDateFormat("yyyy-MM-dd",Locale.US);
    private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);} private GradientDrawable bg(int c,int r){GradientDrawable g=new GradientDrawable();g.setColor(c);g.setCornerRadius(dp(r));return g;}
    private TextView text(String s,int sp){TextView t=new TextView(this);t.setText(s);t.setTextSize(sp);t.setTextColor(Color.WHITE);return t;}

    @Override public void onCreate(Bundle b){super.onCreate(b);db=new DbHelper(this);df.setLenient(false);buildUi();refreshLists();}

    private void buildUi(){
        ScrollView sv=new ScrollView(this);root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(32),dp(24),dp(32),dp(50));root.setBackgroundColor(Color.rgb(11,14,20));sv.addView(root);
        TextView h=text("DINIZ IPTV — ADMIN",28);h.setTypeface(null,1);root.addView(h,new LinearLayout.LayoutParams(-1,dp(56)));
        TextView hint=text("Gerencie servidor local, contas, vencimentos e catálogo.",16);hint.setTextColor(Color.rgb(170,178,192));root.addView(hint,new LinearLayout.LayoutParams(-1,dp(42)));
        section("Servidor local");
        TextView addr=text("Endereço do Player: http://"+localIp()+":"+HttpServerService.PORT,18);addr.setTextColor(Color.rgb(190,200,220));root.addView(addr,new LinearLayout.LayoutParams(-1,dp(46)));
        LinearLayout serverBtns=row(); Button start=button("INICIAR SERVIDOR"), stop=button("PARAR SERVIDOR"); serverBtns.addView(start,new LinearLayout.LayoutParams(dp(260),dp(58)));LinearLayout.LayoutParams slp=new LinearLayout.LayoutParams(dp(240),dp(58));slp.setMargins(dp(12),0,0,0);serverBtns.addView(stop,slp);root.addView(serverBtns);
        start.setOnClickListener(v->{Intent i=new Intent(this,HttpServerService.class);if(Build.VERSION.SDK_INT>=26)startForegroundService(i);else startService(i);Toast.makeText(this,"Servidor iniciado na porta "+HttpServerService.PORT,Toast.LENGTH_LONG).show();});
        stop.setOnClickListener(v->{stopService(new Intent(this,HttpServerService.class));Toast.makeText(this,"Servidor parado.",Toast.LENGTH_SHORT).show();});
        tmdb=field("TMDb Read Access Token (para capas/metadados)");tmdb.setText(getSharedPreferences("admin",MODE_PRIVATE).getString("tmdb",""));Button saveKey=button("SALVAR TOKEN TMDB");root.addView(saveKey,new LinearLayout.LayoutParams(dp(260),dp(54)));saveKey.setOnClickListener(v->{getSharedPreferences("admin",MODE_PRIVATE).edit().putString("tmdb",tmdb.getText().toString().trim()).apply();Toast.makeText(this,"Token salvo.",Toast.LENGTH_SHORT).show();});

        section("Criar conta");LinearLayout acc=row();EditText u=smallField("Usuário",220),p=smallField("Senha",220),exp=smallField("Vencimento AAAA-MM-DD",260);p.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);acc.addView(u);acc.addView(p);acc.addView(exp);Button addAcc=button("CRIAR");acc.addView(addAcc,new LinearLayout.LayoutParams(dp(170),dp(56)));root.addView(acc);addAcc.setOnClickListener(v->{try{String us=u.getText().toString().trim();String pw=p.getText().toString();Date d=df.parse(exp.getText().toString().trim());if(us.isEmpty()||pw.length()<4||d==null)throw new Exception("Dados inválidos");Calendar c=Calendar.getInstance();c.setTime(d);c.set(Calendar.HOUR_OF_DAY,23);c.set(Calendar.MINUTE,59);c.set(Calendar.SECOND,59);db.createAccount(us,pw,c.getTimeInMillis());u.setText("");p.setText("");refreshLists();}catch(Exception e){toast("Erro ao criar conta: "+e.getMessage());}});
        accountsList=new LinearLayout(this);accountsList.setOrientation(LinearLayout.VERTICAL);root.addView(accountsList);

        section("Adicionar filme ou episódio de série");
        type=new Spinner(this);ArrayAdapter<String> ad=new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"Filme","Série"});type.setAdapter(ad);type.setBackground(bg(Color.rgb(21,26,35),8));root.addView(type,new LinearLayout.LayoutParams(dp(260),dp(56)));
        title=field("Título do filme ou da série");episodeTitle=field("Nome do episódio (opcional para série)");
        LinearLayout nums=row();year=smallField("Ano",180);season=smallField("Temporada",180);episode=smallField("Episódio",180);nums.addView(year);nums.addView(season);nums.addView(episode);root.addView(nums);
        stream=field("Link direto do vídeo, ex.: http://video.com/1231.mp4");
        LinearLayout auto=row();Button metadata=button("BUSCAR CAPA/METADADOS");auto.addView(metadata,new LinearLayout.LayoutParams(dp(330),dp(56)));root.addView(auto);
        poster=field("URL da capa");genres=field("Gêneros, separados por vírgula");description=fieldMulti("Sinopse");
        Button addItem=button("ADICIONAR AO CATÁLOGO");root.addView(addItem,new LinearLayout.LayoutParams(dp(350),dp(60)));
        metadata.setOnClickListener(v->fetchMetadata()); addItem.setOnClickListener(v->addContent());
        contentList=new LinearLayout(this);contentList.setOrientation(LinearLayout.VERTICAL);root.addView(contentList);
        setContentView(sv);
    }

    private void fetchMetadata(){String q=title.getText().toString().trim();if(q.isEmpty()){toast("Digite o título primeiro.");return;}String token=tmdb.getText().toString().trim();boolean series=type.getSelectedItemPosition()==1;toast("Consultando TMDb...");io.execute(()->{try{TmdbClient.Meta m=TmdbClient.fetch(token,q,series);runOnUiThread(()->{title.setText(m.title);if(!m.year.isEmpty())year.setText(m.year);description.setText(m.description);poster.setText(m.posterUrl);genres.setText(m.genres);toast("Metadados preenchidos.");});}catch(Exception e){runOnUiThread(()->toast("TMDb: "+e.getMessage()));}});}
    private void addContent(){try{String t=title.getText().toString().trim(),url=stream.getText().toString().trim();if(t.isEmpty()||url.isEmpty())throw new Exception("Título e link são obrigatórios.");Uri parsed=Uri.parse(url);if(parsed.getScheme()==null||(!parsed.getScheme().equals("http")&&!parsed.getScheme().equals("https")))throw new Exception("Use link HTTP ou HTTPS.");ContentValues v=new ContentValues();boolean series=type.getSelectedItemPosition()==1;v.put("type",series?"series":"movie");v.put("title",t);v.put("episodeTitle",episodeTitle.getText().toString().trim());v.put("year",year.getText().toString().trim());v.put("description",description.getText().toString().trim());v.put("posterUrl",poster.getText().toString().trim());v.put("genres",genres.getText().toString().trim());v.put("streamUrl",url);v.put("season",parseInt(season.getText().toString()));v.put("episode",parseInt(episode.getText().toString()));db.addContent(v);clearContentForm();refreshLists();toast("Adicionado ao catálogo.");}catch(Exception e){toast(e.getMessage());}}
    private int parseInt(String s){try{return Integer.parseInt(s.trim());}catch(Exception e){return 0;}}
    private void clearContentForm(){title.setText("");episodeTitle.setText("");year.setText("");description.setText("");poster.setText("");genres.setText("");stream.setText("");season.setText("");episode.setText("");}

    private void refreshLists(){if(accountsList==null||contentList==null)return;accountsList.removeAllViews();contentList.removeAllViews();try{JSONArray a=db.listAccounts();for(int i=0;i<a.length();i++){JSONObject o=a.getJSONObject(i);LinearLayout r=row();Date d=new Date(o.getLong("expiresAt"));TextView tx=text(o.getString("username")+"  • vence "+df.format(d),16);r.addView(tx,new LinearLayout.LayoutParams(0,dp(48),1));Button del=smallButton("Excluir");long id=o.getLong("id");del.setOnClickListener(v->{db.deleteAccount(id);refreshLists();});r.addView(del,new LinearLayout.LayoutParams(dp(140),dp(46)));accountsList.addView(r);}JSONArray c=db.listContent();for(int i=0;i<c.length();i++){JSONObject o=c.getJSONObject(i);LinearLayout r=row();String prefix=o.getString("type").equals("series")?"Série":"Filme";String ep=o.getString("type").equals("series")?"  T"+o.optInt("season")+"E"+o.optInt("episode"):"";TextView tx=text(prefix+" • "+o.getString("title")+ep+" ("+o.optString("year")+")",16);r.addView(tx,new LinearLayout.LayoutParams(0,dp(48),1));Button del=smallButton("Excluir");long id=o.getLong("id");del.setOnClickListener(v->{db.deleteContent(id);refreshLists();});r.addView(del,new LinearLayout.LayoutParams(dp(140),dp(46)));contentList.addView(r);}}catch(Exception e){toast("Banco: "+e.getMessage());}}

    private void section(String s){TextView t=text(s,22);t.setTypeface(null,1);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(54));lp.setMargins(0,dp(18),0,0);root.addView(t,lp);}
    private LinearLayout row(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.HORIZONTAL);l.setGravity(Gravity.CENTER_VERTICAL);return l;}
    private EditText field(String hint){EditText e=new EditText(this);e.setHint(hint);e.setHintTextColor(Color.rgb(145,154,168));e.setTextColor(Color.WHITE);e.setSingleLine(true);e.setPadding(dp(16),0,dp(16),0);e.setBackground(bg(Color.rgb(21,26,35),9));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(56));lp.setMargins(0,dp(7),0,dp(7));root.addView(e,lp);return e;}
    private EditText fieldMulti(String hint){EditText e=new EditText(this);e.setHint(hint);e.setHintTextColor(Color.rgb(145,154,168));e.setTextColor(Color.WHITE);e.setMinLines(3);e.setGravity(Gravity.TOP);e.setPadding(dp(16),dp(12),dp(16),dp(12));e.setBackground(bg(Color.rgb(21,26,35),9));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(110));lp.setMargins(0,dp(7),0,dp(7));root.addView(e,lp);return e;}
    private EditText smallField(String hint,int width){EditText e=new EditText(this);e.setHint(hint);e.setHintTextColor(Color.rgb(145,154,168));e.setTextColor(Color.WHITE);e.setSingleLine(true);e.setPadding(dp(12),0,dp(12),0);e.setBackground(bg(Color.rgb(21,26,35),9));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(dp(width),dp(56));lp.setMargins(0,dp(5),dp(10),dp(5));e.setLayoutParams(lp);return e;}
    private Button button(String s){Button b=new Button(this);b.setText(s);b.setTextColor(Color.WHITE);b.setBackground(bg(Color.rgb(124,77,255),9));b.setFocusable(true);return b;}
    private Button smallButton(String s){Button b=button(s);b.setTextSize(13);return b;}
    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
    private String localIp(){try{Enumeration<NetworkInterface> ifs=NetworkInterface.getNetworkInterfaces();while(ifs.hasMoreElements()){NetworkInterface ni=ifs.nextElement();Enumeration<java.net.InetAddress> as=ni.getInetAddresses();while(as.hasMoreElements()){java.net.InetAddress a=as.nextElement();if(!a.isLoopbackAddress()&&a instanceof Inet4Address)return a.getHostAddress();}}}catch(Exception ignored){}return "IP-DO-ADMIN";}
}
