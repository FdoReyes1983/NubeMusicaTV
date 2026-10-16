package cl.fernando.nubemusica;

import android.os.Bundle;
import android.view.View;
import android.content.res.ColorStateList;
import android.widget.*;
import androidx.core.content.ContextCompat;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.session.*;
import androidx.media3.session.MediaController;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import java.util.Locale;
import static cl.fernando.nubemusica.MusicUi.*;

@UnstableApi
public final class EqualizerActivity extends ConnectedActivity {
    private LinearLayout settings;
    private ScrollView scroll;
    private TextView status;
    private MaterialButton playback;
    private boolean pending,available;
    private final java.util.ArrayDeque<Bundle> queued=new java.util.ArrayDeque<>();
    private long lastPoll;
    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);LinearLayout root=screen(this);header(this,root,"Ecualizador");
        status=text(this,"Conectando al reproductor…",13,MUTED);status.setPadding(0,dp(this,12),0,dp(this,12));root.addView(status,fill(-2));
        playback=button(this,"Reproducir / Pausar",false);root.addView(playback,fill(dp(this,48)));playback.setOnClickListener(v->{togglePlay();request(new Bundle());});
        scroll=new ScrollView(this);settings=col(this);scroll.addView(settings);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        TextView note=text(this,"Los ajustes se guardan automáticamente y se aplican a la música de Nube Música.",12,MUTED);note.setPadding(0,dp(this,12),0,dp(this,6));root.addView(note,fill(-2));
    }
    @Override protected void onConnected(){pending=false;queued.clear();request(new Bundle());onPlaybackChanged(null);}
    @Override protected void onPlaybackChanged(Player.Events events){if(controller!=null)playback.setText(controller.getPlayWhenReady()?"Pausar música":"Reproducir música");}
    @Override protected void onTick(){if(!available&&android.os.SystemClock.elapsedRealtime()-lastPoll>2000){lastPoll=android.os.SystemClock.elapsedRealtime();request(new Bundle());}}
    private void request(Bundle args){
        if(controller==null)return;
        if(pending){if(!"get".equals(args.getString("op","get")))queued.add(new Bundle(args));return;}
        SessionCommand command=new SessionCommand(AudioEqualizer.COMMAND,Bundle.EMPTY);
        if(!controller.getAvailableSessionCommands().contains(command)){status.setText("El ecualizador todavía no está disponible.");return;}
        pending=true;MediaController current=controller;ListenableFuture<SessionResult> future=current.sendCustomCommand(command,args);
        future.addListener(()->{if(isDestroyed()||controller!=current)return;pending=false;try{SessionResult result=future.get();if(result.resultCode==SessionResult.RESULT_SUCCESS)render(result.extras);else status.setText("No se pudo aplicar el ecualizador.");}catch(Exception e){status.setText("No se pudo consultar el ecualizador. Vuelve a abrir esta pantalla.");}if(!queued.isEmpty())request(queued.remove());},ContextCompat.getMainExecutor(this));
    }
    private Bundle operation(String op){Bundle b=new Bundle();b.putString("op",op);return b;}
    private void render(Bundle b){
        int offset=scroll.getScrollY();settings.removeAllViews();available=b.getBoolean("available");boolean enabled=b.getBoolean("enabled");
        String message=b.getString("message","");status.setText(!message.isEmpty()?message:available?"Ajusta el sonido a tu gusto · "+b.getString("profile","Personalizado"):"Reproduce una canción para activar el ecualizador.");
        androidx.appcompat.widget.SwitchCompat power=new androidx.appcompat.widget.SwitchCompat(this);power.setText("Ecualizador");power.setTextColor(INK);power.setTextSize(16);power.setChecked(enabled);power.setEnabled(available);power.setPadding(0,dp(this,10),0,dp(this,14));TvUi.focus(power);settings.addView(power,fill(dp(this,58)));
        power.setOnCheckedChangeListener((v,checked)->{Bundle args=operation("enabled");args.putBoolean("enabled",checked);request(args);});
        if(!available)return;
        String[] presets=b.getStringArray("presets");LinearLayout actions=row(this);
        MaterialButton profiles=button(this,"Perfiles",false);actions.addView(profiles,new LinearLayout.LayoutParams(0,dp(this,48),1));profiles.setEnabled(enabled&&presets!=null&&presets.length>0);
        profiles.setOnClickListener(v->new MaterialAlertDialogBuilder(this).setTitle("Perfiles del dispositivo").setItems(presets,(d,index)->{Bundle args=operation("preset");args.putInt("preset",index);request(args);}).setNegativeButton("Cancelar",null).show());
        MaterialButton flat=button(this,"Restablecer",false);flat.setEnabled(enabled);actions.addView(flat,new LinearLayout.LayoutParams(0,dp(this,48),1));flat.setOnClickListener(v->request(operation("reset")));settings.addView(actions);
        int[] frequencies=b.getIntArray("frequencies"),levels=b.getIntArray("levels");if(frequencies==null||levels==null)return;int min=b.getInt("min"),max=b.getInt("max");
        for(int i=0;i<Math.min(frequencies.length,levels.length);i++){
            final int band=i;float hz=frequencies[i]/1000f;String frequency=hz>=1000?String.format(Locale.ROOT,"%.1f kHz",hz/1000):String.format(Locale.ROOT,"%.0f Hz",hz);
            TextView label=text(this,frequency+"  ·  "+gain(levels[i]),14,INK);label.setPadding(dp(this,4),dp(this,12),0,0);settings.addView(label,fill(dp(this,38)));
            SeekBar slider=new SeekBar(this);slider.setMax(Math.max(1,max-min));TvUi.focus(slider);slider.setProgress(levels[i]-min);slider.setEnabled(enabled);slider.setProgressTintList(ColorStateList.valueOf(ACCENT));slider.setThumbTintList(ColorStateList.valueOf(ACCENT));slider.setContentDescription("Nivel de "+frequency);settings.addView(slider,fill(dp(this,42)));
            slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
                public void onStartTrackingTouch(SeekBar s){}
                public void onProgressChanged(SeekBar s,int value,boolean user){label.setText(frequency+"  ·  "+gain(min+value));}
                public void onStopTrackingTouch(SeekBar s){Bundle args=operation("band");args.putInt("band",band);args.putInt("level",min+s.getProgress());request(args);}
            });
        }
        settings.post(()->scroll.scrollTo(0,offset));
    }
    private static String gain(int milliBel){return String.format(Locale.ROOT,"%+.1f dB",milliBel/100f);}
}
