package cl.fernando.nubemusica;

import android.content.ComponentName;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.media3.common.Player;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.session.*;
import com.google.common.util.concurrent.ListenableFuture;

/** Every screen controls the same service; opening a screen never restarts a track. */
@UnstableApi
public abstract class ConnectedActivity extends AppCompatActivity {
    @Override protected void onCreate(Bundle savedInstanceState){super.onCreate(savedInstanceState);TvUi.configure(this);}
    protected MediaController controller;
    protected final Handler ui=new Handler(Looper.getMainLooper());
    private ListenableFuture<MediaController> connection;
    private boolean visible;
    private final Runnable tick=new Runnable(){public void run(){if(visible){onTick();ui.postDelayed(this,500);}}};
    private final Player.Listener listener=new Player.Listener(){
        @Override public void onEvents(Player player,Player.Events events){onPlaybackChanged(events);}
        @Override public void onPlayerError(PlaybackException e){
            String message="No se pudo reproducir. Revisa la conexión y el acceso al archivo.";
            if(controller!=null&&controller.getCurrentMediaItem()!=null&&controller.getCurrentMediaItem().mediaId.startsWith("mega://"))
                for(Throwable cause=e;cause!=null;cause=cause.getCause())if(cause instanceof java.io.IOException&&cause.getMessage()!=null){message=cause.getMessage();break;}
            toast(message);
        }
    };
    @Override protected void onStart(){
        super.onStart();visible=true;
        ListenableFuture<MediaController> next=new MediaController.Builder(this,new SessionToken(this,new ComponentName(this,PlaybackService.class))).buildAsync();connection=next;
        next.addListener(()->{if(!visible||connection!=next)return;try{controller=next.get();controller.addListener(listener);onConnected();}catch(Exception e){toast("No se pudo conectar al reproductor.");}},ContextCompat.getMainExecutor(this));
        ui.post(tick);
    }
    @Override protected void onStop(){
        visible=false;ui.removeCallbacksAndMessages(null);
        if(controller!=null){controller.removeListener(listener);controller=null;}
        if(connection!=null){MediaController.releaseFuture(connection);connection=null;}
        super.onStop();
    }
    protected void onConnected(){}
    protected void onPlaybackChanged(Player.Events events){}
    protected void onTick(){}
    protected void togglePlay(){
        if(controller==null)return;
        if(controller.getPlayWhenReady()&&controller.getPlaybackState()!=Player.STATE_ENDED)controller.pause();
        else if(controller.getMediaItemCount()>0){if(controller.getPlaybackState()==Player.STATE_IDLE)controller.prepare();if(controller.getPlaybackState()==Player.STATE_ENDED)controller.seekToDefaultPosition();controller.play();}
        else toast("Elige una canción en la biblioteca.");
    }
    protected void seekBy(long delta){
        if(controller!=null&&controller.isCommandAvailable(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)&&controller.isCurrentMediaItemSeekable())
            controller.seekTo(PlaybackRules.seek(controller.getCurrentPosition(),controller.getDuration(),delta));
    }
    protected void toast(String message){if(!isDestroyed())Toast.makeText(this,message,Toast.LENGTH_LONG).show();}
}
