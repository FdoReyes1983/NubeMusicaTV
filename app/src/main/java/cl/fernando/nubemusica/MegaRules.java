package cl.fernando.nubemusica;

import java.io.IOException;

/** Pure validation shared by saved tracks, the catalog and the streaming source. */
public final class MegaRules {
    private MegaRules() {}
    public static boolean validHandle(String value) { return value != null && value.matches("[A-Za-z0-9_-]{8}"); }
    public static boolean validAccount(String value) { return value != null && value.matches("[A-Za-z0-9_-]{11}"); }
    public static String uri(String handle,String account) {
        if(!validHandle(handle)||!validAccount(account))throw new IllegalArgumentException("Referencia MEGA inválida");
        return "mega://file/"+handle+"?account="+account;
    }
    public static long length(long size,long position,long requested) throws IOException {
        if(size<0||position<0||position>size||requested< -1)throw new IOException("Posición fuera del archivo de MEGA");
        long available=size-position;
        return requested<0?available:Math.min(available,requested);
    }
    public static void requireAccount(String expected,String current) throws IOException {
        if(!validAccount(expected)||!expected.equals(current))throw new IOException("Conecta la cuenta de MEGA a la que pertenece esta canción.");
    }
}
