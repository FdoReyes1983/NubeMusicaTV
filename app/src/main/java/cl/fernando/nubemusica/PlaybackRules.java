package cl.fernando.nubemusica;

/** Pure position rules shared by the full players and PiP actions. */
public final class PlaybackRules {
    private PlaybackRules() {}
    public static long seek(long position,long duration,long delta) {
        long start=Math.max(0,position);
        long next=delta>0&&start>Long.MAX_VALUE-delta?Long.MAX_VALUE:start+delta;
        return Math.max(0,duration>0?Math.min(duration,next):next);
    }
    public static long resume(long position,long duration) {
        if(position<0)return 0;
        if(duration>0&&position>=Math.max(0,duration-3000))return 0;
        return position;
    }
}
