package com.diniz.iptv.player;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
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
    private ExoPlayer player; private PlayerView view; private String url; private long resumePos=0; private int retries=0;
    private final Handler handler=new Handler();

    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        url=getIntent().getStringExtra("url");
        view=new PlayerView(this);view.setUseController(true);view.setControllerAutoShow(true);view.setKeepScreenOn(true);setContentView(view);
    }

    @Override protected void onStart(){super.onStart();startPlayer();}

    private void startPlayer(){
        if(url==null||url.trim().isEmpty()){Toast.makeText(this,"Link de vídeo vazio.",Toast.LENGTH_LONG).show();finish();return;}
        DefaultLoadControl load=new DefaultLoadControl.Builder().setBufferDurationsMs(15000,90000,2500,5000).build();
        DefaultHttpDataSource.Factory http=new DefaultHttpDataSource.Factory().setUserAgent("DinizIPTV/1.0").setConnectTimeoutMs(15000).setReadTimeoutMs(30000).setAllowCrossProtocolRedirects(true);
        DefaultMediaSourceFactory mediaSourceFactory=new DefaultMediaSourceFactory(http);
        player=new ExoPlayer.Builder(this).setLoadControl(load).setMediaSourceFactory(mediaSourceFactory).build();
        view.setPlayer(player);
        player.addListener(new Player.Listener(){
            @Override public void onPlayerError(PlaybackException error){
                resumePos=player.getCurrentPosition();
                if(retries<3){retries++;handler.postDelayed(()->{if(player!=null){player.setMediaItem(MediaItem.fromUri(url));player.prepare();player.seekTo(resumePos);player.play();}},2000L*retries);}
                else Toast.makeText(PlaybackActivity.this,"Falha ao reproduzir: "+error.getErrorCodeName(),Toast.LENGTH_LONG).show();
            }
            @Override public void onPlaybackStateChanged(int state){if(state==Player.STATE_READY) retries=0;}
        });
        player.setMediaItem(MediaItem.fromUri(url));player.prepare();if(resumePos>0)player.seekTo(resumePos);player.play();
    }
    @Override protected void onStop(){super.onStop();if(player!=null){resumePos=player.getCurrentPosition();player.release();player=null;}handler.removeCallbacksAndMessages(null);}
}
