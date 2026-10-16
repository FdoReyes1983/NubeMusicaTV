package cl.fernando.nubemusica;

import nz.mega.sdk.MegaManager;
import nz.mega.sdk.MegaDataSource;
import nz.mega.sdk.MegaRepository;

import android.net.Uri;
import androidx.annotation.Nullable;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DataSpec;
import androidx.media3.datasource.TransferListener;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@UnstableApi
final class CloudDataSource implements DataSource {
    private final NubeApp app;private DataSource source;
    private final List<TransferListener> listeners=new ArrayList<>();
    CloudDataSource(NubeApp app){this.app=app;}
    @Override public void addTransferListener(TransferListener listener){listeners.add(listener);if(source!=null)source.addTransferListener(listener);}
    @Override public long open(DataSpec spec) throws IOException {
        if("mega".equals(spec.uri.getScheme()))source=new MegaDataSource(app.mega());
        else if("gdrive".equals(spec.uri.getScheme()))source=new DriveDataSource(app.auth());
        else throw new IOException("Origen multimedia no permitido");
        for(TransferListener listener:listeners)source.addTransferListener(listener);
        return source.open(spec);
    }
    @Override public int read(byte[] b,int o,int l)throws IOException{if(source==null)throw new IOException("Flujo cerrado");return source.read(b,o,l);}
    @Nullable @Override public Uri getUri(){return source==null?null:source.getUri();}
    @Override public Map<String,List<String>> getResponseHeaders(){return source==null?java.util.Collections.emptyMap():source.getResponseHeaders();}
    @Override public void close()throws IOException{DataSource old=source;source=null;if(old!=null)old.close();}
}
