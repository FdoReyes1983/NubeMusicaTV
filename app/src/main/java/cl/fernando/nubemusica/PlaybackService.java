package cl.fernando.nubemusica;

import android.app.PendingIntent;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.os.Bundle;
import androidx.media3.session.SessionCommand;
import androidx.media3.session.SessionResult;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import androidx.annotation.Nullable;
import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.Player;
import androidx.media3.common.Timeline;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.DefaultDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.session.MediaSession;
import androidx.media3.session.MediaSessionService;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

/** Owns playback independently of the screen, plus Android's media notification. */
@UnstableApi
public final class PlaybackService extends MediaSessionService {
    private ExoPlayer player;
    private MediaSession session;
    private SharedPreferences state;
    private AudioEqualizer equalizer;
    private final Runnable saveChangedQueue=this::saveQueue;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable checkpoint = new Runnable() {
        @Override public void run() { savePosition(); handler.postDelayed(this, 10000); }
    };

    @Override public void onCreate() {
        super.onCreate();
        AuthManager auth = ((NubeApp) getApplication()).auth();
        DefaultDataSource.Factory sources = new DefaultDataSource.Factory(this, () -> new CloudDataSource((NubeApp)getApplication()));
        player = new ExoPlayer.Builder(this)
                .setMediaSourceFactory(new DefaultMediaSourceFactory(sources))
                .setAudioAttributes(new AudioAttributes.Builder().setUsage(C.USAGE_MEDIA)
                        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(), true)
                .setSeekBackIncrementMs(5000).setSeekForwardIncrementMs(5000)
                .setHandleAudioBecomingNoisy(true)
                .setWakeMode(C.WAKE_MODE_NETWORK)
                .build();
        state = getSharedPreferences("playback", MODE_PRIVATE);
        equalizer=new AudioEqualizer(this);
        equalizer.attach(player.getAudioSessionId());
        restore();
        player.addListener(new Player.Listener() {
            @Override public void onTimelineChanged(Timeline timeline, int reason) { handler.removeCallbacks(saveChangedQueue);handler.postDelayed(saveChangedQueue,350); }
            @Override public void onAudioSessionIdChanged(int audioSessionId) { equalizer.attach(audioSessionId); }
            @Override public void onMediaItemTransition(@Nullable MediaItem item, int reason) { savePosition(); }
            @Override public void onPlayWhenReadyChanged(boolean ready, int reason) { savePosition(); }
            @Override public void onPositionDiscontinuity(Player.PositionInfo oldPosition, Player.PositionInfo newPosition, int reason) {
                if(oldPosition.mediaItem!=null && !java.util.Objects.equals(oldPosition.mediaItem,newPosition.mediaItem))
                    VideoProgress.save(PlaybackService.this,oldPosition.mediaItem,reason==Player.DISCONTINUITY_REASON_AUTO_TRANSITION?0:oldPosition.positionMs,0);
                savePosition();
            }
            @Override public void onShuffleModeEnabledChanged(boolean enabled) { savePosition(); }
            @Override public void onRepeatModeChanged(int mode) { savePosition(); }
        });
        PendingIntent open = PendingIntent.getActivity(this, 0, new Intent(this, PlayerActivity.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        session = new MediaSession.Builder(this, player).setSessionActivity(open).setCallback(new MediaSession.Callback(){
            @Override public MediaSession.ConnectionResult onConnect(MediaSession s,MediaSession.ControllerInfo c){
                MediaSession.ConnectionResult base=MediaSession.Callback.super.onConnect(s,c);
                if(c.getUid()!=Process.myUid())return base;
                return MediaSession.ConnectionResult.accept(base.availableSessionCommands.buildUpon().add(new SessionCommand(AudioEqualizer.COMMAND,Bundle.EMPTY)).build(),base.availablePlayerCommands);
            }
            @Override public ListenableFuture<SessionResult> onCustomCommand(MediaSession s,MediaSession.ControllerInfo c,SessionCommand command,Bundle args){
                if(c.getUid()==Process.myUid()&&AudioEqualizer.COMMAND.equals(command.customAction))return Futures.immediateFuture(new SessionResult(SessionResult.RESULT_SUCCESS,equalizer.command(args)));
                return Futures.immediateFuture(new SessionResult(androidx.media3.session.SessionError.ERROR_PERMISSION_DENIED));
            }
        }).build();
        handler.postDelayed(checkpoint, 10000);
    }

    @Nullable @Override public MediaSession onGetSession(MediaSession.ControllerInfo controller) {
        // Only this app and trusted Android media controllers may control the service.
        return controller.getUid() == Process.myUid() || controller.isTrusted() ? session : null;
    }

    private void saveQueue() {
        if (player == null) return;
        try {
            JSONArray array = new JSONArray();
            for (int i = 0; i < player.getMediaItemCount(); i++) {
                MediaItem item = player.getMediaItemAt(i);
                if (item.localConfiguration == null) continue;
                array.put(new JSONObject().put("id", item.mediaId)
                        .put("uri", item.localConfiguration.uri.toString())
                        .put("mime", item.localConfiguration.mimeType)
                        .put("title", item.mediaMetadata.title == null ? "Audio" : item.mediaMetadata.title.toString())
                        .put("artist", item.mediaMetadata.artist == null ? "" : item.mediaMetadata.artist.toString()));
                Track track=Track.fromMediaItem(item);
                if(track!=null)array.getJSONObject(array.length()-1).put("track",track.json());
            }
            state.edit().putString("queue", array.toString()).apply();
            savePosition();
        } catch (org.json.JSONException ignored) { }
    }

    private void savePosition() {
        if (player == null || state == null) return;
        VideoProgress.save(this,player.getCurrentMediaItem(),player.getCurrentPosition(),player.getDuration());
        state.edit().putInt("index", Math.max(0, player.getCurrentMediaItemIndex()))
                .putLong("position", Math.max(0, player.getCurrentPosition()))
                .putBoolean("shuffle", player.getShuffleModeEnabled())
                .putInt("repeat", player.getRepeatMode()).apply();
    }

    private void restore() {
        try {
            JSONArray array = new JSONArray(state.getString("queue", "[]"));
            List<MediaItem> items = new ArrayList<>();
            for (int i = 0; i < array.length(); i++) {
                JSONObject json = array.getJSONObject(i);
                String uri = json.getString("uri");
                if (!uri.startsWith("gdrive://file/") && !uri.startsWith("content://") && !uri.startsWith("mega://file/")) continue;
                JSONObject track=json.optJSONObject("track");
                if(track!=null){items.add(Track.fromSaved(track).mediaItem());continue;}
                MediaItem.Builder builder = new MediaItem.Builder().setMediaId(json.getString("id")).setUri(uri)
                        .setMediaMetadata(new MediaMetadata.Builder().setTitle(json.optString("title"))
                                .setArtist(json.optString("artist")).build());
                String mime = json.optString("mime", "");
                if (!mime.isEmpty()) builder.setMimeType(mime);
                items.add(builder.build());
            }
            if (!items.isEmpty()) player.setMediaItems(items,
                    Math.max(0, Math.min(state.getInt("index", 0), items.size() - 1)),
                    Math.max(0, state.getLong("position", 0)));
            player.setShuffleModeEnabled(state.getBoolean("shuffle", false));
            int mode = state.getInt("repeat", Player.REPEAT_MODE_OFF);
            player.setRepeatMode(mode >= 0 && mode <= 2 ? mode : Player.REPEAT_MODE_OFF);
            // Restoring the queue never starts audio automatically.
        } catch (Exception ignored) { state.edit().clear().apply(); }
    }

    @Override public void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        saveQueue();
        if (session != null) session.release();
        if (equalizer != null) equalizer.release();
        if (player != null) player.release();
        session = null;
        player = null;
        super.onDestroy();
    }
}
