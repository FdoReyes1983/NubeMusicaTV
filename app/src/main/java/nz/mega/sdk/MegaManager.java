package nz.mega.sdk;

import cl.fernando.nubemusica.*;
import mega.privacy.android.data.filewrapper.FileWrapper;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import nz.mega.sdk.*;
import org.json.JSONObject;

/** Official MEGA SDK owns encryption and authentication. Calls never run on the UI thread. */
public final class MegaManager {
    private final Context context;
    private final MegaSessionStore sessionStore;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService accountWorker=Executors.newSingleThreadExecutor();
    private final Set<MegaRequestListener> requests=ConcurrentHashMap.newKeySet();
    final Set<MegaTransferListener> transfers=ConcurrentHashMap.newKeySet();
    private final List<Runnable> listeners=new CopyOnWriteArrayList<>();
    private final Object stateLock=new Object();
    private volatile MegaApi api;
    private volatile boolean busy=true,ready,mfa;
    private volatile String email="",account="",status="Preparando MEGA…";
    private volatile long generation;
    public MegaManager(Context context){this.context=context.getApplicationContext();sessionStore=new MegaSessionStore(context);accountWorker.execute(this::initialize);}
    private void initialize(){
        try{
            FileWrapper.configureRoot(context.getNoBackupFilesDir());
            System.loadLibrary("mega");MegaApi.setLogToConsole(false);MegaApi.setLogLevel(MegaApi.LOG_LEVEL_FATAL);
            File cache=new File(context.getNoBackupFilesDir(),"mega-sdk");if(!cache.exists()&&!cache.mkdirs())throw new IOException("No se pudo preparar el almacenamiento privado.");
            api=new MegaApi(null,cache.getAbsolutePath(),"NubeMusica/0.5.0");
            JSONObject saved;
            try{saved=sessionStore.read();}catch(Exception e){sessionStore.clear();saved=null;}
            if(saved!=null){
                email=saved.optString("email");account=saved.optString("account");setStatus("Restaurando conexión con MEGA…");
                final String sid=saved.optString("session");
                int code=request(l->api.fastLogin(sid,l));
                if(code==MegaError.API_OK)finishLogin();
                else {if(code==MegaError.API_ESID||code==MegaError.API_ENOENT){sessionStore.clear();email="";account="";}throw new IOException(error(code));}
            }else status="Conecta tu cuenta para explorar tu música.";
        }catch(Throwable e){status=message(e);}
        finally{busy=false;changed();}
    }
    public void addListener(Runnable listener){listeners.add(listener);}
    public void removeListener(Runnable listener){listeners.remove(listener);}
    public boolean busy(){return busy;}
    public boolean ready(){return ready;}
    public boolean hasAccount(){return MegaRules.validAccount(account);}
    public boolean needsMfa(){return mfa;}
    public String email(){return email;}
    public String account(){return account;}
    public String namespace(){return hasAccount()?"mega:"+account:"";}
    public String status(){return status;}
    public long generation(){return generation;}
    public void login(String user,String password,String code){
        if(busy)return;
        if(user.trim().isEmpty()||password.isEmpty()){setStatus("Escribe tu correo y tu contraseña de MEGA.");return;}
        busy=true;mfa=false;ready=false;generation++;setStatus("Conectando con MEGA…");
        accountWorker.execute(()->{
            try{
                if(api==null)throw new IOException("No se pudo iniciar MEGA en este dispositivo.");
                if(api.isLoggedIn()!=0)request(l->api.localLogout(l));
                int result=request(l->{if(code.trim().isEmpty())api.login(user.trim(),password,l);else api.multiFactorAuthLogin(user.trim(),password,code.trim(),l);});
                if(result==MegaError.API_EMFAREQUIRED){mfa=true;status="Tu cuenta usa verificación en dos pasos. Escribe el código y vuelve a introducir la contraseña.";return;}
                if(result!=MegaError.API_OK)throw new IOException(error(result));
                finishLogin();
            }catch(Exception e){status=message(e);}
            finally{busy=false;changed();}
        });
    }
    private void finishLogin() throws Exception {
        email=api.getMyEmail();account=api.getMyUserHandle();
        if(!MegaRules.validAccount(account))throw new IOException("MEGA no pudo identificar la cuenta.");
        String sid=api.dumpSession();
        if(sid==null||sid.isEmpty())throw new IOException("MEGA no pudo crear la sesión.");
        sessionStore.save(sid,email,account);
        setStatus("Leyendo carpetas de MEGA…");
        int code=request(l->api.fetchNodes(l));if(code!=MegaError.API_OK)throw new IOException(error(code));
        ready=true;status="Conectado · "+email;
    }
    public void retry(){if(busy)return;busy=true;ready=false;generation++;changed();accountWorker.execute(()->{
        try{if(api!=null&&api.isLoggedIn()!=0)finishLogin();else{JSONObject saved=sessionStore.read();if(saved==null)throw new IOException("Vuelve a iniciar sesión en MEGA.");int code=request(l->api.fastLogin(saved.optString("session"),l));if(code!=0)throw new IOException(error(code));finishLogin();}}
        catch(Exception e){status=message(e);}finally{busy=false;changed();}
    });}
    public void logout(){
        if(busy)return;
        ready=false;busy=true;generation++;sessionStore.clear();email="";account="";mfa=false;setStatus("Cerrando sesión…");
        accountWorker.execute(()->{try{if(api!=null)request(l->api.localLogout(l));}catch(Exception ignored){}finally{busy=false;status="Sesión local cerrada. Puedes conectar otra cuenta.";changed();}});
    }
    public void prepareAccount() throws IOException { awaitReady(); }
    MegaApi awaitReady() throws IOException {
        long end=android.os.SystemClock.elapsedRealtime()+125000;
        synchronized(stateLock){while(busy&&!ready&&android.os.SystemClock.elapsedRealtime()<end){try{stateLock.wait(500);}catch(InterruptedException e){Thread.currentThread().interrupt();throw new IOException("Operación cancelada",e);}}}
        if(!ready||api==null)throw new IOException(hasAccount()?"Abre MEGA y pulsa Reintentar conexión.":"Conecta MEGA para reproducir esta canción.");
        return api;
    }
    private interface RequestCall{void run(MegaRequestListener listener);}
    private int request(RequestCall call) throws IOException {
        CountDownLatch done=new CountDownLatch(1);int[] result={MegaError.API_EFAILED};
        MegaRequestListener listener=new MegaRequestListener(){@Override public void onRequestFinish(MegaApi a,MegaRequest r,MegaError e){result[0]=e.getErrorCode();done.countDown();main.post(()->requests.remove(this));}};
        requests.add(listener);
        try{call.run(listener);if(!done.await(120,TimeUnit.SECONDS))throw new IOException("MEGA tardó demasiado. Revisa tu conexión y vuelve a intentarlo.");return result[0];}
        catch(InterruptedException e){Thread.currentThread().interrupt();throw new IOException("Operación cancelada",e);}
        catch(RuntimeException e){requests.remove(listener);throw new IOException("MEGA no pudo completar la solicitud.",e);}
    }
    public List<Track> browse(String folder,boolean shares,String expected) throws IOException {
        MegaApi sdk=awaitReady();MegaRules.requireAccount(expected,account);
        MegaNode parent=null;MegaNodeList nodes=null;
        try{
            if(shares)nodes=sdk.getInShares();
            else {parent=folder.isEmpty()?sdk.getRootNode():sdk.getNodeByHandle(MegaApi.base64ToHandle(folder));if(parent==null)throw new IOException("Esta carpeta ya no está disponible.");nodes=sdk.getChildren(parent);}
            List<Track> tracks=new ArrayList<>();
            if(nodes!=null)for(int i=0;i<nodes.size();i++){MegaNode node=nodes.get(i);if(node==null)continue;String name=node.getName();
                if(node.isFolder()||DriveRules.isMedia(name,""))tracks.add(track(node,expected,shares?2:1,""));}
            MegaRules.requireAccount(expected,account);
            Collections.sort(tracks,(a,b)->a.folder!=b.folder?(a.folder?-1:1):LibraryRules.normalize(a.name).compareTo(LibraryRules.normalize(b.name)));
            return tracks;
        }finally{if(nodes!=null)nodes.delete();if(parent!=null)parent.delete();}
    }
    static Track track(MegaNode node,String account,int source,String path){
        String mime=node.isFolder()?DriveRules.FOLDER:DriveRules.playableMime(node.getName(),"");
        return new Track(node.getBase64Handle(),node.getName(),mime==null?"":mime,"",MegaRules.uri(node.getBase64Handle(),account),node.getSize(),node.isFolder(),true,path,source,0,node.getModificationTime()*1000);
    }
    private void setStatus(String value){status=value;changed();}
    private void changed(){synchronized(stateLock){stateLock.notifyAll();}main.post(()->{for(Runnable listener:listeners)listener.run();});}
    private static String message(Throwable e){return e instanceof IOException?e.getMessage():"No se pudo preparar MEGA. Reabre la aplicación o vuelve a conectar la cuenta.";}
    public static String error(int code){
        switch(code){
            case MegaError.API_ENOENT: case MegaError.API_EKEY: case MegaError.API_EACCESS:return "MEGA no aceptó el acceso. Revisa el correo, la contraseña y el código de verificación.";
            case MegaError.API_EOVERQUOTA:case MegaError.API_EGOINGOVERQUOTA:return "Se alcanzó la cuota de transferencia de MEGA. Inténtalo más tarde.";
            case MegaError.API_ESID:return "La sesión de MEGA venció. Vuelve a conectar tu cuenta.";
            case MegaError.API_EBLOCKED:return "MEGA bloqueó temporalmente esta cuenta. Revisa su aplicación oficial.";
            default:return "MEGA no pudo completar la operación ("+code+"). Revisa la conexión e inténtalo de nuevo.";
        }
    }
}
