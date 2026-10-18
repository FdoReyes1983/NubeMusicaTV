package cl.fernando.nubemusica;

import android.accounts.Account;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Looper;
import android.os.SystemClock;
import com.google.android.gms.auth.api.identity.AuthorizationRequest;
import com.google.android.gms.auth.api.identity.AuthorizationResult;
import com.google.android.gms.auth.api.identity.ClearTokenRequest;
import com.google.android.gms.auth.api.identity.Identity;
import com.google.android.gms.common.api.Scope;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import java.io.IOException;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

/** Teléfono: drive.readonly. Android TV: device flow oficial + drive.file. */
public final class AuthManager {
    public static final String SCOPE = "https://www.googleapis.com/auth/drive.readonly";
    public static final String TV_SCOPE = "https://www.googleapis.com/auth/drive.file";
    private static final String MODE_PHONE="phone", MODE_DEVICE="device";

    private final Context context;
    private final SharedPreferences preferences;
    private final Object refreshLock = new Object();
    private volatile String token, mode;
    private volatile long expiresAt, generation;
    private volatile boolean enabled;

    public AuthManager(Context context) {
        this.context=context.getApplicationContext();
        preferences=this.context.getSharedPreferences("drive-account", Context.MODE_PRIVATE);
        enabled=preferences.getBoolean("enabled", false);
        mode=preferences.getString("mode", MODE_PHONE);
    }

    public boolean isConnected(){return enabled;}
    public boolean isDeviceFlow(){return MODE_DEVICE.equals(mode);}
    public long generation(){return generation;}
    public String accountLabel(){return preferences.getString("email","Google Drive");}

    private AuthorizationRequest request(boolean chooseAccount){
        AuthorizationRequest.Builder builder=AuthorizationRequest.builder().setRequestedScopes(Collections.singletonList(new Scope(SCOPE)));
        String email=preferences.getString("email","");
        if(chooseAccount) builder.setPrompt(AuthorizationRequest.Prompt.SELECT_ACCOUNT);
        else if(!email.isEmpty()) builder.setAccount(new Account(email,"com.google"));
        return builder.build();
    }

    public Task<AuthorizationResult> authorize(Activity activity){return Identity.getAuthorizationClient(activity).authorize(request(!enabled));}

    public synchronized void acceptInteractive(AuthorizationResult result)throws IOException{
        verify(result); enabled=true; mode=MODE_PHONE; generation++; token=result.getAccessToken();
        expiresAt=SystemClock.elapsedRealtime()+TimeUnit.MINUTES.toMillis(50);
        preferences.edit().putBoolean("enabled",true).putString("mode",MODE_PHONE).remove("refresh_token").apply();
    }

    public synchronized void acceptDeviceToken(TvDeviceOAuth.Token result)throws IOException{
        if(result==null||result.accessToken==null||result.accessToken.isEmpty()||result.refreshToken==null||result.refreshToken.isEmpty()) throw new NeedsAuthorization();
        enabled=true; mode=MODE_DEVICE; generation++; token=result.accessToken;
        expiresAt=SystemClock.elapsedRealtime()+TimeUnit.SECONDS.toMillis(Math.max(60,result.expiresIn-60));
        preferences.edit().putBoolean("enabled",true).putString("mode",MODE_DEVICE).putString("refresh_token",result.refreshToken).putString("email","Google Drive TV").apply();
    }

    public synchronized void rememberAccount(String email,long expectedGeneration){
        if(email!=null&&!email.isEmpty()&&enabled&&generation==expectedGeneration) preferences.edit().putString("email",email).apply();
    }

    private static void verify(AuthorizationResult result)throws IOException{
        if(result.hasResolution()||result.getAccessToken()==null||!result.getGrantedScopes().contains(SCOPE)) throw new NeedsAuthorization();
    }

    public String accessToken()throws IOException{
        if(Looper.myLooper()==Looper.getMainLooper()) throw new IllegalStateException("Token requested on main thread");
        if(!enabled) throw new NeedsAuthorization();
        if(token!=null&&SystemClock.elapsedRealtime()<expiresAt) return token;
        synchronized(refreshLock){
            if(!enabled) throw new NeedsAuthorization();
            if(token!=null&&SystemClock.elapsedRealtime()<expiresAt) return token;
            long startedGeneration=generation;
            try{
                String next; long nextExpiry;
                if(MODE_DEVICE.equals(mode)){
                    TvDeviceOAuth.Token refreshed=TvDeviceOAuth.refresh(preferences.getString("refresh_token",""));
                    next=refreshed.accessToken;
                    nextExpiry=SystemClock.elapsedRealtime()+TimeUnit.SECONDS.toMillis(Math.max(60,refreshed.expiresIn-60));
                    if(refreshed.refreshToken!=null&&!refreshed.refreshToken.isEmpty()) preferences.edit().putString("refresh_token",refreshed.refreshToken).apply();
                }else{
                    AuthorizationResult result=Tasks.await(Identity.getAuthorizationClient(context).authorize(request(false)),30,TimeUnit.SECONDS);
                    verify(result); next=result.getAccessToken(); nextExpiry=SystemClock.elapsedRealtime()+TimeUnit.MINUTES.toMillis(50);
                }
                synchronized(this){
                    if(!enabled||generation!=startedGeneration) throw new NeedsAuthorization();
                    token=next; expiresAt=nextExpiry; return token;
                }
            }catch(InterruptedException e){Thread.currentThread().interrupt();throw new IOException("Conexión interrumpida",e);}
            catch(NeedsAuthorization e){throw e;}
            catch(Exception e){if(e instanceof IOException) throw (IOException)e; throw new IOException("No se pudo renovar el acceso a Drive. Abre la app y vuelve a conectar.",e);}
        }
    }

    public void invalidate(String rejectedToken)throws IOException{
        synchronized(this){if(rejectedToken.equals(token)){token=null;expiresAt=0;}}
        if(MODE_DEVICE.equals(mode)) return;
        try{Tasks.await(Identity.getAuthorizationClient(context).clearToken(ClearTokenRequest.builder().setToken(rejectedToken).build()),15,TimeUnit.SECONDS);}
        catch(InterruptedException e){Thread.currentThread().interrupt();throw new IOException("Conexión interrumpida",e);}
        catch(Exception e){throw new IOException("Vuelve a conectar Google Drive",e);}
    }

    public synchronized void disconnect(){
        String old=token,oldMode=mode; enabled=false; mode=MODE_PHONE; token=null; expiresAt=0; generation++; preferences.edit().clear().apply();
        if(old!=null&&!MODE_DEVICE.equals(oldMode)) Identity.getAuthorizationClient(context).clearToken(ClearTokenRequest.builder().setToken(old).build());
    }

    public static class NeedsAuthorization extends IOException{
        public NeedsAuthorization(){super("Conecta Google Drive para continuar.");}
        public NeedsAuthorization(String message){super(message);}
    }
}
