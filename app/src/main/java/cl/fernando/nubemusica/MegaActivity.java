package cl.fernando.nubemusica;

import nz.mega.sdk.MegaManager;
import nz.mega.sdk.MegaDataSource;
import nz.mega.sdk.MegaRepository;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.*;
import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Native account and folder screen. Credentials are entered only on the user's device. */
@UnstableApi
public final class MegaActivity extends ConnectedActivity {
    private MegaManager mega;private NubeApp app;
    private final ExecutorService io=Executors.newSingleThreadExecutor();
    private final List<Track> tracks=new ArrayList<>(),shown=new ArrayList<>();
    private final Deque<Location> history=new ArrayDeque<>();
    private final Adapter adapter=new Adapter();
    private Location location=new Location("","Mi MEGA",false,1);
    private LinearLayout loginPanel,browsePanel,sessionButtons;
    private ScrollView loginScroll;
    private EditText email,password,pin,search;
    private TextView status,breadcrumb,empty,mini,scanStatus;
    private MaterialButton login,retry,logout,scan,miniPlay;
    private ProgressBar progress;
    private int serial;private String loadedAccount="";private boolean wasReady;
    private long lastPoll;
    private final Runnable stateChanged=this::renderState;
    @Override protected void onCreate(Bundle saved){
        super.onCreate(saved);app=(NubeApp)getApplication();mega=app.mega();
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        LinearLayout root=MusicUi.screen(this);MusicUi.header(this,root,"MEGA");
        status=MusicUi.text(this,mega.status(),13,MusicUi.MUTED);status.setPadding(0,8,0,8);root.addView(status);
        progress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);progress.setIndeterminate(true);root.addView(progress,MusicUi.fill(MusicUi.dp(this,3)));
        loginScroll=new ScrollView(this);loginPanel=MusicUi.col(this);loginScroll.addView(loginPanel);root.addView(loginScroll,new LinearLayout.LayoutParams(-1,0,1));
        TextView intro=MusicUi.text(this,"Tu música y videos de MEGA, junto a Drive",22,MusicUi.INK);intro.setPadding(0,24,0,16);loginPanel.addView(intro);
        email=input("Correo de MEGA",InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);loginPanel.addView(email,MusicUi.fill(MusicUi.dp(this,56)));
        password=input("Contraseña de MEGA",InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);password.setSaveEnabled(false);password.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);loginPanel.addView(password,MusicUi.fill(MusicUi.dp(this,56)));
        pin=input("Código de dos pasos (si lo usas)",InputType.TYPE_CLASS_NUMBER);pin.setSaveEnabled(false);pin.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);loginPanel.addView(pin,MusicUi.fill(MusicUi.dp(this,56)));
        login=MusicUi.button(this,"Conectar MEGA",true);loginPanel.addView(login,MusicUi.fill(MusicUi.dp(this,54)));
        login.setOnClickListener(v->{String pass=password.getText().toString(),code=pin.getText().toString();mega.login(email.getText().toString(),pass,code);password.setText("");pin.setText("");});
        TextView privacy=MusicUi.text(this,"Los datos se envían desde este teléfono a MEGA. La contraseña no se guarda; la sesión se protege con el almacén de claves de Android.\n\nNube Música solo explora y reproduce archivos. Se aplican las cuotas de transferencia de MEGA.",13,MusicUi.MUTED);privacy.setPadding(0,18,0,12);loginPanel.addView(privacy);
        sessionButtons=MusicUi.row(this);retry=MusicUi.button(this,"Reintentar conexión",true);logout=MusicUi.button(this,"Desconectar",false);
        sessionButtons.addView(retry,new LinearLayout.LayoutParams(0,MusicUi.dp(this,48),1));sessionButtons.addView(logout,new LinearLayout.LayoutParams(0,MusicUi.dp(this,48),1));root.addView(sessionButtons);
        retry.setOnClickListener(v->mega.retry());logout.setOnClickListener(v->disconnect());
        browsePanel=MusicUi.col(this);root.addView(browsePanel,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout tabs=MusicUi.row(this);for(String title:new String[]{"Mi MEGA","Compartidos","Biblioteca"}){
            MaterialButton button=MusicUi.button(this,title,false);tabs.addView(button,new LinearLayout.LayoutParams(0,MusicUi.dp(this,44),1));
            button.setOnClickListener(v->{if(title.equals("Biblioteca")){startActivity(new Intent(this,MainActivity.class).putExtra("library",true).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP));finish();}
                else {history.clear();location=new Location("",title,title.equals("Compartidos"),title.equals("Compartidos")?2:1);load();}});
        }browsePanel.addView(tabs);
        LinearLayout nav=MusicUi.row(this);MaterialButton up=MusicUi.button(this,"↑",false);up.setContentDescription("Subir una carpeta");nav.addView(up,new LinearLayout.LayoutParams(MusicUi.dp(this,42),MusicUi.dp(this,42)));up.setOnClickListener(v->goUp());
        breadcrumb=MusicUi.text(this,"Mi MEGA",15,MusicUi.INK);breadcrumb.setSingleLine();breadcrumb.setEllipsize(android.text.TextUtils.TruncateAt.START);nav.addView(breadcrumb,new LinearLayout.LayoutParams(0,MusicUi.dp(this,42),1));
        MaterialButton refresh=MusicUi.button(this,"↻",false);refresh.setContentDescription("Actualizar carpeta");nav.addView(refresh,new LinearLayout.LayoutParams(MusicUi.dp(this,42),MusicUi.dp(this,42)));refresh.setOnClickListener(v->load());browsePanel.addView(nav);
        search=input("Buscar en esta carpeta",InputType.TYPE_CLASS_TEXT);search.setTextSize(14);browsePanel.addView(search,MusicUi.fill(MusicUi.dp(this,46)));
        search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int c,int a){}public void onTextChanged(CharSequence s,int st,int b,int c){filter();}public void afterTextChanged(Editable e){}});
        scan=MusicUi.button(this,"Reunir música y videos",true);browsePanel.addView(scan,MusicUi.fill(MusicUi.dp(this,46)));scan.setOnClickListener(v->scanOptions());
        scanStatus=MusicUi.text(this,"",12,MusicUi.MUTED);scanStatus.setMaxLines(2);browsePanel.addView(scanStatus);
        FrameLayout listArea=new FrameLayout(this);RecyclerView list=new RecyclerView(this);list.setLayoutManager(new LinearLayoutManager(this));list.setAdapter(adapter);listArea.addView(list,new FrameLayout.LayoutParams(-1,-1));
        empty=MusicUi.text(this,"",15,MusicUi.MUTED);empty.setGravity(android.view.Gravity.CENTER);listArea.addView(empty,new FrameLayout.LayoutParams(-1,-1));browsePanel.addView(listArea,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout player=MusicUi.row(this);player.setBackground(MusicUi.round(this,MusicUi.CARD,16));
        mini=MusicUi.text(this,"Elige una canción",14,MusicUi.INK);mini.setMaxLines(2);mini.setEllipsize(android.text.TextUtils.TruncateAt.END);mini.setPadding(16,8,8,8);player.addView(mini,new LinearLayout.LayoutParams(0,MusicUi.dp(this,62),1));mini.setOnClickListener(v->startActivity(new Intent(this,PlayerActivity.class)));
        miniPlay=MusicUi.button(this,"▶",false);miniPlay.setContentDescription("Reproducir o pausar");player.addView(miniPlay,new LinearLayout.LayoutParams(MusicUi.dp(this,54),MusicUi.dp(this,54)));miniPlay.setOnClickListener(v->togglePlay());root.addView(player);
        getOnBackPressedDispatcher().addCallback(this,new OnBackPressedCallback(true){public void handleOnBackPressed(){if(history.isEmpty())finish();else goUp();}});
        renderState();
    }
    private EditText input(String hint,int type){EditText field=new EditText(this);field.setHint(hint);field.setSingleLine();field.setInputType(type);field.setTextColor(MusicUi.INK);field.setHintTextColor(MusicUi.MUTED);return field;}
    private void renderState(){
        if(isDestroyed()||status==null)return;
        if(mega.ready())getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE);else getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        status.setText(mega.status());progress.setVisibility(mega.busy()?View.VISIBLE:View.GONE);
        loginScroll.setVisibility(!mega.ready()&&!mega.hasAccount()?View.VISIBLE:View.GONE);login.setEnabled(!mega.busy());
        sessionButtons.setVisibility(mega.hasAccount()?View.VISIBLE:View.GONE);retry.setVisibility(mega.ready()?View.GONE:View.VISIBLE);retry.setEnabled(!mega.busy());logout.setEnabled(!mega.busy());
        browsePanel.setVisibility(mega.ready()?View.VISIBLE:View.GONE);
        if(mega.ready()&&(!wasReady||!loadedAccount.equals(mega.account()))){history.clear();location=new Location("","Mi MEGA",false,1);loadedAccount=mega.account();load();}
        if(!mega.ready()){serial++;tracks.clear();filter();}
        wasReady=mega.ready();if(mega.needsMfa())pin.requestFocus();
    }
    private void load(){
        int request=++serial;String account=mega.account();Location selected=location;search.setText("");breadcrumb.setText(selected.path);
        tracks.clear();filter();empty.setText("Leyendo carpeta…");
        io.execute(()->{try{List<Track> result=mega.browse(selected.id,selected.shares,account);runOnUiThread(()->{if(request!=serial||isDestroyed()||!account.equals(mega.account()))return;tracks.addAll(result);filter();});}
            catch(Exception e){runOnUiThread(()->{if(request==serial&&!isDestroyed()){empty.setText(e.getMessage());empty.setVisibility(View.VISIBLE);}});}});
    }
    private void filter(){if(search==null)return;String query=LibraryRules.normalize(search.getText().toString());shown.clear();for(Track track:tracks)if(LibraryRules.normalize(track.name).contains(query))shown.add(track);adapter.notifyDataSetChanged();if(empty!=null){empty.setText("No hay carpetas ni archivos multimedia que mostrar aquí.");empty.setVisibility(shown.isEmpty()?View.VISIBLE:View.GONE);}}
    private void goUp(){if(!history.isEmpty()){location=history.pop();load();}}
    private void select(Track track){if(track.folder){history.push(location);location=new Location(track.id,location.path+" / "+track.name,false,location.source);load();}else play(track);}
    private void play(Track selected){if(controller==null){toast("El reproductor se está iniciando.");return;}List<MediaItem> queue=new ArrayList<>();int start=0;for(Track track:shown)if(!track.folder){if(track.id.equals(selected.id))start=queue.size();queue.add(track.mediaItem());}controller.setMediaItems(queue,start,VideoProgress.position(this,queue.get(start)));controller.prepare();controller.play();if(selected.isVideo())startActivity(new Intent(this,VideoActivity.class));}
    private void trackMenu(Track track){new MaterialAlertDialogBuilder(this).setTitle(track.name).setItems(new String[]{"Reproducir","Añadir a la cola","Añadir a Favoritos","Añadir a una lista"},(d,which)->{
        if(which==0)play(track);else if(which==1){if(controller!=null){controller.addMediaItem(track.mediaItem());toast("Añadida a la cola");}}
        else if(which==2){app.playlists().add(PlaylistStore.FAVORITES,track);toast("Guardada en Favoritos");}
        else {List<String> names=app.playlists().names();new MaterialAlertDialogBuilder(this).setTitle("Añadir a una lista").setItems(names.toArray(new String[0]),(dialog,index)->{app.playlists().add(names.get(index),track);toast("Guardada en "+names.get(index));}).show();}
    }).show();}
    private void disconnect(){if(LibraryScanService.isRunning(mega.namespace()))stopService(new Intent(this,LibraryScanService.class));if(controller!=null){controller.stop();controller.clearMediaItems();}mega.logout();}
    private void scanOptions(){
        if(LibraryScanService.anyRunning()){if(LibraryScanService.isRunning(mega.namespace()))startService(new Intent(this,LibraryScanService.class).setAction(LibraryScanService.PAUSE));else toast("Hay un escaneo de Drive en curso. Termínalo o páusalo primero.");return;}
        final Location selected=location;
        io.execute(()->{boolean resumable=app.library().status(mega.namespace()).resumable();runOnUiThread(()->{if(isDestroyed())return;List<String> labels=new ArrayList<>();labels.add("Toda mi cuenta y los compartidos");if(!selected.id.isEmpty())labels.add("Crear catálogo solo de esta carpeta");if(resumable)labels.add("Continuar el escaneo anterior");
            new MaterialAlertDialogBuilder(this).setTitle("Reunir música y videos").setItems(labels.toArray(new String[0]),(d,index)->{
                boolean resume=labels.get(index).startsWith("Continuar");Intent scanIntent=new Intent(this,LibraryScanService.class).putExtra("mega",true).putExtra("fresh",!resume);
                if(!resume&&index==1)scanIntent.putExtra("folder",selected.id).putExtra("path",selected.path).putExtra("source",selected.source);
                try{ContextCompat.startForegroundService(this,scanIntent);scanStatus.setText("Preparando catálogo…");lastPoll=0;}catch(RuntimeException e){toast("Android no pudo iniciar el escaneo. Vuelve a intentarlo con la app abierta.");}
            }).setNegativeButton("Cerrar",null).show();});});
    }
    @Override protected void onStart(){super.onStart();mega.addListener(stateChanged);renderState();}
    @Override protected void onStop(){mega.removeListener(stateChanged);password.setText("");pin.setText("");super.onStop();}
    @Override protected void onDestroy(){serial++;io.shutdownNow();super.onDestroy();}
    @Override protected void onConnected(){updateMini();}
    @Override protected void onPlaybackChanged(Player.Events events){updateMini();}
    private void updateMini(){if(controller==null)return;MediaItem item=controller.getCurrentMediaItem();mini.setText(item==null?"Elige una canción":item.mediaMetadata.title);miniPlay.setText(controller.getPlayWhenReady()?"Ⅱ":"▶");}
    @Override protected void onTick(){
        if(!mega.hasAccount()||android.os.SystemClock.elapsedRealtime()-lastPoll<2000)return;lastPoll=android.os.SystemClock.elapsedRealtime();String account=mega.namespace();
        io.execute(()->{LibraryStore.Status s=app.library().status(account);runOnUiThread(()->{if(isDestroyed()||!account.equals(mega.namespace()))return;boolean running=LibraryScanService.isRunning(account);scan.setText(running?"Pausar búsqueda":"Reunir música y videos");scanStatus.setText(running?s.found+" archivos multimedia · "+s.folders+" carpetas":s.total+" archivos multimedia en la biblioteca"+(s.resumable()?" · Escaneo pendiente":"")+(s.message.isEmpty()?"":"\n"+s.message));});});
    }
    private static final class Location{final String id,path;final boolean shares;final int source;Location(String id,String path,boolean shares,int source){this.id=id;this.path=path;this.shares=shares;this.source=source;}}
    private final class Holder extends RecyclerView.ViewHolder{TextView title,detail;MaterialButton options;Holder(LinearLayout row,TextView title,TextView detail,MaterialButton options){super(row);this.title=title;this.detail=detail;this.options=options;}}
    private final class Adapter extends RecyclerView.Adapter<Holder>{
        @NonNull @Override public Holder onCreateViewHolder(@NonNull ViewGroup parent,int type){LinearLayout row=MusicUi.row(MegaActivity.this);row.setMinimumHeight(MusicUi.dp(MegaActivity.this,TvUi.isTv(MegaActivity.this)?88:74));TvUi.row(row);LinearLayout words=MusicUi.col(MegaActivity.this);words.setPadding(8,8,8,8);TextView title=MusicUi.text(MegaActivity.this,"",16,MusicUi.INK);title.setMaxLines(2);title.setEllipsize(android.text.TextUtils.TruncateAt.END);TextView detail=MusicUi.text(MegaActivity.this,"",12,MusicUi.MUTED);words.addView(title);words.addView(detail);row.addView(words,new LinearLayout.LayoutParams(0,-2,1));MaterialButton options=MusicUi.button(MegaActivity.this,"⋮",false);options.setContentDescription("Opciones de la canción");row.addView(options,new LinearLayout.LayoutParams(MusicUi.dp(MegaActivity.this,44),MusicUi.dp(MegaActivity.this,48)));return new Holder(row,title,detail,options);}
        @Override public void onBindViewHolder(@NonNull Holder holder,int position){Track track=shown.get(position);holder.title.setText((track.folder?"▱  ":track.isVideo()?"▶  ":"♪  ")+track.name);holder.detail.setText(track.folder?"Carpeta":track.subtitle());holder.itemView.setOnClickListener(v->select(track));holder.options.setVisibility(track.folder?View.INVISIBLE:View.VISIBLE);holder.options.setOnClickListener(v->trackMenu(track));}
        @Override public int getItemCount(){return shown.size();}
    }
}
