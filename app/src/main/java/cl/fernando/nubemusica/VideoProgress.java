package cl.fernando.nubemusica;

import android.content.Context;
import androidx.media3.common.MediaItem;

/** Resume points are private to the device and do not modify cloud files. */
public final class VideoProgress {
    private VideoProgress() {}
    public static long position(Context context,MediaItem item) {
        Track t=item==null?null:Track.fromMediaItem(item);
        return t!=null&&t.isVideo()?Math.max(0,context.getSharedPreferences("video_progress",Context.MODE_PRIVATE).getLong(item.mediaId,0)):0;
    }
    public static void save(Context context,MediaItem item,long position,long duration) {
        Track t=item==null?null:Track.fromMediaItem(item);
        if(t==null||!t.isVideo())return;
        context.getSharedPreferences("video_progress",Context.MODE_PRIVATE).edit()
                .putLong(item.mediaId,PlaybackRules.resume(position,duration)).apply();
    }
}
