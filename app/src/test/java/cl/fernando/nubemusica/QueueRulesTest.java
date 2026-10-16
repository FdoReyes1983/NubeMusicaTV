package cl.fernando.nubemusica;

import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public final class QueueRulesTest {
    @Test public void playNextKeepsThePlayingOccurrenceAndPlacesSelectedAfterIt(){
        for(int size=2;size<=16;size++)for(int current=0;current<size;current++)for(int selected=0;selected<size;selected++){
            List<Integer> occurrences=new ArrayList<>();for(int i=0;i<size;i++)occurrences.add(i);
            int destination=QueueRules.nextSlot(size,selected,current);
            occurrences.add(destination,occurrences.remove(selected));
            if(selected==current)assertEquals(current,occurrences.indexOf(current));
            else assertEquals(occurrences.indexOf(current)+1,occurrences.indexOf(selected));
            assertEquals(size,new HashSet<>(occurrences).size());
        }
    }
    @Test public void nextRejectsStaleIndexesAndEmptyQueue(){
        assertEquals(-1,QueueRules.nextSlot(0,0,0));
        assertEquals(-1,QueueRules.nextSlot(5,-1,2));
        assertEquals(-1,QueueRules.nextSlot(5,5,2));
        assertEquals(-1,QueueRules.nextSlot(5,2,-1));
        assertEquals(-1,QueueRules.nextSlot(5,2,5));
        assertEquals(0,QueueRules.nextSlot(1,0,0));
    }
    @Test public void savedOrderAcceptsMovesWithRepeatedFiles(){
        assertTrue(QueueRules.sameMembers(Arrays.asList("drive:a","drive:b","drive:a"),Arrays.asList("drive:a","drive:a","drive:b")));
    }
    @Test public void savedOrderRejectsLostOrDuplicatedOccurrences(){
        assertFalse(QueueRules.sameMembers(Arrays.asList("a","a","b"),Arrays.asList("a","b","b")));
        assertFalse(QueueRules.sameMembers(Arrays.asList("a","b"),Collections.singletonList("a")));
        assertFalse(QueueRules.sameMembers(Arrays.asList("a","b"),Arrays.asList("a","c")));
    }
    @Test public void savedOrderAllowsEmptyPlaylist(){assertTrue(QueueRules.sameMembers(Collections.emptyList(),Collections.emptyList()));}
    @Test public void storedEqualizerGainFitsANarrowerDeviceRange(){
        assertEquals(-600,QueueRules.clampGain(-1500,-600,600));
        assertEquals(600,QueueRules.clampGain(1500,-600,600));
        assertEquals(250,QueueRules.clampGain(250,-600,600));
        assertEquals(0,QueueRules.clampGain(500,0,0));
    }
}
