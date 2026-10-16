package cl.fernando.nubemusica;

import org.junit.Test;
import java.io.IOException;
import static org.junit.Assert.*;

public class MegaRulesTest {
    @Test public void savedUriContainsAccountAndHandleWithoutCredentials(){
        assertEquals("mega://file/Ab_cd-12?account=Ab_cd-12345",MegaRules.uri("Ab_cd-12","Ab_cd-12345"));
    }
    @Test public void rejectsMalformedNodeAndAccount(){
        assertFalse(MegaRules.validHandle("../../etc"));assertFalse(MegaRules.validHandle(""));assertFalse(MegaRules.validAccount(null));
        assertThrows(IllegalArgumentException.class,()->MegaRules.uri("abc?&xyz","Ab_cd-12345"));
        assertThrows(IllegalArgumentException.class,()->MegaRules.uri("Ab_cd-12","someone@email.com"));
    }
    @Test public void aDifferentConnectedAccountCannotReadASavedTrack(){
        assertThrows(IOException.class,()->MegaRules.requireAccount("Ab_cd-12345","OtherUser01"));
        assertThrows(IOException.class,()->MegaRules.requireAccount("Ab_cd-12345",""));
    }
    @Test public void correctAccountCanReadItsSavedTrack() throws Exception {MegaRules.requireAccount("Ab_cd-12345","Ab_cd-12345");}
    @Test public void seekingUsesTheRequestedPositionAndStopsAtEof() throws Exception {
        assertEquals(400,MegaRules.length(1000,600,-1));assertEquals(200,MegaRules.length(1000,600,200));
        assertEquals(400,MegaRules.length(1000,600,1000));assertEquals(0,MegaRules.length(1000,1000,-1));
    }
    @Test public void invalidOffsetsFailInsteadOfUnderflowing(){
        assertThrows(IOException.class,()->MegaRules.length(20,21,-1));assertThrows(IOException.class,()->MegaRules.length(20,-1,1));
        assertThrows(IOException.class,()->MegaRules.length(-1,0,-1));assertThrows(IOException.class,()->MegaRules.length(20,0,-2));
    }
    @Test public void largeDjSetsDoNotOverflow32Bits() throws Exception {
        assertEquals(100,MegaRules.length(9_000_000_100L,9_000_000_000L,-1));
        assertEquals(5,MegaRules.length(Long.MAX_VALUE,Long.MAX_VALUE-5,100));
    }
}
