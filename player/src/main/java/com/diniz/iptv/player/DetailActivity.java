package com.diniz.iptv.player;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.widget.*;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class DetailActivity extends Activity {
    private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}
    private GradientDrawable bg(int c,int r){GradientDrawable g=new GradientDrawable();g.setColor(c);g.setCornerRadius(dp(r));return g;}
    private TextView text(String s,int sp){TextView t=new TextView(this);t.setText(s==null?"":s);t.setTextColor(Color.WHITE);t.setTextSize(sp);return t;}

    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        immersive();

        long id=getIntent().getLongExtra("id",-1);
        boolean isSeries=getIntent().getBooleanExtra("series",false);
        String title=getIntent().getStringExtra("title");
        ContentItem base=null;List<ContentItem> eps=new ArrayList<>();
        for(ContentItem x:CatalogStore.ITEMS){
            if(isSeries&&"series".equals(x.type)&&x.title.equals(title))eps.add(x);
            if(!isSeries&&x.id==id)base=x;
        }
        if(isSeries&&!eps.isEmpty()){eps.sort(Comparator.comparingInt((ContentItem x)->x.season).thenComparingInt(x->x.episode));base=eps.get(0);}
        if(base==null){finish();return;}

        LinearLayout page=new LinearLayout(this);page.setOrientation(LinearLayout.HORIZONTAL);page.setPadding(dp(46),dp(34),dp(46),dp(34));page.setBackgroundColor(Color.rgb(11,14,20));
        ImageView poster=new ImageView(this);poster.setScaleType(ImageView.ScaleType.CENTER_CROP);poster.setBackgroundColor(Color.rgb(32,38,48));page.addView(poster,new LinearLayout.LayoutParams(dp(300),dp(440)));ImageLoader.load(poster,base.posterUrl);

        LinearLayout info=new LinearLayout(this);info.setOrientation(LinearLayout.VERTICAL);info.setPadding(dp(38),0,0,0);page.addView(info,new LinearLayout.LayoutParams(0,-1,1));
        TextView name=text(base.title,30);name.setTypeface(null,1);info.addView(name,new LinearLayout.LayoutParams(-1,dp(62)));
        String meta=(base.year==null?"":base.year)+(base.genres==null||base.genres.isEmpty()?"":"  •  "+base.genres);TextView mt=text(meta,17);mt.setTextColor(Color.rgb(170,178,192));info.addView(mt,new LinearLayout.LayoutParams(-1,dp(42)));
        TextView desc=text(base.description,18);desc.setTextColor(Color.rgb(220,224,232));desc.setLineSpacing(0,1.15f);info.addView(desc,new LinearLayout.LayoutParams(-1,0,isSeries?0.42f:1f));

        if(!isSeries){
            Button play=button("▶  REPRODUZIR");ContentItem f=base;play.setOnClickListener(v->play(f));info.addView(play,new LinearLayout.LayoutParams(dp(320),dp(62)));play.requestFocus();
        }else{
            TextView eh=text("Episódios",21);eh.setTypeface(null,1);info.addView(eh,new LinearLayout.LayoutParams(-1,dp(48)));
            ScrollView sv=new ScrollView(this);LinearLayout list=new LinearLayout(this);list.setOrientation(LinearLayout.VERTICAL);sv.addView(list);info.addView(sv,new LinearLayout.LayoutParams(-1,0,0.58f));
            boolean first=true;
            for(ContentItem e:eps){String n="T"+e.season+" E"+e.episode+(e.episodeTitle==null||e.episodeTitle.isEmpty()?"":" — "+e.episodeTitle);Button eb=button(n);eb.setGravity(Gravity.LEFT|Gravity.CENTER_VERTICAL);eb.setOnClickListener(v->play(e));LinearLayout.LayoutParams ep=new LinearLayout.LayoutParams(-1,dp(58));ep.setMargins(0,0,0,dp(8));list.addView(eb,ep);if(first){eb.requestFocus();first=false;}}
        }
        setContentView(page);immersive();
    }

    private Button button(String s){Button b=new Button(this);b.setText(s);b.setTextColor(Color.WHITE);b.setTextSize(16);b.setFocusable(true);b.setFocusableInTouchMode(true);b.setBackground(bg(Color.rgb(124,77,255),10));b.setPadding(dp(18),0,dp(18),0);b.setOnFocusChangeListener((v,has)->v.animate().scaleX(has?1.04f:1f).scaleY(has?1.04f:1f).setDuration(100).start());return b;}

    private void play(ContentItem x){
        try{
            String stream=x.streamUrl==null?"":x.streamUrl.trim();
            if(stream.startsWith("terabox:")){
                String path=stream.substring("terabox:".length());
                SharedPreferences p=getSharedPreferences("player",MODE_PRIVATE);
                String server=p.getString("server","");String token=p.getString("token","");
                if(server.isEmpty()||token.isEmpty())throw new Exception("Sessão do Admin ausente. Faça login novamente.");
                String local=server+"/api/stream?token="+URLEncoder.encode(token,"UTF-8")+"&path="+URLEncoder.encode(path,"UTF-8");
                Intent i=new Intent(this,PlaybackActivity.class);i.putExtra("url",local);i.putExtra("title",x.title);startActivity(i);return;
            }
            Class<?> target;
            if(TeraBoxPlaybackActivity.supports(stream))target=TeraBoxPlaybackActivity.class;
            else if(OkRuPlaybackActivity.supports(stream))target=OkRuPlaybackActivity.class;
            else target=PlaybackActivity.class;
            Intent i=new Intent(this,target);i.putExtra("url",stream);i.putExtra("title",x.title);startActivity(i);
        }catch(Exception e){Toast.makeText(this,e.getMessage(),Toast.LENGTH_LONG).show();}
    }

    private void immersive(){
        if(Build.VERSION.SDK_INT>=30){getWindow().setDecorFitsSystemWindows(false);WindowInsetsController c=getWindow().getInsetsController();if(c!=null){c.hide(WindowInsets.Type.statusBars()|WindowInsets.Type.navigationBars());c.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);}}
        else getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY|View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN|View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
    }
    @Override public void onWindowFocusChanged(boolean has){super.onWindowFocusChanged(has);if(has)immersive();}
}
