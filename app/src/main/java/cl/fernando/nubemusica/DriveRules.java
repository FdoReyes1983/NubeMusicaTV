package cl.fernando.nubemusica;

import java.util.Locale;
import java.util.Set;
import java.util.Arrays;
import java.util.HashSet;

/** Pure rules shared by the REST browser and the player. No Android dependency. */
public final class DriveRules {
    public static final String FOLDER = "application/vnd.google-apps.folder";
    public static final String SHORTCUT = "application/vnd.google-apps.shortcut";
    private static final Set<String> AUDIO = new HashSet<>(Arrays.asList("mp3", "m4a", "aac", "flac", "wav", "ogg", "opus"));

    private static final Set<String> VIDEO = new HashSet<>(Arrays.asList("mp4", "m4v", "mkv", "webm", "mov", "3gp", "3gpp", "ts", "mts", "m2ts", "mpg", "mpeg", "avi", "flv"));

    private DriveRules() {}

    public static boolean validId(String value) {
        return value != null && !value.isEmpty() && value.matches("[A-Za-z0-9_-]+");
    }

    public static String escapeQuery(String text) {
        return text.replace("\\", "\\\\").replace("'", "\\'");
    }

    public static String query(String folderId, boolean shared, String search) {
        if (!shared && !validId(folderId)) throw new IllegalArgumentException("Carpeta inválida");
        String base = "trashed = false and " + (shared ? "sharedWithMe = true" : "'" + folderId + "' in parents");
        if (search != null && !search.trim().isEmpty()) {
            base += " and name contains '" + escapeQuery(search.trim()) + "'";
        }
        return base;
    }

    public static String extension(String name) {
        if (name == null) return "";
        int index = name.lastIndexOf('.');
        return index < 0 ? "" : name.substring(index + 1).toLowerCase(Locale.ROOT);
    }

    public static boolean isAudio(String name, String mime) {
        return (mime != null && mime.startsWith("audio/")) || AUDIO.contains(extension(name));
    }

    public static boolean isVideo(String name, String mime) {
        return (mime != null && mime.startsWith("video/")) || VIDEO.contains(extension(name));
    }

    public static boolean isMedia(String name, String mime) { return isAudio(name,mime) || isVideo(name,mime); }

    public static String playableMime(String name, String reported) {
        if (reported != null && (reported.startsWith("audio/") || reported.startsWith("video/"))) return reported;
        switch (extension(name)) {
            case "mp4": case "m4v": return "video/mp4";
            case "mkv": return "video/x-matroska";
            case "webm": return "video/webm";
            case "mov": return "video/quicktime";
            case "3gp": case "3gpp": return "video/3gpp";
            case "ts": case "mts": case "m2ts": return "video/mp2t";
            case "mpg": case "mpeg": return "video/mpeg";
            case "avi": return "video/x-msvideo";
            case "flv": return "video/x-flv";
            case "mp3": return "audio/mpeg";
            case "m4a": return "audio/mp4";
            case "aac": return "audio/aac";
            case "flac": return "audio/flac";
            case "wav": return "audio/wav";
            case "ogg": case "opus": return "audio/ogg";
            default: return null;
        }
    }

    public static String title(String name) {
        if (name == null) return "Sin nombre";
        int dot = name.lastIndexOf('.');
        return dot > 0 && (AUDIO.contains(extension(name)) || VIDEO.contains(extension(name))) ? name.substring(0, dot) : name;
    }

    public static String time(long milliseconds) {
        long seconds = Math.max(0, milliseconds / 1000);
        return seconds >= 3600
                ? String.format(Locale.ROOT, "%d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60)
                : String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60);
    }
}
