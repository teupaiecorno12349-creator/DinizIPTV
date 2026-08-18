package com.diniz.iptv.player;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.*;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class DetailActivity extends Activity {
    private int dp(int v){ return Math.round(v * getResources().getDisplayMetrics().density); }
    private TextView text(String s,int sp){TextView t=new TextView(this);t.setText(s);t.setTextSize(sp);t.setTextColor(Color.WHITE);return t;}
    private GradientDrawable bg(int c,int r){GradientDrawable g=new GradientDrawable();g.setColor(c);g.setCornerRadius(dp(r));return g;}

    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,WindowManager.LayoutParams.FLAG_FULLSCREEN);
        long id=getIntent().getLongExtra("id",-1); boolean isSeries=getIntent().getBooleanExtra("series",false); String title=getIntent().getStringExtra("title");
        ContentItem base=null; List<ContentItem> eps=new ArrayList<>();
        for(ContentItem x:CatalogStore.ITEMS){
            if(isSeries && x.type.equals("series") && x.title.equals(title)) eps.add(x);
            if(!isSeries && x.id==id) base=x;
        }
        if(isSeries && !eps.isEmpty()){eps.sort(Comparator.comparingInt((ContentItem x)->x.season).thenComparingInt(x->x.episode));base=eps.get(0);}
        if(base==null){finish();return;}

        LinearLayout page=new LinearLayout(this);page.setOrientation(LinearLayout.HORIZONTAL);page.setPadding(dp(48),dp(36),dp(48),dp(36));page.setBackgroundColor(Color.rgb(11,14,20));
        ImageView poster=new ImageView(this);poster.setScaleType(ImageView.ScaleType.CENTER_CROP);poster.setBackgroundColor(Color.rgb(32,38,48));page.addView(poster,new LinearLayout.LayoutParams(dp(300),dp(440)));ImageLoader.load(poster,base.posterUrl);
        LinearLayout info=new LinearLayout(this);info.setOrientation(LinearLayout.VERTICAL);info.setPadding(dp(38),0,0,0);page.addView(info,new LinearLayout.LayoutParams(0,-1,1));
        TextView name=text(base.displayTitle(),30);name.setTypeface(null,1);info.addView(name,new LinearLayout.LayoutParams(-1,dp(62)));
        TextView genres=text(base.genres,17);genres.setTextColor(Color.rgb(170,178,192));info.addView(genres,new LinearLayout.LayoutParams(-1,dp(40)));
        TextView desc=text(base.description,18);desc.setTextColor(Color.rgb(220,224,232));desc.setLineSpacing(0,1.15f);info.addView(desc,new LinearLayout.LayoutParams(-1,0,isSeries?0.42f:1f));
        if(!isSeries){Button play=button("▶  REPRODUZIR");ContentItem finalBase=base;play.setOnClickListener(v->play(finalBase));info.addView(play,new LinearLayout.LayoutParams(dp(300),dp(62)));}
        else {
            TextView eh=text("Episódios",21);eh.setTypeface(null,1);info.addView(eh,new LinearLayout.LayoutParams(-1,dp(48)));
            ScrollView sv=new ScrollView(this);LinearLayout list=new LinearLayout(this);list.setOrientation(LinearLayout.VERTICAL);sv.addView(list);info.addView(sv,new LinearLayout.LayoutParams(-1,0,0.58f));
            for(ContentItem e:eps){String n="T"+e.season+" E"+e.episode+(e.episodeTitle==null||e.episodeTitle.isEmpty()?"":" — "+e.episodeTitle);Button eb=button(n);eb.setGravity(Gravity.LEFT|Gravity.CENTER_VERTICAL);eb.setOnClickListener(v->play(e));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(56));lp.setMargins(0,0,0,dp(8));list.addView(eb,lp);}
        }
        setContentView(page);
    }
    private Button button(String s){Button b=new Button(this);b.setText(s);b.setTextColor(Color.WHITE);b.setTextSize(16);b.setFocusable(true);b.setBackground(bg(Color.rgb(124,77,255),10));b.setPadding(dp(18),0,dp(18),0);return b;}
    private void play(ContentItem x){
        Class<?> target;
        if(TeraBoxPlaybackActivity.supports(x.streamUrl)) target=TeraBoxPlaybackActivity.class;
        else if(OkRuPlaybackActivity.supports(x.streamUrl)) target=OkRuPlaybackActivity.class;
        else target=PlaybackActivity.class;
        Intent i=new Intent(this,target);i.putExtra("url",x.streamUrl);i.putExtra("title",x.title);startActivity(i);
    }
}
