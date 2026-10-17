package cl.fernando.nubemusica;

import nz.mega.sdk.MegaManager;
import nz.mega.sdk.MegaDataSource;
import nz.mega.sdk.MegaRepository;

import android.content.ComponentName;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.IntentSenderRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.session.MediaController;
import androidx.media3.session.SessionToken;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.gms.auth.GoogleAuthUtil;
import com.google.android.gms.auth.UserRecoverableAuthException;
import com.google.android.gms.auth.api.identity.AuthorizationResult;
import com.google.android.gms.auth.api.identity.Identity;
import android.accounts.AccountManager;
import com.google.android.gms.common.AccountPicker;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.common.api.CommonStatusCodes;
import com.google.android.gms.common.api.Scope;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

@UnstableApi
public final class MainActivity extends AppCompatActivity {
    private static final int BG = Color.rgb(12,19,34), CARD = Color.rgb(24,35,53);
    private static final int INK = Color.rgb(238,244,248), MUTED = Color.rgb(168,183,201), ACCENT = Color.rgb(105,225,191);
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newFixedThreadPool(2);
    private final List<Track> tracks = new ArrayList<>();
    private final Deque<Location> history = new ArrayDeque<>();
    private final BrowserAdapter adapter = new BrowserAdapter();
    private AuthManager auth;
    private MegaManager mega;
    private final Runnable megaChanged=() -> {if(this.active){updateAccount();if("library".equals(this.mode))loadLibrary(false);}};
    private DriveRepository drive;
    private PlaylistStore playlists;
    private LibraryStore library;
    private Location location = new Location("root", "Mi Drive", "", false);
    private String mode = "drive", playlistName = "", nextPage = "";
    private int requestId;
    private boolean active, loading;
    private Future<?> request;
    private ListenableFuture<MediaController> controllerFuture;
    private MediaController controller;
    private TextView account, breadcrumb, empty, nowTitle, nowDetail;
    private MaterialButton connect, more, playAll, navigationOptions, editPlaylist;
    private ImageButton play;
    private EditText search;
    private ProgressBar spinner, miniProgress;
    private LinearLayout libraryPanel;
    private TextView libraryStatus;
    private MaterialButton scanLibrary,libraryOrigin,libraryOrder;
    private LibraryStore.Status catalogStatus=new LibraryStore.Status();
    private boolean libraryVideos;
    private int libraryLimit=200,librarySource=0,librarySort=0;
    private long lastLibraryPoll;
    private boolean libraryWasRunning;
    private final Runnable librarySearch=() -> {if("library".equals(mode)){libraryLimit=200;loadLibrary(false);}};
    private ActivityResultLauncher<String> notificationPermission;
    private ActivityResultLauncher<IntentSenderRequest> authorization;
    private ActivityResultLauncher<Intent> tvGoogleSignIn;
    private ActivityResultLauncher<Intent> tvGoogleRecovery;
    private ActivityResultLauncher<String[]> openAudio;
    private String pendingTvEmail = "";
    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            updatePlayer();
            if("library".equals(mode)&&!loading&&auth.isConnected()&&android.os.SystemClock.elapsedRealtime()-lastLibraryPoll>2500) {
                lastLibraryPoll=android.os.SystemClock.elapsedRealtime();
                if(LibraryScanService.anyRunning()||libraryWasRunning||"scanning".equals(catalogStatus.phase)) loadLibrary(false);
            }
            if (active) handler.postDelayed(this, 500);
        }
    };
    private final Player.Listener playerListener = new Player.Listener() {
        @Override public void onEvents(Player player, Player.Events events) { updatePlayer(); }
        @Override public void onPlayerError(PlaybackException error) {
            String text = "No se pudo reproducir. Revisa tu conexión, el permiso del archivo y su formato.";
            for (Throwable cause = error; cause != null; cause = cause.getCause()) {
                if (cause instanceof AuthManager.NeedsAuthorization) {
                    text = "Vuelve a conectar Google Drive para seguir escuchando."; break;
                }
            }
            if(controller!=null&&controller.getCurrentMediaItem()!=null&&controller.getCurrentMediaItem().mediaId.startsWith("mega://"))
                for(Throwable cause=error;cause!=null;cause=cause.getCause())if(cause instanceof java.io.IOException&&cause.getMessage()!=null){text=cause.getMessage();break;}
            toast(text);
        }
    };

    @Override public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        TvUi.configure(this);
        NubeApp app = (NubeApp) getApplication();
        auth = app.auth(); mega=app.mega(); drive = new DriveRepository(auth); playlists = app.playlists();library=app.library();
        notificationPermission=registerForActivityResult(new ActivityResultContracts.RequestPermission(),granted -> beginLibraryScan());
        authorization = registerForActivityResult(new ActivityResultContracts.StartIntentSenderForResult(), result -> {
            connect.setEnabled(true);
            Intent data = result.getData();
            if (data == null) { showAuthError(null); return; }
            if (data.hasExtra(ActivityResultContracts.StartIntentSenderForResult.EXTRA_SEND_INTENT_EXCEPTION)) {
                showAuthLaunchError(); return;
            }
            // A canceled activity result can still carry Google's error. Always parse it.
            try { authorized(Identity.getAuthorizationClient(this).getAuthorizationResultFromIntent(data)); }
            catch (Exception e) { showAuthError(e); }
        });

        tvGoogleSignIn = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            connect.setEnabled(true);
            Intent data = result.getData();

            if (result.getResultCode() != RESULT_OK || data == null) {
                showTvAuthError("No se seleccionó una cuenta de Google.");
                return;
            }

            String email = data.getStringExtra(AccountManager.KEY_ACCOUNT_NAME);

            if (email == null || email.isEmpty()) {
                showTvAuthError("La cuenta seleccionada no entregó un correo válido.");
                return;
            }

            pendingTvEmail = email;
            requestTvToken(email);
        });

        tvGoogleRecovery = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            connect.setEnabled(true);
            if (result.getResultCode() == RESULT_OK && !pendingTvEmail.isEmpty()) requestTvToken(pendingTvEmail);
            else showTvAuthError("Google no completó el permiso de lectura de Drive.");
        });

        openAudio = registerForActivityResult(new ActivityResultContracts.OpenMultipleDocuments(), this::openSelectedAudio);
        buildScreen();
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() {
                if ("drive".equals(mode) && !history.isEmpty()) { location = history.pop(); search.setText(""); load(false); }
                else if ("playlist".equals(mode)) showLists();
                else moveTaskToBack(true);
            }
        });
        updateAccount();
        if (auth.isConnected()) openLibrary(); else showDisconnected();
        if(TvUi.isTv(this)) connect.post(() -> { if(connect.getVisibility()==View.VISIBLE && connect.isEnabled()) connect.requestFocus(); });
    }

    private void buildScreen() {
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        LinearLayout root = vertical(); root.setBackgroundColor(BG);
        setContentView(root);
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.ime());
            int side=TvUi.isTv(this)?dp(32):dp(12);int top=TvUi.isTv(this)?dp(14):dp(4);int bottom=TvUi.isTv(this)?dp(14):dp(4);v.setPadding(side + bars.left, top + bars.top, side + bars.right, bottom + bars.bottom);
            return insets;
        });
        LinearLayout header = horizontal();
        TextView title = label("Nube Música", TvUi.isTv(this)?30:22, INK); title.setTypeface(null, Typeface.BOLD);
        header.addView(title, new LinearLayout.LayoutParams(0, dp(42), 1));
        MaterialButton menu = button("⋮", false); menu.setContentDescription("Opciones de la aplicación");
        header.addView(menu, new LinearLayout.LayoutParams(dp(48), dp(42))); menu.setOnClickListener(v -> appMenu());
        root.addView(header);
        account = label("Tu colección, siempre contigo", 12, MUTED); root.addView(account);
        connect = button("Conectar Google Drive", true); root.addView(connect, full(dp(50)));
        connect.setOnClickListener(v -> connectDrive());

        HorizontalScrollView tabsScroll = new HorizontalScrollView(this); tabsScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout tabs = horizontal();
        for (String tab : new String[]{"Música", "Videos", "Mi Drive", "Compartido", "Mis listas"}) {
            MaterialButton chip = button(tab, false); tabs.addView(chip, new LinearLayout.LayoutParams(-2, dp(42)));
            chip.setOnClickListener(v -> {
                handler.removeCallbacks(librarySearch);search.setText(""); history.clear();
                if("Música".equals(tab)||"Videos".equals(tab)){libraryVideos="Videos".equals(tab);openLibrary();}
                else if ("Mis listas".equals(tab)) showLists();
                else { mode = "drive"; location = new Location("root", tab, "", "Compartido".equals(tab)); load(false); }
            });
        }
        tabsScroll.addView(tabs); root.addView(tabsScroll);
        libraryPanel=vertical();libraryPanel.setVisibility(View.GONE);
        libraryStatus=label("Reúne la música y los videos de Mi unidad y compartidos",11,MUTED);
        libraryStatus.setMaxLines(1);libraryStatus.setEllipsize(android.text.TextUtils.TruncateAt.END);libraryStatus.setPadding(dp(3),dp(3),dp(3),dp(3));
        libraryStatus.setOnClickListener(v -> libraryDetails());libraryPanel.addView(libraryStatus);
        LinearLayout libraryButtons=horizontal();
        scanLibrary=button("Buscar archivos",true);libraryButtons.addView(scanLibrary,new LinearLayout.LayoutParams(0,dp(40),1.4f));
        scanLibrary.setOnClickListener(v -> scanButton());
        libraryOrigin=button("Todas",false);libraryButtons.addView(libraryOrigin,new LinearLayout.LayoutParams(0,dp(40),1));
        libraryOrigin.setOnClickListener(v -> chooseLibraryOrigin());
        libraryOrder=button("Ordenar",false);libraryButtons.addView(libraryOrder,new LinearLayout.LayoutParams(0,dp(40),1));
        libraryOrder.setOnClickListener(v -> chooseLibraryOrder());
        libraryPanel.addView(libraryButtons);root.addView(libraryPanel);
        LinearLayout navigation = horizontal();
        MaterialButton up = button("↑", false); up.setContentDescription("Subir una carpeta");
        navigation.addView(up, new LinearLayout.LayoutParams(dp(40), dp(40)));
        up.setOnClickListener(v -> {
            if ("playlist".equals(mode)) showLists();
            else if ("drive".equals(mode) && !history.isEmpty()) { location = history.pop(); search.setText(""); load(false); }
        });
        breadcrumb = label("Mi Drive", 15, INK); breadcrumb.setSingleLine(); breadcrumb.setEllipsize(android.text.TextUtils.TruncateAt.START);
        navigation.addView(breadcrumb, new LinearLayout.LayoutParams(0, dp(40), 1));
        breadcrumb.setOnClickListener(v -> {if("library".equals(mode))libraryOptions();});
        editPlaylist=button("Ordenar",false);editPlaylist.setContentDescription("Organizar canciones de esta lista");
        navigation.addView(editPlaylist,new LinearLayout.LayoutParams(-2,dp(40)));editPlaylist.setVisibility(View.GONE);
        editPlaylist.setOnClickListener(v -> startActivity(new Intent(this,QueueActivity.class).putExtra("playlist",playlistName)));
        navigationOptions = button("↻", false); navigationOptions.setContentDescription("Actualizar carpeta o lista");
        navigation.addView(navigationOptions, new LinearLayout.LayoutParams(dp(44), dp(40)));
        navigationOptions.setOnClickListener(v -> {if("library".equals(mode))libraryOptions();else refreshView();}); root.addView(navigation);

        search = new EditText(this); search.setSingleLine(); search.setTextColor(INK); search.setTextSize(14);
        search.setHintTextColor(MUTED); search.setHint("Buscar en esta carpeta"); search.setPadding(dp(16),0,dp(16),0);
        search.setBackground(round(CARD, 14)); search.setImeOptions(EditorInfo.IME_ACTION_SEARCH); TvUi.focus(search);
        LinearLayout actions = horizontal();
        actions.addView(search,new LinearLayout.LayoutParams(0,dp(44),1));
        search.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                if("library".equals(mode)) {handler.removeCallbacks(librarySearch);handler.postDelayed(librarySearch,350);}
                else if (!"drive".equals(mode)) refreshRows();
            }
            @Override public void afterTextChanged(android.text.Editable s) { }
        });
        search.setOnEditorActionListener((view, action, event) -> {
            if (action == EditorInfo.IME_ACTION_SEARCH) {
                if ("drive".equals(mode)) load(false);
                else if("library".equals(mode)) {handler.removeCallbacks(librarySearch);libraryLimit=200;loadLibrary(false);}
                else refreshRows();
                return true;
            }
            return false;
        });
        playAll = button("▶", false);playAll.setContentDescription("Reproducir canciones cargadas en esta lista"); actions.addView(playAll, new LinearLayout.LayoutParams(dp(44), dp(46)));
        playAll.setOnClickListener(v -> playTracks(null));
        more = button("Más", false);more.setContentDescription("Cargar más canciones"); actions.addView(more, new LinearLayout.LayoutParams(dp(58), dp(46)));
        more.setVisibility(View.GONE); more.setOnClickListener(v -> {if("library".equals(mode))loadLibrary(true);else load(true);});
        spinner = new ProgressBar(this); actions.addView(spinner, new LinearLayout.LayoutParams(dp(24), dp(24)));
        spinner.setVisibility(View.GONE); root.addView(actions);

        FrameLayout body = new FrameLayout(this);
        RecyclerView list = new RecyclerView(this); list.setLayoutManager(new LinearLayoutManager(this)); list.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
        list.setAdapter(adapter); body.addView(list, new FrameLayout.LayoutParams(-1,-1));
        empty = label("", 15, MUTED); empty.setGravity(Gravity.CENTER); empty.setPadding(dp(20),dp(16),dp(20),dp(16));
        body.addView(empty, new FrameLayout.LayoutParams(-1,-1)); root.addView(body, new LinearLayout.LayoutParams(-1, 0, 1));

        LinearLayout player = vertical(); player.setBackground(round(CARD, 16)); player.setPadding(dp(10),dp(2),dp(8),dp(4));
        LinearLayout mini=horizontal();
        LinearLayout song=vertical();song.setGravity(Gravity.CENTER_VERTICAL);song.setPadding(dp(2),0,dp(6),0);
        nowTitle=label("Elige tu próxima canción",14,INK);nowTitle.setTypeface(null,Typeface.BOLD);nowTitle.setSingleLine();nowTitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
        nowDetail=label("Toca aquí para abrir el reproductor",10,MUTED);nowDetail.setSingleLine();nowDetail.setEllipsize(android.text.TextUtils.TruncateAt.END);
        song.addView(nowTitle);song.addView(nowDetail);mini.addView(song,new LinearLayout.LayoutParams(0,dp(54),1));
        song.setContentDescription("Abrir reproductor completo");TvUi.focus(song);song.setOnClickListener(v -> openPlayer());player.setOnClickListener(v -> openPlayer());
        play=icon(android.R.drawable.ic_media_play,"Reproducir");play.setBackground(round(ACCENT,24));play.setImageTintList(ColorStateList.valueOf(BG));
        mini.addView(play,new LinearLayout.LayoutParams(dp(48),dp(48)));play.setOnClickListener(v -> togglePlay());
        ImageButton next=icon(android.R.drawable.ic_media_next,"Canción siguiente");mini.addView(next,new LinearLayout.LayoutParams(dp(44),dp(48)));
        next.setOnClickListener(v -> {if(controller!=null)controller.seekToNext();});
        MaterialButton queue=button("≡",false);queue.setTextSize(22);queue.setContentDescription("Abrir cola de reproducción");mini.addView(queue,new LinearLayout.LayoutParams(dp(40),dp(48)));queue.setOnClickListener(v -> showQueue());
        player.addView(mini);
        miniProgress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);miniProgress.setMax(1000);miniProgress.setProgressTintList(ColorStateList.valueOf(ACCENT));miniProgress.setProgressBackgroundTintList(ColorStateList.valueOf(BG));
        player.addView(miniProgress,full(dp(2)));root.addView(player,full(-2));
    }

    private void connectDrive() {
        if (TvUi.isTv(this)) {
            connectTvDrive();
            return;
        }
        connect.setEnabled(false);
        auth.authorize(this).addOnSuccessListener(this, result -> {
            if (result.hasResolution()) {
                if (result.getPendingIntent() == null) { connect.setEnabled(true); showAuthLaunchError(); return; }
                try {
                    authorization.launch(new IntentSenderRequest.Builder(result.getPendingIntent().getIntentSender()).build());
                } catch (RuntimeException error) { connect.setEnabled(true); showAuthLaunchError(); }
            } else { connect.setEnabled(true); authorized(result); }
        }).addOnFailureListener(this, error -> { connect.setEnabled(true); showAuthError(error); });
    }

    private void connectTvDrive() {
        connect.setEnabled(false);

        try {
            Intent picker = AccountPicker.newChooseAccountIntent(
                    null,
                    null,
                    new String[]{"com.google"},
                    true,
                    "Selecciona la cuenta de Google para Nube Música",
                    null,
                    null,
                    null);

            tvGoogleSignIn.launch(picker);
        } catch (RuntimeException e) {
            connect.setEnabled(true);
            showTvAuthError("No se pudo abrir el selector de cuenta de Google en Android TV.");
        }
    }

    private void requestTvToken(String email) {
        connect.setEnabled(false);
        io.execute(() -> {
            try {
                String accessToken = GoogleAuthUtil.getToken(getApplicationContext(), email, AuthManager.LEGACY_SCOPE);
                auth.acceptLegacyToken(accessToken, email);
                pendingTvEmail = "";
                runOnUiThread(() -> {
                    if (!isDestroyed()) {
                        connect.setEnabled(true);
                        finishDriveConnection();
                    }
                });
            } catch (UserRecoverableAuthException e) {
                Intent recovery = e.getIntent();
                runOnUiThread(() -> {
                    if (isDestroyed()) return;
                    connect.setEnabled(true);
                    if (recovery != null) tvGoogleRecovery.launch(recovery);
                    else showTvAuthError("Google necesita una confirmación adicional, pero no entregó una pantalla de autorización.");
                });
            } catch (Exception e) {
                String detail = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                runOnUiThread(() -> {
                    if (!isDestroyed()) {
                        connect.setEnabled(true);
                        showTvAuthError("No se pudo obtener el permiso de lectura de Drive: " + detail);
                    }
                });
            }
        });
    }

    private void showTvAuthError(String message) {
        new MaterialAlertDialogBuilder(this)
                .setTitle("No se pudo conectar Google Drive")
                .setMessage(message + "\n\nEsta versión usa el flujo alternativo para Android TV. Comprueba que la cuenta esté añadida a la caja y también como usuario de prueba en Google Cloud.")
                .setPositiveButton("Cerrar", null)
                .setNeutralButton("Reintentar", (dialog, which) -> connectTvDrive())
                .show();
    }

    private void authorized(AuthorizationResult result) {
        try { auth.acceptInteractive(result); }
        catch (Exception e) { showAuthError(e); return; }
        finishDriveConnection();
        long accountGeneration = auth.generation();
        io.execute(() -> {
            try {
                String email = drive.accountEmail();
                auth.rememberAccount(email, accountGeneration);
                runOnUiThread(() -> { if (!isDestroyed()) updateAccount(); });
            } catch (Exception ignored) { }
        });
    }

    private void finishDriveConnection() {
        updateAccount();
        mode = "drive";
        history.clear();
        location = new Location("root", "Mi Drive", "", false);
        search.setText("");
        load(false);
    }

    private void showAuthError(@Nullable Exception error) {
        String message;
        if (error instanceof ApiException) {
            int code = ((ApiException) error).getStatusCode();
            switch (code) {
                case CommonStatusCodes.DEVELOPER_ERROR:
                    message = "Google indica un error de configuración de la aplicación. Revisa el cliente OAuth de tipo Android, el nombre del paquete y la huella SHA-1 en Google Cloud."; break;
                case CommonStatusCodes.NETWORK_ERROR:
                    message = "Google indica un error de red. Comprueba que tienes conexión a Internet y vuelve a intentarlo."; break;
                case CommonStatusCodes.TIMEOUT:
                    message = "Google agotó el tiempo de espera. Vuelve a intentarlo con una conexión estable."; break;
                case CommonStatusCodes.CANCELED:
                    message = "Google devolvió una cancelación de la autorización. Esto no confirma que la hayas cancelado tú. Si la pantalla se cerró sola, revisa el registro de la app y los usuarios de prueba en Google Cloud."; break;
                default:
                    message = "Google no pudo completar la autorización. Revisa el registro de la app, la cuenta añadida como usuario de prueba y los Servicios de Google Play.";
            }
            // Show only the status code, never tokens, raw intents or account data.
            message += "\n\nCódigo de Google: " + code + " (" + CommonStatusCodes.getStatusCodeString(code) + ").";
        } else if (error instanceof AuthManager.NeedsAuthorization) {
            message = "No se recibió el permiso de lectura necesario para Drive. Vuelve a conectar y revisa los permisos en la pantalla de Google.";
        } else if (error == null) {
            message = "Google no devolvió una respuesta de autorización. La pantalla pudo haberse cerrado o el proceso pudo haberse interrumpido. No hay un código de error disponible.";
        } else {
            message = "No se pudo completar la autorización de Google. Vuelve a intentarlo y revisa la configuración de acceso.";
        }
        showAuthMessage(message + "\n\nSi solo instalaste la APK, falta registrar Nube Música en Google Cloud. Pulsa Configurar para ver los pasos.");
    }

    private void showAuthLaunchError() {
        showAuthMessage("No se pudo abrir la pantalla de autorización de Google. Comprueba que los Servicios de Google Play estén disponibles y actualizados, y vuelve a intentarlo.");
    }

    private void showAuthMessage(String message) {
        new MaterialAlertDialogBuilder(this).setTitle("No se pudo conectar Google Drive")
                .setMessage(message).setPositiveButton("Cerrar", null)
                .setNeutralButton("Configurar", (dialog, which) -> showDriveSetup()).show();
    }

    private void showDriveSetup() {
        String details = "Paquete: " + getPackageName()
                + "\nSHA-1: 5B:55:D0:A6:5D:D3:11:1D:0B:B7:15:FC:EA:68:9A:B4:60:AD:52:1A";
        new MaterialAlertDialogBuilder(this).setTitle("Activar Google Drive")
                .setMessage("Esta versión personal requiere registrar la app en tu proyecto de Google Cloud. Instalar la APK no completa ese registro.\n\n"
                        + "1. Crea un proyecto y habilita Google Drive API.\n"
                        + "2. Configura Google Auth Platform: nombre Nube Música, público Externo y tu correo como usuario de prueba.\n"
                        + "3. Añade el permiso de lectura: " + AuthManager.SCOPE + ".\n"
                        + "4. Crea un cliente de tipo Android con estos datos:\n\n" + details
                        + "\n\nDespués vuelve a la app y pulsa Conectar Google Drive. La guía entregada incluye los pasos completos.")
                .setPositiveButton("Abrir Google Cloud", (dialog, which) -> {
                    try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://console.cloud.google.com/"))); }
                    catch (android.content.ActivityNotFoundException e) { toast("Abre console.cloud.google.com en tu navegador."); }
                })
                .setNeutralButton("Copiar datos", (dialog, which) -> {
                    ClipboardManager clipboard = getSystemService(ClipboardManager.class);
                    if (clipboard != null) { clipboard.setPrimaryClip(ClipData.newPlainText("Nube Música", details)); toast("Paquete y huella copiados"); }
                })
                .setNegativeButton("Cerrar", null).show();
    }

    private void updateAccount() {
        account.setText(auth.isConnected() ? auth.accountLabel() + " · Solo lectura" : "Tu colección, siempre contigo");
        connect.setText(auth.isConnected() ? "Renovar conexión con Drive" : "Conectar Google Drive");
        account.setVisibility(auth.isConnected()||mega.hasAccount()?View.GONE:View.VISIBLE);
        connect.setVisibility(auth.isConnected()||mega.hasAccount()?View.GONE:View.VISIBLE);
    }

    private void openLibrary() {
        cancelLoad();mode="library";history.clear();libraryLimit=200;search.setText("");
        handler.removeCallbacks(librarySearch);search.setEnabled(true);search.setHint(libraryVideos?"Buscar videos o carpetas":"Buscar canciones o carpetas");
        tracks.clear();refreshRows();loadLibrary(false);
    }
    private void loadLibrary(boolean append) {
        if(!auth.isConnected()&&!mega.hasAccount()) {cancelLoad();showDisconnected();return;}
        if(append&&loading)return;
        if(append)libraryLimit+=200;
        cancelLoad();int serial=requestId;loading=true;spinner.setVisibility(View.VISIBLE);
        updateLibraryPanel();search.setEnabled(true);search.setHint(libraryVideos?"Buscar videos o carpetas":"Buscar canciones o carpetas");
        String email=auth.isConnected()?auth.accountLabel():"",megaAccount=mega.namespace(),filter=search.getText().toString();int source=librarySource,sort=librarySort,limit=libraryLimit;boolean videos=libraryVideos;
        request=io.submit(() -> {
            try {
                LibraryStore.Result result=library.readAccounts(email,megaAccount,filter,source,sort,limit,videos);
                runOnUiThread(() -> {
                    if(serial!=requestId||isDestroyed()||!"library".equals(mode)||!email.equals(auth.isConnected()?auth.accountLabel():"")||!megaAccount.equals(mega.namespace()))return;
                    loading=false;spinner.setVisibility(View.GONE);catalogStatus=result.status;
                    boolean running=LibraryScanService.anyRunning();
                    libraryWasRunning=running;
                    if(!running&&"scanning".equals(catalogStatus.phase))catalogStatus.phase="paused";
                    tracks.clear();tracks.addAll(result.tracks);refreshRows();
                    breadcrumb.setText((videos?"Videos · ":"Canciones · ")+result.matching);
                    more.setEnabled(true);more.setVisibility(result.matching>tracks.size()?View.VISIBLE:View.GONE);
                    more.setText("Más");more.setContentDescription("Cargar más canciones. "+tracks.size()+" de "+result.matching+" cargadas");
                    playAll.setEnabled(hasAudio());
                    scanLibrary.setEnabled(!LibraryScanService.isStopping(LibraryScanService.currentAccount()));
                    scanLibrary.setText(running?(LibraryScanService.isStopping(LibraryScanService.currentAccount())?"Pausando…":"Pausar"):
                            catalogStatus.resumable()?(catalogStatus.failed>0?"Reintentar":"Continuar"):
                            "idle".equals(catalogStatus.phase)?"Buscar archivos":"Actualizar");
                    if(running)libraryStatus.setText(catalogStatus.found+" archivos multimedia encontrados · "+catalogStatus.folders+" carpetas revisadas\n"+catalogStatus.pending+" ubicaciones pendientes");
                    else if(catalogStatus.failed>0)libraryStatus.setText(catalogStatus.total+" archivos multimedia · "+catalogStatus.failed+" ubicaciones sin acceso\nToca aquí para ver el detalle.");
                    else if(catalogStatus.resumable())libraryStatus.setText("Pausado · "+catalogStatus.found+" archivos multimedia encontrados\n"+(catalogStatus.message.isEmpty()?"El avance está guardado; pulsa Continuar.":catalogStatus.message));
                    else if("complete".equals(catalogStatus.phase))libraryStatus.setText(catalogStatus.total+" archivos multimedia · Escaneo completado\nActualizado: "+android.text.format.DateFormat.format("dd/MM HH:mm",catalogStatus.lastComplete));
                    else libraryStatus.setText("Reúne la música y los videos de Google Drive, incluidas las carpetas compartidas.");
                    empty.setText(running?"Buscando archivos…\n\nPuedes usar otras aplicaciones mientras continúa el escaneo.":
                            catalogStatus.total>0?"No hay archivos que coincidan con esta búsqueda o filtro.":
                            catalogStatus.resumable()?"El escaneo está pausado.\n\nPulsa Continuar para retomar la búsqueda.":
                            "Tu música y videos en un solo lugar\n\nPulsa Buscar archivos para reunir los archivos multimedia de Google Drive y compartidos.");
                    empty.setVisibility(tracks.isEmpty()?View.VISIBLE:View.GONE);
                });
            } catch(Exception error) {
                runOnUiThread(() -> {
                    if(serial!=requestId||isDestroyed())return;
                    loading=false;spinner.setVisibility(View.GONE);toast("No se pudo leer el catálogo del teléfono. Vuelve a abrir Biblioteca.");
                });
            }
        });
    }
    private void scanButton() {
        if(LibraryScanService.anyRunning()){startService(new Intent(this,LibraryScanService.class).setAction(LibraryScanService.PAUSE));return;}
        scanDrive();
    }
    private void scanDrive() {
        if(!auth.isConnected()){connectDrive();return;}
        if(LibraryScanService.isRunning(auth.accountLabel())) {
            startService(new Intent(this,LibraryScanService.class).setAction(LibraryScanService.PAUSE));
            scanLibrary.setText("Pausando…");scanLibrary.setEnabled(false);return;
        }
        if(android.os.Build.VERSION.SDK_INT>=33&&ContextCompat.checkSelfPermission(this,android.Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED
                &&!getPreferences(MODE_PRIVATE).getBoolean("scan_notice_asked",false)) {
            getPreferences(MODE_PRIVATE).edit().putBoolean("scan_notice_asked",true).apply();
            notificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS);
        } else beginLibraryScan();
    }
    private void beginLibraryScan() {
        if(!auth.isConnected())return;
        if(!auth.accountLabel().contains("@")){toast("Renueva la conexión con Drive para identificar tu cuenta y vuelve a pulsar Buscar archivos.");return;}
        try {
            ContextCompat.startForegroundService(this,new Intent(this,LibraryScanService.class));
            catalogStatus.phase="scanning";lastLibraryPoll=0;scanLibrary.setText("Preparando…");
            handler.postDelayed(() -> {if(!isDestroyed()&&"library".equals(mode))loadLibrary(false);},700);
        } catch(RuntimeException error) {toast("Android no pudo iniciar el escaneo. Mantén la app abierta y vuelve a intentarlo.");}
    }
    private void chooseLibraryOrigin() {
        String[] labels={"Todos los archivos de Drive","Mi unidad","Compartidos conmigo"};
        int selected=Math.max(0,Math.min(librarySource,2));
        new MaterialAlertDialogBuilder(this).setTitle("Mostrar archivos de…").setSingleChoiceItems(labels,selected,(dialog,which) -> {
            librarySource=which;libraryOrigin.setText(which==0?"Todos":which==1?"Mi Drive":"Compartidos");libraryLimit=200;dialog.dismiss();loadLibrary(false);
        }).setNegativeButton("Cerrar",null).show();
    }
    private void chooseLibraryOrder() {
        String[] labels={"Título · A–Z","Título · Z–A","Añadidas al catálogo","Modificadas en la nube","Tamaño · mayor primero"};
        new MaterialAlertDialogBuilder(this).setTitle("Ordenar archivos").setSingleChoiceItems(labels,librarySort,(dialog,which) -> {
            librarySort=which;libraryLimit=200;dialog.dismiss();loadLibrary(false);
        }).setNegativeButton("Cerrar",null).show();
    }
    private void updateLibraryPanel(){
        if(libraryPanel==null)return;
        boolean show="library".equals(mode)&&auth.isConnected()&&(catalogStatus.total==0
                ||"idle".equals(catalogStatus.phase)||LibraryScanService.anyRunning()||catalogStatus.resumable()||catalogStatus.failed>0);
        libraryPanel.setVisibility(show?View.VISIBLE:View.GONE);
    }
    private void libraryOptions(){
        boolean scanning=LibraryScanService.anyRunning();
        String scan=scanning?"Pausar búsqueda":catalogStatus.resumable()?"Continuar búsqueda":"Actualizar toda la biblioteca";
        String origin=librarySource==0?"Todos":librarySource==1?"Mi Drive":"Compartidos";
        new MaterialAlertDialogBuilder(this).setTitle("Biblioteca · "+origin)
                .setItems(new String[]{"Ordenar archivos","Elegir origen",scan,"Estado de la biblioteca"},(d,which)->{
                    if(which==0)chooseLibraryOrder();else if(which==1)chooseLibraryOrigin();else if(which==2)scanButton();else libraryDetails();
                }).setNegativeButton("Cerrar",null).show();
    }
    private void libraryDetails() {
        String email=auth.accountLabel();
        io.execute(() -> {
            String issues=library.issues(email);
            runOnUiThread(() -> {if(!isDestroyed())new MaterialAlertDialogBuilder(this).setTitle("Tu biblioteca de música")
                    .setMessage("El catálogo reúne archivos de Google Drive, incluidos los compartidos accesibles. Recorre subcarpetas y puedes actualizarlo desde Buscar archivos.\n\n"
                            +"Se guarda un registro por archivo y cuenta. Las copias que sean archivos distintos conservan su propia entrada.\n\n"
                            +"El escaneo consulta nombres y ubicaciones; el audio se transmite cuando lo reproduces. Puedes pausar y continuar el recorrido.\n\n"
                            +"El botón ▶ junto al buscador reproduce las canciones cargadas. Usa Más para ampliar la lista.\n\n"
                            +"Estado: "+libraryStatus.getText()+"\n\n"
                            +(issues.isEmpty()?"En el menú ⋮ junto al total de canciones puedes actualizar, ordenar y filtrar tu biblioteca.":"Ubicaciones pendientes (hasta 20):\n"+issues))
                    .setPositiveButton("Cerrar",null).show();});
        });
    }

    private void load(boolean append) {
        libraryPanel.setVisibility(View.GONE);more.setText("Más");
        if (!auth.isConnected()) { cancelLoad(); showDisconnected(); return; }
        if (append && (loading || nextPage.isEmpty())) return;
        cancelLoad(); int serial = requestId;
        loading = true; spinner.setVisibility(View.VISIBLE); more.setEnabled(false);
        breadcrumb.setText(location.title); search.setEnabled(true); search.setHint("Buscar en esta carpeta");
        if (!append) { tracks.clear(); refreshRows(); empty.setText("Buscando tu música…"); empty.setVisibility(View.VISIBLE); nextPage = ""; }
        Location folder = location; String query = search.getText().toString(); String page = append ? nextPage : "";
        request = io.submit(() -> {
            try {
                DriveRepository.Page result = drive.list(folder.id, folder.key, folder.shared, query, page);
                runOnUiThread(() -> {
                    if (serial != requestId || isDestroyed()) return;
                    Set<String> ids = new HashSet<>(); for (Track t : tracks) ids.add(t.id);
                    for (Track t : result.tracks) if (ids.add(t.id)) tracks.add(t);
                    Collections.sort(tracks, Comparator.comparing((Track t) -> !t.folder).thenComparing(t -> t.name.toLowerCase(Locale.ROOT)));
                    nextPage = result.next; loading = false; spinner.setVisibility(View.GONE); more.setEnabled(true);
                    more.setVisibility(nextPage.isEmpty() ? View.GONE : View.VISIBLE);
                    empty.setText(nextPage.isEmpty() ? "No hay carpetas ni archivos multimedia compatibles aquí." : "Esta página no contiene archivos multimedia. Pulsa Cargar más.");
                    empty.setVisibility(tracks.isEmpty() ? View.VISIBLE : View.GONE);
                    refreshRows(); playAll.setEnabled(hasAudio());
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    if (serial != requestId || isDestroyed()) return;
                    loading = false; spinner.setVisibility(View.GONE); more.setEnabled(true);
                    String message = e instanceof java.io.IOException ? e.getMessage() : "No se pudo leer esta carpeta.";
                    if (tracks.isEmpty()) { empty.setText(message + "\n\nPulsa ↻ para reintentar."); empty.setVisibility(View.VISIBLE); }
                    else toast(message);
                });
            }
        });
    }

    private void cancelLoad() {
        requestId++; if (request != null) request.cancel(true); loading = false;
        spinner.setVisibility(View.GONE); more.setVisibility(View.GONE);
    }
    private void showDisconnected() {
        breadcrumb.setText(location.title);
        tracks.clear(); refreshRows(); search.setEnabled(false); playAll.setEnabled(false);
        empty.setText("Tu música empieza aquí\n\nConecta Google Drive para abrir tus carpetas.\n\nTambién puedes abrir archivos multimedia desde el menú ⋮."); empty.setVisibility(View.VISIBLE);
    }
    private void refreshView() {
        if("library".equals(mode)) loadLibrary(false);
        else if ("drive".equals(mode)) load(false);
        else if ("lists".equals(mode)) showLists();
        else if ("playlist".equals(mode)) showPlaylist(playlistName);
        else refreshRows();
    }

    private void showLists() {
        cancelLoad(); mode = "lists"; playlistName = ""; search.setText(""); search.setEnabled(false);
        breadcrumb.setText("Mis listas"); tracks.clear();
        for (String name : playlists.names()) tracks.add(new Track(name, name, "", "", "", 0, true, true));
        empty.setVisibility(View.GONE); playAll.setEnabled(false); refreshRows();
    }
    private void showPlaylist(String name) {
        cancelLoad(); mode = "playlist"; playlistName = name; search.setText(""); search.setEnabled(true); search.setHint("Filtrar esta lista");
        breadcrumb.setText(name); tracks.clear(); tracks.addAll(playlists.tracks(name));
        empty.setText("Esta lista está vacía. Añade canciones desde el menú ⋮ de cada archivo.");
        empty.setVisibility(tracks.isEmpty() ? View.VISIBLE : View.GONE); playAll.setEnabled(hasAudio()); refreshRows();
    }
    private boolean hasAudio() { for (Track t : tracks) if (!t.folder && t.downloadable) return true; return false; }
    private List<Track> visibleTracks() {
        String filter = search.getText().toString().trim().toLowerCase(Locale.ROOT);
        if ("drive".equals(mode) || "library".equals(mode) || filter.isEmpty()) return new ArrayList<>(tracks);
        List<Track> filtered = new ArrayList<>();
        for (Track track : tracks) if (track.name.toLowerCase(Locale.ROOT).contains(filter)) filtered.add(track);
        return filtered;
    }

    private void select(Track track) {
        if ("lists".equals(mode)) { showPlaylist(track.name); return; }
        if (track.folder) {
            history.push(location); location = new Location(track.id, track.name, track.resourceKey, false);
            search.setText(""); load(false);
        } else playTracks(track);
    }

    private void refreshRows() {
        updateLibraryPanel();
        editPlaylist.setVisibility("playlist".equals(mode)?View.VISIBLE:View.GONE);
        navigationOptions.setText("library".equals(mode)?"⋮":"↻");
        navigationOptions.setContentDescription("library".equals(mode)?"Ordenar, filtrar y actualizar biblioteca":"Actualizar carpeta o lista");
        adapter.displayed=search==null?new ArrayList<>():visibleTracks();
        adapter.notifyDataSetChanged();
    }
    private void playTracks(@Nullable Track selected) {
        if (controller == null) { toast("El reproductor se está iniciando. Inténtalo en un momento."); return; }
        if (selected != null && !selected.downloadable) { toast("El propietario restringió la descarga o reproducción de este archivo."); return; }
        List<MediaItem> items = new ArrayList<>(); int index = 0;
        for (Track track : visibleTracks()) if (!track.folder && track.downloadable) {
            if (track.localUri.isEmpty() && !auth.isConnected()) { toast("Conecta Google Drive para reproducir esta lista."); return; }
            if(track.isMega()&&!mega.hasAccount()){toast("Conecta MEGA para reproducir esta lista.");return;}
            if (selected != null && track.uri().equals(selected.uri())) index = items.size();
            items.add(track.mediaItem());
        }
        if (items.isEmpty()) { toast("No hay archivos multimedia para reproducir."); return; }
        controller.setMediaItems(items, index, VideoProgress.position(this,items.get(index))); controller.prepare(); controller.play();
        Track chosen=Track.fromMediaItem(items.get(index));if(chosen!=null&&chosen.isVideo())startActivity(new Intent(this,VideoActivity.class));
    }

    private void togglePlay() {
        if (controller == null) return;
        if (controller.getPlayWhenReady() && controller.getPlaybackState()!=Player.STATE_ENDED) controller.pause();
        else if (controller.getMediaItemCount() > 0) {
            if (controller.getPlaybackState() == Player.STATE_IDLE) controller.prepare();
            if (controller.getPlaybackState() == Player.STATE_ENDED) controller.seekToDefaultPosition();
            controller.play();
        } else playTracks(null);
    }

    private void trackMenu(Track track) {
        if ("lists".equals(mode)) {
            if (PlaylistStore.FAVORITES.equals(track.name)) { showPlaylist(track.name); return; }
            new MaterialAlertDialogBuilder(this).setTitle(track.name).setItems(new String[]{"Abrir lista", "Eliminar lista"}, (d, which) -> {
                if (which == 0) showPlaylist(track.name);
                else new MaterialAlertDialogBuilder(this).setTitle("¿Eliminar esta lista?")
                        .setMessage("Los archivos de música se conservarán.").setNegativeButton("Cancelar", null)
                        .setPositiveButton("Eliminar", (dialog, button) -> { playlists.delete(track.name); showLists(); }).show();
            }).show(); return;
        }
        if (track.folder) { select(track); return; }
        List<String> choices = new ArrayList<>(); Collections.addAll(choices, "Añadir a Favoritos", "Añadir a una lista", "Añadir a la cola");
        if ("playlist".equals(mode)) choices.add("Quitar de esta lista");
        else if(!track.originPath.isEmpty()) choices.add("Ver ubicación del archivo");
        new MaterialAlertDialogBuilder(this).setTitle(track.name).setItems(choices.toArray(new String[0]), (dialog, which) -> {
            if (which == 0) toast(playlists.add(PlaylistStore.FAVORITES, track) ? "Guardado en Favoritos" : "Ya está en Favoritos");
            else if (which == 1) {
                List<String> names = playlists.names(); names.add("＋ Crear lista");
                new MaterialAlertDialogBuilder(this).setTitle("Guardar canción").setItems(names.toArray(new String[0]), (d, pos) -> {
                    if (pos == names.size() - 1) newPlaylist(track);
                    else toast(playlists.add(names.get(pos), track) ? "Canción añadida" : "La canción ya está en esa lista");
                }).show();
            } else if (which == 2 && controller != null) {
                if (!track.downloadable) { toast("El propietario restringió este archivo."); return; }
                controller.addMediaItem(track.mediaItem()); toast("Añadido a la cola");
            } else if (which == 3) {
                if("playlist".equals(mode)){playlists.remove(playlistName, track); showPlaylist(playlistName);}
                else new MaterialAlertDialogBuilder(this).setTitle(DriveRules.title(track.name))
                        .setMessage((track.isMega()?"MEGA · ":"Drive · ")+LibraryRules.origin(track.source)+"\n\n"+track.originPath+"\n\n"+track.subtitle())
                        .setPositiveButton("Cerrar",null).show();
            }
        }).show();
    }

    private void newPlaylist(@Nullable Track initial) {
        EditText input = new EditText(this); input.setSingleLine(); input.setHint("Nombre de la lista");
        new MaterialAlertDialogBuilder(this).setTitle("Nueva lista").setView(input).setNegativeButton("Cancelar", null)
                .setPositiveButton("Crear", (d, w) -> {
                    String name = input.getText().toString().trim();
                    if (!playlists.create(name)) { toast("Usa un nombre nuevo de 1 a 80 caracteres."); return; }
                    if (initial != null) playlists.add(name, initial);
                    if ("lists".equals(mode)) showLists();
                    toast("Lista creada");
                }).show();
    }

    private void appMenu() {
        String[] choices={"Abrir reproductor","Ver cola de reproducción","Ecualizador","Nueva lista","Abrir archivos multimedia del teléfono",
                auth.isConnected()?"Renovar conexión con Drive":"Conectar Google Drive","Desconectar Google Drive","Configurar acceso a Drive","Privacidad y versión","Licencias de software"};
        new MaterialAlertDialogBuilder(this).setTitle("Nube Música").setItems(choices,(d,which)->{
            switch(which){
                case 0:openPlayer();break;
                case 1:showQueue();break;
                case 2:startActivity(new Intent(this,EqualizerActivity.class));break;
                case 3:newPlaylist(null);break;
                case 4:openAudio.launch(new String[]{"audio/*","video/*","application/ogg","application/x-matroska"});break;
                case 5:connectDrive();break;
                case 6:
                    cancelLoad();stopService(new Intent(this,LibraryScanService.class));
                    if(controller!=null){controller.stop();controller.clearMediaItems();}
                    auth.disconnect();updateAccount();mode="drive";history.clear();
                    location=new Location("root","Mi Drive","",false);breadcrumb.setText("Mi Drive");showDisconnected();break;
                case 7:showDriveSetup();break;
                case 9:
                    try(java.io.InputStream in=getAssets().open("THIRD_PARTY_NOTICES.txt");java.util.Scanner reader=new java.util.Scanner(in,"UTF-8")){
                        reader.useDelimiter("\\A");new MaterialAlertDialogBuilder(this).setTitle("Licencias").setMessage(reader.hasNext()?reader.next():"").setPositiveButton("Cerrar",null).show();
                    }catch(java.io.IOException e){toast("Las licencias también están incluidas en el proyecto.");}break;
                default:
                    new MaterialAlertDialogBuilder(this).setTitle("Privacidad · v0.5.0")
                            .setMessage("Cuenta: "+auth.accountLabel()+"\n\nGoogle solicita acceso de lectura a los archivos de tu Drive. La app muestra carpetas y archivos multimedia y no modifica, sube ni elimina archivos.\n\nEl audio y el video viajan directamente desde Google Drive al dispositivo. El catálogo, las listas, la cola y los ajustes de sonido se guardan en este dispositivo; no hay anuncios ni analítica.\n\nEl reproductor puede mostrar etiquetas y carátulas que encuentre al reproducir. La biblioteca se busca por nombre y carpeta.\n\nDesconectar detiene la reproducción y el escaneo y cierra el acceso local. Puedes revocar el permiso completo en tu cuenta de Google.\n\nEl ecualizador depende de los efectos de audio disponibles en el dispositivo. Esta variante para Android TV usa solamente Google Drive. Descargas permanentes, Chromecast y Android Auto quedan para futuras versiones.")
                            .setPositiveButton("Cerrar",null).setNeutralButton("Permisos de Google",(dialog,w)->startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse("https://myaccount.google.com/connections")))).show();
            }
        }).show();
    }

    private void openPlayer(){startActivity(new Intent(this,PlayerActivity.class));}
    private void showQueue(){startActivity(new Intent(this,QueueActivity.class));}

    private void openSelectedAudio(List<Uri> uris) {
        if (uris == null || uris.isEmpty()) return;
        cancelLoad(); mode = "local"; search.setText(""); search.setEnabled(true); search.setHint("Filtrar archivos multimedia seleccionados");
        breadcrumb.setText("Archivos seleccionados"); tracks.clear();
        for (Uri uri : uris) {
            try { getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION); }
            catch (SecurityException ignored) { }
            String name = "Audio"; long size = -1;
            try (Cursor cursor = getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE}, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    name = cursor.getString(0); if (!cursor.isNull(1)) size = cursor.getLong(1);
                }
            } catch (Exception ignored) { }
            String mime = getContentResolver().getType(uri);
            tracks.add(new Track(uri.toString(), name == null ? "Audio" : name, mime == null ? "" : mime, "", uri.toString(), size, false, true));
        }
        empty.setVisibility(View.GONE); playAll.setEnabled(true); refreshRows();
    }

    @Override protected void onNewIntent(Intent intent){super.onNewIntent(intent);setIntent(intent);if(intent.getBooleanExtra("library",false))openLibrary();}

    @Override protected void onStart() {
        super.onStart(); active = true;mega.addListener(megaChanged);updateAccount();
        if("library".equals(mode))loadLibrary(false);
        if("playlist".equals(mode))showPlaylist(playlistName);
        else if("lists".equals(mode))showLists();
        final ListenableFuture<MediaController> future = new MediaController.Builder(this,
                new SessionToken(this, new ComponentName(this, PlaybackService.class))).buildAsync();
        controllerFuture = future;
        future.addListener(() -> {
            if (!active || controllerFuture != future) return;
            try { controller = future.get(); controller.addListener(playerListener); updatePlayer(); }
            catch (Exception e) { toast("No se pudo iniciar el reproductor."); }
        }, ContextCompat.getMainExecutor(this));
        handler.post(ticker);
    }
    @Override protected void onStop() {
        active = false; mega.removeListener(megaChanged);handler.removeCallbacks(ticker);
        if (controller != null) { controller.removeListener(playerListener); controller = null; }
        if (controllerFuture != null) { MediaController.releaseFuture(controllerFuture); controllerFuture = null; }
        super.onStop();
    }
    @Override protected void onDestroy() {
        requestId++; if (request != null) request.cancel(true); io.shutdownNow(); handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    private void updatePlayer() {
        if (controller == null || nowTitle == null) return;
        MediaItem item = controller.getCurrentMediaItem();
        nowTitle.setText(item == null ? "Elige tu próxima canción" : item.mediaMetadata.title);
        boolean buffering = controller.getPlaybackState() == Player.STATE_BUFFERING;
        long duration=controller.getDuration(),position=controller.getCurrentPosition();
        nowDetail.setText(item==null?"Toca aquí para abrir el reproductor":buffering?"Cargando audio…":DriveRules.time(position)+" / "+DriveRules.time(Math.max(0,duration))+" · Abrir reproductor");
        boolean playing=controller.getPlayWhenReady()&&controller.getPlaybackState()!=Player.STATE_ENDED;
        play.setImageResource(playing?android.R.drawable.ic_media_pause:android.R.drawable.ic_media_play);
        play.setContentDescription(playing?"Pausar":"Reproducir");
        miniProgress.setProgress(duration>0?(int)Math.min(1000,position*1000/duration):0);
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private LinearLayout vertical() { LinearLayout layout = new LinearLayout(this); layout.setOrientation(LinearLayout.VERTICAL); return layout; }
    private LinearLayout horizontal() { LinearLayout layout = new LinearLayout(this); layout.setOrientation(LinearLayout.HORIZONTAL); layout.setGravity(Gravity.CENTER_VERTICAL); return layout; }
    private LinearLayout.LayoutParams full(int height) { return new LinearLayout.LayoutParams(-1, height); }
    private TextView label(String text, int sp, int color) {
        TextView view = new TextView(this); view.setText(text); view.setTextSize(sp); view.setTextColor(color); view.setGravity(Gravity.CENTER_VERTICAL); return view;
    }
    private GradientDrawable round(int color, int radius) { GradientDrawable shape = new GradientDrawable(); shape.setColor(color); shape.setCornerRadius(dp(radius)); return shape; }
    private MaterialButton button(String text, boolean primary) {
        MaterialButton view = new MaterialButton(this); view.setText(text); view.setTextSize(13); view.setAllCaps(false);
        view.setMinWidth(0); view.setMinimumWidth(0); view.setMinimumHeight(0); view.setCornerRadius(dp(14));
        view.setPadding(dp(10),0,dp(10),0); view.setTextColor(primary ? BG : ACCENT);
        view.setBackgroundTintList(ColorStateList.valueOf(primary ? ACCENT : Color.TRANSPARENT)); TvUi.focus(view); return view;
    }
    private ImageButton icon(int resource, String description) {
        ImageButton view = new ImageButton(this); view.setImageResource(resource); view.setImageTintList(ColorStateList.valueOf(INK));
        view.setBackgroundColor(Color.TRANSPARENT); view.setContentDescription(description); TvUi.focus(view); return view;
    }
    private void toast(String text) { if (!isDestroyed()) Toast.makeText(this, text, Toast.LENGTH_LONG).show(); }

    private static final class Location {
        final String id, title, key; final boolean shared;
        Location(String id, String title, String key, boolean shared) { this.id = id; this.title = title; this.key = key; this.shared = shared; }
    }

    private final class BrowserAdapter extends RecyclerView.Adapter<RowHolder> {
        private List<Track> displayed=new ArrayList<>();
        @NonNull @Override public RowHolder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
            LinearLayout row = horizontal(); row.setPadding(0,dp(6),0,dp(6));
            row.setLayoutParams(new RecyclerView.LayoutParams(-1, dp(TvUi.isTv(MainActivity.this)?82:66))); TvUi.row(row);
            TextView symbol = label("♪",24,ACCENT); symbol.setGravity(Gravity.CENTER); symbol.setBackground(round(CARD,14));
            row.addView(symbol, new LinearLayout.LayoutParams(dp(42),dp(42)));
            LinearLayout text = vertical(); text.setPadding(dp(12),0,0,0); text.setGravity(Gravity.CENTER_VERTICAL);
            TextView name = label("",15,INK); name.setSingleLine(); name.setEllipsize(android.text.TextUtils.TruncateAt.END);
            TextView detail = label("",11,MUTED); detail.setSingleLine(); detail.setEllipsize(android.text.TextUtils.TruncateAt.END);
            text.addView(name); text.addView(detail); row.addView(text,new LinearLayout.LayoutParams(0,-1,1));
            MaterialButton options = button("⋮",false); row.addView(options,new LinearLayout.LayoutParams(dp(44),dp(48)));
            return new RowHolder(row,symbol,name,detail,options);
        }
        @Override public void onBindViewHolder(@NonNull RowHolder holder, int position) {
            Track track = displayed.get(position);
            holder.symbol.setText("lists".equals(mode) ? "≡" : track.folder ? "▱" : track.isVideo()?"▶":"♪");
            holder.title.setText(track.folder ? track.name : DriveRules.title(track.name));
            holder.detail.setText("lists".equals(mode) ? playlists.tracks(track.name).size() + " canciones" :
                    "library".equals(mode)?(track.isMega()?"MEGA · ":"Drive · ")+track.originPath:track.subtitle());
            holder.detail.setEllipsize("library".equals(mode)?android.text.TextUtils.TruncateAt.START:android.text.TextUtils.TruncateAt.END);
            holder.itemView.setOnClickListener(v -> select(track));
            holder.options.setContentDescription("Opciones de " + track.name); holder.options.setOnClickListener(v -> trackMenu(track));
            holder.itemView.setOnLongClickListener(v -> { trackMenu(track); return true; });
        }
        @Override public int getItemCount() { return displayed.size(); }
    }
    private static final class RowHolder extends RecyclerView.ViewHolder {
        final TextView symbol,title,detail; final MaterialButton options;
        RowHolder(View view,TextView symbol,TextView title,TextView detail,MaterialButton options) {
            super(view); this.symbol=symbol; this.title=title; this.detail=detail; this.options=options;
        }
    }
}
