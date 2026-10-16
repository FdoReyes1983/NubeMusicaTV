package cl.fernando.nubemusica;

import android.net.Uri;
import androidx.annotation.Nullable;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DataSpec;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.datasource.HttpDataSource;
import androidx.media3.datasource.TransferListener;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Resolves a Drive ID to a fixed Google endpoint; credentials never enter saved URIs. */
@UnstableApi
public final class DriveDataSource implements DataSource {
    private final AuthManager auth;
    private final List<TransferListener> listeners = new ArrayList<>();
    private HttpDataSource upstream;
    private Uri originalUri;

    public DriveDataSource(AuthManager auth) { this.auth = auth; }
    @Override public void addTransferListener(TransferListener listener) {
        listeners.add(listener);
        if (upstream != null) upstream.addTransferListener(listener);
    }

    @Override public long open(DataSpec spec) throws IOException {
        originalUri = spec.uri;
        String id = spec.uri.getLastPathSegment();
        if (!"gdrive".equals(spec.uri.getScheme()) || !"file".equals(spec.uri.getHost()) || !DriveRules.validId(id)) {
            throw new IOException("Origen multimedia no permitido");
        }
        Uri media = Uri.parse("https://www.googleapis.com/drive/v3/files").buildUpon()
                .appendPath(id).appendQueryParameter("alt", "media")
                .appendQueryParameter("supportsAllDrives", "true").build();
        for (int attempt = 0; attempt < 2; attempt++) {
            String token = auth.accessToken();
            Map<String, String> headers = new HashMap<>();
            headers.put("Authorization", "Bearer " + token);
            String key = DriveRepository.resourceHeader(id, spec.uri.getQueryParameter("resourceKey"));
            if (!key.isEmpty()) headers.put("X-Goog-Drive-Resource-Keys", key);
            upstream = new DefaultHttpDataSource.Factory().setUserAgent("NubeMusica/0.1")
                    .setConnectTimeoutMs(15000).setReadTimeoutMs(30000).createDataSource();
            for (TransferListener listener : listeners) upstream.addTransferListener(listener);
            try {
                // Retains position and length so ExoPlayer can issue Range requests for seeking.
                return upstream.open(spec.buildUpon().setUri(media).setHttpRequestHeaders(headers).build());
            } catch (HttpDataSource.InvalidResponseCodeException e) {
                closeUpstream();
                if (e.responseCode == 401 && attempt == 0) { auth.invalidate(token); continue; }
                throw e;
            } catch (IOException e) { closeUpstream(); throw e; }
        }
        throw new AuthManager.NeedsAuthorization();
    }

    @Override public int read(byte[] buffer, int offset, int length) throws IOException {
        if (upstream == null) throw new IOException("Flujo cerrado");
        return upstream.read(buffer, offset, length);
    }
    @Nullable @Override public Uri getUri() { return originalUri; }
    @Override public Map<String, List<String>> getResponseHeaders() {
        return upstream == null ? Collections.emptyMap() : upstream.getResponseHeaders();
    }
    private void closeUpstream() throws IOException {
        HttpDataSource source = upstream;
        upstream = null;
        if (source != null) source.close();
    }
    @Override public void close() throws IOException { originalUri = null; closeUpstream(); }
}
