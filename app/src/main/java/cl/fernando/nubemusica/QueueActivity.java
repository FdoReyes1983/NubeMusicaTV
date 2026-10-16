package cl.fernando.nubemusica;

import android.content.Intent;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.media3.common.*;
import androidx.media3.common.util.UnstableApi;
import androidx.recyclerview.widget.*;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import java.util.*;
import static cl.fernando.nubemusica.MusicUi.*;

/** Edits queue occurrences without resetting playback, or a saved playlist's independent order. */
@UnstableApi
public final class QueueActivity extends ConnectedActivity {
    private final List<MediaItem> items=new ArrayList<>();
    private final Rows adapter=new Rows();
    private PlaylistStore lists;
    private String playlist;
    private TextView count,empty;
    private MaterialButton manual;
    private ItemTouchHelper touch;
    private boolean dragging,mutating,dirty;
    private int highlighted=-1;
    @Override public void onCreate(Bundle state){
        super.onCreate(state);lists=((NubeApp)getApplication()).playlists();playlist=getIntent().getStringExtra("playlist");
        LinearLayout root=screen(this),bar=header(this,root,playlist==null?"Cola de reproducción":playlist);
        MaterialButton menu=button(this,"⋮",false);menu.setContentDescription("Opciones de la lista");bar.addView(menu,new LinearLayout.LayoutParams(dp(this,48),dp(this,48)));menu.setOnClickListener(v->menu());
        count=text(this,"Cargando canciones…",12,MUTED);root.addView(count,fill(dp(this,30)));
        TextView tip=text(this,"Arrastra ≡ para cambiar el orden",12,ACCENT);root.addView(tip,fill(dp(this,28)));
        manual=button(this,"",false);root.addView(manual,fill(dp(this,38)));manual.setVisibility(View.GONE);manual.setOnClickListener(v->{if(controller!=null)controller.setShuffleModeEnabled(false);});
        FrameLayout body=new FrameLayout(this);root.addView(body,new LinearLayout.LayoutParams(-1,0,1));
        RecyclerView list=new RecyclerView(this);list.setLayoutManager(new LinearLayoutManager(this));list.setAdapter(adapter);body.addView(list,new FrameLayout.LayoutParams(-1,-1));
        empty=text(this,"No hay canciones en esta lista",15,MUTED);empty.setGravity(Gravity.CENTER);body.addView(empty,new FrameLayout.LayoutParams(-1,-1));
        touch=new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP|ItemTouchHelper.DOWN,0){
            @Override public boolean isLongPressDragEnabled(){return false;}
            @Override public boolean onMove(@NonNull RecyclerView r,@NonNull RecyclerView.ViewHolder from,@NonNull RecyclerView.ViewHolder to){return move(from.getBindingAdapterPosition(),to.getBindingAdapterPosition());}
            @Override public void onSwiped(@NonNull RecyclerView.ViewHolder v,int direction){}
            @Override public void onSelectedChanged(RecyclerView.ViewHolder v,int action){super.onSelectedChanged(v,action);if(action==ItemTouchHelper.ACTION_STATE_DRAG)dragging=true;}
            @Override public void clearView(@NonNull RecyclerView r,@NonNull RecyclerView.ViewHolder v){super.clearView(r,v);dragging=false;saveOrder();reload();}
        });touch.attachToRecyclerView(list);
    }
    @Override protected void onConnected(){reload();}
    @Override protected void onPlaybackChanged(Player.Events e){
        if(mutating||dragging)return;
        if(playlist==null&&e.contains(Player.EVENT_TIMELINE_CHANGED))reload();else updateStatus();
    }
    @Override protected void onStop(){saveOrder();super.onStop();}
    private void reload(){
        if(dragging)return;items.clear();
        if(playlist!=null)for(Track t:lists.tracks(playlist))items.add(t.mediaItem());
        else if(controller!=null)for(int i=0;i<controller.getMediaItemCount();i++)items.add(controller.getMediaItemAt(i));
        adapter.notifyDataSetChanged();empty.setVisibility(items.isEmpty()?View.VISIBLE:View.GONE);updateStatus();
    }
    private void updateStatus(){
        int before=highlighted;highlighted=playlist==null&&controller!=null?controller.getCurrentMediaItemIndex():-1;
        if(before>=0&&before<items.size())adapter.notifyItemChanged(before);if(highlighted>=0&&highlighted<items.size())adapter.notifyItemChanged(highlighted);
        count.setText(items.size()+" canciones"+(playlist==null?" · Se guarda automáticamente":" · Orden guardado en esta lista"));
        boolean random=playlist==null&&controller!=null&&controller.getShuffleModeEnabled();manual.setVisibility(random?View.VISIBLE:View.GONE);manual.setText("Aleatorio activo · Usar el orden de la lista");
    }
    private boolean unchanged(){
        if(playlist!=null)return true;
        if(controller==null||controller.getMediaItemCount()!=items.size())return false;
        for(int i=0;i<items.size();i++)if(!items.get(i).mediaId.equals(controller.getMediaItemAt(i).mediaId))return false;
        return true;
    }
    private boolean move(int from,int to){
        if(from<0||to<0||from>=items.size()||to>=items.size()||from==to)return false;
        if(!unchanged()){toast("La cola cambió. Suelta la canción y vuelve a intentarlo.");return false;}
        if(playlist==null&&(controller==null||!controller.isCommandAvailable(Player.COMMAND_CHANGE_MEDIA_ITEMS)))return false;
        mutating=true;
        try{
            if(playlist==null){
                if(controller.getShuffleModeEnabled()){controller.setShuffleModeEnabled(false);toast("Orden manual: reproducción aleatoria desactivada.");}
                controller.moveMediaItem(from,to);
            }else dirty=true;
            items.add(to,items.remove(from));adapter.notifyItemMoved(from,to);return true;
        }finally{mutating=false;}
    }
    private void saveOrder(){
        if(playlist==null||!dirty)return;
        List<Track> ordered=new ArrayList<>();for(MediaItem item:items){Track t=Track.fromMediaItem(item);if(t!=null)ordered.add(t);}
        if(!lists.reorder(playlist,ordered))toast("La lista cambió mientras la ordenabas. Vuelve a abrirla.");dirty=false;
    }
    private void playAt(int index){
        if(controller==null||index<0||index>=items.size())return;
        if(!unchanged()){reload();toast("La cola cambió. Selecciona la canción nuevamente.");return;}
        if(playlist!=null){saveOrder();controller.setMediaItems(new ArrayList<>(items),index,0);}
        else controller.seekToDefaultPosition(index);
        controller.prepare();controller.play();
    }
    private void options(int index){
        if(index<0||index>=items.size())return;List<String> snapshot=new ArrayList<>();for(MediaItem item:items)snapshot.add(item.mediaId);
        new MaterialAlertDialogBuilder(this).setTitle(String.valueOf(items.get(index).mediaMetadata.title))
                .setItems(playlist==null?new String[]{"Reproducir a continuación","Mover al principio","Subir un puesto","Bajar un puesto","Quitar de la cola"}:new String[]{"Reproducir","Mover al principio","Subir un puesto","Bajar un puesto","Quitar de esta lista"},(d,which)->{
                    List<String> current=new ArrayList<>();for(MediaItem item:items)current.add(item.mediaId);
                    if(!snapshot.equals(current)||!unchanged()){reload();toast("La lista cambió. Abre las opciones nuevamente.");return;}
                    if(which==0){if(playlist!=null)playAt(index);else {int next=QueueRules.nextSlot(items.size(),index,controller.getCurrentMediaItemIndex());if(next>=0)move(index,next);reload();}}
                    else if(which==1){move(index,0);saveOrder();reload();}
                    else if(which==2||which==3){move(index,index+(which==2?-1:1));saveOrder();reload();}
                    else {if(playlist!=null){saveOrder();lists.removeAt(playlist,index);}else controller.removeMediaItem(index);reload();}
                }).setNegativeButton("Cerrar",null).show();
    }
    private void menu(){
        if(playlist!=null){new MaterialAlertDialogBuilder(this).setTitle(playlist).setItems(new String[]{"Reproducir desde el principio","Ver canción actual"},(d,i)->{if(i==0)playAt(0);else startActivity(new Intent(this,PlayerActivity.class));}).show();return;}
        new MaterialAlertDialogBuilder(this).setTitle("Cola de reproducción").setItems(new String[]{"Guardar como nueva lista","Vaciar cola","Ver canción actual"},(d,i)->{
            if(i==0)saveQueueAsList();
            else if(i==1)new MaterialAlertDialogBuilder(this).setTitle("¿Vaciar la cola?").setMessage("Se detendrá la reproducción. Los archivos de tu nube se conservarán.").setNegativeButton("Cancelar",null).setPositiveButton("Vaciar",(a,b)->{if(controller!=null){controller.stop();controller.clearMediaItems();reload();}}).show();
            else startActivity(new Intent(this,PlayerActivity.class));
        }).show();
    }
    private void saveQueueAsList(){
        if(controller==null||controller.getMediaItemCount()==0){toast("La cola está vacía.");return;}
        List<Track> snapshot=new ArrayList<>();for(int i=0;i<controller.getMediaItemCount();i++){Track t=Track.fromMediaItem(controller.getMediaItemAt(i));if(t!=null)snapshot.add(t);}
        if(snapshot.size()!=controller.getMediaItemCount()){toast("No se pudo guardar toda la cola. Vuelve a cargar las canciones desde la biblioteca.");return;}
        EditText input=new EditText(this);input.setSingleLine();input.setHint("Nombre de la lista");
        new MaterialAlertDialogBuilder(this).setTitle("Guardar cola como lista").setMessage("Cada archivo se guardará una vez, en este orden.").setView(input).setNegativeButton("Cancelar",null)
                .setPositiveButton("Guardar",(d,w)->toast(lists.saveNew(input.getText().toString(),snapshot)?"Lista guardada en Mis listas":"Usa un nombre nuevo de 1 a 80 caracteres.")).show();
    }
    private final class Rows extends RecyclerView.Adapter<Holder>{
        @NonNull @Override public Holder onCreateViewHolder(@NonNull ViewGroup parent,int type){
            LinearLayout row=row(QueueActivity.this);row.setPadding(0,dp(QueueActivity.this,5),0,dp(QueueActivity.this,5));row.setLayoutParams(new RecyclerView.LayoutParams(-1,dp(QueueActivity.this,TvUi.isTv(QueueActivity.this)?86:70)));TvUi.row(row);
            TextView handle=new DragHandle();handle.setText("≡");handle.setTextSize(25);handle.setTextColor(MUTED);handle.setGravity(Gravity.CENTER);row.addView(handle,new LinearLayout.LayoutParams(dp(QueueActivity.this,48),-1));
            LinearLayout labels=col(QueueActivity.this);labels.setGravity(Gravity.CENTER_VERTICAL);
            TextView name=text(QueueActivity.this,"",15,INK);name.setSingleLine();name.setEllipsize(android.text.TextUtils.TruncateAt.END);
            TextView detail=text(QueueActivity.this,"",11,MUTED);detail.setSingleLine();detail.setEllipsize(android.text.TextUtils.TruncateAt.END);labels.addView(name);labels.addView(detail);row.addView(labels,new LinearLayout.LayoutParams(0,-1,1));
            MaterialButton options=button(QueueActivity.this,"⋮",false);row.addView(options,new LinearLayout.LayoutParams(dp(QueueActivity.this,44),dp(QueueActivity.this,48)));return new Holder(row,handle,name,detail,options);
        }
        @Override public void onBindViewHolder(@NonNull Holder h,int position){
            MediaItem item=items.get(position);boolean current=playlist==null&&controller!=null&&position==controller.getCurrentMediaItemIndex();
            h.name.setText(item.mediaMetadata.title);h.name.setTextColor(current?ACCENT:INK);h.detail.setText(current?"En reproducción":item.mediaMetadata.artist);
            h.handle.setText("≡");h.handle.setContentDescription("Mover "+item.mediaMetadata.title);h.handle.setOnClickListener(v->options(h.getBindingAdapterPosition()));h.handle.setOnTouchListener((v,e)->{if(e.getActionMasked()==MotionEvent.ACTION_DOWN){touch.startDrag(h);return true;}if(e.getActionMasked()==MotionEvent.ACTION_UP)v.performClick();return true;});
            h.itemView.setOnClickListener(v->playAt(h.getBindingAdapterPosition()));h.options.setContentDescription("Opciones de "+item.mediaMetadata.title);h.options.setOnClickListener(v->options(h.getBindingAdapterPosition()));
        }
        @Override public int getItemCount(){return items.size();}
    }
    private final class DragHandle extends androidx.appcompat.widget.AppCompatTextView {
        DragHandle(){super(QueueActivity.this);}
        @Override public boolean performClick(){return super.performClick();}
    }
    private static final class Holder extends RecyclerView.ViewHolder{
        final TextView handle,name,detail;final MaterialButton options;
        Holder(View v,TextView h,TextView n,TextView d,MaterialButton o){super(v);handle=h;name=n;detail=d;options=o;}
    }
}
