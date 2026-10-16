package cl.fernando.nubemusica;

import java.text.Normalizer;
import java.util.Locale;

public final class LibraryRules {
    private LibraryRules() { }
    public static String normalize(String text) {
        return Normalizer.normalize(text == null ? "" : text,Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "").toLowerCase(Locale.ROOT);
    }
    public static String like(String text) {
        return "%"+normalize(text.trim()).replace("\\","\\\\").replace("%","\\%").replace("_","\\_")+"%";
    }
    public static String order(int sort) {
        switch(sort) {
            case 1: return "sort_name DESC,file_id ASC";
            case 2: return "added DESC,sort_name ASC,file_id ASC";
            case 3: return "modified DESC,sort_name ASC,file_id ASC";
            case 4: return "size DESC,sort_name ASC,file_id ASC";
            default: return "sort_name ASC,file_id ASC";
        }
    }
    public static String origin(int source) {
        if (source == 3) return "Mi unidad y compartidos";
        return source == 2 ? "Compartidos" : "Mi unidad";
    }
}
