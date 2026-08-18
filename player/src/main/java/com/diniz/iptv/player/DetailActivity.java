package com.diniz.iptv.player;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.*;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class DetailActivity extends Activity {
    private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}    
    private GradientDrawable bg(int c,int r){GradientDrawable g=new GradientDrawable();g.setColor(c);g.setCornerRadius(dp(r));return g;}
    private TextView text(String s,int sp){TextView t=new TextView(this);t.setText(s==null?"":s);t.setTextColor(Color.WHITE);t.setTextSize(sp);return t;}

    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        getWindow().setStatusBarColor(Color.rgb(11,14,20));
        getWindow().setNavigationBarColor(Color.rgb(11,14,20));

        long id=getIntent().getLongExtra("id",-1);
        boolean isSeries=getIntent().getBooleanExtra("series",false);
        String title=getIntent().getStringExtra("title");
        ContentItem base=null; List<ContentItem> eps=new ArrayList<>();
        for(ContentItem x:CatalogStore.ITEMS){
            if(isSeries&&"series".equals(x.type)&&x.title.equals(title))eps.add(x);
            if(!isSeries&&x.id==id)base=x;
        }
        if(isSeries&&!eps.isEmpty()){eps.sort(Comparator.comparingInt((ContentItem x)->x.season).thenComparingInt(x->x.episode));base=eps.get(0);}        
        if(base==null){finish();return;}

        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);
        LinearLayout page=new LinearLayout(this);page.setOrientation(LinearLayout.VERTICAL);page.setPadding(dp(18),dp(18),dp(18),dp(32));page.setBackgroundColor(Color.rgb(11,14,20));scroll.addView(page);

        ImageView poster=new ImageView(this);poster.setScaleType(ImageView.ScaleType.CENTER_CROP);poster.setBackgroundColor(Color.rgb(32,38,48));LinearLayout.LayoutParams pp=new LinearLayout.LayoutParams(-1,dp(390));pp.setMargins(0,0,0,dp(18));page.addView(poster,pp);ImageLoader.load(poster,base.posterUrl);
        TextView name=text(base.title,26);name.setTypeface(null,1);page.addView(name);
        String meta=(base.year==null?"":base.year)+(base.genres==null||base.genres.isEmpty()?"":"  •  "+base.genres);TextView mt=text(meta,15);mt.setTextColor(Color.rgb(170,178,192));LinearLayout.LayoutParams mp=new LinearLayout.LayoutParams(-1,-2);mp.setMargins(0,dp(6),0,dp(16));page.addView(mt,mp);
        TextView desc=text(base.description,16);desc.setTextColor(Color.rgb(225,228,235));desc.setLineSpacing(0,1.15f);page.addView(desc,new LinearLayout.LayoutParams(-1,-2));

        if(!isSeries){
            Button play=button("▶  REPRODUZIR EM TELA CHEIA");ContentItem f=base;play.setOnClickListener(v->play(f));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(58));p.setMargins(0,dp(22),0,0);page.addView(play,p);
        }else{
            TextView eh=text("Episódios",21);eh.setTypeface(null,1);LinearLayout.LayoutParams hp=new LinearLayout.LayoutParams(-1,-2);hp.setMargins(0,dp(22),0,dp(10));page.addView(eh,hp);
            for(ContentItem e:eps){String n="T"+e.season+" E"+e.episode+(e.episodeTitle==null||e.episodeTitle.isEmpty()?"":" — "+e.episodeTitle);Button eb=button(n);eb.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);eb.setOnClickListener(v->play(e));LinearLayout.LayoutParams ep=new LinearLayout.LayoutParams(-1,dp(56));ep.setMargins(0,0,0,dp(8));page.addView(eb,ep);}
        }
        setContentView(scroll);
    }

    private Button button(String s){Button b=new Button(this);b.setText(s);b.setAllCaps(false);b.setTextColor(Color.WHITE);b.setTextSize(16);b.setBackground(bg(Color.rgb(124,77,255),12));b.setPadding(dp(16),0,dp(16),0);return b;}
    private void play(ContentItem x){Class<?> target;if(TeraBoxPlaybackActivity.supports(x.streamUrl))target=TeraBoxPlaybackActivity.class;else if(OkRuPlaybackActivity.supports(x.streamUrl))target=OkRuPlaybackActivity.class;else target=PlaybackActivity.class;Intent i=new Intent(this,target);i.putExtra("url",x.streamUrl);i.putExtra("title",x.title);startActivity(i);}
}
