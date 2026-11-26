package com.diniz.iptv.player;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
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

import org.json.JSONObject;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@UnstableApi
public class PlaybackActivity extends Activity {
    private static final long REVALIDATE_INTERVAL_MS = 5 * 60 * 1000L;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private ExoPlayer player;
    private PlayerView playerView;
    private String mediaKind;
    private String mediaId;
    private String extension;
    private String mediaTitle;
    private long resumePosition;
    private int retries;
    private boolean active;
    private boolean validating;
    private String streamUrl;

    private final Runnable revalidateTask = new Runnable() {
        @Override public void run() {
            if (active) validateAccess(false);
        }
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        mediaKind = getIntent().getStringExtra("kind");
        mediaId = getIntent().getStringExtra("id");
        extension = getIntent().getStringExtra("extension");
        mediaTitle = getIntent().getStringExtra("title");

        playerView = new PlayerView(this);
        playerView.setUseController(true);
        playerView.setControllerAutoShow(true);
        playerView.setKeepScreenOn(true);
        playerView.setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING);
        setContentView(playerView);
    }

    @Override protected void onStart() {
        super.onStart();
        active = true;
        validateAccess(true);
    }

    private void validateAccess(boolean firstStart) {
        if (!active || validating) return;
        if (!SessionStore.isActive()) {
            Toast.makeText(this, "Sua sessão terminou. Entre novamente.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        validating = true;
        io.execute(() -> {
            try {
                JSONObject response = MarcinApi.login(SessionStore.username(), SessionStore.password());
                String base = response.getJSONObject("provider").getString("base_url");
                SessionStore.updateProviderBase(base);
                String url = SessionStore.playbackUrl(mediaKind, mediaId, extension);
                runOnUiThread(() -> {
                    validating = false;
                    if (!active) return;
                    streamUrl = url;
                    if (player == null) startPlayer();
                    handler.removeCallbacks(revalidateTask);
                    handler.postDelayed(revalidateTask, REVALIDATE_INTERVAL_MS);
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    validating = false;
                    if (!active) return;
                    releasePlayer();
                    Toast.makeText(this, safeMessage(e), Toast.LENGTH_LONG).show();
                    finish();
                });
            }
        });
    }

    private void startPlayer() {
        if (streamUrl == null || streamUrl.trim().isEmpty()) {
            Toast.makeText(this, "Não foi possível montar o endereço de reprodução.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        DefaultLoadControl loadControl = new DefaultLoadControl.Builder()
                .setBufferDurationsMs(15000, 90000, 2500, 5000)
                .build();
        DefaultHttpDataSource.Factory http = new DefaultHttpDataSource.Factory()
                .setUserAgent("DinizIPTV/1.1")
                .setConnectTimeoutMs(20000)
                .setReadTimeoutMs(45000)
                .setAllowCrossProtocolRedirects(true);
        DefaultMediaSourceFactory mediaSourceFactory = new DefaultMediaSourceFactory(http);

        player = new ExoPlayer.Builder(this)
                .setLoadControl(loadControl)
                .setMediaSourceFactory(mediaSourceFactory)
                .build();
        playerView.setPlayer(player);
        player.addListener(new Player.Listener() {
            @Override public void onPlayerError(PlaybackException error) {
                if (player == null) return;
                resumePosition = player.getCurrentPosition();
                if (retries < 3 && active) {
                    retries++;
                    handler.postDelayed(() -> {
                        if (player != null && active && streamUrl != null) {
                            player.setMediaItem(MediaItem.fromUri(streamUrl));
                            player.prepare();
                            player.seekTo(resumePosition);
                            player.play();
                        }
                    }, 2000L * retries);
                } else {
                    Toast.makeText(PlaybackActivity.this,
                            "Falha na reprodução. Verifique sua conexão ou tente outro canal.",
                            Toast.LENGTH_LONG).show();
                }
            }

            @Override public void onPlaybackStateChanged(int state) {
                if (state == Player.STATE_READY) retries = 0;
            }
        });
        if (mediaTitle != null) playerView.setCustomErrorMessage("Não foi possível reproduzir este conteúdo.");
        player.setMediaItem(MediaItem.fromUri(streamUrl));
        player.prepare();
        if (resumePosition > 0) player.seekTo(resumePosition);
        player.play();
    }

    private void releasePlayer() {
        if (player != null) {
            resumePosition = player.getCurrentPosition();
            playerView.setPlayer(null);
            player.release();
            player = null;
        }
    }

    private String safeMessage(Exception e) {
        String message = e.getMessage();
        return message == null || message.trim().isEmpty()
                ? "Não foi possível validar o acesso. Tente novamente." : message;
    }

    @Override protected void onStop() {
        active = false;
        handler.removeCallbacksAndMessages(null);
        releasePlayer();
        super.onStop();
    }

    @Override protected void onDestroy() {
        io.shutdown();
        super.onDestroy();
    }
}
