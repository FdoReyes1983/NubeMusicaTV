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

public final class FullDriveBridgeClient {
    private static final String BRIDGE = BuildConfig.FULL_DRIVE_BRIDGE_URL;
    private FullDriveBridgeClient() {}

    public static boolean configured() {
        return BRIDGE != null && BRIDGE.startsWith("https://script.google.com/") && BRIDGE.endsWith("/exec");
    }

    public static PairCode requestPair() throws IOException {
        requireConfigured();
        JSONObject json = getJson(BRIDGE + "?action=start&v=5&t=" + System.currentTimeMillis());
        if (!json.optBoolean("ok")) throw bridgeError("No se pudo iniciar la vinculación", json);

        String pairId = json.optString("pair_id");
        String secret = json.optString("secret");
        String userCode = json.optString("user_code");
        String verificationUrl = json.optString("verification_url");
        long expiresIn = Math.max(60, json.optLong("expires_in", 900));

        if (pairId.isEmpty() || secret.isEmpty() || userCode.isEmpty() || verificationUrl.isEmpty())
            throw new IOException("El Bridge devolvió una respuesta incompleta.");

        return new PairCode(pairId, secret, userCode, verificationUrl, expiresIn);
    }

    public static Token poll(PairCode pair, BooleanSupplier keepWaiting) throws IOException {
        long deadline = SystemClock.elapsedRealtime() + pair.expiresIn * 1000L;

        while (SystemClock.elapsedRealtime() < deadline) {
            if (!keepWaiting.getAsBoolean()) throw new Cancelled();
            JSONObject json = status(pair.pairId, pair.secret);

            if (json.optBoolean("ok") && json.optBoolean("authorized"))
                return tokenFrom(json, pair.pairId, pair.secret);

            if (!json.optBoolean("ok")) {
                String error = json.optString("error");
                if ("expired".equals(error)) throw new IOException("El código de vinculación venció. Genera uno nuevo.");
                if ("pair_not_found".equals(error)) throw new IOException("La vinculación ya no existe. Genera un código nuevo.");
                if ("unauthorized".equals(error)) throw new IOException("El Bridge rechazó esta TV. Genera un código nuevo.");
                if ("google_authorization_lost".equals(error))
                    throw new AuthManager.NeedsAuthorization("Google revocó o perdió la autorización. Vuelve a vincular la TV.");
                throw bridgeError("El Bridge rechazó la vinculación", json);
            }

            sleep(3000L, keepWaiting);
        }

        throw new IOException("El código de vinculación venció. Genera uno nuevo.");
    }

    public static Token refresh(String pairId, String secret) throws IOException {
        requireConfigured();
        if (pairId == null || pairId.isEmpty() || secret == null || secret.isEmpty())
            throw new AuthManager.NeedsAuthorization("Falta la vinculación Full Drive. Vuelve a conectar Google Drive.");

        JSONObject json = status(pairId, secret);
        if (json.optBoolean("ok") && json.optBoolean("authorized"))
            return tokenFrom(json, pairId, secret);

        String error = json.optString("error");
        if ("pair_not_found".equals(error) || "expired".equals(error) ||
            "unauthorized".equals(error) || "google_authorization_lost".equals(error))
            throw new AuthManager.NeedsAuthorization("La vinculación Full Drive venció o fue revocada. Vuelve a conectar Google Drive.");

        throw bridgeError("No se pudo renovar Full Drive", json);
    }

    public static void revoke(String pairId, String secret) {
        if (!configured() || pairId == null || pairId.isEmpty() || secret == null || secret.isEmpty()) return;
        try {
            Map<String,String> form = new LinkedHashMap<>();
            form.put("action", "revoke");
            form.put("pair_id", pairId);
            form.put("secret", secret);
            postJson(BRIDGE, form);
        } catch (Exception ignored) {}
    }

    private static JSONObject status(String pairId, String secret) throws IOException {
        Map<String,String> form = new LinkedHashMap<>();
        form.put("action", "status");
        form.put("pair_id", pairId);
        form.put("secret", secret);
        return postJson(BRIDGE, form);
    }

    private static Token tokenFrom(JSONObject json, String pairId, String secret) throws IOException {
        String accessToken = json.optString("access_token");
        if (accessToken.isEmpty()) throw new IOException("El Bridge autorizó la cuenta pero no devolvió un token de acceso.");
        return new Token(accessToken, Math.max(300, json.optLong("expires_in", 3000)),
                json.optString("scope"), pairId, secret);
    }

    private static void requireConfigured() throws IOException {
        if (!configured()) throw new IOException("La URL de Nube Música Full Drive Bridge no está configurada.");
    }

    private static IOException bridgeError(String prefix, JSONObject json) {
        String error = json.optString("error");
        String detail = json.optString("message");
        StringBuilder text = new StringBuilder(prefix);
        if (!error.isEmpty()) text.append(": ").append(error);
        if (!detail.isEmpty()) text.append(" · ").append(detail);
        return new IOException(text.toString());
    }

    private static JSONObject getJson(String url) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(25000);
        connection.setInstanceFollowRedirects(true);
        connection.setRequestProperty("Accept", "application/json");
        try {
            int status = connection.getResponseCode();
            String text = readLimited(status >= 200 && status < 400 ? connection.getInputStream() : connection.getErrorStream(), 1024 * 1024);
            if (status < 200 || status >= 400) throw new IOException("Bridge HTTP " + status);
            try { return new JSONObject(text); }
            catch (Exception e) { throw new IOException("El Bridge no devolvió JSON. Revisa que la implementación web sea pública.", e); }
        } finally { connection.disconnect(); }
    }

    private static JSONObject postJson(String url, Map<String,String> form) throws IOException {
        byte[] body = encode(form).getBytes(StandardCharsets.UTF_8);
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(25000);
        connection.setInstanceFollowRedirects(true);
        connection.setRequestMethod("POST");
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
        connection.setRequestProperty("Accept", "application/json");
        connection.setFixedLengthStreamingMode(body.length);
        try {
            try (OutputStream out = connection.getOutputStream()) { out.write(body); }
            int status = connection.getResponseCode();
            String text = readLimited(status >= 200 && status < 400 ? connection.getInputStream() : connection.getErrorStream(), 1024 * 1024);
            if (status < 200 || status >= 400) throw new IOException("Bridge HTTP " + status);
            try { return new JSONObject(text); }
            catch (Exception e) { throw new IOException("El Bridge no devolvió JSON. Revisa la implementación de Apps Script.", e); }
        } finally { connection.disconnect(); }
    }

    private static String encode(Map<String,String> form) throws IOException {
        StringBuilder result = new StringBuilder();
        for (Map.Entry<String,String> entry : form.entrySet()) {
            if (result.length() > 0) result.append('&');
            result.append(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8.name()));
            result.append('=');
            result.append(URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8.name()));
        }
        return result.toString();
    }

    private static String readLimited(InputStream input, int limit) throws IOException {
        if (input == null) return "";
        try (InputStream in = input; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int n;
            while ((n = in.read(buffer)) != -1) {
                if (out.size() + n > limit) throw new IOException("La respuesta del Bridge es demasiado grande.");
                out.write(buffer, 0, n);
            }
            return out.toString(StandardCharsets.UTF_8.name());
        }
    }

    private static void sleep(long millis, BooleanSupplier keepWaiting) throws IOException {
        long end = SystemClock.elapsedRealtime() + millis;
        while (SystemClock.elapsedRealtime() < end) {
            if (!keepWaiting.getAsBoolean()) throw new Cancelled();
            try { Thread.sleep(Math.min(500, Math.max(1, end - SystemClock.elapsedRealtime()))); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new Cancelled(); }
        }
    }

    public static final class PairCode {
        public final String pairId, secret, userCode, verificationUrl;
        public final long expiresIn;
        PairCode(String pairId, String secret, String userCode, String verificationUrl, long expiresIn) {
            this.pairId = pairId; this.secret = secret; this.userCode = userCode;
            this.verificationUrl = verificationUrl; this.expiresIn = expiresIn;
        }
    }

    public static final class Token {
        public final String accessToken, scope, pairId, secret;
        public final long expiresIn;
        Token(String accessToken, long expiresIn, String scope, String pairId, String secret) {
            this.accessToken = accessToken; this.expiresIn = expiresIn; this.scope = scope;
            this.pairId = pairId; this.secret = secret;
        }
    }

    public static final class Cancelled extends IOException {
        Cancelled() { super("Vinculación cancelada."); }
    }
}
