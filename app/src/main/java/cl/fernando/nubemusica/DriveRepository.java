package cl.fernando.nubemusica;

import android.net.Uri;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Read-only Drive v3 client. Folder pages are loaded on demand. */
public final class DriveRepository implements CatalogScanner.Source {
    private final AuthManager auth;
    public DriveRepository(AuthManager auth) { this.auth = auth; }

    public static final class Page {
        public final List<Track> tracks;
        public final String next;
        Page(List<Track> tracks, String next) { this.tracks = tracks; this.next = next; }
    }

    public Page list(String parent, String resourceKey, boolean shared, String search, String pageToken) throws IOException {
        Uri.Builder url = Uri.parse("https://www.googleapis.com/drive/v3/files").buildUpon()
                .appendQueryParameter("q", DriveRules.query(parent, shared, search))
                .appendQueryParameter("pageSize", "100")
                .appendQueryParameter("orderBy", "folder,name_natural")
                .appendQueryParameter("spaces", "drive")
                .appendQueryParameter("supportsAllDrives", "true")
                .appendQueryParameter("includeItemsFromAllDrives", "true")
                .appendQueryParameter("fields", "nextPageToken,files(id,name,mimeType,size,resourceKey,capabilities(canDownload),shortcutDetails(targetId,targetMimeType,targetResourceKey))");
        if (pageToken != null && !pageToken.isEmpty()) url.appendQueryParameter("pageToken", pageToken);
        JSONObject json = get(url.build().toString(), resourceHeader(parent, resourceKey));
        List<Track> tracks = new ArrayList<>();
        JSONArray files = json.optJSONArray("files");
        if (files != null) for (int i = 0; i < files.length(); i++) {
            JSONObject file = files.optJSONObject(i);
            if (file == null) continue;
            Track track = Track.fromDrive(file);
            if (track != null) tracks.add(track);
        }
        return new Page(tracks, json.optString("nextPageToken"));
    }

    public String accountEmail() throws IOException {
        JSONObject json = get("https://www.googleapis.com/drive/v3/about?fields=user(emailAddress)", "");
        JSONObject user = json.optJSONObject("user");
        return user == null ? "" : user.optString("emailAddress");
    }

    @Override public CatalogScanner.Page fetch(CatalogScanner.Task task) throws IOException {
        String fileFields="id,name,mimeType,size,resourceKey,modifiedTime,driveId,capabilities(canDownload),shortcutDetails(targetId,targetMimeType,targetResourceKey)";
        Uri.Builder url;
        if(task.kind==CatalogScanner.DRIVES) {
            url=Uri.parse("https://www.googleapis.com/drive/v3/drives").buildUpon()
                    .appendQueryParameter("pageSize","100")
                    .appendQueryParameter("fields","nextPageToken,drives(id,name)");
        } else if(task.kind==CatalogScanner.TARGET) {
            if(!DriveRules.validId(task.node)) throw new IOException("Acceso directo inválido");
            url=Uri.parse("https://www.googleapis.com/drive/v3/files").buildUpon().appendPath(task.node)
                    .appendQueryParameter("supportsAllDrives","true").appendQueryParameter("fields",fileFields+",trashed");
        } else {
            url=Uri.parse("https://www.googleapis.com/drive/v3/files").buildUpon()
                    .appendQueryParameter("q",DriveRules.query(task.node,task.kind==CatalogScanner.SHARED,""))
                    .appendQueryParameter("pageSize","1000").appendQueryParameter("spaces","drive")
                    .appendQueryParameter("corpora",task.driveId.isEmpty()?"user":"drive")
                    .appendQueryParameter("supportsAllDrives","true").appendQueryParameter("includeItemsFromAllDrives","true")
                    .appendQueryParameter("fields","nextPageToken,incompleteSearch,files("+fileFields+")");
            if(!task.driveId.isEmpty())url.appendQueryParameter("driveId",task.driveId);
        }
        if(!task.page.isEmpty()) url.appendQueryParameter("pageToken",task.page);
        JSONObject json;
        try { json=get(url.build().toString(),resourceHeader(task.node,task.key)); }
        catch(HttpFailure failure) {
            if(failure.status==400&&!task.page.isEmpty()) throw new CatalogScanner.ExpiredPage();
            if(failure.status==403||failure.status==404) throw new CatalogScanner.MissingAccess(failure.getMessage());
            throw failure;
        }
        List<CatalogScanner.Entry> entries=new ArrayList<>();
        if(task.kind==CatalogScanner.TARGET) {
            if(!json.optBoolean("trashed")) entries.add(catalogEntry(json,task.key));
        } else {
            JSONArray files=json.optJSONArray(task.kind==CatalogScanner.DRIVES?"drives":"files");
            // Google may omit an empty repeated field, e.g. no Workspace shared drives.
            if(files!=null) for(int i=0;i<files.length();i++) {
                JSONObject file=files.optJSONObject(i);if(file==null) continue;
                if(task.kind==CatalogScanner.DRIVES) entries.add(new CatalogScanner.Entry(file.optString("id"),file.optString("name"),DriveRules.FOLDER,"",-1,0,true,"","",file.optString("id")));
                else entries.add(catalogEntry(file,""));
            }
        }
        return new CatalogScanner.Page(entries,json.optString("nextPageToken"),json.optBoolean("incompleteSearch"));
    }

    private static CatalogScanner.Entry catalogEntry(JSONObject file,String fallbackKey) {
        JSONObject caps=file.optJSONObject("capabilities"),shortcut=file.optJSONObject("shortcutDetails");
        long modified=0;
        try {modified=java.time.Instant.parse(file.optString("modifiedTime")).toEpochMilli();} catch(Exception ignored) { }
        String key=file.optString("resourceKey");if(key.isEmpty()) key=fallbackKey;
        return new CatalogScanner.Entry(file.optString("id"),file.optString("name","Sin nombre"),file.optString("mimeType"),key,
                file.optLong("size",-1),modified,caps==null||caps.optBoolean("canDownload",true),
                shortcut==null?"":shortcut.optString("targetId"),shortcut==null?"":shortcut.optString("targetResourceKey"),file.optString("driveId"));
    }

    public static String resourceHeader(String id, String key) {
        return DriveRules.validId(id) && DriveRules.validId(key) ? id + "/" + key : "";
    }

    private JSONObject get(String url, String resourceHeader) throws IOException {
        boolean refreshed=false;
        for (int attempt = 0; attempt < 5; attempt++) {
            if(Thread.currentThread().isInterrupted()) throw new IOException("Consulta pausada");
            String token = auth.accessToken();
            HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(25000);
            connection.setInstanceFollowRedirects(false);
            connection.setRequestProperty("Authorization", "Bearer " + token);
            connection.setRequestProperty("Accept", "application/json");
            if (!resourceHeader.isEmpty()) connection.setRequestProperty("X-Goog-Drive-Resource-Keys", resourceHeader);
            try {
                int status = connection.getResponseCode();
                if (status == 401 && !refreshed) { auth.invalidate(token);refreshed=true;continue; }
                if (status < 200 || status >= 300) {
                    String reason=errorReason(connection);
                    boolean rate=reason.equals("rateLimitExceeded")||reason.equals("userRateLimitExceeded");
                    if((status==429||status>=500||rate)&&attempt<4) {
                        connection.disconnect();
                        try {Thread.sleep(Math.min(8000,1000L<<attempt));}
                        catch(InterruptedException e) {Thread.currentThread().interrupt();throw new IOException("Consulta pausada",e);}
                        continue;
                    }
                    if(status==401) throw new AuthManager.NeedsAuthorization();
                    // Exhausted rate-limit responses pause the job instead of skipping a folder.
                    if(rate) throw new IOException("Drive limitó temporalmente las consultas. El avance se guardó; pulsa Continuar más tarde.");
                    throw error(status);
                }
                try (InputStream input = connection.getInputStream(); ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
                    byte[] buffer = new byte[8192];
                    int n;
                    while ((n = input.read(buffer)) != -1) {
                        if (Thread.currentThread().isInterrupted()) throw new IOException("Consulta cancelada");
                        if (bytes.size() + n > 8 * 1024 * 1024) throw new IOException("Respuesta de Drive demasiado grande");
                        bytes.write(buffer, 0, n);
                    }
                    return new JSONObject(bytes.toString(StandardCharsets.UTF_8.name()));
                } catch (org.json.JSONException e) { throw new IOException("Drive devolvió una respuesta no válida", e); }
            } finally { connection.disconnect(); }
        }
        throw new AuthManager.NeedsAuthorization();
    }

    private static String errorReason(HttpURLConnection connection) {
        try(InputStream input=connection.getErrorStream();ByteArrayOutputStream bytes=new ByteArrayOutputStream()) {
            if(input==null) return "";
            byte[] buffer=new byte[1024];int n;
            while(bytes.size()<16384&&(n=input.read(buffer))!=-1) bytes.write(buffer,0,n);
            JSONObject error=new JSONObject(bytes.toString(StandardCharsets.UTF_8.name())).optJSONObject("error");
            JSONArray details=error==null?null:error.optJSONArray("errors");
            return details==null||details.length()==0?"":details.getJSONObject(0).optString("reason");
        } catch(Exception ignored) {return "";}
    }

    private static final class HttpFailure extends IOException {
        final int status;
        HttpFailure(int status,String message) {super(message);this.status=status;}
    }
    private static IOException error(int status) {
        switch (status) {
            case 401: return new AuthManager.NeedsAuthorization();
            case 403: return new HttpFailure(status,"Drive no permite acceder a esta ubicación. Revisa el permiso de la carpeta.");
            case 404: return new HttpFailure(status,"La ubicación ya no existe o no está compartida con esta cuenta.");
            case 429: return new IOException("Drive ha limitado temporalmente las consultas. Espera un momento y actualiza.");
            default: return new HttpFailure(status,"No se pudo consultar Drive (" + status + "). Inténtalo nuevamente.");
        }
    }
}
