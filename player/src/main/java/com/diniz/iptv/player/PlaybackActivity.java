package com.diniz.iptv.player;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
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
import java.util.Locale;

@UnstableApi
public class PlaybackActivity extends Activity {
    private static final long OVERLAY_TIMEOUT_MS=2600L;
    private static final long UI_REFRESH_MS=1000L;
    private ExoPlayer player;
    private PlayerView view;
    private FrameLayout playerRoot;
    private LinearLayout bottomPanel;
    private TextView pauseIndicator,titleText,timeText;
    private SeekBar timeBar;
    private Button nextEpisodeButton;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final Runnable hideOverlayTask=()->{
        if(player!=null&&player.isPlaying()&&bottomPanel!=null&&(timeBar==null||!timeBar.hasFocus())&&(nextEpisodeButton==null||!nextEpisodeButton.hasFocus()))bottomPanel.setVisibility(View.GONE);
    };
    private final Runnable updateTask=new Runnable(){
        @Override public void run(){
            updatePlaybackTime();
            saveProgress(false);
            if(player!=null&&!released)handler.postDelayed(this,UI_REFRESH_MS);
        }
    };
    private final List<MediaItem> mediaItems=new ArrayList<>();
    private long resumePos=0;
    private long lastProgressSaveAt=0L;
    private String singleResumeKey;
    private android.content.SharedPreferences playbackProgress;
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
        playbackProgress=getSharedPreferences("marcin_playback_progress",MODE_PRIVATE);
        buildPlayerSurface();
        buildQueue(getIntent());
        if(mediaItems.isEmpty()){
            Toast.makeText(this,"URL de reprodução vazia.",Toast.LENGTH_LONG).show();
            finish();
        }
    }

    private int dp(float value){return Math.round(value*getResources().getDisplayMetrics().density);}

    private GradientDrawable rounded(int color,int radius){
        GradientDrawable shape=new GradientDrawable();shape.setColor(color);shape.setCornerRadius(dp(radius));return shape;
    }

    private TextView label(String value,int size){
        TextView t=new TextView(this);t.setText(value);t.setTextColor(Color.WHITE);t.setTextSize(size);return t;
    }

    private void buildPlayerSurface(){
        playerRoot=new FrameLayout(this);
        playerRoot.setBackgroundColor(Color.BLACK);
        playerRoot.setFocusable(true);
        playerRoot.setFocusableInTouchMode(true);

        view=new PlayerView(this);
        view.setUseController(false);
        view.setKeepScreenOn(true);
        view.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FIT);
        playerRoot.addView(view,new FrameLayout.LayoutParams(-1,-1));

        pauseIndicator=label("Ⅱ  PAUSADO",28);
        pauseIndicator.setTypeface(Typeface.DEFAULT_BOLD);
        pauseIndicator.setGravity(Gravity.CENTER);
        pauseIndicator.setPadding(dp(28),dp(18),dp(28),dp(18));
        pauseIndicator.setBackground(rounded(Color.argb(205,7,12,21),16));
        pauseIndicator.setVisibility(View.GONE);
        FrameLayout.LayoutParams pauseParams=new FrameLayout.LayoutParams(-2,-2,Gravity.CENTER);
        playerRoot.addView(pauseIndicator,pauseParams);

        bottomPanel=new LinearLayout(this);
        bottomPanel.setOrientation(LinearLayout.VERTICAL);
        bottomPanel.setPadding(dp(26),dp(12),dp(26),dp(16));
        bottomPanel.setBackground(rounded(Color.argb(224,5,9,16),12));
        bottomPanel.setVisibility(View.GONE);
        FrameLayout.LayoutParams bottomParams=new FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM);
        bottomParams.setMargins(dp(22),0,dp(22),dp(20));
        playerRoot.addView(bottomPanel,bottomParams);

        titleText=label("",18);titleText.setTypeface(Typeface.DEFAULT_BOLD);
        titleText.setMaxLines(1);titleText.setEllipsize(android.text.TextUtils.TruncateAt.END);
        bottomPanel.addView(titleText,new LinearLayout.LayoutParams(-1,dp(30)));

        timeText=label("00:00 / --:--  •  Tempo restante --:--",14);
        timeText.setTextColor(Color.rgb(210,220,235));
        LinearLayout.LayoutParams timeParams=new LinearLayout.LayoutParams(-1,dp(25));
        timeParams.setMargins(0,dp(2),0,dp(3));
        bottomPanel.addView(timeText,timeParams);

        timeBar=new SeekBar(this);
        timeBar.setMax(1000);
        timeBar.setProgress(0);
        timeBar.setFocusable(true);
        timeBar.setFocusableInTouchMode(true);
        timeBar.setContentDescription("Barra de busca do vídeo. Use esquerda e direita para escolher qualquer ponto da reprodução.");
        timeBar.setProgressTintList(ColorStateList.valueOf(Color.rgb(78,132,255)));
        timeBar.setProgressBackgroundTintList(ColorStateList.valueOf(Color.rgb(63,72,86)));
        timeBar.setThumbTintList(ColorStateList.valueOf(Color.rgb(245,248,255)));
        bottomPanel.addView(timeBar,new LinearLayout.LayoutParams(-1,dp(26)));
        timeBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            @Override public void onProgressChanged(SeekBar bar,int progress,boolean fromUser){
                if(fromUser&&player!=null){
                    long duration=player.getDuration();
                    if(duration>0&&duration!=C.TIME_UNSET){
                        long target=duration*progress/1000L;
                        timeText.setText(formatTime(target)+" / "+formatTime(duration)+"  •  Restam "+formatTime(Math.max(0,duration-target)));
                        if(player.isCurrentMediaItemSeekable())player.seekTo(target);
                    }
                }
            }
            @Override public void onStartTrackingTouch(SeekBar bar){
                showOverlay();handler.removeCallbacks(hideOverlayTask);
            }
            @Override public void onStopTrackingTouch(SeekBar bar){
                if(player!=null){
                    long duration=player.getDuration();
                    if(duration>0&&duration!=C.TIME_UNSET)player.seekTo(duration*bar.getProgress()/1000L);
                    saveProgress(true);updatePlaybackTime();
                    if(player.isPlaying())scheduleHideOverlay();
                }
            }
        });

        nextEpisodeButton=new Button(this);
        nextEpisodeButton.setAllCaps(false);
        nextEpisodeButton.setText("PRÓXIMO EPISÓDIO  ›");
        nextEpisodeButton.setTextColor(Color.WHITE);
        nextEpisodeButton.setTextSize(15);
        nextEpisodeButton.setTypeface(Typeface.DEFAULT_BOLD);
        nextEpisodeButton.setGravity(Gravity.CENTER);
        nextEpisodeButton.setFocusable(true);
        nextEpisodeButton.setBackground(rounded(Color.rgb(48,93,184),9));
        LinearLayout.LayoutParams nextParams=new LinearLayout.LayoutParams(-2,dp(48));
        nextParams.setMargins(0,dp(9),0,0);
        bottomPanel.addView(nextEpisodeButton,nextParams);
        nextEpisodeButton.setOnClickListener(v->playNextEpisode());
        nextEpisodeButton.setVisibility(View.GONE);

        view.setOnClickListener(v->togglePlayback());
        setContentView(playerRoot);
    }

    private void buildQueue(android.content.Intent intent){
        singleResumeKey=intent.getStringExtra("resume_key");
        ArrayList<String> urls=intent.getStringArrayListExtra("playlist_urls");
        ArrayList<String> titles=intent.getStringArrayListExtra("playlist_titles");
        episodeIds=intent.getLongArrayExtra("episode_ids");
        seriesIds=intent.getLongArrayExtra("series_ids");
        seasons=intent.getIntArrayExtra("episode_seasons");
        episodeNumbers=intent.getIntArrayExtra("episode_numbers");
        startIndex=Math.max(0,intent.getIntExtra("start_index",0));
        if(urls!=null){
            for(int i=0;i<urls.size();i++){
                String title=titles!=null&&i<titles.size()?titles.get(i):intent.getStringExtra("title");
                mediaItems.add(mediaItem(urls.get(i),title));
            }
        }else{
            String url=intent.getStringExtra("url");
            if(url!=null&&!url.trim().isEmpty())mediaItems.add(mediaItem(url,intent.getStringExtra("title")));
        }
        if(startIndex>=mediaItems.size())startIndex=0;
        boolean episodeQueue=episodeIds!=null&&seriesIds!=null&&mediaItems.size()>0;
        if(episodeQueue)nextEpisodeButton.setVisibility(View.VISIBLE);
    }

    private MediaItem mediaItem(String url,String title){
        MediaItem.Builder b=new MediaItem.Builder().setUri(Uri.parse(url));
        if(title!=null&&!title.isEmpty())b.setMediaMetadata(new MediaMetadata.Builder().setTitle(title).build());
        String lower=url==null?"":url.toLowerCase(Locale.ROOT);
        if(lower.contains(".m3u8"))b.setMimeType(MimeTypes.APPLICATION_M3U8);
        else if(lower.contains(".mpd"))b.setMimeType(MimeTypes.APPLICATION_MPD);
        return b.build();
    }

    @Override protected void onStart(){
        super.onStart();released=false;
        if(player==null)startPlayer();
    }

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
                updatePlaybackTime();
                if(state==Player.STATE_ENDED){
                    saveProgress(true);
                    showOverlay();
                    if(pauseIndicator!=null)pauseIndicator.setVisibility(View.VISIBLE);
                }
            }
            @Override public void onIsPlayingChanged(boolean isPlaying){
                if(isPlaying){
                    if(pauseIndicator!=null)pauseIndicator.setVisibility(View.GONE);
                    scheduleHideOverlay();
                }else if(player!=null&&!player.getPlayWhenReady()&&player.getPlaybackState()!=Player.STATE_ENDED){
                    showOverlay();
                    if(pauseIndicator!=null)pauseIndicator.setVisibility(View.VISIBLE);
                }
                updatePlaybackTime();
            }
            @Override public void onMediaItemTransition(MediaItem item,int reason){
                if(player==null)return;
                updateTitle();
                updatePlaybackTime();
                if(reason!=Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED){
                    int current=player.getCurrentMediaItemIndex();
                    String key=progressKeyForIndex(current);
                    long saved=key==null?0L:playbackProgress.getLong("pos_"+key,0L);
                    if(saved>0L){
                        handler.post(()->{
                            if(player!=null&&!released&&player.getCurrentMediaItemIndex()==current){
                                long duration=player.getDuration();
                                long target=duration>0&&duration!=C.TIME_UNSET?Math.min(saved,Math.max(0,duration-1000L)):saved;
                                player.seekTo(target);
                                resumePos=target;
                                updatePlaybackTime();
                            }
                        });
                    }
                }
                if(reason==Player.MEDIA_ITEM_TRANSITION_REASON_AUTO
                        &&!getSharedPreferences(NetworkStack.PREFS,MODE_PRIVATE).getBoolean(NetworkStack.AUTOPLAY_NEXT,true)){
                    player.pause();
                    showOverlay();
                    pauseIndicator.setVisibility(View.VISIBLE);
                }
            }
        });
        view.setPlayer(player);
        String initialKey=progressKeyForIndex(startIndex);
        resumePos=initialKey==null?0L:playbackProgress.getLong("pos_"+initialKey,0L);
        if(resumePos<0L)resumePos=0L;
        player.setMediaItems(mediaItems,startIndex,resumePos);
        player.prepare();
        player.play();
        updateTitle();
        updatePlaybackTime();
        playerRoot.requestFocus();
        handler.post(updateTask);
    }

    private boolean hasEpisodeQueue(){
        return episodeIds!=null&&seriesIds!=null&&episodeIds.length>0&&seriesIds.length>0;
    }

    private void updateTitle(){
        if(titleText==null||player==null)return;
        MediaItem item=player.getCurrentMediaItem();
        CharSequence title=item==null?null:item.mediaMetadata.title;
        titleText.setText(title==null||title.length()==0?"Marcin IPTV":title);
    }

    private void updatePlaybackTime(){
        if(player==null||timeText==null)return;
        long position=Math.max(0,player.getCurrentPosition());
        long duration=player.getDuration();
        if(duration<0||duration==C.TIME_UNSET){
            timeText.setText(formatTime(position)+" / --:--  •  Tempo restante --:--");
            if(!timeBar.hasFocus())timeBar.setProgress(0);
        }else if(!timeBar.hasFocus()){
            long remaining=Math.max(0,duration-position);
            timeText.setText(formatTime(position)+" / "+formatTime(duration)+"  •  Restam "+formatTime(remaining));
            timeBar.setProgress(duration>0?(int)Math.min(1000,(position*1000L)/duration):0);
        }
        if(hasEpisodeQueue()){
            boolean canNext=player.hasNextMediaItem();
            nextEpisodeButton.setEnabled(canNext);
            nextEpisodeButton.setAlpha(canNext?1f:0.55f);
            nextEpisodeButton.setText(canNext?"PRÓXIMO EPISÓDIO  ›":"ÚLTIMO EPISÓDIO");
            if(nextEpisodeButton.getVisibility()!=View.VISIBLE)nextEpisodeButton.setVisibility(View.VISIBLE);
        }
    }

    private String formatTime(long millis){
        long total=Math.max(0,millis/1000L);
        long h=total/3600L,m=(total%3600L)/60L,s=total%60L;
        return h>0?String.format(Locale.ROOT,"%02d:%02d:%02d",h,m,s)
                :String.format(Locale.ROOT,"%02d:%02d",m,s);
    }

    private void showOverlay(){
        if(bottomPanel==null)return;
        updateTitle();
        updatePlaybackTime();
        bottomPanel.setVisibility(View.VISIBLE);
        handler.removeCallbacks(hideOverlayTask);
    }

    private void scheduleHideOverlay(){
        handler.removeCallbacks(hideOverlayTask);
        handler.postDelayed(hideOverlayTask,OVERLAY_TIMEOUT_MS);
    }

    private void togglePlayback(){
        if(player==null)return;
        if(player.isPlaying()||player.getPlayWhenReady()){
            player.pause();
            showOverlay();
            pauseIndicator.setVisibility(View.VISIBLE);
            playerRoot.requestFocus();
        }else{
            player.play();
            pauseIndicator.setVisibility(View.GONE);
            showOverlay();
            scheduleHideOverlay();
        }
    }

    private void playNextEpisode(){
        if(player==null||!player.hasNextMediaItem()){
            Toast.makeText(this,"Não há outro episódio nesta lista.",Toast.LENGTH_SHORT).show();
            return;
        }
        handler.removeCallbacks(hideOverlayTask);
        player.seekToNextMediaItem();
        player.play();
        pauseIndicator.setVisibility(View.GONE);
        showOverlay();
        scheduleHideOverlay();
        playerRoot.requestFocus();
    }

    @Override public boolean dispatchKeyEvent(KeyEvent event){
        int key=event.getKeyCode();
        boolean confirm=key==KeyEvent.KEYCODE_DPAD_CENTER||key==KeyEvent.KEYCODE_ENTER||key==KeyEvent.KEYCODE_NUMPAD_ENTER;
        boolean left=key==KeyEvent.KEYCODE_DPAD_LEFT,right=key==KeyEvent.KEYCODE_DPAD_RIGHT;
        if(event.getAction()==KeyEvent.ACTION_DOWN&&key==KeyEvent.KEYCODE_DPAD_DOWN
                &&timeBar!=null&&timeBar.hasFocus()&&nextEpisodeButton!=null
                &&nextEpisodeButton.getVisibility()==View.VISIBLE&&nextEpisodeButton.isEnabled()){
            nextEpisodeButton.requestFocus();return true;
        }
        if(event.getAction()==KeyEvent.ACTION_DOWN&&key==KeyEvent.KEYCODE_DPAD_UP
                &&nextEpisodeButton!=null&&nextEpisodeButton.hasFocus()){
            timeBar.requestFocus();showOverlay();return true;
        }
        if(event.getAction()==KeyEvent.ACTION_DOWN&&key==KeyEvent.KEYCODE_DPAD_UP
                &&timeBar!=null&&timeBar.hasFocus()){
            playerRoot.requestFocus();return true;
        }
        if(event.getAction()==KeyEvent.ACTION_DOWN&&key==KeyEvent.KEYCODE_DPAD_UP
                &&playerRoot!=null&&playerRoot.hasFocus()){
            showOverlay();timeBar.requestFocus();return true;
        }
        if(timeBar!=null&&timeBar.hasFocus()&&(left||right||confirm)){
            if(event.getAction()==KeyEvent.ACTION_DOWN)showOverlay();
            return super.dispatchKeyEvent(event);
        }
        if(confirm&&nextEpisodeButton!=null&&nextEpisodeButton.hasFocus())
            return super.dispatchKeyEvent(event);
        if(confirm){
            if(event.getAction()==KeyEvent.ACTION_DOWN){togglePlayback();return true;}
            if(event.getAction()==KeyEvent.ACTION_UP)return true;
        }
        if(key==KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE||key==KeyEvent.KEYCODE_SPACE){
            if(event.getAction()==KeyEvent.ACTION_DOWN){togglePlayback();return true;}
            if(event.getAction()==KeyEvent.ACTION_UP)return true;
        }
        if(event.getAction()==KeyEvent.ACTION_UP&&(left||right)&&player!=null){
            long offset=right?30000L:-30000L;
            long duration=player.getDuration();long position=Math.max(0,player.getCurrentPosition()+offset);
            if(duration>=0&&duration!=C.TIME_UNSET)position=Math.min(duration,position);
            player.seekTo(position);saveProgress(true);showOverlay();
            if(player.isPlaying())scheduleHideOverlay();
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    private void retryPlayback(){
        if(player==null||released)return;
        try{
            player.setMediaItems(mediaItems,player.getCurrentMediaItemIndex(),resumePos);
            player.prepare();player.play();
            pauseIndicator.setVisibility(View.GONE);
        }catch(Exception e){
            Toast.makeText(this,"Não foi possível retomar a reprodução.",Toast.LENGTH_LONG).show();
        }
    }

    private String progressKeyForIndex(int index){
        if(seriesIds!=null&&episodeIds!=null&&index>=0&&index<seriesIds.length&&index<episodeIds.length
                &&seriesIds[index]>0&&episodeIds[index]>0)
            return "series_"+seriesIds[index]+"_episode_"+episodeIds[index];
        return singleResumeKey==null||singleResumeKey.trim().isEmpty()?null:singleResumeKey;
    }

    private void saveProgress(){saveProgress(false);}

    private void saveProgress(boolean force){
        if(player==null||playbackProgress==null)return;
        long now=System.currentTimeMillis();
        if(!force&&now-lastProgressSaveAt<5000L)return;
        int ix=player.getCurrentMediaItemIndex();
        if(ix<0)return;
        String key=progressKeyForIndex(ix);
        if(key==null)return;
        long position=player.getPlaybackState()==Player.STATE_ENDED?0L:Math.max(0L,player.getCurrentPosition());
        resumePos=position;lastProgressSaveAt=now;
        playbackProgress.edit().putLong("pos_"+key,position).apply();
        if(seriesIds!=null&&episodeIds!=null&&ix<seriesIds.length&&ix<episodeIds.length){
            long sid=seriesIds[ix],eid=episodeIds[ix];
            if(sid>0&&eid>0)getSharedPreferences("marcin_progress",MODE_PRIVATE).edit()
                    .putLong("last_episode_"+sid,eid)
                    .putInt("last_season_"+sid,seasons!=null&&ix<seasons.length?seasons[ix]:0)
                    .putInt("last_episode_number_"+sid,episodeNumbers!=null&&ix<episodeNumbers.length?episodeNumbers[ix]:0)
                    .apply();
        }
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
        if(player!=null){
            saveProgress(true);resumePos=player.getCurrentPosition();
            view.setPlayer(null);player.release();player=null;
        }
    }

    @Override public void onUserInteraction(){
        super.onUserInteraction();
        CatalogRefreshScheduler.onUserActivity(this);
    }

    @Override public void onBackPressed(){
        if(player!=null&&player.isPlaying())player.pause();
        super.onBackPressed();
    }
}
