package fr.ruins.launcher;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;

public class Frame extends JFrame {

    private static Frame instance;

    private static AuthPanel     authPanel;
    private static HomePanel     homePanel;
    private static SettingsPanel settingsPanel;

    public Frame() throws IOException {
        instance = this;

        this.setTitle("Ruins Launcher");
        this.setDefaultCloseOperation(EXIT_ON_CLOSE);
        this.setSize(1920, 1080);
        this.setUndecorated(false);
        this.setLocationRelativeTo(null);
        this.setIconImage(getImage("ruins_logo.png"));

        // Panels
        authPanel = new AuthPanel();

        this.setContentPane(authPanel);

        this.setVisible(true);

    }

    public static void main(String[] args) throws IOException {
        if (!isLaunchedFromBootstrap()) {
            JOptionPane.showMessageDialog(
                    null,
                    "Veuillez lancer Ruins via le launcher officiel (Bootstrap).",
                    "Ruins Launcher",
                    JOptionPane.ERROR_MESSAGE
            );
            System.exit(0);
        }


        Launcher.crashFile.mkdir();
        instance = new Frame();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // NAVIGATION
    // ─────────────────────────────────────────────────────────────────────────

    /** Affiche le panneau d'accueil. */
    public void showHome() {
        try {
            // Invalider le SettingsPanel pour qu'il recharge les prefs à la prochaine ouverture
            settingsPanel = null;
            if (homePanel == null) homePanel = new HomePanel();
            switchPanel(homePanel);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /** Affiche le panneau de connexion / inscription. */
    public void showAuth() {
        try {
            if (authPanel == null) authPanel = new AuthPanel();
            switchPanel(authPanel);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /** Affiche le panneau de paramètres. */
    public void showSettings() {
        try {
            if (settingsPanel == null) settingsPanel = new SettingsPanel();
            switchPanel(settingsPanel);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Remplace le panneau courant par le nouveau.
     * Arrête le polling de maintenance si on quitte HomePanel.
     */
    private void switchPanel(JPanel newPanel) {
        // Si on quitte HomePanel → arrêter le polling maintenance
        if (this.getContentPane() instanceof HomePanel && !(newPanel instanceof HomePanel)) {
            MaintenanceChecker.stop();
        }

        this.setContentPane(newPanel);
        this.revalidate();
        this.repaint();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // UTILITAIRES IMAGES
    // ─────────────────────────────────────────────────────────────────────────

    public static Image getImage(String fichier) throws IOException {
        try (InputStream inputStream = Frame.class.getClassLoader().getResourceAsStream(fichier)) {
            if (inputStream == null) throw new IOException("Resource not found: " + fichier);
            return ImageIO.read(inputStream);
        }
    }

    public static BufferedImage getBufferdImage(String fichier) throws IOException {
        try (InputStream inputStream = Frame.class.getClassLoader().getResourceAsStream(fichier)) {
            if (inputStream == null) throw new IOException("Resource not found: " + fichier);
            return ImageIO.read(inputStream);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GETTERS
    // ─────────────────────────────────────────────────────────────────────────

    public static Frame getInstance() { return instance; }

    public static HomePanel getHomePanel() throws IOException {
        if (homePanel == null) homePanel = new HomePanel();
        return homePanel;
    }

    public static AuthPanel getLoginPanel() throws IOException {
        if (authPanel == null) authPanel = new AuthPanel();
        return authPanel;
    }

    // BOOTSTRAP CHECK
    private static boolean isLaunchedFromBootstrap() {
        return java.nio.file.Files.exists(
                java.nio.file.Paths.get(
                        System.getenv("APPDATA"),
                        ".RuinsLauncher",
                        "bootstrap.lock"
                )
        );
    }

}