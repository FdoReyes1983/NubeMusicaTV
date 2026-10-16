package cl.fernando.nubemusica;

import nz.mega.sdk.MegaManager;
import nz.mega.sdk.MegaDataSource;
import nz.mega.sdk.MegaRepository;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.SystemClock;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.ServiceCompat;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/** User-started, visible scan. SQLite checkpoints survive process death and service limits. */
public final class LibraryScanService extends Service {
    public static final String PAUSE="cl.fernando.nubemusica.PAUSE_SCAN";
    private static final String CHANNEL="music-library-scan";
    private static final int NOTICE=204;
    private static volatile boolean running;
    private static volatile String runningAccount="";
    private static volatile LibraryScanService owner;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private volatile boolean stopping;
    private Future<?> job;
    private volatile Thread scanThread;
    private LibraryStore library;
    private String account="";
    private PowerManager.WakeLock wake;
    private long lastNotice;
    public static boolean anyRunning(){return running;}
    public static String currentAccount(){return runningAccount;}
    public static boolean isRunning(String account) {return running&&runningAccount.equals(account);}
    public static boolean isStopping(String account) {LibraryScanService current=owner;return isRunning(account)&&current!=null&&current.stopping;}
    @Override public void onCreate() {
        super.onCreate();library=((NubeApp)getApplication()).library();
        getSystemService(NotificationManager.class).createNotificationChannel(
                new NotificationChannel(CHANNEL,"Búsqueda de música y videos",NotificationManager.IMPORTANCE_LOW));
    }
    @Override public int onStartCommand(Intent intent,int flags,int startId) {
        if(intent!=null&&PAUSE.equals(intent.getAction())) {pause("Escaneo pausado. Puedes continuarlo cuando quieras.");return START_NOT_STICKY;}
        if(running) return START_NOT_STICKY;
        NubeApp app=(NubeApp)getApplication();AuthManager auth=app.auth();MegaManager mega=app.mega();
        final boolean isMega=intent!=null&&intent.getBooleanExtra("mega",false);
        account=isMega?mega.namespace():auth.accountLabel();
        final String folder=intent==null?"":intent.getStringExtra("folder"),path=intent==null?"":intent.getStringExtra("path");
        final int source=intent==null?1:intent.getIntExtra("source",1);
        final boolean fresh=intent!=null&&intent.getBooleanExtra("fresh",false);
        try {
            ServiceCompat.startForeground(this,NOTICE,notice("Preparando tu biblioteca…"),
                    Build.VERSION.SDK_INT>=29?ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC:0);
        } catch(RuntimeException error) {
            android.widget.Toast.makeText(this,"Android no pudo iniciar el escaneo. Abre Biblioteca y vuelve a intentarlo.",android.widget.Toast.LENGTH_LONG).show();
            stopSelf();return START_NOT_STICKY;
        }
        if(isMega?!mega.hasAccount():(!auth.isConnected()||!account.contains("@"))) {stopForeground(STOP_FOREGROUND_REMOVE);stopSelf();return START_NOT_STICKY;}
        running=true;runningAccount=account;owner=this;stopping=false;long generation=isMega?mega.generation():auth.generation();
        PowerManager power=getSystemService(PowerManager.class);
        wake=power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"NubeMusica:LibraryScan");wake.acquire(30*60*1000L);
        job=worker.submit(() -> {
            scanThread=Thread.currentThread();
            try {
                if(isMega)mega.prepareAccount();
                LibraryStore.Session session=isMega?library.prepareMega(account,folder==null?"":folder,path==null?"":path,source,fresh):library.prepare(account);
                new CatalogScanner(isMega?new MegaRepository(mega):new DriveRepository(auth),session,new CatalogScanner.Control() {
                    @Override public boolean stopped() {return stopping||(isMega?(!mega.ready()||mega.generation()!=generation||!account.equals(mega.namespace())):(!auth.isConnected()||auth.generation()!=generation||!account.equals(auth.accountLabel())));}
                    @Override public void progressed() {
                        if(SystemClock.elapsedRealtime()-lastNotice<2000) return;
                        lastNotice=SystemClock.elapsedRealtime();
                        renewWake();
                        LibraryStore.Status status=library.status(account);
                        getSystemService(NotificationManager.class).notify(NOTICE,notice(status.found+" archivos · "+status.folders+" carpetas revisadas"));
                    }
                }).run();
                if(owner==this)library.pause(account,"Escaneo pausado. El avance quedó guardado.");
            } catch(Exception error) {
                if(owner==this)library.pause(account,stopping?"Escaneo pausado. El avance quedó guardado.":
                        error instanceof java.io.IOException?error.getMessage():"No se pudo completar el escaneo. El avance quedó guardado; pulsa Continuar.");
            } finally {
                releaseWake();scanThread=null;
                if(owner==this){stopForeground(STOP_FOREGROUND_REMOVE);stopSelf();}
            }
        });
        return START_NOT_STICKY;
    }
    @androidx.annotation.OptIn(markerClass=androidx.media3.common.util.UnstableApi.class)
    private Notification notice(String text) {
        PendingIntent open=PendingIntent.getActivity(this,204,new Intent(this,MainActivity.class).putExtra("library",true),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        PendingIntent pause=PendingIntent.getService(this,205,new Intent(this,LibraryScanService.class).setAction(PAUSE),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        return new NotificationCompat.Builder(this,CHANNEL).setSmallIcon(android.R.drawable.ic_popup_sync)
                .setContentTitle("Reuniendo música y videos").setContentText(text).setContentIntent(open)
                .setOngoing(true).setOnlyAlertOnce(true).setProgress(0,0,true)
                .addAction(0,"Pausar",pause).build();
    }
    private void pause(String message) {
        stopping=true;
        Thread thread=scanThread;if(thread!=null)thread.interrupt();
        if(!account.isEmpty()) library.pause(account,message);
        if(job==null){stopForeground(STOP_FOREGROUND_REMOVE);stopSelf();}
    }
    @Override public void onTimeout(int startId,int fgsType) {
        pause("Android pausó el escaneo. Abre la biblioteca y pulsa Continuar.");
        stopForeground(STOP_FOREGROUND_REMOVE);stopSelf();
    }
    @Override public void onDestroy() {
        stopping=true;if(job!=null) job.cancel(true);worker.shutdownNow();
        if(owner==this){library.pause(account,"Escaneo pausado. El avance quedó guardado.");running=false;runningAccount="";owner=null;}
        releaseWake();
        super.onDestroy();
    }
    private synchronized void renewWake() {if(wake!=null&&!wake.isHeld()&&!stopping)wake.acquire(30*60*1000L);}
    private synchronized void releaseWake() {if(wake!=null&&wake.isHeld())wake.release();}
    @Nullable @Override public IBinder onBind(Intent intent) {return null;}
}
