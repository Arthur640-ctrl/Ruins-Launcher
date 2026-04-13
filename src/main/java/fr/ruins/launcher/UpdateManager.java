package fr.ruins.launcher;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * UpdateManager — Gère la mise à jour du modpack depuis GitHub.
 *
 * Respecte le preset qualité de UserPreferences :
 *   LOW / MEDIUM → shaderpacks ignorés au téléchargement
 *   HIGH         → tout est téléchargé
 */
public class UpdateManager {

    // ---------------------------
    // CALLBACK INTERFACE
    // ---------------------------

    public interface UpdateCallback {
        void onProgress(int percent, String currentFileName);
        void onSuccess(String serverAddress);
        void onError(String message);
        void onAlreadyUpToDate(String serverAddress);
    }

    // ---------------------------
    // CONSTANTES
    // ---------------------------

    private static final String API_BASE_URL   = "http://137.74.42.218:5400";
    private static final String VERSION_FILE   = "modpack_version.txt";
    private static final int    MAX_RETRIES    = 3;
    private static final int    BUFFER_SIZE    = 8192;
    private static final String ALL_WORLDS_TAG = "__all_worlds__";

    // ---------------------------
    // POINT D'ENTRÉE
    // ---------------------------

    public static void run(Path gamePath, UpdateCallback callback, Boolean download_shaders) {
        new Thread(() -> {
            try {
                runInternal(gamePath, callback, download_shaders);
            } catch (Exception e) {
                e.printStackTrace();
                callback.onError("Erreur inattendue : " + e.getMessage());
            }
        }, "UpdateManager-Thread").start();
    }

    // ---------------------------
    // LOGIQUE INTERNE
    // ---------------------------

    private static void runInternal(Path gamePath, UpdateCallback callback, Boolean download_shaders) throws Exception {

        // ── Étape 1 : Appel API /update/check ──────────────────────────────
        callback.onProgress(0, "Vérification des mises à jour...");

        JsonObject updateInfo = fetchUpdateInfo();
        String remoteVersion = updateInfo.get("version").getAsString();
        String manifestUrl = updateInfo.get("manifest_url").getAsString();
        String serverAddress = updateInfo.get("address").getAsString();

        Launcher.serverAddress = serverAddress;

        // ── Étape 2 : Comparaison version locale ───────────────────────────
        Path essentialFile = gamePath.resolve("1.20.1.json");
        boolean baseGameMissing = !Files.exists(essentialFile);

        String localVersion = readLocalVersion(gamePath);

        if (localVersion != null && localVersion.equals(remoteVersion) && !baseGameMissing) {
            callback.onAlreadyUpToDate(serverAddress);
            return;
        }



        // ── Étape 3 : Téléchargement du manifeste ──────────────────────────
        callback.onProgress(2, "Téléchargement du manifeste...");
        JsonObject manifest = fetchManifest(manifestUrl);
        JsonArray  files    = manifest.getAsJsonArray("files");

        // ── Étape 4 : Collecte des fichiers à télécharger ──────────────────
        List<JsonObject> toDownload = collectFilesToDownload(gamePath, files, download_shaders);

        if (toDownload.isEmpty()) {
            saveLocalVersion(gamePath, remoteVersion);
            callback.onSuccess(serverAddress);
            return;
        }

        // Calcul du poids total
        long totalBytes = 0;
        for (JsonObject entry : toDownload) totalBytes += entry.get("size").getAsLong();

        long downloadedBytes = 0;

        // ── Étape 5 : Téléchargement ───────────────────────────────────────
        for (JsonObject entry : toDownload) {
            String fileName = entry.get("name").getAsString();
            String fileUrl  = entry.get("url").getAsString();
            String type     = entry.get("type").getAsString();
            String destPath = entry.get("path").getAsString();
            String sha256   = entry.get("sha256").getAsString();
            long   size     = entry.get("size").getAsLong();

            int percent = (int)(5 + (downloadedBytes * 90.0 / totalBytes));
            callback.onProgress(percent, fileName);

            if (type.equals("datapack")) {
                downloadDatapack(gamePath, fileUrl, fileName, sha256);
            } else {
                Path dest = gamePath.resolve(destPath);
                Files.createDirectories(dest);
                downloadFileWithRetry(fileUrl, dest.resolve(fileName), sha256);
            }

            downloadedBytes += size;
        }

        // ── Étape 6 : Nettoyage ────────────────────────────────────────────
        callback.onProgress(96, "Nettoyage des anciens fichiers...");
        cleanObsoleteFiles(gamePath, files, false);

        // ── Étape 7 : Sauvegarde version ───────────────────────────────────
        saveLocalVersion(gamePath, remoteVersion);

        // ── Étape 8 : Succès ───────────────────────────────────────────────
        callback.onProgress(100, "Mise à jour terminée !");
        callback.onSuccess(serverAddress);
    }

    // ---------------------------
    // COLLECTE DES FICHIERS
    // ---------------------------

    /**
     * Compare chaque entrée du manifeste avec le fichier local.
     * Respecte le preset qualité : si downloadShaderpacks = false,
     * les fichiers de type "shaderpack" sont ignorés.
     */
    private static List<JsonObject> collectFilesToDownload(Path gamePath, JsonArray files,
                                                           boolean downloadShaderpacks) {
        List<JsonObject> toDownload = new ArrayList<>();

        for (int i = 0; i < files.size(); i++) {
            JsonObject entry    = files.get(i).getAsJsonObject();
            String     fileName = entry.get("name").getAsString();
            String     type     = entry.get("type").getAsString();
            String     destPath = entry.get("path").getAsString();
            String     sha256   = entry.get("sha256").getAsString();

            // ── Respect du preset qualité ──────────────────────────────────
            if (type.equals("shaderpack") && !downloadShaderpacks) {
                System.out.println("[UpdateManager] Shader ignoré : " + fileName);
                continue;
            }

            Path localFile;
            if (type.equals("datapack")) {
                localFile = findDatapackInAnyWorld(gamePath, fileName);
                if (localFile == null) { toDownload.add(entry); continue; }
            } else {
                localFile = gamePath.resolve(destPath).resolve(fileName);
            }

            if (!Files.exists(localFile)) {
                toDownload.add(entry);
            } else {
                String localHash = sha256Of(localFile);
                if (!sha256.equals(localHash)) toDownload.add(entry);
            }
        }

        return toDownload;
    }

    // ---------------------------
    // NETTOYAGE
    // ---------------------------

    /**
     * Supprime les fichiers locaux absents du manifeste.
     * Si downloadShaderpacks = false → ne touche pas au dossier shaderpacks.
     */
    private static void cleanObsoleteFiles(Path gamePath, JsonArray files,
                                           boolean downloadShaderpacks) {
        Set<String> allowedMods           = new HashSet<>();
        Set<String> allowedShaderpacks    = new HashSet<>();
        Set<String> allowedResourcepacks  = new HashSet<>();
        Set<String> allowedDatapacks      = new HashSet<>();

        for (int i = 0; i < files.size(); i++) {
            JsonObject entry = files.get(i).getAsJsonObject();
            String name = entry.get("name").getAsString();
            String type = entry.get("type").getAsString();
            switch (type) {
                case "mod" -> allowedMods.add(name);
                case "shaderpack" -> allowedShaderpacks.add(name);
                case "resourcepack" -> allowedResourcepacks.add(name);
                case "datapack" -> allowedDatapacks.add(name);
            }
        }

        deleteUnlistedFiles(gamePath.resolve("mods"), allowedMods, ".jar");

        if (downloadShaderpacks) {
            deleteUnlistedFiles(gamePath.resolve("shaderpacks"), allowedShaderpacks, ".zip", ".jar");
        }

        deleteUnlistedFiles(gamePath.resolve("resourcepacks"), allowedResourcepacks, ".zip");

        cleanDatapacksInAllWorlds(gamePath, allowedDatapacks);
    }

    private static void deleteUnlistedFiles(Path dir, Set<String> allowed, String... extensions) {
        if (!Files.exists(dir)) return;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            for (Path file : stream) {
                if (!Files.isRegularFile(file)) continue;
                String name = file.getFileName().toString();
                boolean hasValidExt = false;
                for (String ext : extensions) { if (name.endsWith(ext)) { hasValidExt = true; break; } }
                if (hasValidExt && !allowed.contains(name)) {
                    Files.deleteIfExists(file);
                    System.out.println("[UpdateManager] Supprimé (obsolète) : " + name);
                }
            }
        } catch (IOException e) { e.printStackTrace(); }
    }

    private static void cleanDatapacksInAllWorlds(Path gamePath, Set<String> allowedDatapacks) {
        Path savesDir = gamePath.resolve("saves");
        if (!Files.exists(savesDir)) return;
        try (DirectoryStream<Path> worlds = Files.newDirectoryStream(savesDir)) {
            for (Path world : worlds) {
                if (!Files.isDirectory(world)) continue;
                deleteUnlistedFiles(world.resolve("datapacks"), allowedDatapacks, ".zip");
            }
        } catch (IOException e) { e.printStackTrace(); }
    }

    // ---------------------------
    // TÉLÉCHARGEMENT
    // ---------------------------

    private static void downloadFileWithRetry(String fileUrl, Path dest, String expectedSha256) throws Exception {
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                downloadFile(fileUrl, dest);
                String actualHash = sha256Of(dest);
                if (actualHash.equals(expectedSha256)) return;
                System.err.println("[UpdateManager] SHA-256 invalide pour " + dest.getFileName()
                        + " (tentative " + attempt + "/" + MAX_RETRIES + ")");
                Files.deleteIfExists(dest);
            } catch (IOException e) {
                System.err.println("[UpdateManager] Erreur téléchargement " + dest.getFileName()
                        + " (tentative " + attempt + "/" + MAX_RETRIES + ") : " + e.getMessage());
                if (attempt == MAX_RETRIES) throw e;
            }
        }
        throw new IOException("Échec du téléchargement après " + MAX_RETRIES + " tentatives : " + dest.getFileName());
    }

    private static void downloadDatapack(Path gamePath, String fileUrl, String fileName, String sha256) throws Exception {
        Path tempFile = gamePath.resolve("tmp_" + fileName);
        downloadFileWithRetry(fileUrl, tempFile, sha256);

        Path savesDir = gamePath.resolve("saves");
        if (!Files.exists(savesDir)) {
            Path cache = gamePath.resolve("datapacks_cache");
            Files.createDirectories(cache);
            Files.move(tempFile, cache.resolve(fileName), StandardCopyOption.REPLACE_EXISTING);
            return;
        }

        try (DirectoryStream<Path> worlds = Files.newDirectoryStream(savesDir)) {
            for (Path world : worlds) {
                if (!Files.isDirectory(world)) continue;
                Path dp = world.resolve("datapacks");
                Files.createDirectories(dp);
                Files.copy(tempFile, dp.resolve(fileName), StandardCopyOption.REPLACE_EXISTING);
                System.out.println("[UpdateManager] Datapack installé dans : " + world.getFileName());
            }
        }
        Files.deleteIfExists(tempFile);
    }

    /**
     * Téléchargement HTTP avec gestion manuelle des redirects GitHub (302 → CDN).
     */
    private static void downloadFile(String fileUrl, Path dest) throws IOException {
        String currentUrl = clean_url(fileUrl);
        int maxRedirects  = 5;

        for (int redirect = 0; redirect < maxRedirects; redirect++) {
            HttpURLConnection connection = (HttpURLConnection) new URL(currentUrl).openConnection();
            connection.setInstanceFollowRedirects(false);
            connection.setRequestProperty("User-Agent", "RuinsLauncher/1.0");
            connection.setConnectTimeout(10_000);
            connection.setReadTimeout(30_000);

            int responseCode = connection.getResponseCode();

            if (responseCode == 301 || responseCode == 302 ||
                    responseCode == 303 || responseCode == 307 || responseCode == 308) {
                String location = connection.getHeaderField("Location");
                connection.disconnect();
                if (location == null || location.isEmpty())
                    throw new IOException("Redirect sans Location header pour : " + currentUrl);
                if (location.startsWith("/")) {
                    URL base = new URL(currentUrl);
                    location = base.getProtocol() + "://" + base.getHost() + location;
                }
                currentUrl = location;
                System.out.println("[UpdateManager] Redirect → " + currentUrl);
                continue;
            }

            if (responseCode != 200)
                throw new IOException("HTTP " + responseCode + " pour " + currentUrl);

            Files.createDirectories(dest.getParent());
            try (InputStream in  = new BufferedInputStream(connection.getInputStream());
                 OutputStream out = new BufferedOutputStream(Files.newOutputStream(dest))) {
                byte[] buffer = new byte[BUFFER_SIZE];
                int bytesRead;
                while ((bytesRead = in.read(buffer)) != -1) out.write(buffer, 0, bytesRead);
            }
            return;
        }
        throw new IOException("Trop de redirects pour : " + fileUrl);
    }

    // ---------------------------
    // APPEL API
    // ---------------------------

    private static JsonObject fetchUpdateInfo() throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(API_BASE_URL + "/update/check").openConnection();
        conn.setRequestProperty("User-Agent", "RuinsLauncher/1.0");
        conn.setConnectTimeout(10_000);
        conn.setReadTimeout(10_000);
        if (conn.getResponseCode() != 200)
            throw new IOException("Impossible de contacter le serveur (HTTP " + conn.getResponseCode() + ")");
        try (InputStreamReader reader = new InputStreamReader(conn.getInputStream())) {
            return new Gson().fromJson(reader, JsonObject.class);
        }
    }

    private static JsonObject fetchManifest(String manifestUrl) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(manifestUrl).openConnection();
        conn.setRequestProperty("User-Agent", "RuinsLauncher/1.0");
        conn.setConnectTimeout(10_000);
        conn.setReadTimeout(15_000);
        if (conn.getResponseCode() != 200)
            throw new IOException("Impossible de télécharger le manifeste (HTTP " + conn.getResponseCode() + ")");
        try (InputStreamReader reader = new InputStreamReader(conn.getInputStream())) {
            return new Gson().fromJson(reader, JsonObject.class);
        }
    }

    // ---------------------------
    // VERSION LOCALE
    // ---------------------------

    private static String readLocalVersion(Path gamePath) {
        Path versionFile = gamePath.resolve(VERSION_FILE);
        if (!Files.exists(versionFile)) return null;
        try { return Files.readString(versionFile).trim(); }
        catch (IOException e) { return null; }
    }

    private static void saveLocalVersion(Path gamePath, String version) {
        try { Files.writeString(gamePath.resolve(VERSION_FILE), version); }
        catch (IOException e) { e.printStackTrace(); }
    }

    // ---------------------------
    // UTILITAIRES
    // ---------------------------

    private static String clean_url(String name) {
        return name.replace(" ", ".");
    }

    private static String sha256Of(Path file) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream in = new BufferedInputStream(Files.newInputStream(file))) {
                byte[] buffer = new byte[BUFFER_SIZE];
                int read;
                while ((read = in.read(buffer)) != -1) digest.update(buffer, 0, read);
            }
            StringBuilder sb = new StringBuilder();
            for (byte b : digest.digest()) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (NoSuchAlgorithmException | IOException e) {
            e.printStackTrace(); return "";
        }
    }

    private static Path findDatapackInAnyWorld(Path gamePath, String fileName) {
        Path savesDir = gamePath.resolve("saves");
        if (!Files.exists(savesDir)) return null;
        try (DirectoryStream<Path> worlds = Files.newDirectoryStream(savesDir)) {
            for (Path world : worlds) {
                Path candidate = world.resolve("datapacks").resolve(fileName);
                if (Files.exists(candidate)) return candidate;
            }
        } catch (IOException e) { e.printStackTrace(); }
        return null;
    }
}