package com.mgplay.app;

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
public final class PlayerActivity extends Activity {
    private ExoPlayer player;
    private PlayerView playerView;
    private String streamUrl;
    private String title;
    private long resumePosition;
    private int retries;
    private final Handler handler = new Handler();

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        streamUrl = getIntent().getStringExtra("url");
        title = getIntent().getStringExtra("title");
        playerView = new PlayerView(this);
        playerView.setUseController(true);
        playerView.setControllerAutoShow(true);
        playerView.setKeepScreenOn(true);
        playerView.setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING);
        setContentView(playerView);
    }

    @Override protected void onStart() { super.onStart(); startPlayer(); }

    private void startPlayer() {
        if (streamUrl == null || !(streamUrl.startsWith("http://") || streamUrl.startsWith("https://"))) {
            Toast.makeText(this, "Endereço de reprodução inválido.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        DefaultLoadControl loadControl = new DefaultLoadControl.Builder()
            .setBufferDurationsMs(12000, 60000, 1800, 3500).build();
        DefaultHttpDataSource.Factory http = new DefaultHttpDataSource.Factory()
            .setUserAgent("MGPlay/1.0 AndroidTV")
            .setConnectTimeoutMs(15000).setReadTimeoutMs(30000)
            .setAllowCrossProtocolRedirects(true);
        player = new ExoPlayer.Builder(this)
            .setLoadControl(loadControl)
            .setMediaSourceFactory(new DefaultMediaSourceFactory(http))
            .build();
        playerView.setPlayer(player);
        player.addListener(new Player.Listener() {
            @Override public void onPlayerError(PlaybackException error) {
                resumePosition = player.getCurrentPosition();
                if (retries < 2) {
                    retries++;
                    handler.postDelayed(() -> {
                        if (player != null) {
                            player.setMediaItem(MediaItem.fromUri(streamUrl));
                            player.prepare();
                            if (resumePosition > 0) player.seekTo(resumePosition);
                            player.play();
                        }
                    }, 1500L * retries);
                } else Toast.makeText(PlayerActivity.this, "Falha ao reproduzir: " + error.getErrorCodeName(), Toast.LENGTH_LONG).show();
            }
            @Override public void onPlaybackStateChanged(int state) { if (state == Player.STATE_READY) retries = 0; }
        });
        player.setMediaItem(MediaItem.fromUri(streamUrl));
        player.prepare();
        if (resumePosition > 0) player.seekTo(resumePosition);
        player.play();
    }

    @Override protected void onStop() {
        super.onStop();
        if (player != null) {
            resumePosition = player.getCurrentPosition();
            player.release();
            player = null;
        }
        handler.removeCallbacksAndMessages(null);
    }
}