package cl.fernando.nubemusica;

import nz.mega.sdk.MegaManager;
import nz.mega.sdk.MegaDataSource;
import nz.mega.sdk.MegaRepository;

import android.app.Application;

public final class NubeApp extends Application {
    private AuthManager auth;
    private PlaylistStore playlists;
    private LibraryStore library;
    private MegaManager mega;
    @Override public void onCreate() {
        super.onCreate();
        androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(
                androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES);
        auth = new AuthManager(this);
        playlists = new PlaylistStore(this);
        library = new LibraryStore(this);
        mega = new MegaManager(this);
    }
    public AuthManager auth() { return auth; }
    public PlaylistStore playlists() { return playlists; }
    public MegaManager mega() { return mega; }
    public LibraryStore library() { return library; }
}
