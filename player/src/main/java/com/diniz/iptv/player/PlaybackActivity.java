package com.diniz.iptv.player;

import android.app.Activity;
import android.app.AlertDialog;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.WindowManager;
import android.widget.Toast;

import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.TrackSelectionParameters;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.DefaultLoadControl;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector;
import androidx.media3.datasource.okhttp.OkHttpDataSource;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.ui.AspectRatioFrameLayout;
import androidx.media3.ui.PlayerView;

import java.util.ArrayList;
import java.util.List;

@UnstableApi
public class PlaybackActivity extends Activity {
    private ExoPlayer player;
    private PlayerView view;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final List<MediaItem> mediaItems=new ArrayList<>();
    private long resumePos=0;
    private int startIndex=0,retries=0;
    private long[] episodeIds,seriesIds;
    private int[] seasons,episodeNumbers;
    private long lastErrorAt=0;
    private boolean released=false;

    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().getDecorView().setSystemUiVisibility(5894|1024|512);
        view=new PlayerView(this);view.setUseController(true);view.setControllerAutoShow(true);view.setKeepScreenOn(true);
        view.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FIT);
        setContentView(view);
        buildQueue(getIntent());
        if(mediaItems.isEmpty()){Toast.makeText(this,"URL de reprodução vazia.",Toast.LENGTH_LONG).show();finish();}
    }

    private void buildQueue(android.content.Intent intent){
        ArrayList<String> urls=intent.getStringArrayListExtra("playlist_urls");
        ArrayList<String> titles=intent.getStringArrayListExtra("playlist_titles");
        episodeIds=intent.getLongArrayExtra("episode_ids");seriesIds=intent.getLongArrayExtra("series_ids");
        seasons=intent.getIntArrayExtra("episode_seasons");episodeNumbers=intent.getIntArrayExtra("episode_numbers");
        startIndex=Math.max(0,intent.getIntExtra("start_index",0));
        if(urls!=null){
            for(int i=0;i<urls.size();i++){
                String title=titles!=null&&i<titles.size()?titles.get(i):intent.getStringExtra("title");
                mediaItems.add(mediaItem(urls.get(i),title));
            }
        }else{
            String url=intent.getStringExtra("url");if(url!=null&&!url.trim().isEmpty())mediaItems.add(mediaItem(url,intent.getStringExtra("title")));
        }
        if(startIndex>=mediaItems.size())startIndex=0;
    }

    private MediaItem mediaItem(String url,String title){
        MediaItem.Builder b=new MediaItem.Builder().setUri(Uri.parse(url));
        if(title!=null&&!title.isEmpty())b.setMediaMetadata(new MediaMetadata.Builder().setTitle(title).build());
        String lower=url==null?"":url.toLowerCase(java.util.Locale.ROOT);
        if(lower.contains(".m3u8"))b.setMimeType(MimeTypes.APPLICATION_M3U8);
        else if(lower.contains(".mpd"))b.setMimeType(MimeTypes.APPLICATION_MPD);
        return b.build();
    }

    @Override protected void onStart(){super.onStart();released=false;if(player==null)startPlayer();}
    private void startPlayer(){
        String buffer=getSharedPreferences(NetworkStack.PREFS,MODE_PRIVATE).getString(NetworkStack.BUFFER_MODE,"stable");
        int min=20000,max=65000,start=3000,rebuffer=6000;
        if("balanced".equals(buffer)){min=10000;max=45000;start=2500;rebuffer=5000;}
        else if("low".equals(buffer)){min=5000;max=20000;start=1500;rebuffer=3000;}
        DefaultLoadControl load=new DefaultLoadControl.Builder().setBufferDurationsMs(min,max,start,rebuffer).build();

        DefaultTrackSelector selector=new DefaultTrackSelector(this);
        String resolution=getSharedPreferences(NetworkStack.PREFS,MODE_PRIVATE).getString(NetworkStack.RESOLUTION,"auto");
        DefaultTrackSelector.Parameters.Builder track=selector.buildUponParameters();
        if("720".equals(resolution))track.setMaxVideoSize(1280,720);
        else if("1080".equals(resolution))track.setMaxVideoSize(1920,1080);
        else if("2160".equals(resolution))track.setMaxVideoSize(3840,2160);
        selector.setParameters(track.build());

        okhttp3.OkHttpClient streamingClient=NetworkStack.client(this).newBuilder()
                .connectTimeout(java.time.Duration.ofSeconds(15))
                .readTimeout(java.time.Duration.ofSeconds(35))
                .build();
        OkHttpDataSource.Factory http=new OkHttpDataSource.Factory(streamingClient)
                .setUserAgent("MarcinIPTV/1.0 (Android TV)");
        DefaultMediaSourceFactory sourceFactory=new DefaultMediaSourceFactory(http);
        player=new ExoPlayer.Builder(this).setLoadControl(load).setTrackSelector(selector).setMediaSourceFactory(sourceFactory).build();
        player.setRepeatMode(Player.REPEAT_MODE_OFF);
        player.addListener(new Player.Listener(){
            @Override public void onPlayerError(PlaybackException error){
                long now=System.currentTimeMillis();if(now-lastErrorAt<750)return;lastErrorAt=now;
                resumePos=player==null?0:player.getCurrentPosition();
                if(retries<3){
                    int attempt=++retries;
                    Toast.makeText(PlaybackActivity.this,"Falha de rede. Tentativa "+attempt+" de 3…",Toast.LENGTH_SHORT).show();
                    handler.postDelayed(()->retryPlayback(),1000L*attempt);
                }else showError(error);
            }
            @Override public void onPlaybackStateChanged(int state){
                if(state==Player.STATE_READY)retries=0;
            }
            @Override public void onMediaItemTransition(MediaItem item,int reason){
                if(player==null)return;
                saveProgress();
                if(reason==Player.MEDIA_ITEM_TRANSITION_REASON_AUTO &&
                   !getSharedPreferences(NetworkStack.PREFS,MODE_PRIVATE).getBoolean(NetworkStack.AUTOPLAY_NEXT,true)){
                    player.pause();
                    view.showController();
                }
            }
        });
        view.setPlayer(player);
        player.setMediaItems(mediaItems,startIndex,resumePos);
        player.prepare();player.play();
        saveProgress();
    }

    private void retryPlayback(){
        if(player==null||released)return;
        try{
            player.setMediaItems(mediaItems,player.getCurrentMediaItemIndex(),resumePos);
            player.prepare();player.play();
        }catch(Exception e){Toast.makeText(this,"Não foi possível retomar a reprodução.",Toast.LENGTH_LONG).show();}
    }
    private void saveProgress(){
        if(player==null||seriesIds==null||episodeIds==null)return;
        int ix=player.getCurrentMediaItemIndex();
        if(ix<0||ix>=seriesIds.length||ix>=episodeIds.length)return;
        long sid=seriesIds[ix],eid=episodeIds[ix];
        if(sid>0&&eid>0)getSharedPreferences("marcin_progress",MODE_PRIVATE).edit()
                .putLong("last_episode_"+sid,eid)
                .putInt("last_season_"+sid,seasons!=null&&ix<seasons.length?seasons[ix]:0)
                .putInt("last_episode_number_"+sid,episodeNumbers!=null&&ix<episodeNumbers.length?episodeNumbers[ix]:0)
                .apply();
    }
    private void showError(PlaybackException error){
        String code=error==null?"erro desconhecido":error.getErrorCodeName();
        new AlertDialog.Builder(this).setTitle("Não foi possível reproduzir")
                .setMessage("O servidor ou o formato do vídeo pode estar indisponível.\nCódigo: "+code)
                .setPositiveButton("Tentar novamente",(d,w)->{retries=0;retryPlayback();})
                .setNegativeButton("Fechar",(d,w)->finish()).show();
    }
    @Override protected void onStop(){
        super.onStop();released=true;handler.removeCallbacksAndMessages(null);
        if(player!=null){resumePos=player.getCurrentPosition();saveProgress();view.setPlayer(null);player.release();player=null;}
    }
    @Override public void onBackPressed(){if(player!=null&&player.isPlaying())player.pause();super.onBackPressed();}
}