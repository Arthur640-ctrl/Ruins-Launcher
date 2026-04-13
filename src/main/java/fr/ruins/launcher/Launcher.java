package fr.ruins.launcher;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import fr.flowarg.flowupdater.FlowUpdater;
import fr.flowarg.flowupdater.utils.UpdaterOptions;
import fr.flowarg.flowupdater.versions.forge.ForgeVersionBuilder;
import fr.flowarg.flowupdater.versions.forge.ForgeVersion;
import fr.flowarg.flowupdater.versions.VanillaVersion;
import fr.flowarg.openlauncherlib.NoFramework;
import fr.litarvan.openauth.microsoft.MicrosoftAuthResult;
import fr.litarvan.openauth.microsoft.MicrosoftAuthenticationException;
import fr.litarvan.openauth.microsoft.MicrosoftAuthenticator;
import fr.ruins.launcher.utils.ApiClient;
import fr.ruins.launcher.utils.Session;
import fr.theshark34.openlauncherlib.minecraft.*;
import fr.theshark34.openlauncherlib.util.CrashReporter;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class Launcher {

    // Jeu
    private static GameInfos gameInfos = new GameInfos(
            "RuinsLauncher",
            new GameVersion("1.20.1", GameType.V1_13_HIGHER_FORGE),
            new GameTweak[]{GameTweak.FORGE}
    );
    public static Path path = gameInfos.getGameDir();
    public static File crashFile = new File(String.valueOf(path), "crashes");
    private static CrashReporter reporter = new CrashReporter(String.valueOf(crashFile), path);

    // Authentification
    private static AuthInfos authInfos;
    public static Session session;
    private static final File SESSION_FILE = new File(path.toFile(), "session.json");

    // Serveur
    public static String serverAddress = null;

    // Utils
    private static Gson gson = new Gson();

    // Prefs
    public static UserPreferences userPreferences = new UserPreferences();

    static {
        userPreferences.load(path);
    }

    // Dev mode
    public static boolean dev_mode = false;

    // ==========================================================================
    // AUTH
    // ==========================================================================

    // Func : auth() -> Connexion via Microsoft (non utilisé)
    public static void auth() throws MicrosoftAuthenticationException {

        MicrosoftAuthenticator authenticator = new MicrosoftAuthenticator();
        MicrosoftAuthResult result = authenticator.loginWithWebview();

        authInfos = new AuthInfos(
                result.getProfile().getName(),
                result.getAccessToken(),
                result.getProfile().getId()
        );
    }

    // Func : crack() -> Connexion crack à partir des informations de la session
    public static void crack() {
        authInfos = new AuthInfos(session.getUsername(), "0123456789", session.getUuid());
    }

    // SESSION :

    // Func : save_session() -> Sauvegarde la session dans un fichier json
    public static void save_session(Session session) {
        // 1. On récupère le chemin du fichier (en supposant que SESSION_FILE est un String ou un File)
        File file = new File(String.valueOf(SESSION_FILE));
        File parentDir = file.getParentFile();

        // 2. On recrée les dossiers parents s'ils ont été supprimés
        if (parentDir != null && !parentDir.exists()) {
            parentDir.mkdirs();
        }

        // 3. On écrit le fichier normalement
        try (FileWriter writer = new FileWriter(file)) {
            JsonObject json = new JsonObject();

            json.addProperty("uuid", session.getUuid());
            json.addProperty("username", session.getUsername());
            json.addProperty("session_token", session.getSessionToken());
            json.addProperty("email", session.getEmail());

            gson.toJson(json, writer);

        } catch (IOException e) {
            System.err.println("Erreur lors de la sauvegarde de la session : " + e.getMessage());
            e.printStackTrace();
        }
    }

    // Func : load_session() -> Charge la session depuis le fichier json
    public static Session load_session() {

        if (!SESSION_FILE.exists()) {
            return null;
        }

        try (FileReader reader = new FileReader(SESSION_FILE)) {

            JsonObject json = gson.fromJson(reader, JsonObject.class);

            return new Session(
                    json.get("uuid").getAsString(),
                    json.get("username").getAsString(),
                    json.get("session_token").getAsString(),
                    json.get("email").getAsString()
            );

        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    // UPDATE :

    // Func : update() -> Appelle le callback définis dans UpdateManager
    public static void update(UpdateManager.UpdateCallback callback, boolean download_shaders) throws Exception {
        VanillaVersion vanillaVersion = new VanillaVersion.VanillaVersionBuilder().withName("1.20.1").build();
        UpdaterOptions options = new UpdaterOptions.UpdaterOptionsBuilder().build();

        ForgeVersion version = new ForgeVersionBuilder()
                .withForgeVersion("1.20.1-47.4.10")
                .build();

        FlowUpdater updater = new FlowUpdater.FlowUpdaterBuilder().withVanillaVersion(vanillaVersion).withUpdaterOptions(options).withModLoaderVersion(version).build();
        updater.update(path);

        UpdateManager.run(path, callback, download_shaders);
    }

    // LAUNCH

    // Func : launch() -> Lance le jeu
    public static Process launch() throws Exception {

        if (authInfos == null) {
            throw new IllegalStateException("authInfos non défini — appeler auth() ou crack() avant launch()");
        }
        if (serverAddress == null) {
            throw new IllegalStateException("serverAddress non défini — update() doit être appelé avant launch()");
        }

        ApiClient.prejoin(session.getEmail(), session.getSessionToken());

        NoFramework noFramework = new NoFramework(path, authInfos, GameFolder.FLOW_UPDATER_1_19_SUP);

        List<String> args = new ArrayList<>();

        if (dev_mode == false) {
            args.add("--quickPlayMultiplayer");
            args.add(serverAddress);
            args.addAll(userPreferences.getJvmArgs());

        } else {
            args.add("--quickPlayMultiplayer");
            args.add("0.0.0.0:25565");
            args.addAll(userPreferences.getJvmArgs());
        }

        noFramework.setAdditionalArgs(args);

        return noFramework.launch("1.20.1", "47.4.10", NoFramework.ModLoader.FORGE);
    }

    // UTILS

    // Func : get_reporter() -> Récupère le reporter
    public static CrashReporter get_reporter() {
        return reporter;
    }

    // Func : set_session() -> Définis la session
    public static void set_session(Session new_session) {
        session = new_session;
    }

    // Func : get_session() -> Récupère la session
    public Session get_session() {
        return session;
    }
}