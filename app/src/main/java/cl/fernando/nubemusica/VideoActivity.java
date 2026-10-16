package cl.fernando.nubemusica;

import android.app.PendingIntent;
import android.app.PictureInPictureParams;
import android.app.RemoteAction;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.os.Bundle;
import android.util.Rational;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.*;
import androidx.activity.OnBackPressedCallback;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.*;
import androidx.media3.common.*;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.ui.PlayerView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import java.util.Arrays;
import static cl.fernando.nubemusica.MusicUi.*;

/** A video surface attached to the same session that owns music and the queue. */
@UnstableApi
public final class VideoActivity extends ConnectedActivity {
    private static final String ACTION="cl.fernando.nubemusica.VIDEO_CONTROL";
    private PlayerView video;
    private FrameLayout root;
    private LinearLayout top,bottom;
    private TextView title,times,status;
    private MaterialButton back5,forward5,play;
    private SeekBar seek;
    private boolean chrome=true,seeking,navigating,registered,pipSupported;
    private Insets insets=Insets.NONE;
    private final BroadcastReceiver actions=new BroadcastReceiver(){
        @Override public void onReceive(Context c,Intent intent){
            if(!ACTION.equals(intent.getAction()))return;
            int action=intent.getIntExtra("control",0);
            if(action==1)seekBy(-5000);else if(action==2)togglePlay();else if(action==3)seekBy(5000);
            refresh();
        }
    };

    @Override protected void onCreate(Bundle saved){
        super.onCreate(saved);
        pipSupported=getPackageManager().hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE);
        WindowCompat.setDecorFitsSystemWindows(getWindow(),false);
        root=new FrameLayout(this);root.setBackgroundColor(Color.BLACK);setContentView(root);
        video=new PlayerView(this);video.setUseController(false);video.setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING);
        root.addView(video,new FrameLayout.LayoutParams(-1,-1));
        video.setContentDescription("Video. Toca para mostrar u ocultar los controles");
        video.setFocusable(TvUi.isTv(this));
        video.setOnClickListener(v->{chrome=!chrome;showChrome();});
        video.setOnKeyListener((v,key,event)->{if(!TvUi.isTv(this)||event.getAction()!=android.view.KeyEvent.ACTION_DOWN)return false;if(key==android.view.KeyEvent.KEYCODE_DPAD_CENTER||key==android.view.KeyEvent.KEYCODE_ENTER){togglePlay();return true;}if(key==android.view.KeyEvent.KEYCODE_DPAD_LEFT){seekBy(-10000);return true;}if(key==android.view.KeyEvent.KEYCODE_DPAD_RIGHT){seekBy(10000);return true;}return false;});

        top=row(this);top.setBackgroundColor(0xc00c1322);
        MaterialButton back=button(this,"‹",false);back.setTextSize(30);back.setContentDescription("Volver a la biblioteca");
        top.addView(back,new LinearLayout.LayoutParams(dp(this,44),dp(this,48)));back.setOnClickListener(v->leaveVideo());
        title=text(this,"Video",16,INK);title.setMaxLines(2);title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        top.addView(title,new LinearLayout.LayoutParams(0,dp(this,54),1));
        MaterialButton pip=button(this,"PiP",false);pip.setContentDescription("Ver en ventana flotante");pip.setEnabled(pipSupported);
        top.addView(pip,new LinearLayout.LayoutParams(dp(this,52),dp(this,48)));pip.setOnClickListener(v->enterPip());
        MaterialButton menu=button(this,"⋮",false);menu.setContentDescription("Opciones del video");top.addView(menu,new LinearLayout.LayoutParams(dp(this,44),dp(this,48)));menu.setOnClickListener(v->options());
        root.addView(top,new FrameLayout.LayoutParams(-1,-2,Gravity.TOP));

        bottom=col(this);bottom.setBackgroundColor(0xdd0c1322);
        status=text(this,"",12,ACCENT);bottom.addView(status,fill(dp(this,24)));
        seek=new SeekBar(this);seek.setMax(10000);seek.setContentDescription("Posición del video");TvUi.focus(seek);bottom.addView(seek,fill(dp(this,36)));
        times=text(this,"0:00 / 0:00",12,INK);times.setGravity(Gravity.CENTER);bottom.addView(times,fill(dp(this,22)));
        LinearLayout controls=row(this);back5=button(this,"−5 s",false);play=button(this,"▶",true);forward5=button(this,"+5 s",false);
        back5.setContentDescription("Retroceder 5 segundos");forward5.setContentDescription("Adelantar 5 segundos");
        for(MaterialButton b:new MaterialButton[]{back5,play,forward5})controls.addView(b,new LinearLayout.LayoutParams(0,dp(this,52),1));
        back5.setOnClickListener(v->seekBy(-5000));play.setOnClickListener(v->togglePlay());forward5.setOnClickListener(v->seekBy(5000));bottom.addView(controls);
        LinearLayout queueControls=row(this);
        MaterialButton previous=button(this,"Anterior",false),queue=button(this,"≡ Cola",false),next=button(this,"Siguiente",false);
        for(MaterialButton b:new MaterialButton[]{previous,queue,next})queueControls.addView(b,new LinearLayout.LayoutParams(0,dp(this,44),1));
        previous.setOnClickListener(v->{if(controller!=null)controller.seekToPrevious();});next.setOnClickListener(v->{if(controller!=null)controller.seekToNext();});
        queue.setOnClickListener(v->{navigating=true;updatePip();startActivity(new Intent(this,QueueActivity.class));});bottom.addView(queueControls);
        root.addView(bottom,new FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM));
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            public void onStartTrackingTouch(SeekBar s){seeking=true;}
            public void onProgressChanged(SeekBar s,int p,boolean user){if(user&&controller!=null&&controller.getDuration()>0)times.setText(DriveRules.time(controller.getDuration()*p/10000)+" / "+DriveRules.time(controller.getDuration()));}
            public void onStopTrackingTouch(SeekBar s){if(controller!=null&&controller.getDuration()>0&&controller.isCurrentMediaItemSeekable())controller.seekTo(controller.getDuration()*s.getProgress()/10000);seeking=false;}
        });
        ViewCompat.setOnApplyWindowInsetsListener(root,(v,w)->{insets=w.getInsets(WindowInsetsCompat.Type.systemBars()|WindowInsetsCompat.Type.displayCutout());applyInsets();return w;});
        video.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->updatePip());
        ContextCompat.registerReceiver(this,actions,new IntentFilter(ACTION),ContextCompat.RECEIVER_NOT_EXPORTED);registered=true;
        getOnBackPressedDispatcher().addCallback(this,new OnBackPressedCallback(true){public void handleOnBackPressed(){leaveVideo();}});
        chrome=TvUi.isTv(this)||getResources().getConfiguration().orientation!=Configuration.ORIENTATION_LANDSCAPE;showChrome();if(TvUi.isTv(this)) play.post(() -> play.requestFocus());
    }
    private boolean currentVideo(){Track t=controller==null||controller.getCurrentMediaItem()==null?null:Track.fromMediaItem(controller.getCurrentMediaItem());return t!=null&&t.isVideo();}
    private boolean autoPip(){return getSharedPreferences("video_options",MODE_PRIVATE).getBoolean("auto_pip",true);}
    private void leaveVideo(){navigating=true;if(controller!=null&&currentVideo())controller.pause();updatePip();finish();}
    private void options(){new MaterialAlertDialogBuilder(this).setTitle("Video")
        .setMultiChoiceItems(new String[]{"Ventana flotante al ir a Inicio"},new boolean[]{autoPip()},(d,w,checked)->{getSharedPreferences("video_options",MODE_PRIVATE).edit().putBoolean("auto_pip",checked).apply();updatePip();})
        .setPositiveButton("Listo",null).show();}
    private RemoteAction action(int id,int icon,String label,boolean enabled){
        Intent intent=new Intent(ACTION).setPackage(getPackageName()).putExtra("control",id);
        PendingIntent pending=PendingIntent.getBroadcast(this,id,intent,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        RemoteAction a=new RemoteAction(Icon.createWithResource(this,icon),label,label,pending);a.setEnabled(enabled);return a;
    }
    private PictureInPictureParams params(){
        VideoSize size=controller==null?VideoSize.UNKNOWN:controller.getVideoSize();
        double ratio=size.height>0?size.width*size.pixelWidthHeightRatio/size.height:16.0/9;
        ratio=Math.max(1/2.39,Math.min(2.39,ratio));
        PictureInPictureParams.Builder b=new PictureInPictureParams.Builder().setAspectRatio(new Rational((int)(ratio*10000),10000));
        Rect rect=new Rect();if(video.getGlobalVisibleRect(rect)&&rect.width()>0&&rect.height()>0)b.setSourceRectHint(rect);
        boolean ready=controller!=null&&controller.getPlayWhenReady()&&controller.getPlaybackState()!=Player.STATE_ENDED;
        boolean canSeek=controller!=null&&controller.isCommandAvailable(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)&&controller.isCurrentMediaItemSeekable();
        b.setActions(Arrays.asList(action(1,android.R.drawable.ic_media_rew,"Retroceder 5 segundos",canSeek),
            action(2,ready?android.R.drawable.ic_media_pause:android.R.drawable.ic_media_play,ready?"Pausar":"Reproducir",controller!=null),
            action(3,android.R.drawable.ic_media_ff,"Adelantar 5 segundos",canSeek)));
        if(Build.VERSION.SDK_INT>=31)b.setAutoEnterEnabled(autoPip()&&ready&&currentVideo()&&!navigating).setSeamlessResizeEnabled(true);
        return b.build();
    }
    private void updatePip(){if(!pipSupported||video==null||isFinishing())return;try{setPictureInPictureParams(params());}catch(IllegalStateException|IllegalArgumentException ignored){}}
    private void enterPip(){
        if(!pipSupported||!currentVideo()){toast("Abre un video para usar la ventana flotante.");return;}
        try{if(!enterPictureInPictureMode(params()))toast("Activa Imagen en imagen para Nube Música en los ajustes del teléfono.");}
        catch(IllegalStateException|IllegalArgumentException e){toast("La ventana flotante no está disponible. Revisa el permiso Imagen en imagen.");}
    }
    @Override protected void onUserLeaveHint(){super.onUserLeaveHint();if(Build.VERSION.SDK_INT<31&&autoPip()&&!navigating&&controller!=null&&controller.getPlayWhenReady()&&currentVideo())enterPip();}
    @Override public void onPictureInPictureModeChanged(boolean pip,Configuration config){super.onPictureInPictureModeChanged(pip,config);showChrome();}
    @Override public void onConfigurationChanged(Configuration config){super.onConfigurationChanged(config);if(!isInPictureInPictureMode())chrome=config.orientation!=Configuration.ORIENTATION_LANDSCAPE;showChrome();updatePip();}
    private void applyInsets(){
        boolean pip=isInPictureInPictureMode();
        top.setPadding(pip?0:insets.left,pip?0:insets.top,pip?0:insets.right,0);
        bottom.setPadding(dp(this,12)+(pip?0:insets.left),0,dp(this,12)+(pip?0:insets.right),pip?0:insets.bottom);
    }
    private void showChrome(){
        boolean show=(TvUi.isTv(this)||chrome)&&!isInPictureInPictureMode();top.setVisibility(show?View.VISIBLE:View.GONE);bottom.setVisibility(show?View.VISIBLE:View.GONE);
        WindowInsetsControllerCompat bars=WindowCompat.getInsetsController(getWindow(),root);
        bars.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        if(show)bars.show(WindowInsetsCompat.Type.systemBars());else bars.hide(WindowInsetsCompat.Type.systemBars());applyInsets();
    }
    @Override protected void onResume(){super.onResume();navigating=false;updatePip();}
    @Override protected void onConnected(){video.setPlayer(controller);refresh();}
    @Override protected void onPlaybackChanged(Player.Events events){refresh();}
    private void refresh(){
        if(controller==null)return;
        if(controller.getCurrentMediaItem()!=null&&!currentVideo()&&!navigating){
            navigating=true;if(!isInPictureInPictureMode())startActivity(new Intent(this,PlayerActivity.class));finish();return;
        }
        title.setText(controller.getMediaMetadata().title==null?"Video":controller.getMediaMetadata().title);
        boolean playing=controller.getPlayWhenReady()&&controller.getPlaybackState()!=Player.STATE_ENDED;
        play.setText(playing?"Ⅱ":"▶");play.setContentDescription(playing?"Pausar":"Reproducir");
        if(playing)getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        status.setText(controller.getPlaybackState()==Player.STATE_BUFFERING?"Cargando video…":controller.getPlayerError()!=null?"No se pudo reproducir este video":controller.getPlaybackState()==Player.STATE_ENDED?"Video terminado":"");
        refreshPosition();updatePip();
    }
    private void refreshPosition(){
        if(controller==null)return;long p=controller.getCurrentPosition(),d=controller.getDuration();
        boolean enabled=controller.isCommandAvailable(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)&&controller.isCurrentMediaItemSeekable();
        back5.setEnabled(enabled);forward5.setEnabled(enabled);seek.setEnabled(enabled&&d>0);
        if(!seeking){seek.setProgress(d>0?(int)Math.min(10000,p*10000/d):0);times.setText(DriveRules.time(p)+" / "+(d>0?DriveRules.time(d):"—"));}
    }
    @Override protected void onTick(){refreshPosition();}
    @Override protected void onStop(){
        // A dismissed PiP or hidden video must not continue invisibly. Queue navigation is explicit.
        if(controller!=null&&!navigating&&!isChangingConfigurations()&&currentVideo())controller.pause();
        video.setPlayer(null);super.onStop();
    }
    @Override protected void onDestroy(){if(registered)unregisterReceiver(actions);super.onDestroy();}
}
