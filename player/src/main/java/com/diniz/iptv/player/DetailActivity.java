package com.diniz.iptv.player;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class DetailActivity extends Activity {
    private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}
    private TextView text(String s,int sp){TextView t=new TextView(this);t.setText(s==null?"":s);t.setTextSize(sp);t.setTextColor(Color.WHITE);return t;}
    private GradientDrawable bg(int c,int r){GradientDrawable g=new GradientDrawable();g.setColor(c);g.setCornerRadius(dp(r));return g;}
    private android.content.SharedPreferences progress;
    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        progress=getSharedPreferences("marcin_progress",MODE_PRIVATE);
        long id=getIntent().getLongExtra("id",-1);String type=getIntent().getStringExtra("type");
        if(type==null)type="movie";
        ContentItem item=find(type,id);
        if(item==null){finish();return;}
        List<ContentItem> episodes="series".equals(type)?CatalogStore.EPISODES.getOrDefault(item.id,new ArrayList<>()):new ArrayList<>();
        build(item,episodes);
    }
    private ContentItem find(String type,long id){
        for(ContentItem x:CatalogStore.list(type))if(x.id==id)return x;
        return null;
    }
    private Button button(String title){
        Button b=new Button(this);b.setText(title);b.setTextColor(Color.WHITE);b.setTextSize(15);b.setAllCaps(false);b.setFocusable(true);b.setBackground(bg(Color.rgb(48,93,184),10));return b;
    }
    private void build(ContentItem item,List<ContentItem> episodes){
        ScrollView outer=new ScrollView(this);outer.setFillViewport(true);outer.setBackgroundColor(Color.rgb(7,10,16));
        LinearLayout page=new LinearLayout(this);page.setOrientation(LinearLayout.HORIZONTAL);page.setPadding(dp(30),dp(24),dp(30),dp(24));outer.addView(page);
        FrameLayout posterBox=new FrameLayout(this);posterBox.setBackground(bg(Color.rgb(20,28,40),12));LinearLayout.LayoutParams pp=new LinearLayout.LayoutParams(dp(260),dp(385));pp.setMargins(0,0,dp(28),0);page.addView(posterBox,pp);
        ImageView poster=new ImageView(this);poster.setScaleType(ImageView.ScaleType.CENTER_CROP);posterBox.addView(poster,new FrameLayout.LayoutParams(-1,-1));
        if(item.posterUrl!=null&&!item.posterUrl.isEmpty())ImageLoader.load(poster,item.posterUrl);
        LinearLayout info=new LinearLayout(this);info.setOrientation(LinearLayout.VERTICAL);page.addView(info,new LinearLayout.LayoutParams(0,-1,1));
        TextView title=text(item.displayTitle(),29);title.setTypeface(Typeface.DEFAULT_BOLD);info.addView(title,new LinearLayout.LayoutParams(-1,dp(52)));
        TextView meta=text(buildMeta(item),15);meta.setTextColor(Color.rgb(157,177,207));meta.setPadding(0,dp(2),0,dp(12));info.addView(meta,new LinearLayout.LayoutParams(-1,-2));
        if(item.rating!=null&&!item.rating.isEmpty()){TextView rating=text("★  "+item.rating,15);rating.setTextColor(Color.rgb(255,206,105));info.addView(rating,new LinearLayout.LayoutParams(-1,dp(29)));}
        if(item.director!=null&&!item.director.isEmpty())addInfo(info,"Direção",item.director);
        if(item.duration!=null&&!item.duration.isEmpty())addInfo(info,"Duração",item.duration);
        if(item.cast!=null&&!item.cast.isEmpty())addInfo(info,"Elenco",item.cast);
        TextView synopsis=text(item.description==null||item.description.trim().isEmpty()?"Sinopse não fornecida pelo servidor.":item.description,17);synopsis.setTextColor(Color.rgb(220,225,234));synopsis.setLineSpacing(0,1.12f);info.addView(synopsis,new LinearLayout.LayoutParams(-1,-2));
        if("series".equals(item.type)){
            LinearLayout buttons=new LinearLayout(this);buttons.setOrientation(LinearLayout.HORIZONTAL);LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(-1,dp(58));bp.setMargins(0,dp(15),0,dp(9));info.addView(buttons,bp);
            Button next=button(episodes.isEmpty()?"EPISÓDIOS INDISPONÍVEIS":"▶  PRÓXIMO EPISÓDIO");next.setEnabled(!episodes.isEmpty());buttons.addView(next,new LinearLayout.LayoutParams(dp(255),-1));
            if(!episodes.isEmpty())next.setOnClickListener(v->playEpisodeList(item,episodes,nextIndex(item,episodes)));
            TextView heading=text("TEMPORADAS E EPISÓDIOS  ("+episodes.size()+")",17);heading.setTypeface(Typeface.DEFAULT_BOLD);heading.setPadding(0,dp(13),0,dp(9));info.addView(heading);
            if(episodes.isEmpty()){TextView none=text("O servidor não forneceu episódios para esta série.",14);none.setTextColor(Color.LTGRAY);info.addView(none);}
            else{
                LinearLayout list=new LinearLayout(this);list.setOrientation(LinearLayout.VERTICAL);info.addView(list);
                int lastSeason=-1;
                for(int i=0;i<episodes.size();i++){
                    ContentItem ep=episodes.get(i);
                    if(ep.season!=lastSeason){lastSeason=ep.season;TextView sh=text("TEMPORADA "+lastSeason,13);sh.setTextColor(Color.rgb(130,167,255));sh.setTypeface(Typeface.DEFAULT_BOLD);sh.setPadding(0,dp(12),0,dp(5));list.addView(sh);}
                    Button eb=button(String.format(Locale.ROOT,"%02d  %s",ep.episode,ep.episodeTitle==null||ep.episodeTitle.isEmpty()?"Episódio "+ep.episode:ep.episodeTitle));
                    eb.setGravity(Gravity.LEFT|Gravity.CENTER_VERTICAL);eb.setBackground(bg(Color.rgb(17,24,35),8));LinearLayout.LayoutParams elp=new LinearLayout.LayoutParams(-1,dp(47));elp.setMargins(0,dp(2),0,dp(2));list.addView(eb,elp);
                    final int ix=i;eb.setOnClickListener(v->playEpisodeList(item,episodes,ix));
                }
            }
        }else{
            Button play=button("▶  ASSISTIR AGORA");LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(dp(265),dp(58));p.setMargins(0,dp(20),0,0);info.addView(play,p);
            play.setOnClickListener(v->{Intent intent=new Intent(this,PlaybackActivity.class);intent.putExtra("url",item.streamUrl);intent.putExtra("title",item.displayTitle());intent.putExtra("content_type",item.type);startActivity(intent);});
        }
        setContentView(outer);
    }
    private void addInfo(LinearLayout parent,String label,String value){
        TextView t=text(label+": "+value,13);t.setTextColor(Color.rgb(165,177,196));t.setPadding(0,dp(2),0,dp(2));t.setMaxLines(2);parent.addView(t,new LinearLayout.LayoutParams(-1,-2));
    }
    private String buildMeta(ContentItem x){
        List<String> p=new ArrayList<>();
        if(x.year!=null&&!x.year.isEmpty())p.add(x.year);
        if(x.genres!=null&&!x.genres.isEmpty())p.add(x.genres);
        if(x.categoryName!=null&&!x.categoryName.isEmpty())p.add(x.categoryName);
        if(x.duration!=null&&!x.duration.isEmpty()&&p.size()<2)p.add(x.duration);
        return android.text.TextUtils.join("  •  ",p);
    }
    private int nextIndex(ContentItem series,List<ContentItem> eps){
        long last=progress.getLong("last_episode_"+series.id,-1L);
        if(last<0)return 0;
        for(int i=0;i<eps.size();i++)if(eps.get(i).id==last)return Math.min(eps.size()-1,i+1);
        return 0;
    }
    private void playEpisodeList(ContentItem series,List<ContentItem> episodes,int start){
        ArrayList<String> urls=new ArrayList<>(),titles=new ArrayList<>();
        long[] ids=new long[episodes.size()-start],seriesIds=new long[episodes.size()-start];
        int[] seasons=new int[episodes.size()-start],episodeNums=new int[episodes.size()-start];
        for(int i=start,j=0;i<episodes.size();i++,j++){
            ContentItem ep=episodes.get(i);urls.add(ep.streamUrl);
            String label=series.title+"  •  T"+ep.season+" E"+ep.episode;
            if(ep.episodeTitle!=null&&!ep.episodeTitle.isEmpty())label+=" — "+ep.episodeTitle;
            titles.add(label);ids[j]=ep.id;seriesIds[j]=series.id;seasons[j]=ep.season;episodeNums[j]=ep.episode;
        }
        Intent it=new Intent(this,PlaybackActivity.class);it.putStringArrayListExtra("playlist_urls",urls);it.putStringArrayListExtra("playlist_titles",titles);
        it.putExtra("episode_ids",ids);it.putExtra("series_ids",seriesIds);it.putExtra("episode_seasons",seasons);it.putExtra("episode_numbers",episodeNums);
        it.putExtra("start_index",0);it.putExtra("series_title",series.title);it.putExtra("series_id",series.id);it.putExtra("content_type","episode");
        startActivity(it);
    }
}