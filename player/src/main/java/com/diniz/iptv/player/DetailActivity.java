package com.diniz.iptv.player;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DetailActivity extends Activity {
    private final ExecutorService metadataExecutor=Executors.newSingleThreadExecutor();
    private volatile boolean detailLoading;
    private String detailError;
    private ContentItem activeItem;
    private String activeType="movie";

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
        activeType=type;
        activeItem=find(type,id);
        if(activeItem==null){finish();return;}
        detailLoading=shouldFetchDetails(type,id);
        build(activeItem,currentEpisodes(activeItem));
        if(detailLoading)fetchDetails(activeItem,type);
    }
    private boolean shouldFetchDetails(String type,long id){
        long checked=getSharedPreferences("marcin_metadata",MODE_PRIVATE)
                .getLong("checked_"+type+"_"+id,0L);
        long age=System.currentTimeMillis()-checked;
        // Trust metadata already supplied in the bulk catalogue; do not make a
        // second round-trip just to repeat a synopsis the provider already sent.
        boolean hasSynopsis=activeItem!=null&&activeItem.description!=null
                &&!activeItem.description.trim().isEmpty();
        boolean hasEpisodes=false;
        if("series".equals(type)){
            synchronized(CatalogStore.EPISODES){
                List<ContentItem> saved=CatalogStore.EPISODES.get(id);
                hasEpisodes=saved!=null&&!saved.isEmpty();
            }
        }
        if(hasSynopsis&&(!"series".equals(type)||hasEpisodes))return false;
        // Successful details/episode responses stay cached for seven days.
        return checked==0L||age>7L*24L*60L*60L*1000L;
    }

    private List<ContentItem> currentEpisodes(ContentItem item){
        if(item==null||!"series".equals(item.type))return new ArrayList<>();
        synchronized(CatalogStore.EPISODES){
            List<ContentItem> saved=CatalogStore.EPISODES.get(item.id);
            return saved==null?new ArrayList<>():new ArrayList<>(saved);
        }
    }

    private void fetchDetails(ContentItem item,String type){
        android.content.SharedPreferences account=getSharedPreferences("xtream_account",MODE_PRIVATE);
        String base=account.getString("server",""),user=account.getString("username",""),pass=account.getString("password","");
        if(base.isEmpty()||user.isEmpty()||pass.isEmpty()){
            detailLoading=false;
            detailError="Entre novamente na conta Xtream Codes para consultar os detalhes.";
            build(item,currentEpisodes(item));
            return;
        }
        metadataExecutor.execute(()->{
            try{
                XtreamClient client=new XtreamClient(getApplicationContext(),base,user,pass);
                List<ContentItem> episodes=null;
                if("series".equals(type)){
                    episodes=client.enrichSeries(item);
                    synchronized(CatalogStore.EPISODES){
                        CatalogStore.EPISODES.put(item.id,new ArrayList<>(episodes));
                    }
                }else if("movie".equals(type)){
                    client.enrichMovie(item);
                }
                getSharedPreferences("marcin_metadata",MODE_PRIVATE).edit()
                        .putLong("checked_"+type+"_"+item.id,System.currentTimeMillis()).apply();
                CatalogStore.save(getApplicationContext());
                List<ContentItem> resultEpisodes=currentEpisodes(item);
                runOnUiThread(()->{
                    if(isFinishing()||isDestroyed())return;
                    detailLoading=false;
                    detailError=null;
                    build(item,resultEpisodes);
                });
            }catch(Exception e){
                runOnUiThread(()->{
                    if(isFinishing()||isDestroyed())return;
                    detailLoading=false;
                    detailError="Não foi possível consultar os detalhes agora. Tente abrir este título novamente mais tarde.";
                    build(item,currentEpisodes(item));
                });
            }
        });
    }

    @Override protected void onDestroy(){
        metadataExecutor.shutdownNow();
        super.onDestroy();
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
        String synopsisText=item.description==null||item.description.trim().isEmpty()
                ?(detailLoading?"Consultando sinopse e informações do título…":(detailError!=null?detailError:"Sinopse não fornecida pelo servidor."))
                :item.description;
        TextView synopsis=text(synopsisText,17);synopsis.setTextColor(Color.rgb(220,225,234));synopsis.setLineSpacing(0,1.12f);info.addView(synopsis,new LinearLayout.LayoutParams(-1,-2));
        if("series".equals(item.type)){
            LinearLayout buttons=new LinearLayout(this);buttons.setOrientation(LinearLayout.HORIZONTAL);LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(-1,dp(58));bp.setMargins(0,dp(15),0,dp(9));info.addView(buttons,bp);
            boolean canResume=hasSavedSeriesProgress(item,episodes);
            Button next=button(episodes.isEmpty()?(detailLoading?"CONSULTANDO EPISÓDIOS…":"EPISÓDIOS INDISPONÍVEIS"):(canResume?"▶  CONTINUAR ASSISTINDO":"▶  ASSISTIR SÉRIE"),15);
            next.setEnabled(!episodes.isEmpty());buttons.addView(next,new LinearLayout.LayoutParams(dp(300),-1));
            if(!episodes.isEmpty())next.setOnClickListener(v->playEpisodeList(item,episodes,seriesStartIndex(item,episodes)));
            TextView heading=text("TEMPORADAS E EPISÓDIOS  ("+episodes.size()+")",17);heading.setTypeface(Typeface.DEFAULT_BOLD);heading.setPadding(0,dp(13),0,dp(9));info.addView(heading);
            if(episodes.isEmpty()){
                String episodeMessage=detailLoading?"Buscando temporadas e episódios desta série no servidor…":
                        (detailError!=null?detailError:"O servidor não forneceu episódios para esta série.");
                TextView none=text(episodeMessage,14);none.setTextColor(Color.LTGRAY);info.addView(none);
            }
            else{
                LinearLayout list=new LinearLayout(this);list.setOrientation(LinearLayout.VERTICAL);info.addView(list);
                int lastSeason=-1;
                for(int i=0;i<episodes.size();i++){
                    ContentItem ep=episodes.get(i);
                    if(ep.season!=lastSeason){lastSeason=ep.season;TextView sh=text("TEMPORADA "+lastSeason,13);sh.setTextColor(Color.rgb(130,167,255));sh.setTypeface(Typeface.DEFAULT_BOLD);sh.setPadding(0,dp(12),0,dp(5));list.addView(sh);}
                    Button eb=button(String.format(Locale.ROOT,"%02d  %s",ep.episode,ep.episodeTitle==null||ep.episodeTitle.isEmpty()?"Episódio "+ep.episode:ep.episodeTitle));
                    eb.setGravity(Gravity.LEFT|Gravity.CENTER_VERTICAL);
                    eb.setPadding(dp(16),0,dp(12),0);
                    eb.setBackground(bg(Color.rgb(17,24,35),8));
                    eb.setFocusable(true);
                    eb.setFocusableInTouchMode(true);
                    eb.setOnFocusChangeListener((focusedView,hasFocus)->{
                        focusedView.setBackground(bg(hasFocus?Color.rgb(41,75,132):Color.rgb(17,24,35),8));
                        focusedView.animate().scaleX(hasFocus?1.025f:1f).scaleY(hasFocus?1.025f:1f).setDuration(100).start();
                    });
                    LinearLayout.LayoutParams elp=new LinearLayout.LayoutParams(-1,dp(49));elp.setMargins(0,dp(3),0,dp(3));list.addView(eb,elp);
                    final int ix=i;eb.setOnClickListener(v->playEpisodeList(item,episodes,ix));
                }
            }
        }else{
            android.content.SharedPreferences playbackPrefs=getSharedPreferences("marcin_playback_progress",MODE_PRIVATE);
            long savedPos=playbackPrefs.getLong("pos_movie_"+item.id,0L);
            String playLabel=savedPos>0?"▶  CONTINUAR  ·  "+formatSavedTime(savedPos):"▶  ASSISTIR AGORA";
            Button play=button(playLabel,15);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(dp(300),dp(58));p.setMargins(0,dp(20),0,0);info.addView(play,p);
            play.setOnClickListener(v->{
                Intent intent=new Intent(this,PlaybackActivity.class);
                intent.putExtra("url",item.streamUrl);intent.putExtra("title",item.displayTitle());
                intent.putExtra("content_type",item.type);intent.putExtra("resume_key","movie_"+item.id);
                startActivity(intent);
            });
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
    private String formatSavedTime(long millis){
        long total=Math.max(0,millis/1000L);
        long h=total/3600L,m=(total%3600L)/60L;
        return h>0?String.format(Locale.ROOT,"%02d:%02d:%02d",h,m,total%60L)
                :String.format(Locale.ROOT,"%02d:%02d",m,total%60L);
    }
    private boolean hasSavedSeriesProgress(ContentItem series,List<ContentItem> episodes){
        if(episodes.isEmpty())return false;
        int index=findLastEpisodeIndex(series,episodes);
        if(index<0)return false;
        ContentItem ep=episodes.get(index);
        return getSharedPreferences("marcin_playback_progress",MODE_PRIVATE)
                .getLong("pos_series_"+series.id+"_episode_"+ep.id,0L)>0L;
    }
    private int findLastEpisodeIndex(ContentItem series,List<ContentItem> episodes){
        long last=progress.getLong("last_episode_"+series.id,-1L);
        for(int i=0;i<episodes.size();i++)if(episodes.get(i).id==last)return i;
        return -1;
    }
    private int seriesStartIndex(ContentItem series,List<ContentItem> episodes){
        if(episodes.isEmpty())return 0;
        int lastIndex=findLastEpisodeIndex(series,episodes);
        if(lastIndex<0)return 0;
        ContentItem ep=episodes.get(lastIndex);
        long saved=getSharedPreferences("marcin_playback_progress",MODE_PRIVATE)
                .getLong("pos_series_"+series.id+"_episode_"+ep.id,0L);
        if(saved>0L)return lastIndex;
        return Math.min(episodes.size()-1,lastIndex+1);
    }
    private int nextIndex(ContentItem series,List<ContentItem> eps){return seriesStartIndex(series,eps);}
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