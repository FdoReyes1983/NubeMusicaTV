package cl.fernando.nubemusica;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.audiofx.Equalizer;
import android.os.Bundle;

/** Owned by PlaybackService, so settings remain active with the screen off. */
final class AudioEqualizer {
    static final String COMMAND="cl.fernando.nubemusica.EQUALIZER";
    private final SharedPreferences saved;
    private Equalizer effect;
    private int session;
    private String message="Reproduce una canción para activar el ecualizador.";
    AudioEqualizer(Context c){saved=c.getSharedPreferences("equalizer",Context.MODE_PRIVATE);}
    void attach(int id){
        if(id==session&&effect!=null)return;release();session=id;
        if(id<=0){message="Reproduce una canción para activar el ecualizador.";return;}
        try{
            effect=new Equalizer(0,id);
            if(!effect.hasControl()){message="Otro efecto de audio tiene el control. Desactívalo y vuelve a probar.";return;}
            short[] range=effect.getBandLevelRange();
            for(short band=0;band<effect.getNumberOfBands();band++)effect.setBandLevel(band,(short)QueueRules.clampGain(saved.getInt("hz_"+effect.getCenterFreq(band),0),range[0],range[1]));
            effect.setEnabled(saved.getBoolean("enabled",false));message="";
        }catch(RuntimeException e){release();message="El ecualizador no está disponible con esta salida de audio.";}
    }
    Bundle command(Bundle args){
        String operation=args.getString("op","get");
        try{if(!"get".equals(operation)&&effect!=null&&effect.hasControl()){
            if("enabled".equals(operation)){
                boolean enabled=args.getBoolean("enabled");
                if(effect.setEnabled(enabled)!=android.media.audiofx.AudioEffect.SUCCESS)throw new IllegalStateException();
                saved.edit().putBoolean("enabled",enabled).apply();
            }else if("band".equals(operation)){
                int band=args.getInt("band",-1);short[] range=effect.getBandLevelRange();
                if(band>=0&&band<effect.getNumberOfBands()){
                    effect.setBandLevel((short)band,(short)QueueRules.clampGain(args.getInt("level"),range[0],range[1]));persist("Personalizado");
                }
            }else if("preset".equals(operation)){
                int preset=args.getInt("preset",-1);
                if(preset>=0&&preset<effect.getNumberOfPresets()){effect.usePreset((short)preset);persist(effect.getPresetName((short)preset));}
            }else if("reset".equals(operation)){
                for(short band=0;band<effect.getNumberOfBands();band++)effect.setBandLevel(band,(short)0);persist("Plano");
            }
            message="";
        }}catch(RuntimeException error){message="No se pudo aplicar el ajuste. Comprueba la salida de audio.";}
        return snapshot();
    }
    private void persist(String name){
        SharedPreferences.Editor edit=saved.edit().putString("profile",name);
        for(short band=0;band<effect.getNumberOfBands();band++)edit.putInt("hz_"+effect.getCenterFreq(band),effect.getBandLevel(band));edit.apply();
    }
    private Bundle snapshot(){
        Bundle b=new Bundle();b.putString("message",message);b.putString("profile",saved.getString("profile","Plano"));
        if(effect==null)return b;
        try{
            boolean control=effect.hasControl();b.putBoolean("available",control);b.putBoolean("enabled",effect.getEnabled());
            if(!control){b.putString("message","Otro efecto de audio tiene el control. Desactívalo y vuelve a probar.");return b;}
            short[] range=effect.getBandLevelRange();b.putInt("min",range[0]);b.putInt("max",range[1]);
            int count=effect.getNumberOfBands();int[] frequencies=new int[count],levels=new int[count];
            for(short i=0;i<count;i++){frequencies[i]=effect.getCenterFreq(i);levels[i]=effect.getBandLevel(i);}
            b.putIntArray("frequencies",frequencies);b.putIntArray("levels",levels);
            String[] presets=new String[effect.getNumberOfPresets()];for(short i=0;i<presets.length;i++)presets[i]=effect.getPresetName(i);b.putStringArray("presets",presets);
        }catch(RuntimeException e){b.clear();b.putString("message","El ecualizador dejó de estar disponible. Vuelve a reproducir una canción.");}
        return b;
    }
    void release(){if(effect!=null){try{effect.release();}catch(RuntimeException ignored){}effect=null;}session=0;}
}
