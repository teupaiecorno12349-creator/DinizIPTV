package com.diniz.iptv.player;

import android.app.Activity;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.widget.Toast;

import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.DefaultLoadControl;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.ui.PlayerView;

@UnstableApi
public class PlaybackActivity extends Activity {
    private ExoPlayer player;
    private PlayerView view;
    private String url;
    private long resumePos=0;
    private int retries=0;
    private final Handler handler=new Handler(Looper.getMainLooper());

    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        immersive();
        url=getIntent().getStringExtra("url");
        view=new PlayerView(this);
        view.setUseController(true);
        view.setControllerAutoShow(true);
        view.setControllerHideOnTouch(true);
        view.setKeepScreenOn(true);
        view.setFocusable(true);
        setContentView(view);
    }

    @Override protected void onStart(){super.onStart();startPlayer();}

    private void startPlayer(){
        if(url==null||url.trim().isEmpty()){Toast.makeText(this,"Link de vídeo vazio.",Toast.LENGTH_LONG).show();finish();return;}
        if(player!=null) return;
        DefaultLoadControl load=new DefaultLoadControl.Builder().setBufferDurationsMs(15000,90000,2500,5000).build();
        DefaultHttpDataSource.Factory http=new DefaultHttpDataSource.Factory().setUserAgent("DinizIPTV/1.1").setConnectTimeoutMs(15000).setReadTimeoutMs(30000).setAllowCrossProtocolRedirects(true);
        DefaultMediaSourceFactory mediaSourceFactory=new DefaultMediaSourceFactory(http);
        player=new ExoPlayer.Builder(this).setLoadControl(load).setMediaSourceFactory(mediaSourceFactory).build();
        view.setPlayer(player);
        player.addListener(new Player.Listener(){
            @Override public void onPlayerError(PlaybackException error){
                if(player==null)return;
                resumePos=player.getCurrentPosition();
                if(retries<3){
                    retries++;
                    handler.postDelayed(()->{
                        if(player!=null){
                            player.setMediaItem(MediaItem.fromUri(url));
                            player.prepare();
                            player.seekTo(resumePos);
                            player.play();
                        }
                    },2000L*retries);
                } else Toast.makeText(PlaybackActivity.this,"Falha ao reproduzir: "+error.getErrorCodeName(),Toast.LENGTH_LONG).show();
            }
            @Override public void onPlaybackStateChanged(int state){if(state==Player.STATE_READY) retries=0;}
        });
        player.setMediaItem(MediaItem.fromUri(url));
        player.prepare();
        if(resumePos>0)player.seekTo(resumePos);
        player.play();
    }

    private void immersive(){
        if(Build.VERSION.SDK_INT>=30){
            getWindow().setDecorFitsSystemWindows(false);
            WindowInsetsController c=getWindow().getInsetsController();
            if(c!=null){c.hide(WindowInsets.Type.statusBars()|WindowInsets.Type.navigationBars());c.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);}
        }else{
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY|View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN|View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        }
    }

    @Override public void onWindowFocusChanged(boolean hasFocus){super.onWindowFocusChanged(hasFocus);if(hasFocus)immersive();}
    @Override protected void onResume(){super.onResume();immersive();}
    @Override protected void onStop(){
        if(player!=null){resumePos=player.getCurrentPosition();player.release();player=null;}
        handler.removeCallbacksAndMessages(null);
        super.onStop();
    }
}
