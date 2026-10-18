package cl.fernando.nubemusica;

import android.os.SystemClock;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BooleanSupplier;

/** OAuth 2.0 oficial de Google para TVs y dispositivos de entrada limitada. */
public final class TvDeviceOAuth {
    private static final String DEVICE_ENDPOINT = "https://oauth2.googleapis.com/device/code";
    private static final String TOKEN_ENDPOINT = "https://oauth2.googleapis.com/token";

    private TvDeviceOAuth() {}

    public static boolean configured() {
        return !BuildConfig.GOOGLE_TV_CLIENT_ID.isEmpty()
                && !BuildConfig.GOOGLE_TV_CLIENT_SECRET.isEmpty();
    }

    public static DeviceCode requestCode() throws IOException {
        requireConfigured();
        Map<String,String> form = new LinkedHashMap<>();
        form.put("client_id", BuildConfig.GOOGLE_TV_CLIENT_ID);
        form.put("scope", AuthManager.TV_SCOPE);
        Response response = post(DEVICE_ENDPOINT, form);
        JSONObject json = response.json;
        if (response.status < 200 || response.status >= 300)
            throw oauthError("Google no pudo iniciar la vinculación del televisor", json);

        String deviceCode = json.optString("device_code");
        String userCode = json.optString("user_code");
        String verificationUrl = json.optString("verification_url");
        if (verificationUrl.isEmpty()) verificationUrl = json.optString("verification_uri");
        long expiresIn = json.optLong("expires_in", 1800);
        int interval = Math.max(5, json.optInt("interval", 5));
        if (deviceCode.isEmpty() || userCode.isEmpty() || verificationUrl.isEmpty())
            throw new IOException("Google devolvió una respuesta incompleta al iniciar la vinculación.");
        return new DeviceCode(deviceCode, userCode, verificationUrl, expiresIn, interval);
    }

    public static Token poll(DeviceCode code, BooleanSupplier keepWaiting) throws IOException {
        requireConfigured();
        long deadline = SystemClock.elapsedRealtime() + code.expiresIn * 1000L;
        int interval = code.interval;
        while (SystemClock.elapsedRealtime() < deadline) {
            if (!keepWaiting.getAsBoolean()) throw new Cancelled();
            sleep(interval * 1000L, keepWaiting);
            Map<String,String> form = new LinkedHashMap<>();
            form.put("client_id", BuildConfig.GOOGLE_TV_CLIENT_ID);
            form.put("client_secret", BuildConfig.GOOGLE_TV_CLIENT_SECRET);
            form.put("device_code", code.deviceCode);
            form.put("grant_type", "urn:ietf:params:oauth:grant-type:device_code");
            Response response = post(TOKEN_ENDPOINT, form);
            JSONObject json = response.json;
            String accessToken = json.optString("access_token");
            if (!accessToken.isEmpty()) {
                String refreshToken = json.optString("refresh_token");
                if (refreshToken.isEmpty())
                    throw new IOException("Google autorizó el televisor, pero no entregó un token de actualización.");
                return new Token(accessToken, refreshToken, json.optLong("expires_in",3600), json.optString("scope"));
            }
            String error = json.optString("error");
            if ("authorization_pending".equals(error)) continue;
            if ("slow_down".equals(error)) { interval += 5; continue; }
            if ("access_denied".equals(error)) throw new IOException("La autorización fue rechazada desde el teléfono.");
            if ("expired_token".equals(error)) throw new IOException("El código de vinculación venció. Pulsa Reintentar para obtener uno nuevo.");
            if ("invalid_client".equals(error)) throw new IOException("El cliente OAuth de Nube Música TV no es válido. Revisa el tipo de cliente y las credenciales.");
            throw oauthError("Google no pudo completar la vinculación", json);
        }
        throw new IOException("El código de vinculación venció. Pulsa Reintentar para obtener uno nuevo.");
    }

    public static Token refresh(String refreshToken) throws IOException {
        requireConfigured();
        if (refreshToken == null || refreshToken.isEmpty()) throw new IOException("Falta el token de actualización del televisor.");
        Map<String,String> form = new LinkedHashMap<>();
        form.put("client_id", BuildConfig.GOOGLE_TV_CLIENT_ID);
        form.put("client_secret", BuildConfig.GOOGLE_TV_CLIENT_SECRET);
        form.put("refresh_token", refreshToken);
        form.put("grant_type", "refresh_token");
        Response response = post(TOKEN_ENDPOINT, form);
        JSONObject json = response.json;
        String accessToken = json.optString("access_token");
        if (response.status >= 200 && response.status < 300 && !accessToken.isEmpty())
            return new Token(accessToken, json.optString("refresh_token", refreshToken), json.optLong("expires_in",3600), json.optString("scope"));
        if ("invalid_grant".equals(json.optString("error")))
            throw new AuthManager.NeedsAuthorization("La vinculación de Google Drive venció o fue revocada. Vuelve a conectar el televisor.");
        throw oauthError("No se pudo renovar la conexión de Google Drive", json);
    }

    private static void requireConfigured() throws IOException {
        if (!configured()) throw new IOException("Esta APK no contiene las credenciales del cliente OAuth Nube Música TV. Configura GOOGLE_TV_CLIENT_ID y GOOGLE_TV_CLIENT_SECRET en GitHub Actions.");
    }

    private static void sleep(long millis, BooleanSupplier keepWaiting) throws IOException {
        long end = SystemClock.elapsedRealtime() + millis;
        while (SystemClock.elapsedRealtime() < end) {
            if (!keepWaiting.getAsBoolean()) throw new Cancelled();
            long remaining = end - SystemClock.elapsedRealtime();
            try { Thread.sleep(Math.min(500, Math.max(1, remaining))); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new Cancelled(); }
        }
    }

    private static IOException oauthError(String prefix, JSONObject json) {
        String error = json.optString("error"), description = json.optString("error_description");
        StringBuilder message = new StringBuilder(prefix);
        if (!error.isEmpty()) message.append(": ").append(error);
        if (!description.isEmpty()) message.append(" · ").append(description);
        return new IOException(message.toString());
    }

    private static Response post(String endpoint, Map<String,String> form) throws IOException {
        byte[] body = encode(form).getBytes(StandardCharsets.UTF_8);
        HttpURLConnection connection = (HttpURLConnection) new URL(endpoint).openConnection();
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(25000);
        connection.setRequestMethod("POST");
        connection.setDoOutput(true);
        connection.setInstanceFollowRedirects(false);
        connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
        connection.setRequestProperty("Accept", "application/json");
        connection.setFixedLengthStreamingMode(body.length);
        try {
            try (OutputStream output = connection.getOutputStream()) { output.write(body); }
            int status = connection.getResponseCode();
            InputStream input = status >= 200 && status < 300 ? connection.getInputStream() : connection.getErrorStream();
            String text = readLimited(input, 1024 * 1024);
            JSONObject json;
            try { json = text.isEmpty() ? new JSONObject() : new JSONObject(text); }
            catch (Exception e) { throw new IOException("Google devolvió una respuesta OAuth no válida.", e); }
            return new Response(status, json);
        } finally { connection.disconnect(); }
    }

    private static String encode(Map<String,String> form) throws IOException {
        StringBuilder body = new StringBuilder();
        for (Map.Entry<String,String> entry : form.entrySet()) {
            if (body.length() > 0) body.append('&');
            body.append(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8.name()));
            body.append('=');
            body.append(URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8.name()));
        }
        return body.toString();
    }

    private static String readLimited(InputStream input, int limit) throws IOException {
        if (input == null) return "";
        try (InputStream in = input; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096]; int n;
            while ((n = in.read(buffer)) != -1) {
                if (out.size() + n > limit) throw new IOException("La respuesta OAuth de Google es demasiado grande.");
                out.write(buffer, 0, n);
            }
            return out.toString(StandardCharsets.UTF_8.name());
        }
    }

    private static final class Response {
        final int status; final JSONObject json;
        Response(int status, JSONObject json) { this.status=status; this.json=json; }
    }

    public static final class DeviceCode {
        public final String deviceCode, userCode, verificationUrl;
        public final long expiresIn; public final int interval;
        DeviceCode(String deviceCode, String userCode, String verificationUrl, long expiresIn, int interval) {
            this.deviceCode=deviceCode; this.userCode=userCode; this.verificationUrl=verificationUrl; this.expiresIn=expiresIn; this.interval=interval;
        }
    }

    public static final class Token {
        public final String accessToken, refreshToken, scope;
        public final long expiresIn;
        Token(String accessToken, String refreshToken, long expiresIn, String scope) {
            this.accessToken=accessToken; this.refreshToken=refreshToken; this.expiresIn=expiresIn; this.scope=scope;
        }
    }

    public static final class Cancelled extends IOException { Cancelled() { super("Vinculación cancelada."); } }
}
