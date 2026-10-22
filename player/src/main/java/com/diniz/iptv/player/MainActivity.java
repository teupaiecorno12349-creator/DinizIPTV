package com.diniz.iptv.player;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.*;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final int REQ_SETTINGS=410;
    private static final String ACCOUNT="xtream_account";
    private final ExecutorService io=Executors.newFixedThreadPool(3);
    private final ExecutorService categoryIo=Executors.newSingleThreadExecutor();
    private SharedPreferences account;
    private LinearLayout root,categoryList,contentArea;
    private GridLayout grid;
    private ScrollView contentScroll,categoryScroll;
    private final Map<String,Button> tabButtons=new LinkedHashMap<>();
    private static final int GRID_BATCH_SIZE=18;
    private List<ContentItem> displayedItems=Collections.emptyList();
    private int gridRenderVersion=0,categoryRenderVersion=0,renderedCount=0;
    private boolean appendingGridBatch=false;
    private EditText serverField,userField,passwordField,searchField;
    private TextView statusView,contentTitle,contentCount,loadingTitle,loadingMessage;
    private ProgressBar loadingBar;
    private String currentTab="none",currentCategory="all",currentQuery="";
    private Button refreshButton;
    private TextView footerHint;
    private long lastRenderedCatalogRefresh=0L;
    private CatalogLoader loader;
    private XtreamClient api;
    private int accent=Color.rgb(79,130,255),bgColor=Color.rgb(7,10,16),panel=Color.rgb(17,24,35),muted=Color.rgb(160,172,191);
    private final SharedPreferences.OnSharedPreferenceChangeListener unused=(p,k)->{};

    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        immersive();
        CatalogRefreshScheduler.onAppLaunched(this);
        account=getSharedPreferences(ACCOUNT,MODE_PRIVATE);
        NetworkStack.apply(this,NetworkStack.mode(this));
        boolean hasCache=CatalogStore.load(this);
        lastRenderedCatalogRefresh=getSharedPreferences("marcin_catalog_state",MODE_PRIVATE).getLong("last_successful_sync",0L);
        String savedBase=account.getString("server",""),savedUser=account.getString("username",""),savedPass=account.getString("password","");
        if(hasCache&&!savedBase.isEmpty()&&!savedUser.isEmpty()&&!savedPass.isEmpty()){
            api=new XtreamClient(this,savedBase,savedUser,savedPass);
            showHome();
        }else showLogin(null);
    }

    private void immersive(){
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY|View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN|View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
    }
    private int dp(float v){return Math.round(v*getResources().getDisplayMetrics().density);}
    private TextView text(String s,int size){TextView t=new TextView(this);t.setText(s);t.setTextColor(Color.WHITE);t.setTextSize(size);return t;}
    private GradientDrawable rounded(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
    private void focusStyle(View v,int normal,int focused){
        v.setBackground(rounded(normal,10));
        v.setOnFocusChangeListener((view,has)->{
            view.setBackground(rounded(has?focused:normal,10));
            view.animate().scaleX(has?1.025f:1f).scaleY(has?1.025f:1f).setDuration(110).start();
        });
    }
    private Button button(String label,int size){
        Button b=new Button(this);b.setText(label);b.setTextSize(size);b.setTextColor(Color.WHITE);b.setAllCaps(false);b.setFocusable(true);b.setGravity(Gravity.CENTER);
        b.setPadding(dp(10),0,dp(10),0);focusStyle(b,panel,accent);return b;
    }

    private void showLogin(String error){
        ScrollView scroll=new ScrollView(this);
        LinearLayout page=new LinearLayout(this);page.setOrientation(LinearLayout.VERTICAL);page.setGravity(Gravity.CENTER_HORIZONTAL);
        page.setPadding(dp(56),dp(24),dp(56),dp(28));page.setBackgroundColor(bgColor);scroll.addView(page);
        LinearLayout brandRow=new LinearLayout(this);brandRow.setGravity(Gravity.CENTER_VERTICAL);page.addView(brandRow,new LinearLayout.LayoutParams(Math.min(dp(800),getResources().getDisplayMetrics().widthPixels-dp(120)),dp(76)));
        TextView logo=text("MARCIN",31);logo.setTypeface(Typeface.DEFAULT_BOLD);logo.setTextColor(accent);brandRow.addView(logo);
        TextView sub=text(" IPTV",28);sub.setTypeface(Typeface.DEFAULT_BOLD);brandRow.addView(sub);
        View spacer=new View(this);brandRow.addView(spacer,new LinearLayout.LayoutParams(0,1,1));
        Button settings=button("⚙",24);brandRow.addView(settings,new LinearLayout.LayoutParams(dp(64),dp(58)));
        settings.setOnClickListener(v->openSettings());
        TextView headline=text("Seu entretenimento, organizado.",22);headline.setGravity(Gravity.CENTER);page.addView(headline,new LinearLayout.LayoutParams(-1,dp(48)));
        TextView hint=text("O catálogo abre primeiro; sinopses e episódios são consultados ao selecionar cada título.",15);hint.setTextColor(muted);hint.setGravity(Gravity.CENTER);page.addView(hint,new LinearLayout.LayoutParams(-1,dp(35)));
        LinearLayout form=new LinearLayout(this);form.setOrientation(LinearLayout.VERTICAL);form.setPadding(dp(26),dp(20),dp(26),dp(22));form.setBackground(rounded(panel,16));
        LinearLayout.LayoutParams fp=new LinearLayout.LayoutParams(Math.min(dp(690),getResources().getDisplayMetrics().widthPixels-dp(150)),-2);fp.setMargins(0,dp(15),0,0);page.addView(form,fp);
        serverField=field(form,"URL do servidor (ex.: http://servidor:porta)",InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_URI);
        serverField.setText(account.getString("server",""));
        userField=field(form,"Nome de usuário",InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        userField.setText(account.getString("username",""));
        passwordField=field(form,"Senha",InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);
        passwordField.setText(account.getString("password",""));
        LinearLayout dnsRow=new LinearLayout(this);dnsRow.setGravity(Gravity.CENTER_VERTICAL);form.addView(dnsRow,new LinearLayout.LayoutParams(-1,dp(48)));
        TextView dnsLabel=text("DNS do aplicativo",14);dnsLabel.setTextColor(muted);dnsRow.addView(dnsLabel,new LinearLayout.LayoutParams(0,-2,1));
        Spinner dns=new Spinner(this);String[] names={"DNS automático do Android","Cloudflare DNS","Google DNS"};
        dns.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,names));
        String mode=NetworkStack.mode(this);dns.setSelection("google".equals(mode)?2:("cloudflare".equals(mode)?1:0));
        dnsRow.addView(dns,new LinearLayout.LayoutParams(dp(285),dp(44)));
        dns.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){
            @Override public void onItemSelected(android.widget.AdapterView<?> p,View v,int pos,long id){NetworkStack.apply(MainActivity.this,pos==1?"cloudflare":(pos==2?"google":"system"));}
            @Override public void onNothingSelected(android.widget.AdapterView<?> p){}
        });
        Button login=button("CONECTAR E CARREGAR CATÁLOGO",17);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(58));lp.setMargins(0,dp(14),0,0);form.addView(login,lp);
        statusView=text(error==null?"A lista, capas e sinopses serão carregadas antes de abrir a biblioteca.":error,14);statusView.setTextColor(error==null?muted:Color.rgb(255,138,138));statusView.setGravity(Gravity.CENTER);statusView.setPadding(dp(8),dp(10),dp(8),dp(7));form.addView(statusView,new LinearLayout.LayoutParams(-1,-2));
        login.setOnClickListener(v->{
            String base=XtreamClient.normalizeBase(serverField.getText().toString());
            String u=userField.getText().toString().trim(),p=passwordField.getText().toString();
            if(base.isEmpty()||u.isEmpty()||p.isEmpty()){statusView.setText("Preencha URL, usuário e senha.");return;}
            api=new XtreamClient(this,base,u,p);
            account.edit().putString("server",base).putString("username",u).putString("password",p).apply();
            startCatalogLoad(api,true);
        });
        setContentView(scroll);
        if(userField.getText().length()>0 && serverField.getText().length()>0)passwordField.requestFocus();else serverField.requestFocus();
    }

    private EditText field(LinearLayout parent,String hint,int inputType){
        EditText e=new EditText(this);e.setSingleLine(true);e.setTextSize(17);e.setTextColor(Color.WHITE);e.setHintTextColor(muted);e.setHint(hint);e.setInputType(inputType);
        e.setPadding(dp(16),0,dp(16),0);e.setBackground(rounded(Color.rgb(25,34,48),9));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(56));lp.setMargins(0,dp(6),0,dp(9));parent.addView(e,lp);return e;
    }

    private void startCatalogLoad(XtreamClient client,boolean loginFlow){
        if(loader!=null)loader.cancel();
        showLoading("Conectando à lista…","Validando sua conta e descobrindo categorias.",loginFlow);
        loader=new CatalogLoader(this,client,new CatalogLoader.Listener(){
            @Override public void onProgress(int done,int total,String message){runOnUiThread(()->{
                if(loadingMessage==null)return;loadingMessage.setText(message);
                if(total>0){loadingBar.setIndeterminate(false);loadingBar.setMax(100);loadingBar.setProgress(Math.max(0,Math.min(100,(int)(done*100L/total))));}
                else loadingBar.setIndeterminate(true);
                if(message.contains("Validando")||message.contains("Baixando"))loadingBar.setIndeterminate(true);
            });}
            @Override public void onComplete(){runOnUiThread(()->{if(isFinishing())return;loader=null;CatalogRefreshScheduler.onUserActivity(MainActivity.this);lastRenderedCatalogRefresh=getSharedPreferences("marcin_catalog_state",MODE_PRIVATE).getLong("last_successful_sync",0L);showHome();Toast.makeText(MainActivity.this,"Catálogo carregado: "+CatalogStore.CHANNELS.size()+" canais, "+CatalogStore.MOVIES.size()+" filmes e "+CatalogStore.SERIES.size()+" séries.",Toast.LENGTH_LONG).show();});}
            @Override public void onError(String message){runOnUiThread(()->{loader=null;if(!CatalogStore.CHANNELS.isEmpty()||!CatalogStore.MOVIES.isEmpty()||!CatalogStore.SERIES.isEmpty()){showHome();Toast.makeText(MainActivity.this,"Não foi possível atualizar: "+message,Toast.LENGTH_LONG).show();}else showLogin(message);});}
        });
        loader.start();
    }

    private void showLoading(String title,String message,boolean loginFlow){
        LinearLayout page=new LinearLayout(this);page.setOrientation(LinearLayout.VERTICAL);page.setGravity(Gravity.CENTER);page.setPadding(dp(55),dp(40),dp(55),dp(40));page.setBackgroundColor(bgColor);
        TextView logo=text("MARCIN IPTV",30);logo.setTypeface(Typeface.DEFAULT_BOLD);logo.setTextColor(accent);logo.setGravity(Gravity.CENTER);page.addView(logo,new LinearLayout.LayoutParams(-1,dp(70)));
        loadingTitle=text(title,23);loadingTitle.setTypeface(Typeface.DEFAULT_BOLD);loadingTitle.setGravity(Gravity.CENTER);page.addView(loadingTitle,new LinearLayout.LayoutParams(-1,dp(55)));
        loadingMessage=text(message,16);loadingMessage.setTextColor(muted);loadingMessage.setGravity(Gravity.CENTER);loadingMessage.setPadding(0,dp(10),0,dp(16));page.addView(loadingMessage,new LinearLayout.LayoutParams(-1,-2));
        loadingBar=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);loadingBar.setIndeterminate(true);LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(Math.min(dp(620),getResources().getDisplayMetrics().widthPixels-dp(130)),dp(10));bp.setMargins(0,dp(10),0,0);page.addView(loadingBar,bp);
        TextView foot=text("As informações são obtidas diretamente do seu servidor Xtream Codes.",13);foot.setTextColor(muted);foot.setGravity(Gravity.CENTER);LinearLayout.LayoutParams hp=new LinearLayout.LayoutParams(-1,dp(52));hp.setMargins(0,dp(20),0,0);page.addView(foot,hp);
        setContentView(page);
    }

    private void showHome(){
        immersive();
        root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(20),dp(12),dp(20),dp(12));root.setBackgroundColor(bgColor);
        LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);root.addView(header,new LinearLayout.LayoutParams(-1,dp(66)));
        LinearLayout branding=new LinearLayout(this);branding.setOrientation(LinearLayout.VERTICAL);branding.setGravity(Gravity.CENTER_VERTICAL);header.addView(branding,new LinearLayout.LayoutParams(dp(205),-1));
        TextView logo=text("MARCIN IPTV",23);logo.setTextColor(accent);logo.setTypeface(Typeface.DEFAULT_BOLD);branding.addView(logo);
        TextView strap=text("XTREAM PLAYER",10);strap.setTextColor(muted);branding.addView(strap);
        addTab(header,"📺  CANAIS","live");addTab(header,"🎬  FILMES","movie");addTab(header,"📚  SÉRIES","series");
        View flex=new View(this);header.addView(flex,new LinearLayout.LayoutParams(0,1,1));
        searchField=new EditText(this);searchField.setSingleLine(true);searchField.setImeOptions(EditorInfo.IME_ACTION_SEARCH);searchField.setTextSize(16);searchField.setTextColor(Color.WHITE);searchField.setHintTextColor(muted);searchField.setHint("⌕  Buscar nesta aba…");searchField.setPadding(dp(14),0,dp(14),0);searchField.setBackground(rounded(panel,10));
        LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(dp(280),dp(48));sp.setMargins(dp(8),0,dp(8),0);header.addView(searchField,sp);
        Button search=button("⌕",23);header.addView(search,new LinearLayout.LayoutParams(dp(54),dp(48)));search.setOnClickListener(v->{currentQuery=searchField.getText().toString().trim();renderGrid();});
        Button gear=button("⚙",23);LinearLayout.LayoutParams gp=new LinearLayout.LayoutParams(dp(54),dp(48));gp.setMargins(dp(8),0,0,0);header.addView(gear,gp);gear.setContentDescription("Configurações");gear.setOnClickListener(v->openSettings());

        View line=new View(this);line.setBackgroundColor(Color.rgb(38,48,64));root.addView(line,new LinearLayout.LayoutParams(-1,dp(1)));
        LinearLayout body=new LinearLayout(this);body.setOrientation(LinearLayout.HORIZONTAL);LinearLayout.LayoutParams bodylp=new LinearLayout.LayoutParams(-1,0,1);bodylp.setMargins(0,dp(10),0,0);root.addView(body,bodylp);
        categoryScroll=new ScrollView(this);categoryScroll.setFillViewport(true);categoryScroll.setBackground(rounded(Color.rgb(12,17,25),12));LinearLayout.LayoutParams clp=new LinearLayout.LayoutParams(dp(238),-1);clp.setMargins(0,0,dp(14),0);body.addView(categoryScroll,clp);
        categoryList=new LinearLayout(this);categoryList.setOrientation(LinearLayout.VERTICAL);categoryList.setPadding(dp(10),dp(12),dp(10),dp(12));categoryScroll.addView(categoryList);
        contentArea=new LinearLayout(this);contentArea.setOrientation(LinearLayout.VERTICAL);body.addView(contentArea,new LinearLayout.LayoutParams(0,-1,1));
        LinearLayout titleRow=new LinearLayout(this);titleRow.setGravity(Gravity.CENTER_VERTICAL);contentArea.addView(titleRow,new LinearLayout.LayoutParams(-1,dp(42)));
        contentTitle=text("",23);contentTitle.setTypeface(Typeface.DEFAULT_BOLD);titleRow.addView(contentTitle,new LinearLayout.LayoutParams(0,-2,1));
        contentCount=text("",13);contentCount.setTextColor(muted);titleRow.addView(contentCount);
        contentScroll=new ScrollView(this);contentScroll.setFillViewport(false);contentScroll.setVerticalScrollBarEnabled(false);contentArea.addView(contentScroll,new LinearLayout.LayoutParams(-1,0,1));
        grid=new GridLayout(this);grid.setColumnCount(gridColumns());grid.setUseDefaultMargins(false);contentScroll.addView(grid,new ScrollView.LayoutParams(-1,-2));
        contentScroll.setOnScrollChangeListener((View v,int sx,int sy,int oldSx,int oldSy)->{
            if(contentScroll!=null&&grid!=null&&sy+contentScroll.getHeight()>=grid.getHeight()-dp(220))
                appendGridBatch(gridRenderVersion);
        });
        searchField.setOnEditorActionListener((v,id,event)->{
            boolean enter=id==EditorInfo.IME_ACTION_SEARCH||id==EditorInfo.IME_ACTION_DONE||id==EditorInfo.IME_ACTION_GO
                    ||(event!=null&&event.getKeyCode()==KeyEvent.KEYCODE_ENTER);
            if(enter){currentQuery=searchField.getText().toString().trim();renderGrid();return true;}
            return false;
        });
        searchField.setOnKeyListener((v,keyCode,event)->{
            if((keyCode==KeyEvent.KEYCODE_ENTER||keyCode==KeyEvent.KEYCODE_NUMPAD_ENTER)
                    &&event.getAction()==KeyEvent.ACTION_UP){
                currentQuery=searchField.getText().toString().trim();renderGrid();return true;
            }
            return false;
        });
        currentCategory="all";
        LinearLayout footer=new LinearLayout(this);
        footer.setGravity(Gravity.CENTER_VERTICAL);
        footer.setPadding(dp(4),dp(5),dp(4),0);
        footerHint=text("Escolha Canais, Filmes ou Séries para abrir a biblioteca.",12);
        footerHint.setTextColor(muted);
        footer.addView(footerHint,new LinearLayout.LayoutParams(0,-2,1));
        refreshButton=button("↻  ATUALIZAR ABA",14);
        LinearLayout.LayoutParams refreshParams=new LinearLayout.LayoutParams(dp(205),dp(42));
        refreshParams.setMargins(dp(8),0,0,0);
        footer.addView(refreshButton,refreshParams);
        refreshButton.setOnClickListener(v->refreshCurrentTab());
        root.addView(footer,new LinearLayout.LayoutParams(-1,dp(50)));
        updateTabStyles();
        updateFooter();
        renderGrid();renderCategories();setContentView(root);
        lastRenderedCatalogRefresh=getSharedPreferences("marcin_catalog_state",MODE_PRIVATE).getLong("last_successful_sync",lastRenderedCatalogRefresh);
        // Reapply after setContentView, since this can reset focus on some Android TV launchers.
        immersive();
    }

    private void addTab(LinearLayout header,String name,String type){
        Button b=button(name,14);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(dp(116),dp(46));
        lp.setMargins(dp(3),0,dp(3),0);header.addView(b,lp);
        tabButtons.put(type,b);
        b.setOnClickListener(v->selectTab(type));
        b.setOnFocusChangeListener((view,hasFocus)->{
            int normal=tabColor(type);
            int color=hasFocus?accent:(currentTab.equals(type)?Color.rgb(36,55,88):normal);
            view.setBackground(rounded(color,10));
            view.animate().scaleX(hasFocus?1.025f:1f).scaleY(hasFocus?1.025f:1f).setDuration(110).start();
        });
    }

    private int tabColor(String type){
        return "live".equals(type)?Color.rgb(14,82,88):("movie".equals(type)?Color.rgb(104,61,27):Color.rgb(73,43,119));
    }

    private void updateTabStyles(){
        for(Map.Entry<String,Button> entry:tabButtons.entrySet()){
            String type=entry.getKey();Button b=entry.getValue();
            b.setVisibility("none".equals(currentTab)?View.GONE:View.VISIBLE);
            int color=b.hasFocus()?accent:(currentTab.equals(type)?Color.rgb(36,55,88):tabColor(type));
            b.setBackground(rounded(color,10));
        }
    }

    private void selectTab(String type){
        if(type==null||!("live".equals(type)||"movie".equals(type)||"series".equals(type)))return;
        currentTab=type;
        currentCategory="all";
        currentQuery=searchField==null?"":searchField.getText().toString().trim();
        if(categoryScroll!=null)categoryScroll.setVisibility(View.VISIBLE);
        if(grid!=null)grid.setColumnCount(gridColumns());
        updateTabStyles();
        updateFooter();
        renderCategories();
        renderGrid();
    }

    private void showSectionPicker(int generation){
        if(grid==null||generation!=gridRenderVersion)return;
        int channels,movies,series;
        synchronized(CatalogStore.class){
            channels=CatalogStore.CHANNELS.size();
            movies=CatalogStore.MOVIES.size();
            series=CatalogStore.SERIES.size();
        }
        grid.removeAllViews();
        grid.setColumnCount(3);
        String[] types={"live","movie","series"};
        String[] icons={"📺","🎬","📚"};
        String[] names={"CANAIS","FILMES","SÉRIES"};
        String[] details={channels+" canais disponíveis",movies+" filmes disponíveis",series+" séries disponíveis"};
        int[] tints={Color.rgb(14,82,88),Color.rgb(104,61,27),Color.rgb(73,43,119)};
        for(int i=0;i<types.length;i++){
            final String type=types[i];
            LinearLayout tile=new LinearLayout(this);
            tile.setOrientation(LinearLayout.VERTICAL);
            tile.setGravity(Gravity.CENTER);
            tile.setPadding(dp(18),dp(18),dp(18),dp(18));
            tile.setFocusable(true);tile.setClickable(true);
            tile.setBackground(rounded(tints[i],18));
            LinearLayout.LayoutParams iconParams=new LinearLayout.LayoutParams(-1,dp(80));
            TextView icon=text(icons[i],54);icon.setGravity(Gravity.CENTER);tile.addView(icon,iconParams);
            TextView title=text(names[i],27);title.setTypeface(Typeface.DEFAULT_BOLD);title.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams titleParams=new LinearLayout.LayoutParams(-1,dp(48));titleParams.setMargins(0,dp(6),0,0);tile.addView(title,titleParams);
            TextView count=text(details[i],15);count.setTextColor(Color.rgb(225,232,245));count.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams countParams=new LinearLayout.LayoutParams(-1,dp(42));countParams.setMargins(0,dp(4),0,dp(18));tile.addView(count,countParams);
            Button refresh=button("↻  ATUALIZAR",15);
            refresh.setBackground(rounded(Color.rgb(27,38,58),10));
            LinearLayout.LayoutParams refreshParams=new LinearLayout.LayoutParams(-1,dp(54));
            tile.addView(refresh,refreshParams);
            refresh.setOnClickListener(v->refreshSection(type,refresh,count));
            focusStyle(tile,tints[i],accent);
            tile.setOnClickListener(v->selectTab(type));
            GridLayout.LayoutParams tileParams=new GridLayout.LayoutParams();
            tileParams.rowSpec=GridLayout.spec(0);
            tileParams.columnSpec=GridLayout.spec(i,1,GridLayout.FILL,1f);
            tileParams.width=0;
            tileParams.height=dp(330);
            tileParams.setMargins(dp(10),dp(12),dp(10),dp(12));
            grid.addView(tile,tileParams);
        }
    }

    private void refreshSection(String type,Button refresh,TextView countLabel){
        String server=account.getString("server",""),user=account.getString("username",""),pass=account.getString("password","");
        if(server.isEmpty()||user.isEmpty()||pass.isEmpty()){
            Toast.makeText(this,"Entre com sua conta Xtream Codes.",Toast.LENGTH_LONG).show();return;
        }
        refresh.setEnabled(false);refresh.setText("ATUALIZANDO…");
        io.execute(()->{
            String message=null;
            try{
                CatalogSync.refreshTab(MainActivity.this,new XtreamClient(MainActivity.this,server,user,pass),type);
            }catch(Exception error){
                message="Não foi possível atualizar essa lista agora.";
            }
            final String resultMessage=message;
            runOnUiThread(()->{
                if(isFinishing()||isDestroyed())return;
                refresh.setEnabled(true);refresh.setText("↻  ATUALIZAR");
                int count;
                synchronized(CatalogStore.class){count=CatalogStore.list(type).size();}
                String noun="live".equals(type)?" canais":("movie".equals(type)?" filmes":" séries");
                countLabel.setText(count+noun+" disponíveis");
                if(type.equals(currentTab)){renderCategories();renderGrid();}
                if("none".equals(currentTab))showSectionPicker(gridRenderVersion);
                updateFooter();
                Toast.makeText(MainActivity.this,resultMessage==null?"Lista atualizada.":resultMessage,Toast.LENGTH_SHORT).show();
            });
        });
    }
    private int gridColumns(){int w=getResources().getConfiguration().screenWidthDp;return Math.max(3,Math.min(6,(w-290)/205));}

    private static final class CategoryRow {
        final String name,key;
        final int count;
        final boolean heading;
        CategoryRow(String name,String key,int count,boolean heading){this.name=name;this.key=key;this.count=count;this.heading=heading;}
    }

    private void renderCategories(){
        if(categoryList==null)return;
        final int generation=++categoryRenderVersion;
        final String tab=currentTab;
        if("none".equals(tab)){
            categoryList.removeAllViews();
            return;
        }
        int initialCount;
        List<CatalogCategory> cachedCategories;
        synchronized(CatalogStore.class){
            initialCount=CatalogStore.list(tab).size();
            List<CatalogCategory> saved=CatalogStore.CATEGORIES.get(tab);
            cachedCategories=saved==null?new ArrayList<>():new ArrayList<>(saved);
        }
        // Immediately replace the old tab's sidebar with the new tab's names.
        // Counts/years are recalculated on the dedicated worker afterwards.
        categoryList.removeAllViews();
        TextView library=text("BIBLIOTECA",11);library.setTextColor(muted);library.setTypeface(Typeface.DEFAULT_BOLD);
        library.setPadding(dp(10),dp(3),0,dp(10));categoryList.addView(library);
        addCategory("Todos","all",initialCount);
        addCategory("★  Favoritos","favorites",0);
        if(!"live".equals(tab))addCategory("✦  Lançamentos","new",0);
        TextView section=text("CATEGORIAS",11);section.setTextColor(muted);section.setTypeface(Typeface.DEFAULT_BOLD);
        section.setPadding(dp(10),dp(15),0,dp(10));categoryList.addView(section);
        for(CatalogCategory category:cachedCategories)addCategory(category.name,"cat:"+category.id,0);
        TextView loading=text("Contagens atualizando…",11);loading.setTextColor(muted);
        loading.setPadding(dp(10),dp(10),0,dp(8));categoryList.addView(loading);

        categoryIo.execute(()->{
            List<ContentItem> source;
            List<CatalogCategory> categories;
            synchronized(CatalogStore.class){
                source=new ArrayList<>(CatalogStore.list(tab));
                List<CatalogCategory> saved=CatalogStore.CATEGORIES.get(tab);
                categories=saved==null?new ArrayList<>():new ArrayList<>(saved);
            }
            SharedPreferences favorites=getSharedPreferences("marcin_favorites",MODE_PRIVATE);
            Map<String,Integer> categoryCounts=new HashMap<>();
            TreeMap<Integer,Integer> yearCounts=new TreeMap<>(Collections.reverseOrder());
            int favoriteTotal=0,newTotal=0;
            int calendarYear=java.util.Calendar.getInstance().get(java.util.Calendar.YEAR);
            for(ContentItem item:source){
                if(favorites.getBoolean(item.key(),false))favoriteTotal++;
                if(isNew(item))newTotal++;
                if(item.categoryId!=null&&!item.categoryId.isEmpty())
                    categoryCounts.put(item.categoryId,categoryCounts.getOrDefault(item.categoryId,0)+1);
                if(item.year!=null&&item.year.matches("\\d{4}")){
                    try{int y=Integer.parseInt(item.year);yearCounts.put(y,yearCounts.getOrDefault(y,0)+1);}catch(Exception ignored){}
                }
            }
            if(yearCounts.isEmpty()&&!"live".equals(tab))
                for(int y=calendarYear;y>=calendarYear-5;y--)yearCounts.put(y,0);
            List<CategoryRow> rows=new ArrayList<>();
            rows.add(new CategoryRow("BIBLIOTECA","",0,true));
            rows.add(new CategoryRow("Todos","all",source.size(),false));
            rows.add(new CategoryRow("★  Favoritos","favorites",favoriteTotal,false));
            if(!"live".equals(tab)){
                rows.add(new CategoryRow("✦  Lançamentos","new",newTotal,false));
                for(Map.Entry<Integer,Integer> year:yearCounts.entrySet())
                    rows.add(new CategoryRow(("series".equals(tab)?"Séries ":"Filmes ")+year.getKey(),"year:"+year.getKey(),year.getValue(),false));
            }
            rows.add(new CategoryRow("CATEGORIAS","",0,true));
            for(CatalogCategory category:categories)
                rows.add(new CategoryRow(category.name,"cat:"+category.id,categoryCounts.getOrDefault(category.id,0),false));
            runOnUiThread(()->{
                if(isFinishing()||isDestroyed()||generation!=categoryRenderVersion||!tab.equals(currentTab)||categoryList==null)return;
                categoryList.removeAllViews();
                for(CategoryRow row:rows){
                    if(row.heading){
                        TextView heading=text(row.name,11);heading.setTextColor(muted);heading.setTypeface(Typeface.DEFAULT_BOLD);
                        heading.setPadding(dp(10),dp("BIBLIOTECA".equals(row.name)?3:15),0,dp(10));categoryList.addView(heading);
                    }else addCategory(row.name,row.key,row.count);
                }
            });
        });
    }
    private void addCategory(String name,String key,int count){
        LinearLayout row=new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(13),0,dp(11),0);
        row.setFocusable(true);
        row.setClickable(true);
        row.setTag(key);
        row.setContentDescription(name+", "+count+" itens");
        TextView categoryName=text(name,14);
        categoryName.setSingleLine(true);
        categoryName.setEllipsize(android.text.TextUtils.TruncateAt.END);
        row.addView(categoryName,new LinearLayout.LayoutParams(0,-2,1));
        TextView countLabel=text(Integer.toString(count),12);
        countLabel.setTextColor(muted);
        LinearLayout.LayoutParams countParams=new LinearLayout.LayoutParams(-2,-2);
        countParams.setMargins(dp(8),0,0,0);
        row.addView(countLabel,countParams);
        row.setBackground(rounded(currentCategory.equals(key)?Color.rgb(36,55,88):Color.TRANSPARENT,10));
        row.setOnFocusChangeListener((view,hasFocus)->{
            int color=hasFocus?Color.rgb(41,63,101):(currentCategory.equals(key)?Color.rgb(36,55,88):Color.TRANSPARENT);
            view.setBackground(rounded(color,10));
            view.animate().scaleX(hasFocus?1.015f:1f).scaleY(hasFocus?1.015f:1f).setDuration(100).start();
        });
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(46));
        lp.setMargins(0,dp(2),0,dp(2));
        categoryList.addView(row,lp);
        row.setOnClickListener(v->{
            currentCategory=key;
            updateCategorySelection();
            renderGrid();
        });
    }

    private void updateCategorySelection(){
        if(categoryList==null)return;
        for(int i=0;i<categoryList.getChildCount();i++){
            View child=categoryList.getChildAt(i);
            Object value=child.getTag();
            if(!(value instanceof String))continue;
            String key=(String)value;
            int color=child.hasFocus()?Color.rgb(41,63,101):(currentCategory.equals(key)?Color.rgb(36,55,88):Color.TRANSPARENT);
            child.setBackground(rounded(color,10));
        }
    }
    private int favoriteCount(){int n=0;for(ContentItem x:CatalogStore.list(currentTab))if(isFavorite(x))n++;return n;}
    private int newCount(){int n=0;for(ContentItem x:CatalogStore.list(currentTab))if(isNew(x))n++;return n;}
    private int countForYear(int y){int n=0;for(ContentItem x:CatalogStore.list(currentTab))if(Integer.toString(y).equals(x.year))n++;return n;}
    private int countForCategory(String id){int n=0;for(ContentItem x:CatalogStore.list(currentTab))if(id.equals(x.categoryId))n++;return n;}

    private void renderGrid(){
        if(grid==null)return;
        final int generation=++gridRenderVersion;
        final String tab=currentTab,selected=currentCategory;
        if("none".equals(tab)){
            if(categoryScroll!=null)categoryScroll.setVisibility(View.GONE);
            displayedItems=Collections.emptyList();renderedCount=0;appendingGridBatch=false;
            contentTitle.setText("O QUE VOCÊ QUER ASSISTIR?");
            contentCount.setText("");
            if(contentScroll!=null)contentScroll.scrollTo(0,0);
            showSectionPicker(generation);
            return;
        }
        if(categoryScroll!=null)categoryScroll.setVisibility(View.VISIBLE);
        grid.setColumnCount(gridColumns());
        final String query=currentQuery==null?"":currentQuery.trim().toLowerCase(Locale.ROOT);
        grid.removeAllViews();
        displayedItems=Collections.emptyList();
        renderedCount=0;
        appendingGridBatch=false;
        grid.setColumnCount(gridColumns());
        if(contentScroll!=null)contentScroll.scrollTo(0,0);
        contentTitle.setText(("live".equals(tab)?"Canais":("movie".equals(tab)?"Filmes":"Séries"))+(categoryLabel().isEmpty()?"":"  ·  "+categoryLabel()));
        contentCount.setText("Carregando…");
        showGridMessage("Preparando catálogo…");
        io.execute(()->{
            List<ContentItem> source;
            synchronized(CatalogStore.class){source=new ArrayList<>(CatalogStore.list(tab));}
            SharedPreferences favorites=getSharedPreferences("marcin_favorites",MODE_PRIVATE);
            List<ContentItem> items=new ArrayList<>();
            for(ContentItem item:source){
                if("favorites".equals(selected)&&!favorites.getBoolean(item.key(),false))continue;
                if("new".equals(selected)&&!isNew(item))continue;
                if(selected.startsWith("year:")&&!selected.substring(5).equals(item.year))continue;
                if(selected.startsWith("cat:")&&!selected.substring(4).equals(item.categoryId))continue;
                if(!query.isEmpty()){
                    String hay=(item.title+" "+item.year+" "+item.genres+" "+item.description+" "+item.categoryName+" "+item.director+" "+item.cast).toLowerCase(Locale.ROOT);
                    if(!hay.contains(query))continue;
                }
                items.add(item);
            }
            if("new".equals(selected))items.sort((a,b)->Long.compare(parseAdded(b.added),parseAdded(a.added)));
            else if("all".equals(selected)&&query.isEmpty())items.sort((a,b)->a.title.compareToIgnoreCase(b.title));
            else if(!query.isEmpty()&&items.size()<5000)items.sort((a,b)->a.title.compareToIgnoreCase(b.title));
            final List<ContentItem> result=items;
            final boolean sourceWasEmpty=source.isEmpty();
            runOnUiThread(()->{
                if(isFinishing()||isDestroyed()||generation!=gridRenderVersion
                        ||!tab.equals(currentTab)||!selected.equals(currentCategory))return;
                displayedItems=result;
                contentCount.setText(result.size()+" itens");
                grid.removeAllViews();
                if(result.isEmpty()){
                    showGridMessage(sourceWasEmpty?"Nenhum conteúdo carregado. Entre com uma conta Xtream Codes ou atualize o catálogo.":"Nada encontrado nesta categoria.");
                    return;
                }
                appendGridBatch(generation);
            });
        });
    }

    private void showGridMessage(String message){
        if(grid==null)return;
        grid.removeAllViews();
        TextView empty=text(message,18);
        empty.setTextColor(muted);empty.setGravity(Gravity.CENTER);
        GridLayout.LayoutParams p=new GridLayout.LayoutParams();
        p.width=-1;p.height=dp(170);p.columnSpec=GridLayout.spec(0,gridColumns());
        grid.addView(empty,p);
    }

    private void appendGridBatch(int generation){
        if(grid==null||appendingGridBatch||generation!=gridRenderVersion
                ||renderedCount>=displayedItems.size())return;
        appendingGridBatch=true;
        int end=Math.min(displayedItems.size(),renderedCount+GRID_BATCH_SIZE);
        while(renderedCount<end){
            ContentItem item=displayedItems.get(renderedCount++);
            grid.addView(card(item));
        }
        appendingGridBatch=false;
    }
    private String categoryLabel(){
        if("all".equals(currentCategory))return "";
        if("favorites".equals(currentCategory))return "Favoritos";
        if("new".equals(currentCategory))return "Lançamentos";
        if(currentCategory.startsWith("year:"))return currentCategory.substring(5);
        if(currentCategory.startsWith("cat:")){List<CatalogCategory> list=CatalogStore.CATEGORIES.get(currentTab);if(list!=null)for(CatalogCategory c:list)if(c.id.equals(currentCategory.substring(4)))return c.name;}
        return "";
    }
    private long parseAdded(String s){try{return Long.parseLong(s);}catch(Exception e){return 0;}}
    private boolean isNew(ContentItem x){
        long added=parseAdded(x.added);if(added>0){if(added>100000000000L)added/=1000L;long now=System.currentTimeMillis()/1000L;return added<=now&&now-added<60L*86400L;}
        int y=0;try{y=Integer.parseInt(x.year);}catch(Exception ignored){}
        return y>=java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)-1;
    }
    private boolean isFavorite(ContentItem x){return getSharedPreferences("marcin_favorites",MODE_PRIVATE).getBoolean(x.key(),false);}
    private void toggleFavorite(ContentItem x,TextView star){
        SharedPreferences f=getSharedPreferences("marcin_favorites",MODE_PRIVATE);boolean next=!f.getBoolean(x.key(),false);f.edit().putBoolean(x.key(),next).apply();star.setText(next?"★":"☆");star.setTextColor(next?Color.YELLOW:Color.WHITE);renderCategories();
    }

    private View card(ContentItem x){
        LinearLayout outer=new LinearLayout(this);outer.setOrientation(LinearLayout.VERTICAL);outer.setPadding(dp(7),dp(7),dp(7),dp(8));outer.setBackground(rounded(panel,12));outer.setFocusable(true);outer.setClickable(true);
        GridLayout.LayoutParams gp=new GridLayout.LayoutParams();gp.width=dp(181);gp.height=dp("live".equals(currentTab)?252:310);gp.setMargins(dp(5),dp(5),dp(5),dp(9));outer.setLayoutParams(gp);
        FrameLayout frame=new FrameLayout(this);frame.setBackground(rounded(Color.rgb(25,33,45),8));outer.addView(frame,new LinearLayout.LayoutParams(-1,dp("live".equals(currentTab)?156:214)));
        ImageView poster=new ImageView(this);poster.setScaleType(ImageView.ScaleType.CENTER_CROP);frame.addView(poster,new FrameLayout.LayoutParams(-1,-1));
        if(x.posterUrl!=null&&!x.posterUrl.isEmpty())ImageLoader.load(poster,x.posterUrl);
        else{TextView placeholder=text((x.title==null||x.title.isEmpty()?"M":x.title.substring(0,1)).toUpperCase(Locale.ROOT),42);placeholder.setTypeface(Typeface.DEFAULT_BOLD);placeholder.setTextColor(accent);placeholder.setGravity(Gravity.CENTER);frame.addView(placeholder,new FrameLayout.LayoutParams(-1,-1));}
        TextView star=text(isFavorite(x)?"★":"☆",22);star.setGravity(Gravity.CENTER);star.setTextColor(isFavorite(x)?Color.YELLOW:Color.WHITE);star.setBackground(rounded(Color.rgb(0,0,0),20));FrameLayout.LayoutParams slp=new FrameLayout.LayoutParams(dp(40),dp(40),Gravity.RIGHT|Gravity.TOP);slp.setMargins(0,dp(4),dp(4),0);frame.addView(star,slp);star.setFocusable(true);star.setClickable(true);star.setOnClickListener(v->toggleFavorite(x,star));
        TextView name=text(x.displayTitle(),14);name.setTypeface(Typeface.DEFAULT_BOLD);name.setMaxLines(2);name.setEllipsize(android.text.TextUtils.TruncateAt.END);name.setPadding(dp(3),dp(6),dp(3),0);outer.addView(name,new LinearLayout.LayoutParams(-1,dp(40)));
        String sub="live".equals(currentTab)?(x.categoryName==null?"":x.categoryName):((x.genres==null?"":x.genres)+(x.rating==null||x.rating.isEmpty()?"":"  ★ "+x.rating));
        TextView meta=text(sub,11);meta.setTextColor(muted);meta.setMaxLines(1);meta.setEllipsize(android.text.TextUtils.TruncateAt.END);meta.setPadding(dp(3),dp(2),dp(3),0);outer.addView(meta,new LinearLayout.LayoutParams(-1,dp(21)));
        focusStyle(outer,panel,Color.rgb(41,63,101));
        outer.setOnClickListener(v->{if("live".equals(currentTab))playItem(x);else{Intent i=new Intent(this,DetailActivity.class);i.putExtra("id",x.id);i.putExtra("type",currentTab);startActivity(i);}});
        outer.setLongClickable(true);
        outer.setOnLongClickListener(v->{
            boolean wasFavorite=isFavorite(x);
            toggleFavorite(x,star);
            Toast.makeText(MainActivity.this,wasFavorite?"Removido dos favoritos":"Adicionado aos favoritos",Toast.LENGTH_SHORT).show();
            return true;
        });
        return outer;
    }
    private void playItem(ContentItem item){
        Intent i=new Intent(this,PlaybackActivity.class);i.putExtra("url",item.streamUrl);i.putExtra("title",item.displayTitle());i.putExtra("content_type",item.type);startActivity(i);
    }

    private void updateFooter(){
        if(refreshButton==null||footerHint==null)return;
        boolean selected=!"none".equals(currentTab);
        refreshButton.setEnabled(selected);
        refreshButton.setVisibility(selected?View.VISIBLE:View.GONE);
        refreshButton.setAlpha(selected?1f:0.5f);
        String tabName="live".equals(currentTab)?"Canais":("movie".equals(currentTab)?"Filmes":("series".equals(currentTab)?"Séries":"nenhuma aba"));
        footerHint.setText(selected?"Aba atual: "+tabName+" · Atualização manual apenas desta aba.":"Use os cartões grandes acima para escolher uma lista; cada um tem atualização própria.");
    }

    private void refreshCurrentTab(){
        if("none".equals(currentTab)){
            Toast.makeText(this,"Primeiro escolha Canais, Filmes ou Séries.",Toast.LENGTH_SHORT).show();return;
        }
        String tab=currentTab;
        String server=account.getString("server",""),user=account.getString("username",""),pass=account.getString("password","");
        if(server.isEmpty()||user.isEmpty()||pass.isEmpty()){
            Toast.makeText(this,"Entre com sua conta Xtream Codes.",Toast.LENGTH_LONG).show();return;
        }
        if(refreshButton!=null){refreshButton.setEnabled(false);refreshButton.setText("ATUALIZANDO…");}
        if(footerHint!=null)footerHint.setText("Atualizando apenas "+("live".equals(tab)?"Canais":("movie".equals(tab)?"Filmes":"Séries"))+"…");
        io.execute(()->{
            try{
                CatalogSync.refreshTab(MainActivity.this,new XtreamClient(MainActivity.this,server,user,pass),tab);
                runOnUiThread(()->{
                    if(isFinishing()||isDestroyed())return;
                    if(refreshButton!=null){refreshButton.setEnabled(true);refreshButton.setText("↻  ATUALIZAR ABA");}
                    if(tab.equals(currentTab)){renderCategories();renderGrid();}
                    updateFooter();
                    Toast.makeText(MainActivity.this,"Aba atualizada com sucesso.",Toast.LENGTH_SHORT).show();
                });
            }catch(Exception error){
                runOnUiThread(()->{
                    if(isFinishing()||isDestroyed())return;
                    if(refreshButton!=null){refreshButton.setEnabled(true);refreshButton.setText("↻  ATUALIZAR ABA");}
                    updateFooter();
                    Toast.makeText(MainActivity.this,"Não foi possível atualizar essa aba agora.",Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void refreshUiIfCatalogChanged(){
        long latest=getSharedPreferences("marcin_catalog_state",MODE_PRIVATE).getLong("last_successful_sync",0L);
        if(latest<=lastRenderedCatalogRefresh)return;
        lastRenderedCatalogRefresh=latest;
        CatalogStore.load(this);
        if(root!=null){renderCategories();renderGrid();}
    }

    @Override protected void onResume(){
        super.onResume();
        refreshUiIfCatalogChanged();
    }

    @Override public void onUserInteraction(){
        super.onUserInteraction();
        CatalogRefreshScheduler.onUserActivity(this);
        refreshUiIfCatalogChanged();
    }

    private void openSettings(){Intent i=new Intent(this,SettingsActivity.class);startActivityForResult(i,REQ_SETTINGS);}
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);
        if(request!=REQ_SETTINGS||result!=RESULT_OK||data==null)return;
        if(data.getBooleanExtra("logout",false)){
            account.edit().clear().apply();CatalogStore.clear();showLogin(null);return;
        }
        if(data.getBooleanExtra("refresh",false)){
            String base=account.getString("server",""),u=account.getString("username",""),p=account.getString("password","");
            if(!base.isEmpty()&&!u.isEmpty()&&!p.isEmpty())startCatalogLoad(new XtreamClient(this,base,u,p),false);
            else showLogin("Entre com sua conta Xtream Codes.");
        }else{
            if(root!=null){renderCategories();renderGrid();}
        }
    }
    @Override public void onBackPressed(){
        if(!"all".equals(currentCategory)){currentCategory="all";updateCategorySelection();renderGrid();}
        else if(!currentQuery.isEmpty()){currentQuery="";if(searchField!=null)searchField.setText("");renderGrid();}
        else if(!"none".equals(currentTab)){
            currentTab="none";currentCategory="all";currentQuery="";
            if(searchField!=null)searchField.setText("");
            updateTabStyles();updateFooter();renderCategories();renderGrid();
        }else super.onBackPressed();
    }
    @Override protected void onDestroy(){if(loader!=null)loader.cancel();io.shutdownNow();super.onDestroy();}
}