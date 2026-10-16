package cl.fernando.nubemusica;
import org.junit.Test;
import static org.junit.Assert.*;
public final class PlaybackRulesTest {
    @Test public void fiveSecondsForwardAndBack(){assertEquals(17000,PlaybackRules.seek(12000,60000,5000));assertEquals(7000,PlaybackRules.seek(12000,60000,-5000));}
    @Test public void cannotCrossStartOrEnd(){assertEquals(0,PlaybackRules.seek(2000,60000,-5000));assertEquals(60000,PlaybackRules.seek(58000,60000,5000));}
    @Test public void unknownDurationStillAllowsRelativeSeek(){assertEquals(15000,PlaybackRules.seek(10000,-9223372036854775807L,5000));}
    @Test public void overflowCannotWrapPosition(){assertEquals(Long.MAX_VALUE,PlaybackRules.seek(Long.MAX_VALUE-1,0,5000));}
    @Test public void finishedVideosRestartAndOthersResume(){assertEquals(0,PlaybackRules.resume(59000,60000));assertEquals(12000,PlaybackRules.resume(12000,60000));assertEquals(0,PlaybackRules.resume(-1,0));}
}
