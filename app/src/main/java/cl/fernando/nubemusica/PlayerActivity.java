package cl.fernando.nubemusica;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.*;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import androidx.media3.common.*;
import androidx.media3.common.util.UnstableApi;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import java.util.*;
import static cl.fernando.nubemusica.MusicUi.*;

@UnstableApi
public final class PlayerActivity extends ConnectedActivity {
    private TextView title,artist,elapsed,duration,state;
    private ImageButton play;
    private SeekBar seek;
    private MaterialButton repeat,shuffle,favorite,rewind,forward;
    private boolean openingVideo;
    private Artwork artwork;
    private boolean seeking;
    private PlaylistStore lists;
    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);lists=((NubeApp)getApplication()).playlists();
        LinearLayout root=screen(this),bar=header(this,root,"Ahora suena");
        MaterialButton menu=button(this,"⋮",false);menu.setContentDescription("Opciones de la canción");bar.addView(menu,new LinearLayout.LayoutParams(dp(this,48),dp(this,48)));menu.setOnClickListener(v->songMenu());
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout body=col(this);body.setGravity(Gravity.CENTER_HORIZONTAL);scroll.addView(body);
        artwork=new Artwork();LinearLayout.LayoutParams artParams=new LinearLayout.LayoutParams(-1,-2);artParams.topMargin=dp(this,12);artParams.bottomMargin=dp(this,20);body.addView(artwork,artParams);
        title=text(this,"Elige una canción",23,INK);title.setTypeface(null,Typeface.BOLD);title.setMaxLines(3);title.setEllipsize(android.text.TextUtils.TruncateAt.END);body.addView(title,fill(-2));
        title.setOnClickListener(v->{if(controller!=null&&controller.getCurrentMediaItem()!=null)new MaterialAlertDialogBuilder(this).setTitle("Canción").setMessage(String.valueOf(controller.getMediaMetadata().title)).setPositiveButton("Cerrar",null).show();});
        artist=text(this,"Tu música, a tu ritmo",14,MUTED);artist.setMaxLines(2);body.addView(artist,fill(dp(this,38)));
        state=text(this,"",11,ACCENT);body.addView(state,fill(dp(this,20)));
        seek=new SeekBar(this);seek.setMax(10000);TvUi.focus(seek);seek.setProgressTintList(ColorStateList.valueOf(ACCENT));seek.setThumbTintList(ColorStateList.valueOf(ACCENT));body.addView(seek,fill(dp(this,40)));
        LinearLayout times=row(this);elapsed=text(this,"0:00",12,MUTED);duration=text(this,"0:00",12,MUTED);duration.setGravity(Gravity.END|Gravity.CENTER_VERTICAL);times.addView(elapsed,new LinearLayout.LayoutParams(0,dp(this,24),1));times.addView(duration);body.addView(times,fill(-2));
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            public void onStartTrackingTouch(SeekBar s){seeking=true;}
            public void onProgressChanged(SeekBar s,int progress,boolean user){if(user&&controller!=null&&controller.getDuration()>0)elapsed.setText(DriveRules.time(controller.getDuration()*progress/10000));}
            public void onStopTrackingTouch(SeekBar s){if(controller!=null&&controller.getDuration()>0)controller.seekTo(controller.getDuration()*s.getProgress()/10000);seeking=false;}
        });
        LinearLayout controls=row(this);controls.setGravity(Gravity.CENTER);
        shuffle=button(this,"⇄",false);shuffle.setTextSize(24);controls.addView(shuffle,new LinearLayout.LayoutParams(0,dp(this,64),1));shuffle.setOnClickListener(v->{if(controller!=null)controller.setShuffleModeEnabled(!controller.getShuffleModeEnabled());});
        ImageButton previous=icon(this,android.R.drawable.ic_media_previous,"Anterior");controls.addView(previous,new LinearLayout.LayoutParams(dp(this,56),dp(this,64)));previous.setOnClickListener(v->{if(controller!=null)controller.seekToPrevious();});
        play=icon(this,android.R.drawable.ic_media_play,"Reproducir");play.setBackground(round(this,ACCENT,40));play.setImageTintList(ColorStateList.valueOf(BG));controls.addView(play,new LinearLayout.LayoutParams(dp(this,76),dp(this,76)));play.setOnClickListener(v->togglePlay());
        ImageButton next=icon(this,android.R.drawable.ic_media_next,"Siguiente");controls.addView(next,new LinearLayout.LayoutParams(dp(this,56),dp(this,64)));next.setOnClickListener(v->{if(controller!=null)controller.seekToNext();});
        repeat=button(this,"↻",false);repeat.setTextSize(24);controls.addView(repeat,new LinearLayout.LayoutParams(0,dp(this,64),1));repeat.setOnClickListener(v->{if(controller!=null)controller.setRepeatMode((controller.getRepeatMode()+1)%3);});
        LinearLayout.LayoutParams controlParams=fill(dp(this,104));controlParams.topMargin=dp(this,10);body.addView(controls,controlParams);
        LinearLayout jumps=row(this);rewind=button(this,"−5 s",false);forward=button(this,"+5 s",false);
        rewind.setContentDescription("Retroceder 5 segundos");forward.setContentDescription("Adelantar 5 segundos");
        jumps.addView(rewind,new LinearLayout.LayoutParams(0,dp(this,48),1));jumps.addView(forward,new LinearLayout.LayoutParams(0,dp(this,48),1));
        rewind.setOnClickListener(v->seekBy(-5000));forward.setOnClickListener(v->seekBy(5000));body.addView(jumps,fill(-2));
        LinearLayout actions=row(this);
        favorite=button(this,"♡ Favorito",false);actions.addView(favorite,new LinearLayout.LayoutParams(0,dp(this,52),1));favorite.setOnClickListener(v->toggleFavorite());
        MaterialButton eq=button(this,"Ecualizador",false);actions.addView(eq,new LinearLayout.LayoutParams(0,dp(this,52),1));eq.setOnClickListener(v->startActivity(new Intent(this,EqualizerActivity.class)));
        MaterialButton queue=button(this,"≡ Cola",false);actions.addView(queue,new LinearLayout.LayoutParams(0,dp(this,52),1));queue.setOnClickListener(v->startActivity(new Intent(this,QueueActivity.class)));
        body.addView(actions,fill(-2));
        TextView tip=text(this,"Tu colección sigue en la biblioteca",11,MUTED);tip.setGravity(Gravity.CENTER);body.addView(tip,fill(dp(this,36)));
        if(TvUi.isTv(this)) play.post(() -> play.requestFocus());
    }
    @Override protected void onConnected(){refresh();}
    @Override protected void onPlaybackChanged(Player.Events e){refresh();}
    @Override protected void onTick(){refreshPosition();}
    private void refresh(){
        if(controller==null)return;MediaItem item=controller.getCurrentMediaItem();MediaMetadata metadata=controller.getMediaMetadata();
        Track current=item==null?null:Track.fromMediaItem(item);
        if(current!=null&&current.isVideo()&&!openingVideo){openingVideo=true;startActivity(new Intent(this,VideoActivity.class));finish();return;}
        title.setText(item==null?"Elige una canción":metadata.title==null?item.mediaMetadata.title:metadata.title);
        artist.setText(item==null?"Abre tu biblioteca para empezar":metadata.artist==null?"Google Drive":metadata.artist);
        artwork.setData(metadata.artworkData);
        boolean playing=controller.getPlayWhenReady()&&controller.getPlaybackState()!=Player.STATE_ENDED;
        play.setImageResource(playing?android.R.drawable.ic_media_pause:android.R.drawable.ic_media_play);play.setContentDescription(playing?"Pausar":"Reproducir");
        shuffle.setTextColor(controller.getShuffleModeEnabled()?ACCENT:MUTED);shuffle.setContentDescription(controller.getShuffleModeEnabled()?"Desactivar aleatorio":"Activar aleatorio");
        int r=controller.getRepeatMode();repeat.setText(r==Player.REPEAT_MODE_ONE?"↻1":"↻");repeat.setTextColor(r==Player.REPEAT_MODE_OFF?MUTED:ACCENT);repeat.setContentDescription(r==0?"Repetición desactivada":r==1?"Repetir canción":"Repetir cola");
        favorite.setText(isFavorite(currentTrack())?"♥ Favorito":"♡ Favorito");refreshPosition();
    }
    private void refreshPosition(){
        if(controller==null)return;long d=controller.getDuration(),p=controller.getCurrentPosition();
        boolean canSeek=controller.isCommandAvailable(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)&&controller.isCurrentMediaItemSeekable();
        rewind.setEnabled(canSeek);forward.setEnabled(canSeek);
        seek.setEnabled(d>0&&d!=C.TIME_UNSET&&canSeek);
        if(!seeking){seek.setProgress(d>0?(int)Math.min(10000,p*10000/d):0);elapsed.setText(DriveRules.time(p));}
        duration.setText(DriveRules.time(Math.max(0,d)));
        String status=controller.getPlaybackState()==Player.STATE_BUFFERING?"Cargando audio…":controller.isPlaying()?"Reproduciendo":controller.getMediaItemCount()>0?"En pausa":"";
        state.setText(status+(controller.getMediaItemCount()>0?" · "+(controller.getCurrentMediaItemIndex()+1)+" / "+controller.getMediaItemCount():""));
    }
    private Track currentTrack(){return controller==null||controller.getCurrentMediaItem()==null?null:Track.fromMediaItem(controller.getCurrentMediaItem());}
    private boolean isFavorite(Track t){if(t==null)return false;for(Track saved:lists.tracks(PlaylistStore.FAVORITES))if(saved.uri().equals(t.uri()))return true;return false;}
    private void toggleFavorite(){Track t=currentTrack();if(t==null)return;if(isFavorite(t))lists.remove(PlaylistStore.FAVORITES,t);else lists.add(PlaylistStore.FAVORITES,t);refresh();}
    private void songMenu(){
        new MaterialAlertDialogBuilder(this).setTitle("Tu canción").setItems(new String[]{"Añadir a una lista","Organizar cola","Ecualizador"},(d,which)->{
            if(which==1)startActivity(new Intent(this,QueueActivity.class));
            else if(which==2)startActivity(new Intent(this,EqualizerActivity.class));
            else {Track t=currentTrack();if(t==null){toast("Elige una canción primero.");return;}
                List<String> names=lists.names();names.add("＋ Crear lista");
                new MaterialAlertDialogBuilder(this).setTitle("Añadir canción").setItems(names.toArray(new String[0]),(dialog,index)->{
                    if(index<names.size()-1)toast(lists.add(names.get(index),t)?"Canción añadida":"La canción ya está en esa lista");
                    else {EditText input=new EditText(this);input.setSingleLine();input.setHint("Nombre de la lista");new MaterialAlertDialogBuilder(this).setTitle("Nueva lista").setView(input).setNegativeButton("Cancelar",null).setPositiveButton("Guardar",(a,b)->toast(lists.saveNew(input.getText().toString(),Collections.singletonList(t))?"Lista guardada":"Usa un nombre nuevo de 1 a 80 caracteres.")).show();}
                }).show();
            }
        }).show();
    }
    private final class Artwork extends View {
        final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);Bitmap bitmap;byte[] last;
        final Path clip=new Path();final RectF bounds=new RectF(),coverBounds=new RectF();Shader background;
        Artwork(){super(PlayerActivity.this);setContentDescription("Carátula de la canción; disco decorativo cuando no está disponible");}
        void setData(byte[] data){if(last==data)return;last=data;Bitmap old=bitmap;bitmap=null;
            if(data!=null&&data.length<=8*1024*1024)try{BitmapFactory.Options o=new BitmapFactory.Options();o.inJustDecodeBounds=true;BitmapFactory.decodeByteArray(data,0,data.length,o);o.inSampleSize=1;while(o.outWidth/o.inSampleSize>800||o.outHeight/o.inSampleSize>800)o.inSampleSize*=2;o.inJustDecodeBounds=false;bitmap=BitmapFactory.decodeByteArray(data,0,data.length,o);}catch(RuntimeException ignored){}
            if(old!=null)old.recycle();invalidate();
        }
        @Override protected void onMeasure(int w,int h){int size=Math.min(MeasureSpec.getSize(w),dp(getContext(),TvUi.isTv(getContext())?360:300));setMeasuredDimension(size,size);}
        @Override protected void onSizeChanged(int w,int h,int oldW,int oldH){
            bounds.set(0,0,w,h);clip.reset();clip.addRoundRect(bounds,dp(getContext(),24),dp(getContext(),24),Path.Direction.CW);
            background=new LinearGradient(0,0,w,h,new int[]{0xff28453e,0xff17283c,0xff172235},null,Shader.TileMode.CLAMP);
        }
        @Override protected void onDraw(Canvas c){super.onDraw(c);float s=getWidth(),m=s/2;
            c.save();c.clipPath(clip);
            if(bitmap!=null){float ratio=Math.max(s/bitmap.getWidth(),s/bitmap.getHeight());float bw=bitmap.getWidth()*ratio,bh=bitmap.getHeight()*ratio;coverBounds.set((s-bw)/2,(s-bh)/2,(s+bw)/2,(s+bh)/2);c.drawBitmap(bitmap,null,coverBounds,paint);c.restore();return;}
            paint.setShader(background);c.drawRect(bounds,paint);paint.setShader(null);
            paint.setColor(0xff0e1725);c.drawCircle(m,m,s*.35f,paint);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(1);
            for(int i=0;i<7;i++){paint.setColor(0xff263747);c.drawCircle(m,m,s*(.19f+i*.022f),paint);}paint.setStyle(Paint.Style.FILL);paint.setColor(ACCENT);c.drawCircle(m,m,s*.13f,paint);paint.setColor(BG);c.drawCircle(m,m,s*.027f,paint);
            paint.setTextAlign(Paint.Align.CENTER);paint.setTextSize(s*.038f);paint.setColor(MUTED);c.drawText("NUBE MÚSICA",m,s*.91f,paint);c.restore();
        }
    }
}
