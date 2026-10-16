package cl.fernando.nubemusica;
import java.util.*;

/** Positions are occurrence-based: a queue may contain the same song more than once. */
public final class QueueRules {
    private QueueRules(){}
    public static int nextSlot(int size,int from,int current){
        if(size<=0||from<0||from>=size||current<0||current>=size)return -1;
        if(from==current)return from;
        return Math.min(size-1,from<current?current:current+1);
    }
    public static boolean sameMembers(List<String> before,List<String> after){
        if(before.size()!=after.size())return false;
        Map<String,Integer> counts=new HashMap<>();for(String s:before)counts.put(s,counts.getOrDefault(s,0)+1);
        for(String s:after){int count=counts.getOrDefault(s,0);if(count==0)return false;counts.put(s,count-1);}return true;
    }
    public static int clampGain(int gain,int min,int max){return Math.max(min,Math.min(max,gain));}
}
