import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.zip.*;
import java.nio.file.Paths;

/**
 * Bootstrap — Point d'entrée du launcher Ruins
 *
 * Ce programme est distribué aux joueurs. Il s'occupe de :
 *   1. Afficher une fenetre de chargement (logo + progression)
 *   2. Verifier que Java 17 est disponible localement
 *      Si non : telecharger le JRE 17 depuis Adoptium (Eclipse Temurin)
 *   3. Verifier la version du launcher sur GitHub
 *      Si nouvelle version : telecharger le JAR
 *   4. Lancer le launcher avec Java 17
 *
 * CONFIGURATION — seules ces constantes sont a modifier :
 *   GITHUB_OWNER, GITHUB_REPO, LAUNCHER_JAR_NAME
 *
 * Repertoire de travail cree a cote du bootstrap :
 *   ./ruins-data/
 *     runtime/          JRE 17 telecharge ici
 *     launcher.jar      JAR du launcher
 *     launcher.version  version locale sauvegardee
 */
public class Bootstrap {

    // ══════════════════════════════════════════════════════════
    //  CONFIGURATION
    //  Modifier uniquement ces constantes selon votre projet
    // ══════════════════════════════════════════════════════════

    /** Proprietaire du repo GitHub (ton username). */
    private static final String GITHUB_OWNER = "Arthur640-ctrl";

    /** Nom du repo GitHub contenant les releases du launcher. */
    private static final String GITHUB_REPO = "Ruins-Launcher";

    /** Nom du fichier JAR dans la GitHub Release. */
    private static final String LAUNCHER_JAR_NAME = "launcher.jar";

    /** Version Java requise pour le launcher. */
    private static final int JAVA_VERSION_REQUIRED = 17;

    // ══════════════════════════════════════════════════════════
    //  COULEURS DE LA FENETRE SPLASH
    // ══════════════════════════════════════════════════════════

    private static final Color COLOR_ACCENT = new Color(217, 4, 4);       // Rouge Ruins
    private static final Color COLOR_BG     = new Color(13, 13, 13);      // Fond noir
    private static final Color COLOR_TEXT   = new Color(175, 168, 168);   // Texte gris clair

    // ══════════════════════════════════════════════════════════
    //  CHEMINS LOCAUX
    // ══════════════════════════════════════════════════════════

    /** Dossier de donnees cree a cote du JAR bootstrap. */
    private static final Path DATA_DIR = get_data_dir();

    /** Dossier ou le JRE 17 sera extrait. */
    private static final Path RUNTIME_DIR = DATA_DIR.resolve("runtime");

    /** Chemin du JAR du launcher. */
    private static final Path LAUNCHER_JAR_PATH = DATA_DIR.resolve("launcher.jar");

    /** Fichier texte contenant la version locale du launcher. */
    private static final Path LAUNCHER_VERSION_PATH = DATA_DIR.resolve("launcher.version");


    private static Path get_data_dir() {
        String os = System.getProperty("os.name").toLowerCase();

        if (os.contains("win")) {
            return Paths.get(System.getenv("APPDATA"), ".RuinsLauncher");
        } else if (os.contains("mac")) {
            return Paths.get(System.getProperty("user.home"),
                    "Library", "Application Support", ".RuinsLauncher");
        } else {
            return Paths.get(System.getProperty("user.home"), ".RuinsLauncher");
        }
    }

    // ══════════════════════════════════════════════════════════
    //  COMPOSANTS UI
    // ══════════════════════════════════════════════════════════

    private static JWindow splash_window;
    private static JLabel  status_label;
    private static JPanel  progress_bar_fill;
    private static JPanel  progress_bar_track;

    // ══════════════════════════════════════════════════════════
    //  POINT D'ENTREE
    // ══════════════════════════════════════════════════════════

    public static void main(String[] args) {
        System.setProperty("awt.useSystemAAFontSettings", "on");
        System.setProperty("swing.aatext", "true");

        SwingUtilities.invokeLater(() -> {
            build_splash_window();
            splash_window.setVisible(true);
            // Tout le travail dans un thread separe pour ne jamais geler l'UI
            new Thread(Bootstrap::run_bootstrap, "BootstrapThread").start();
        });
    }

    // ══════════════════════════════════════════════════════════
    //  LOGIQUE PRINCIPALE
    // ══════════════════════════════════════════════════════════

    /**
     * Execute les 3 etapes dans l'ordre.
     * Toujours appele depuis un thread separe, jamais depuis l'EDT.
     */
    private static void run_bootstrap() {
        try {
            Files.createDirectories(DATA_DIR);

            // Etape 1 : Java 17
            set_status("Verification de Java...", 5);
            String java_executable = ensure_java_17();

            // Etape 2 : Launcher a jour
            set_status("Verification du launcher...", 40);
            ensure_launcher_up_to_date();

            // Etape 3 : Lancement
            set_status("Lancement de Ruins...", 95);
            Thread.sleep(600);

            create_bootstrap_lock();

            launch_launcher(java_executable);

            set_progress(100);
            Thread.sleep(400);
            SwingUtilities.invokeLater(() -> splash_window.dispose());

        } catch (Exception error) {
            error.printStackTrace();
            show_fatal_error(error.getMessage());
        }
    }

    // ══════════════════════════════════════════════════════════
    //  ETAPE 1 — JAVA 17
    // ══════════════════════════════════════════════════════════

    /**
     * Verifie si Java 17 est disponible localement dans RUNTIME_DIR.
     * Si non, le telecharge depuis Adoptium (Eclipse Temurin).
     *
     * @return Chemin absolu vers l'executable Java 17
     */
    private static String ensure_java_17() throws Exception {
        String local_java = find_local_java_executable();
        if (local_java != null) {
            set_status("Java 17 trouve localement.", 15);
            return local_java;
        }

        // Pas de JRE local -> telecharger depuis Adoptium
        set_status("Telechargement de Java 17...", 10);
        download_java_17();

        local_java = find_local_java_executable();
        if (local_java == null) {
            throw new RuntimeException(
                    "Java 17 a ete telecharge mais l'executable est introuvable. "
                            + "Verifiez le dossier : " + RUNTIME_DIR.toAbsolutePath()
            );
        }
        return local_java;
    }

    /**
     * Cherche l'executable Java dans RUNTIME_DIR.
     * Gere les differences de chemin entre Windows, macOS et Linux.
     *
     * @return Chemin absolu ou null si non trouve
     */
    private static String find_local_java_executable() {
        if (!Files.exists(RUNTIME_DIR)) return null;
        String java_binary_name = is_windows() ? "java.exe" : "java";
        try {
            return Files.walk(RUNTIME_DIR)
                    .filter(p -> p.getFileName().toString().equals(java_binary_name))
                    .filter(p -> p.toString().contains("bin"))
                    .findFirst()
                    .map(p -> p.toAbsolutePath().toString())
                    .orElse(null);
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * Telecharge et extrait le JRE 17 depuis l'API Adoptium.
     * Selectionne automatiquement la bonne archive selon l'OS et l'architecture.
     */
    private static void download_java_17() throws Exception {
        String os_name          = detect_os_name();
        String arch_name        = detect_arch_name();
        String archive_ext      = is_windows() ? "zip" : "tar.gz";

        // URL de l'API Adoptium pour le dernier JRE 17
        String download_url = String.format(
                "https://api.adoptium.net/v3/binary/latest/%d/ga/%s/%s/jre/hotspot/normal/eclipse?project=jdk",
                JAVA_VERSION_REQUIRED, os_name, arch_name
        );

        set_status("Telechargement Java 17 (" + os_name + "/" + arch_name + ")...", 12);

        Path archive_temp = DATA_DIR.resolve("java17_temp." + archive_ext);
        download_file(download_url, archive_temp, 10, 35);

        set_status("Installation de Java 17...", 36);
        Files.createDirectories(RUNTIME_DIR);

        if (is_windows()) {
            extract_zip(archive_temp, RUNTIME_DIR);
        } else {
            extract_tar_gz(archive_temp, RUNTIME_DIR);
        }

        Files.deleteIfExists(archive_temp);

        // Sur Linux/Mac : rendre l'executable... executable
        if (!is_windows()) {
            String java_bin = find_local_java_executable();
            if (java_bin != null) {
                new ProcessBuilder("chmod", "+x", java_bin).start().waitFor();
            }
        }

        set_status("Java 17 installe avec succes.", 38);
    }

    // ══════════════════════════════════════════════════════════
    //  ETAPE 2 — VERIFICATION DU LAUNCHER
    // ══════════════════════════════════════════════════════════

    /**
     * Compare la version locale avec la derniere release GitHub.
     * Telecharge le JAR si une nouvelle version est disponible ou si absent.
     */
    private static void ensure_launcher_up_to_date() throws Exception {
        String github_api_url = "https://api.github.com/repos/"
                + GITHUB_OWNER + "/" + GITHUB_REPO + "/releases/latest";

        String api_response   = http_get(github_api_url);
        String remote_version = extract_json_string(api_response, "tag_name");
        String download_url   = extract_download_url(api_response, LAUNCHER_JAR_NAME);

        if (remote_version == null || download_url == null) {
            throw new RuntimeException(
                    "Impossible de recuperer les informations de la release GitHub.\n"
                            + "Verifiez votre connexion internet et que le repo est public."
            );
        }

        String local_version = read_local_version();
        System.out.println("[Bootstrap] Version locale   : " + local_version);
        System.out.println("[Bootstrap] Version distante : " + remote_version);

        if (local_version != null
                && local_version.equals(remote_version)
                && Files.exists(LAUNCHER_JAR_PATH)) {
            set_status("Launcher a jour (" + remote_version + ")", 85);
            return;
        }

        set_status("Mise a jour du launcher " + remote_version + "...", 45);
        download_file(download_url, LAUNCHER_JAR_PATH, 45, 90);

        Files.write(LAUNCHER_VERSION_PATH, remote_version.getBytes(StandardCharsets.UTF_8));
        set_status("Launcher mis a jour vers " + remote_version, 92);
    }

    // ══════════════════════════════════════════════════════════
    //  ETAPE 3 — LANCEMENT DU LAUNCHER
    // ══════════════════════════════════════════════════════════

    private static void create_bootstrap_lock() throws IOException {
        Files.write(
                DATA_DIR.resolve("bootstrap.lock"),
                "ok".getBytes(StandardCharsets.UTF_8)
        );
    }

    /**
     * Lance le launcher JAR avec Java 17.
     * Le bootstrap se ferme ensuite, le launcher tourne independamment.
     *
     * @param java_executable Chemin absolu vers java (ou java.exe)
     */
    private static void launch_launcher(String java_executable) throws Exception {
        if (!Files.exists(LAUNCHER_JAR_PATH)) {
            throw new RuntimeException(
                    "Le fichier launcher.jar est introuvable : "
                            + LAUNCHER_JAR_PATH.toAbsolutePath()
            );
        }

        System.out.println("[Bootstrap] Lancement avec : " + java_executable);

        ProcessBuilder process_builder = new ProcessBuilder(
                java_executable,
                "-jar",
                LAUNCHER_JAR_PATH.toAbsolutePath().toString()
        );
        process_builder.directory(new File("."));
        process_builder.inheritIO();
        process_builder.start();
        // Pas de waitFor() : le bootstrap se ferme, le launcher tourne seul
    }

    // ══════════════════════════════════════════════════════════
    //  TELECHARGEMENT AVEC PROGRESSION
    // ══════════════════════════════════════════════════════════

    /**
     * Telecharge un fichier depuis une URL en suivant les redirects.
     * Met a jour la barre entre progress_start % et progress_end %.
     */
    private static void download_file(String url, Path dest,
                                      int progress_start, int progress_end)
            throws Exception {

        Files.createDirectories(dest.getParent());

        // Suivre les redirects manuellement (important pour GitHub et Adoptium)
        String current_url = url;
        HttpURLConnection connection;
        int redirect_count = 0;

        while (true) {
            connection = (HttpURLConnection) new URL(current_url).openConnection();
            connection.setInstanceFollowRedirects(false);
            connection.setRequestProperty("User-Agent", "RuinsBootstrap/1.0");
            connection.setConnectTimeout(15_000);
            connection.setReadTimeout(60_000);

            int response_code = connection.getResponseCode();

            if (response_code == 301 || response_code == 302
                    || response_code == 303 || response_code == 307
                    || response_code == 308) {

                String location = connection.getHeaderField("Location");
                connection.disconnect();

                if (location == null || ++redirect_count > 10) {
                    throw new IOException("Trop de redirections pour : " + url);
                }
                if (location.startsWith("/")) {
                    URL base = new URL(current_url);
                    location = base.getProtocol() + "://" + base.getHost() + location;
                }
                current_url = location;
                continue;
            }

            if (response_code != 200) {
                throw new IOException("Erreur HTTP " + response_code + " pour : " + current_url);
            }
            break;
        }

        long total_bytes    = connection.getContentLengthLong();
        long downloaded     = 0;
        int  progress_range = progress_end - progress_start;

        try (InputStream  in  = new BufferedInputStream(connection.getInputStream());
             OutputStream out = new BufferedOutputStream(Files.newOutputStream(dest))) {

            byte[] buffer = new byte[8192];
            int bytes_read;
            while ((bytes_read = in.read(buffer)) != -1) {
                out.write(buffer, 0, bytes_read);
                downloaded += bytes_read;
                if (total_bytes > 0) {
                    set_progress(progress_start + (int)((downloaded * progress_range) / total_bytes));
                }
            }
        }
        set_progress(progress_end);
    }

    // ══════════════════════════════════════════════════════════
    //  EXTRACTION D'ARCHIVES
    // ══════════════════════════════════════════════════════════

    /** Extrait une archive ZIP dans dest_dir. Utilise sur Windows. */
    private static void extract_zip(Path zip_file, Path dest_dir) throws Exception {
        try (ZipInputStream zis = new ZipInputStream(
                new BufferedInputStream(Files.newInputStream(zip_file)))) {

            ZipEntry entry;
            byte[] buffer = new byte[8192];

            while ((entry = zis.getNextEntry()) != null) {

                Path entry_path = dest_dir.resolve(entry.getName()).normalize();

                // Protection contre Zip Slip
                if (!entry_path.startsWith(dest_dir)) {
                    throw new SecurityException("Entree ZIP suspecte : " + entry.getName());
                }

                if (entry.isDirectory()) {
                    Files.createDirectories(entry_path);
                } else {
                    Files.createDirectories(entry_path.getParent());

                    try (OutputStream out = Files.newOutputStream(entry_path)) {
                        int len;
                        while ((len = zis.read(buffer)) > 0) {
                            out.write(buffer, 0, len);
                        }
                    }
                }

                zis.closeEntry();
            }
        }
    }

    /** Extrait une archive .tar.gz dans dest_dir. Utilise sur Linux et macOS. */
    private static void extract_tar_gz(Path tar_gz_file, Path dest_dir) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(
                "tar", "-xzf",
                tar_gz_file.toAbsolutePath().toString(),
                "-C", dest_dir.toAbsolutePath().toString()
        );
        pb.inheritIO();
        int exit_code = pb.start().waitFor();
        if (exit_code != 0) {
            throw new IOException("Echec extraction tar.gz (code " + exit_code + ")");
        }
    }

    // ══════════════════════════════════════════════════════════
    //  UTILITAIRES RESEAU
    // ══════════════════════════════════════════════════════════

    /** Effectue une requete HTTP GET et retourne le corps de la reponse. */
    private static String http_get(String url) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setRequestProperty("User-Agent", "RuinsBootstrap/1.0");
        conn.setRequestProperty("Accept", "application/vnd.github.v3+json");
        conn.setConnectTimeout(10_000);
        conn.setReadTimeout(10_000);

        if (conn.getResponseCode() != 200) {
            throw new IOException("GitHub API : HTTP " + conn.getResponseCode());
        }

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(conn.getInputStream()))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
            return sb.toString();
        }
    }

    // ══════════════════════════════════════════════════════════
    //  UTILITAIRES JSON (sans dependance externe)
    // ══════════════════════════════════════════════════════════

    /** Extrait la valeur d'une cle simple dans un JSON brut. */
    private static String extract_json_string(String json, String key) {
        String search    = "\"" + key + "\"";
        int    key_index = json.indexOf(search);
        if (key_index == -1) return null;

        int colon_index = json.indexOf(":", key_index);
        if (colon_index == -1) return null;

        int q1 = json.indexOf("\"", colon_index + 1);
        if (q1 == -1) return null;

        int q2 = json.indexOf("\"", q1 + 1);
        if (q2 == -1) return null;

        return json.substring(q1 + 1, q2);
    }

    /**
     * Cherche l'URL de telechargement d'un asset dans une release GitHub.
     * Localise "browser_download_url" a proximite du nom du fichier.
     */
    private static String extract_download_url(String json, String file_name) {
        int name_pos = json.indexOf("\"" + file_name + "\"");
        if (name_pos == -1) return null;

        // Chercher browser_download_url apres le nom du fichier
        String after_name  = json.substring(name_pos);
        String url_key     = "\"browser_download_url\"";
        int    url_key_pos = after_name.indexOf(url_key);

        if (url_key_pos == -1) {
            // Chercher avant le nom si non trouve apres
            String before_name = json.substring(0, name_pos);
            int last_pos = before_name.lastIndexOf(url_key);
            if (last_pos == -1) return null;
            after_name  = json.substring(last_pos);
            url_key_pos = 0;
        }

        int colon = after_name.indexOf(":", url_key_pos);
        int q1    = after_name.indexOf("\"", colon + 1);
        int q2    = after_name.indexOf("\"", q1 + 1);
        if (q1 == -1 || q2 == -1) return null;

        return after_name.substring(q1 + 1, q2);
    }

    // ══════════════════════════════════════════════════════════
    //  UTILITAIRES SYSTEME
    // ══════════════════════════════════════════════════════════

    /** Retourne true si l'OS est Windows. */
    private static boolean is_windows() {
        return System.getProperty("os.name").toLowerCase().contains("win");
    }

    /** Retourne le nom de l'OS au format Adoptium : windows / linux / mac */
    private static String detect_os_name() {
        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("win")) return "windows";
        if (os.contains("mac")) return "mac";
        return "linux";
    }

    /** Retourne l'architecture CPU au format Adoptium : x64 / aarch64 */
    private static String detect_arch_name() {
        String arch = System.getProperty("os.arch").toLowerCase();
        if (arch.contains("aarch64") || arch.contains("arm64")) return "aarch64";
        return "x64";
    }

    /** Lit la version locale sauvegardee, ou null si absente. */
    private static String read_local_version() {
        if (!Files.exists(LAUNCHER_VERSION_PATH)) return null;
        try {
            return new String(Files.readAllBytes(LAUNCHER_VERSION_PATH), StandardCharsets.UTF_8).trim();
        } catch (IOException e) {
            return null;
        }
    }

    // ══════════════════════════════════════════════════════════
    //  INTERFACE GRAPHIQUE — Fenetre Splash
    // ══════════════════════════════════════════════════════════

    /**
     * Construit la fenetre splash centree et non-deplacable.
     * Taille fixe : 500 x 300 px.
     */
    private static void build_splash_window() {
        splash_window = new JWindow();
        splash_window.setSize(500, 300);
        splash_window.setLocationRelativeTo(null);

        JPanel root = new JPanel(null) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(COLOR_BG);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 16, 16);
                g2.setColor(COLOR_ACCENT.darker());
                g2.setStroke(new BasicStroke(1.5f));
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 16, 16);
                g2.dispose();
            }
        };
        root.setOpaque(false);

        // Logo
        JLabel logo_label = new JLabel("RUINS", SwingConstants.CENTER);
        try {
            InputStream logo_stream = Bootstrap.class.getClassLoader()
                    .getResourceAsStream("ruins_logo.png");
            if (logo_stream != null) {
                BufferedImage logo_img = ImageIO.read(logo_stream);
                logo_label.setIcon(new ImageIcon(logo_img.getScaledInstance(100, 100, Image.SCALE_SMOOTH)));
                logo_label.setText("");
            }
        } catch (Exception ignored) { /* Fallback texte */ }
        logo_label.setFont(new Font("Segoe UI", Font.BOLD, 42));
        logo_label.setForeground(COLOR_ACCENT);
        logo_label.setBounds(0, 34, 500, 120);
        root.add(logo_label);

        // Texte de statut
        status_label = new JLabel("Demarrage...", SwingConstants.CENTER);
        status_label.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        status_label.setForeground(COLOR_TEXT);
        status_label.setBounds(20, 200, 460, 20);
        root.add(status_label);

        // Piste de la barre de progression
        progress_bar_track = new JPanel(null) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new Color(38, 38, 38));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
                g2.dispose();
            }
        };
        progress_bar_track.setOpaque(false);
        progress_bar_track.setBounds(30, 232, 440, 10);
        root.add(progress_bar_track);

        // Remplissage rouge
        progress_bar_fill = new JPanel(null) {
            @Override protected void paintComponent(Graphics g) {
                if (getWidth() <= 0) return;
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(COLOR_ACCENT);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
                g2.dispose();
            }
        };
        progress_bar_fill.setOpaque(false);
        progress_bar_fill.setBounds(0, 0, 0, 10);
        progress_bar_track.add(progress_bar_fill);

        // Version
        JLabel version_label = new JLabel("Bootstrap v1.0", SwingConstants.CENTER);
        version_label.setFont(new Font("Segoe UI", Font.PLAIN, 10));
        version_label.setForeground(new Color(55, 55, 55));
        version_label.setBounds(0, 270, 500, 16);
        root.add(version_label);

        splash_window.setContentPane(root);
    }

    /**
     * Met a jour le texte de statut ET la progression.
     * Thread-safe : deleguera toujours a l'EDT.
     */
    private static void set_status(String message, int percent) {
        System.out.println("[Bootstrap] " + message);
        SwingUtilities.invokeLater(() -> {
            if (status_label != null) status_label.setText(message);
            update_progress_bar(percent);
        });
    }

    /** Met a jour uniquement la barre de progression (0-100). */
    private static void set_progress(int percent) {
        SwingUtilities.invokeLater(() -> update_progress_bar(percent));
    }

    private static void update_progress_bar(int percent) {
        if (progress_bar_fill == null || progress_bar_track == null) return;
        int clamped   = Math.max(0, Math.min(100, percent));
        int track_w   = progress_bar_track.getWidth();
        int fill_w    = (int)(track_w * clamped / 100.0);
        progress_bar_fill.setBounds(0, 0, fill_w, progress_bar_fill.getHeight());
        progress_bar_track.repaint();
    }

    /** Affiche une erreur fatale et quitte. */
    private static void show_fatal_error(String error_message) {
        SwingUtilities.invokeLater(() -> {
            JOptionPane.showMessageDialog(
                    null,
                    "<html><b>Le bootstrap a rencontre une erreur :</b><br><br>"
                            + error_message + "<br><br>"
                            + "Verifiez votre connexion internet et reessayez.</html>",
                    "Erreur — Ruins Bootstrap",
                    JOptionPane.ERROR_MESSAGE
            );
            System.exit(1);
        });
    }
}