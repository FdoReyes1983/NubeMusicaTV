package nz.mega.sdk;

import cl.fernando.nubemusica.*;

import android.net.Uri;
import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.BaseDataSource;
import androidx.media3.datasource.DataSpec;
import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import nz.mega.sdk.*;

/** Bounded, decrypted ranges delivered by the official SDK. No audio is stored on disk. */
@UnstableApi
public final class MegaDataSource extends BaseDataSource {
    private static final int CHUNK=1024*1024;
    private final MegaManager manager;
    private volatile boolean closed=true;
    private volatile Chunk pending;
    private MegaApi sdk;
    private Uri uri;
    private String account;
    private long handle,position,remaining,generation;
    private byte[] data;
    private int cursor;
    private boolean started;
    public MegaDataSource(MegaManager manager){super(true);this.manager=manager;}
    @Override public long open(DataSpec spec) throws IOException {
        transferInitializing(spec);
        String id=spec.uri.getLastPathSegment();account=spec.uri.getQueryParameter("account");
        if(!"mega".equals(spec.uri.getScheme())||!"file".equals(spec.uri.getHost())||!MegaRules.validHandle(id))throw new IOException("Referencia de MEGA inválida");
        sdk=manager.awaitReady();MegaRules.requireAccount(account,manager.account());generation=manager.generation();
        handle=MegaApi.base64ToHandle(id);MegaNode node=sdk.getNodeByHandle(handle);
        if(node==null)throw new IOException("El archivo de MEGA ya no está disponible.");
        try{if(!node.isFile())throw new IOException("El elemento no es un archivo reproducible.");remaining=MegaRules.length(node.getSize(),spec.position,spec.length);}
        finally{node.delete();}
        uri=spec.uri;position=spec.position;cursor=0;data=null;closed=false;started=true;transferStarted(spec);return remaining;
    }
    @Override public int read(byte[] buffer,int offset,int length) throws IOException {
        if(length==0)return 0;
        if(closed)throw new IOException("Flujo cerrado");
        if(remaining==0)return C.RESULT_END_OF_INPUT;
        if(generation!=manager.generation())throw new IOException("La conexión de MEGA cambió. Vuelve a seleccionar la canción.");
        MegaRules.requireAccount(account,manager.account());
        if(data==null||cursor>=data.length){
            MegaNode node=sdk.getNodeByHandle(handle);if(node==null)throw new IOException("El archivo de MEGA ya no está disponible.");
            Chunk chunk=new Chunk(node,(int)Math.min(CHUNK,remaining));pending=chunk;manager.transfers.add(chunk);
            sdk.startStreaming(node,position,chunk.bytes.length,chunk);
            if(closed)chunk.cancel();
            try{if(!chunk.done.await(60,TimeUnit.SECONDS)){chunk.cancel();throw new IOException("MEGA tardó demasiado en enviar el archivo. Inténtalo de nuevo.");}}
            catch(InterruptedException e){chunk.cancel();Thread.currentThread().interrupt();throw new IOException("Reproducción cancelada",e);}
            finally{pending=null;}
            if(closed)throw new IOException("Flujo cerrado");
            if(chunk.failure!=null)throw chunk.failure;
            if(chunk.written!=chunk.bytes.length)throw new IOException("MEGA envió un fragmento incompleto. Intenta reproducir nuevamente.");
            data=chunk.bytes;cursor=0;
        }
        byte[] available=data;if(closed||available==null)throw new IOException("Flujo cerrado");
        int count=Math.min(length,available.length-cursor);System.arraycopy(available,cursor,buffer,offset,count);
        cursor+=count;position+=count;remaining-=count;bytesTransferred(count);return count;
    }
    @Nullable @Override public Uri getUri(){return uri;}
    @Override public void close(){closed=true;Chunk chunk=pending;if(chunk!=null)chunk.cancel();data=null;uri=null;if(started){started=false;transferEnded();}}
    private final class Chunk extends MegaTransferListener {
        final byte[] bytes;final MegaNode node;final CountDownLatch done=new CountDownLatch(1);
        volatile int tag=-1,written;volatile boolean canceled;volatile IOException failure;
        Chunk(MegaNode node,int size){this.node=node;bytes=new byte[size];}
        void cancel(){canceled=true;int current=tag;if(current>=0)sdk.cancelTransferByTag(current);done.countDown();}
        @Override public void onTransferStart(MegaApi a,MegaTransfer t){tag=t.getTag();if(canceled||closed)sdk.cancelTransferByTag(tag);}
        @Override public boolean onTransferData(MegaApi a,MegaTransfer t,byte[] buffer){
            if(canceled||closed||generation!=manager.generation())return false;
            if(buffer==null||buffer.length>bytes.length-written){failure=new IOException("Fragmento multimedia inválido");return false;}
            System.arraycopy(buffer,0,bytes,written,buffer.length);written+=buffer.length;return true;
        }
        @Override public void onTransferTemporaryError(MegaApi a,MegaTransfer t,MegaError e){
            if(e.getErrorCode()==MegaError.API_EOVERQUOTA||e.getErrorCode()==MegaError.API_ESID){failure=new IOException(MegaManager.error(e.getErrorCode()));cancel();}
        }
        @Override public void onTransferFinish(MegaApi a,MegaTransfer t,MegaError e){
            if(e.getErrorCode()!=MegaError.API_OK&&failure==null)failure=new IOException(MegaManager.error(e.getErrorCode()));
            done.countDown();
            // Keep the SWIG director strongly reachable until this native callback returns.
            new android.os.Handler(android.os.Looper.getMainLooper()).post(()->{manager.transfers.remove(this);node.delete();});
        }
    }
}
