package cl.fernando.nubemusica;

import android.accounts.Account;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Looper;
import android.os.SystemClock;

import com.google.android.gms.auth.GoogleAuthException;
import com.google.android.gms.auth.GoogleAuthUtil;
import com.google.android.gms.auth.UserRecoverableAuthException;
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

public final class AuthManager {
    public static final String SCOPE = "https://www.googleapis.com/auth/drive.readonly";
    public static final String LEGACY_SCOPE = "oauth2:" + SCOPE;

    private final Context context;
    private final SharedPreferences preferences;
    private final Object refreshLock = new Object();
    private volatile String token;
    private volatile long expiresAt;
    private volatile long generation;
    private volatile boolean enabled;
    private volatile boolean legacy;

    public AuthManager(Context context) {
        this.context = context.getApplicationContext();
        preferences = context.getSharedPreferences("drive-account", Context.MODE_PRIVATE);
        enabled = preferences.getBoolean("enabled", false);
        legacy = preferences.getBoolean("legacy", false);
    }

    public boolean isConnected() { return enabled; }
    public boolean isLegacy() { return legacy; }
    public long generation() { return generation; }
    public String accountLabel() { return preferences.getString("email", "Google Drive"); }

    private AuthorizationRequest request(boolean chooseAccount) {
        AuthorizationRequest.Builder builder = AuthorizationRequest.builder()
                .setRequestedScopes(Collections.singletonList(new Scope(SCOPE)));
        String email = preferences.getString("email", "");
        if (chooseAccount) builder.setPrompt(AuthorizationRequest.Prompt.SELECT_ACCOUNT);
        else if (!email.isEmpty()) builder.setAccount(new Account(email, "com.google"));
        return builder.build();
    }

    public Task<AuthorizationResult> authorize(Activity activity) {
        return Identity.getAuthorizationClient(activity).authorize(request(!enabled));
    }

    public synchronized void acceptInteractive(AuthorizationResult result) throws IOException {
        verify(result);
        enabled = true;
        legacy = false;
        generation++;
        token = result.getAccessToken();
        expiresAt = SystemClock.elapsedRealtime() + TimeUnit.MINUTES.toMillis(50);
        preferences.edit().putBoolean("enabled", true).putBoolean("legacy", false).apply();
    }

    public synchronized void acceptLegacyToken(String accessToken, String email) throws IOException {
        if (accessToken == null || accessToken.isEmpty() || email == null || email.isEmpty()) throw new NeedsAuthorization();
        enabled = true;
        legacy = true;
        generation++;
        token = accessToken;
        expiresAt = SystemClock.elapsedRealtime() + TimeUnit.MINUTES.toMillis(50);
        preferences.edit().putBoolean("enabled", true).putBoolean("legacy", true).putString("email", email).apply();
    }

    public synchronized void rememberAccount(String email, long expectedGeneration) {
        if (email != null && !email.isEmpty() && enabled && generation == expectedGeneration)
            preferences.edit().putString("email", email).apply();
    }

    private static void verify(AuthorizationResult result) throws IOException {
        if (result.hasResolution() || result.getAccessToken() == null || !result.getGrantedScopes().contains(SCOPE))
            throw new NeedsAuthorization();
    }

    private String legacyToken() throws IOException {
        String email = preferences.getString("email", "");
        if (email.isEmpty()) throw new NeedsAuthorization();
        try {
            return GoogleAuthUtil.getToken(context, email, LEGACY_SCOPE);
        } catch (UserRecoverableAuthException e) {
            throw new NeedsAuthorization("Google necesita confirmar nuevamente el acceso. Abre Nube Música y pulsa Conectar Google Drive.");
        } catch (GoogleAuthException e) {
            throw new IOException("Google rechazó la autorización de Drive para esta cuenta.", e);
        }
    }

    public String accessToken() throws IOException {
        if (Looper.myLooper() == Looper.getMainLooper()) throw new IllegalStateException("Token requested on main thread");
        if (!enabled) throw new NeedsAuthorization();
        if (token != null && SystemClock.elapsedRealtime() < expiresAt) return token;
        synchronized (refreshLock) {
            if (!enabled) throw new NeedsAuthorization();
            if (token != null && SystemClock.elapsedRealtime() < expiresAt) return token;
            long startedGeneration = generation;
            try {
                String next;
                if (legacy) next = legacyToken();
                else {
                    AuthorizationResult result = Tasks.await(Identity.getAuthorizationClient(context).authorize(request(false)), 30, TimeUnit.SECONDS);
                    verify(result);
                    next = result.getAccessToken();
                }
                synchronized (this) {
                    if (!enabled || generation != startedGeneration) throw new NeedsAuthorization();
                    token = next;
                    expiresAt = SystemClock.elapsedRealtime() + TimeUnit.MINUTES.toMillis(50);
                    return token;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Conexión interrumpida", e);
            } catch (NeedsAuthorization e) {
                throw e;
            } catch (Exception e) {
                throw new IOException("No se pudo renovar el acceso a Drive. Abre la app y vuelve a conectar.", e);
            }
        }
    }

    public void invalidate(String rejectedToken) throws IOException {
        boolean legacyNow = legacy;
        synchronized (this) {
            if (rejectedToken.equals(token)) { token = null; expiresAt = 0; }
        }
        try {
            if (legacyNow) GoogleAuthUtil.clearToken(context, rejectedToken);
            else Tasks.await(Identity.getAuthorizationClient(context).clearToken(ClearTokenRequest.builder().setToken(rejectedToken).build()), 15, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Conexión interrumpida", e);
        } catch (Exception e) {
            throw new IOException("Vuelve a conectar Google Drive", e);
        }
    }

    public synchronized void disconnect() {
        String old = token;
        boolean oldLegacy = legacy;
        enabled = false;
        legacy = false;
        token = null;
        expiresAt = 0;
        generation++;
        preferences.edit().clear().apply();
        if (old != null) {
            if (oldLegacy) {
                new Thread(() -> { try { GoogleAuthUtil.clearToken(context, old); } catch (Exception ignored) {} }, "drive-token-clear").start();
            } else {
                Identity.getAuthorizationClient(context).clearToken(ClearTokenRequest.builder().setToken(old).build());
            }
        }
    }

    public static class NeedsAuthorization extends IOException {
        public NeedsAuthorization() { super("Conecta Google Drive para continuar."); }
        public NeedsAuthorization(String message) { super(message); }
    }
}
