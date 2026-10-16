package cl.fernando.nubemusica;

import android.net.Uri;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.Locale;

public final class Track {
    public final String id, name, mime, resourceKey, localUri;
    public final long size;
    public final boolean folder, downloadable;
    public final String originPath;
    public final int source;
    public final long addedAt, modifiedAt;

    public Track(String id, String name, String mime, String resourceKey, String localUri,
                 long size, boolean folder, boolean downloadable) {
        this(id,name,mime,resourceKey,localUri,size,folder,downloadable,"",0,0,0);
    }

    public Track(String id, String name, String mime, String resourceKey, String localUri,
                 long size, boolean folder, boolean downloadable, String originPath, int source, long addedAt, long modifiedAt) {
        this.id = id;
        this.name = name;
        this.mime = mime;
        this.resourceKey = resourceKey;
        this.localUri = localUri;
        this.size = size;
        this.folder = folder;
        this.downloadable = downloadable;
        this.originPath=originPath;this.source=source;this.addedAt=addedAt;this.modifiedAt=modifiedAt;
    }

    public static Track fromDrive(JSONObject json) {
        JSONObject shortcut = json.optJSONObject("shortcutDetails");
        String mime = json.optString("mimeType");
        String id = json.optString("id");
        String key = json.optString("resourceKey");
        if (DriveRules.SHORTCUT.equals(mime)) {
            if (shortcut == null) return null;
            id = shortcut.optString("targetId");
            mime = shortcut.optString("targetMimeType");
            key = shortcut.optString("targetResourceKey");
        }
        if (!DriveRules.validId(id)) return null;
        String name = json.optString("name", "Sin nombre");
        boolean folder = DriveRules.FOLDER.equals(mime);
        if (!folder && !DriveRules.isMedia(name, mime)) return null;
        JSONObject caps = json.optJSONObject("capabilities");
        return new Track(id, name, mime, key, "", json.optLong("size", -1), folder,
                caps == null || caps.optBoolean("canDownload", true));
    }

    public boolean isVideo() { return !folder && DriveRules.isVideo(name,mime); }

    public boolean isMega() { return localUri.startsWith("mega://file/"); }

    public Uri uri() {
        if (!localUri.isEmpty()) return Uri.parse(localUri);
        Uri.Builder builder = new Uri.Builder().scheme("gdrive").authority("file").appendPath(id);
        if (!resourceKey.isEmpty()) builder.appendQueryParameter("resourceKey", resourceKey);
        return builder.build();
    }

    public MediaItem mediaItem() {
        android.os.Bundle extras=new android.os.Bundle();
        try {extras.putString("nube_track",json().toString());}catch(JSONException ignored){}
        MediaItem.Builder builder = new MediaItem.Builder().setMediaId(uri().toString()).setUri(uri())
                .setMediaMetadata(new MediaMetadata.Builder()
                        .setTitle(DriveRules.title(name))
                        .setArtist(isMega() ? "MEGA" : localUri.isEmpty() ? "Google Drive" : "Archivo seleccionado")
                        .setExtras(extras)
                        .build());
        String playable = DriveRules.playableMime(name, mime);
        if (playable != null) builder.setMimeType(playable);
        return builder.build();
    }

    /** Controller items may omit localConfiguration; this app also uses the URI as mediaId. */
    public static Track fromMediaItem(MediaItem item) {
        try {
            android.os.Bundle extras=item.mediaMetadata.extras;
            if(extras!=null&&extras.containsKey("nube_track"))return fromSaved(new JSONObject(extras.getString("nube_track")));
            Uri uri=item.localConfiguration!=null?item.localConfiguration.uri:Uri.parse(item.mediaId);
            String name=item.mediaMetadata.title==null?"Audio":item.mediaMetadata.title.toString();
            String mime=item.localConfiguration==null?"":item.localConfiguration.mimeType;
            if("gdrive".equals(uri.getScheme())&&"file".equals(uri.getHost())&&DriveRules.validId(uri.getLastPathSegment())) {
                String key=uri.getQueryParameter("resourceKey");return new Track(uri.getLastPathSegment(),name,mime==null?"":mime,key==null?"":key,"",-1,false,true);
            }
            if("mega".equals(uri.getScheme())&&"file".equals(uri.getHost())&&MegaRules.validHandle(uri.getLastPathSegment())&&MegaRules.validAccount(uri.getQueryParameter("account")))return new Track(uri.getLastPathSegment(),name,mime==null?"":mime,"",uri.toString(),-1,false,true);
            if("content".equals(uri.getScheme()))return new Track(uri.toString(),name,mime==null?"":mime,"",uri.toString(),-1,false,true);
        }catch(Exception ignored){}
        return null;
    }

    public String subtitle() {
        if (folder) return "Carpeta";
        String ext = DriveRules.extension(name).toUpperCase(Locale.ROOT);
        if (!downloadable) return ext + " · Acceso restringido por el propietario";
        if (size >= 0) return ext + String.format(Locale.forLanguageTag("es-CL"), " · %.1f MB", size / 1048576.0);
        return ext.isEmpty() ? "Audio" : ext;
    }

    public JSONObject json() throws JSONException {
        return new JSONObject().put("id", id).put("name", name).put("mime", mime)
                .put("resourceKey", resourceKey).put("localUri", localUri)
                .put("size", size).put("folder", folder).put("downloadable", downloadable)
                .put("originPath",originPath).put("source",source).put("addedAt",addedAt).put("modifiedAt",modifiedAt);
    }

    public static Track fromSaved(JSONObject json) {
        return new Track(json.optString("id"), json.optString("name"), json.optString("mime"),
                json.optString("resourceKey"), json.optString("localUri"), json.optLong("size", -1),
                json.optBoolean("folder"), json.optBoolean("downloadable", true),json.optString("originPath"),json.optInt("source"),json.optLong("addedAt"),json.optLong("modifiedAt"));
    }
}
