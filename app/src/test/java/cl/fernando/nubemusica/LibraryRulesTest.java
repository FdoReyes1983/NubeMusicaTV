package cl.fernando.nubemusica;

import org.junit.Test;
import static org.junit.Assert.*;

public class LibraryRulesTest {
    @Test public void searchMatchesAccentsAndUppercaseNames(){
        assertEquals("musica clasica / cancion.mp3",LibraryRules.normalize("MÚSICA CLÁSICA / Canción.mp3"));
        assertEquals(LibraryRules.normalize("José"),LibraryRules.normalize("Jose\u0301"));
    }
    @Test public void percentUnderscoreAndBackslashAreLiteralSearchCharacters(){
        assertEquals("%100\\%\\_mix\\\\nuevo%",LibraryRules.like(" 100%_MIX\\Nuevo "));
    }
    @Test public void invalidSortFallsBackToStableTitleOrder(){
        assertEquals(LibraryRules.order(0),LibraryRules.order(Integer.MAX_VALUE));
        for(int i=0;i<5;i++)assertTrue(LibraryRules.order(i).endsWith("file_id ASC"));
    }
    @Test public void mergedOriginsRemainRecognizable(){
        assertEquals("Mi unidad y compartidos",LibraryRules.origin(3));
        assertEquals("Compartidos",LibraryRules.origin(2));
    }
}
