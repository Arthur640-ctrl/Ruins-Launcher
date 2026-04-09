package fr.ruins.launcher;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.image.BufferedImage;
import java.awt.image.BufferedImageOp;
import java.awt.image.ConvolveOp;
import java.awt.image.Kernel;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;

public class HomePanel extends JPanel {

    private JPanel sidebar;
    private JPanel sidebar_top;
    private JPanel sidebar_bottom;

    private JLabel skinHead;
    private JLabel username;

    private JButton bottomButton;
    private ImageIcon buttonIcon;

    private float sidebar_percentage = 0.05f;
    private float square_percentage  = 0.60f;

    private Image skinImage;

    private JPanel    centralContent;
    private JLabel    titleLabel;
    private JTextArea descArea;
    private JButton   playButton;
    private ImageIcon playIcon;

    // ── Barre de progression ──────────────────────────────────────────────────
    private JPanel progressBarContainer;
    private JPanel progressBarTrack;
    private JPanel progressBarFill;
    private JLabel progressLabel;
    private JLabel progressFileName;
    private int    currentProgress = 0;
    private boolean isUpdating = false;

    // ── Bandeau de maintenance ────────────────────────────────────────────────
    private JPanel maintenanceBanner;
    private JLabel maintenanceIcon;
    private JLabel maintenanceMessage;
    private JLabel maintenanceEndTime;

    // ── État serveur ──────────────────────────────────────────────────────────
    private enum ServerStatus { OPERATIONAL, MAINTENANCE, API_DOWN }
    private ServerStatus currentStatus = ServerStatus.OPERATIONAL;

    // ── Couleurs ─────────────────────────────────────────────────────────────
    private final Color BACKGROUND_COLOR   = new Color(13, 13, 13);
    private final Color ACCENT_COLOR       = new Color(217, 4, 4);
    private final Color ACCENT_HOVER_COLOR = new Color(140, 3, 3);
    private final Color TEXT_COLOR         = new Color(191, 184, 184);
    private final Color ERROR_COLOR        = new Color(255, 100, 120);
    private final Color WARNING_BG_COLOR   = new Color(180, 80, 0, 180);
    private final Color API_DOWN_BG_COLOR  = new Color(120, 0, 0, 180);
    private final Color DISABLED_BTN_COLOR = new Color(80, 80, 80);

    // ── Background ───────────────────────────────────────────────────────────
    private BufferedImage backgroundImage;
    private BufferedImage blurredBackground;

    public HomePanel() throws IOException {
        this.setLayout(null);
        this.setOpaque(false);

        backgroundImage = Frame.getBufferdImage("background_1.png");
        if (backgroundImage != null) {
            blurredBackground = createBlurredImage(backgroundImage, 5);
        }

        // ── SIDEBAR ──────────────────────────────────────────────────────────
        sidebar = new JPanel(null) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                int arc = 10, shadowSize = 8;
                for (int i = 0; i < shadowSize; i++) {
                    float opacity = (shadowSize - i) / (float) shadowSize * 0.15f;
                    g2.setColor(new Color(0, 0, 0, (int)(opacity * 255)));
                    g2.fillRoundRect(i, i, getWidth() - i*2, getHeight() - i*2, arc, arc);
                }
                g2.setColor(new Color(25, 25, 25, 153));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), arc, arc);
                g2.dispose();
            }
        };
        sidebar.setOpaque(false);
        this.add(sidebar);

        sidebar_top = new JPanel(new BorderLayout());
        sidebar_top.setOpaque(false);
        sidebar.add(sidebar_top);

        skinHead = new JLabel();
        skinHead.setHorizontalAlignment(SwingConstants.CENTER);

        username = new JLabel(Launcher.session.getUsername());
        username.setHorizontalAlignment(SwingConstants.CENTER);
        username.setForeground(TEXT_COLOR);
        username.setFont(new Font("Segoe UI", Font.BOLD, 16));

        sidebar_top.add(skinHead, BorderLayout.CENTER);
        sidebar_top.add(username, BorderLayout.SOUTH);

        sidebar_bottom = new JPanel(null);
        sidebar_bottom.setOpaque(false);
        sidebar.add(sidebar_bottom);

        bottomButton = new JButton();
        bottomButton.setBorderPainted(false);
        bottomButton.setContentAreaFilled(false);
        bottomButton.setFocusPainted(false);
        bottomButton.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseEntered(java.awt.event.MouseEvent evt) {
                bottomButton.setCursor(new Cursor(Cursor.HAND_CURSOR));
            }
        });

        bottomButton.addActionListener(e -> Frame.getInstance().showSettings());

        try {
            Image img = Frame.getImage("settings_button.png");
            buttonIcon = new ImageIcon(img);
        } catch (IOException e) { e.printStackTrace(); }

        sidebar_bottom.add(bottomButton);

        try {
            URL url = new URL("https://mc-heads.net/avatar/" + Launcher.session.getUsername());
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestProperty("User-Agent", "Mozilla/5.0");
            skinImage = ImageIO.read(connection.getInputStream());
        } catch (IOException e) { e.printStackTrace(); }

        // ── CONTENU CENTRAL ───────────────────────────────────────────────────
        centralContent = new JPanel();
        centralContent.setLayout(new BoxLayout(centralContent, BoxLayout.Y_AXIS));
        centralContent.setOpaque(false);

        titleLabel = new JLabel("<html>Bienvenue sur <span style='color: #D90404;'>RUINS !</span></html>");
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 48));
        titleLabel.setForeground(Color.WHITE);
        titleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        descArea = new JTextArea("Plongez dans un univers unique où chaque ruine raconte une histoire. " +
                "Préparez-vous pour l'aventure ultime sur notre serveur Minecraft.");
        descArea.setFont(new Font("Segoe UI", Font.PLAIN, 18));
        descArea.setForeground(TEXT_COLOR);
        descArea.setOpaque(false);
        descArea.setEditable(false);
        descArea.setLineWrap(true);
        descArea.setWrapStyleWord(true);
        descArea.setMaximumSize(new Dimension(500, 100));
        descArea.setAlignmentX(Component.LEFT_ALIGNMENT);

        // ── BOUTON JOUER ──────────────────────────────────────────────────────
        playButton = new JButton("JOUER") {
            private boolean hovered = false;
            {
                addMouseListener(new java.awt.event.MouseAdapter() {
                    public void mouseEntered(java.awt.event.MouseEvent e) { hovered = true;  repaint(); }
                    public void mouseExited (java.awt.event.MouseEvent e) { hovered = false; repaint(); }
                });
            }
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                if (!isEnabled()) {
                    g2.setColor(DISABLED_BTN_COLOR);
                } else {
                    g2.setColor(hovered ? ACCENT_HOVER_COLOR : ACCENT_COLOR);
                }
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 30, 30);
                super.paintComponent(g);
                g2.dispose();
            }
            @Override protected void paintBorder(Graphics g) {}
        };
        playButton.setOpaque(false);
        playButton.setContentAreaFilled(false);
        playButton.setBorderPainted(false);
        playButton.setFocusPainted(false);
        playButton.setForeground(Color.WHITE);
        playButton.setFont(new Font("Segoe UI", Font.BOLD, 20));
        playButton.setCursor(new Cursor(Cursor.HAND_CURSOR));
        playButton.setAlignmentX(Component.LEFT_ALIGNMENT);
        playButton.setMaximumSize(new Dimension(180, 48));
        playButton.setPreferredSize(new Dimension(180, 48));

        // ── BANDEAU MAINTENANCE ───────────────────────────────────────────────
        maintenanceBanner = new JPanel(null) {
            @Override
            protected void paintComponent(Graphics g) {
                if (!isVisible()) return;
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                Color bg = (currentStatus == ServerStatus.API_DOWN) ? API_DOWN_BG_COLOR : WARNING_BG_COLOR;
                g2.setColor(bg);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 12, 12);
                g2.setColor(bg.brighter());
                g2.setStroke(new BasicStroke(1f));
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 12, 12);
                g2.dispose();
            }
        };
        maintenanceBanner.setOpaque(false);
        maintenanceBanner.setAlignmentX(Component.LEFT_ALIGNMENT);
        maintenanceBanner.setMaximumSize(new Dimension(500, 56));
        maintenanceBanner.setPreferredSize(new Dimension(500, 56));
        maintenanceBanner.setMinimumSize(new Dimension(500, 56));
        maintenanceBanner.setVisible(false);

        maintenanceIcon = new JLabel("⚠");
        maintenanceIcon.setForeground(Color.WHITE);
        maintenanceIcon.setFont(new Font("Segoe UI", Font.BOLD, 20));

        maintenanceMessage = new JLabel("Serveur en maintenance");
        maintenanceMessage.setForeground(Color.WHITE);
        maintenanceMessage.setFont(new Font("Segoe UI", Font.BOLD, 13));

        maintenanceEndTime = new JLabel("");
        maintenanceEndTime.setForeground(new Color(255, 220, 180));
        maintenanceEndTime.setFont(new Font("Segoe UI", Font.PLAIN, 11));

        maintenanceBanner.add(maintenanceIcon);
        maintenanceBanner.add(maintenanceMessage);
        maintenanceBanner.add(maintenanceEndTime);

        // ── ZONE DE PROGRESSION ───────────────────────────────────────────────
        progressBarContainer = new JPanel(null);
        progressBarContainer.setOpaque(false);
        progressBarContainer.setAlignmentX(Component.LEFT_ALIGNMENT);
        progressBarContainer.setMaximumSize(new Dimension(500, 38));
        progressBarContainer.setPreferredSize(new Dimension(500, 38));
        progressBarContainer.setMinimumSize(new Dimension(500, 38));

        progressBarTrack = new JPanel(null) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new Color(255, 255, 255, 30));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 12, 12);
                g2.setColor(new Color(255, 255, 255, 60));
                g2.setStroke(new BasicStroke(1f));
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 12, 12);
                g2.dispose();
            }
        };
        progressBarTrack.setOpaque(false);

        progressBarFill = new JPanel(null) {
            @Override
            protected void paintComponent(Graphics g) {
                if (getWidth() <= 0) return;
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(ACCENT_COLOR);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 12, 12);
                g2.dispose();
            }
        };
        progressBarFill.setOpaque(false);

        progressLabel = new JLabel("0%");
        progressLabel.setForeground(Color.WHITE);
        progressLabel.setFont(new Font("Segoe UI", Font.BOLD, 13));

        progressFileName = new JLabel("");
        progressFileName.setForeground(TEXT_COLOR);
        progressFileName.setFont(new Font("Segoe UI", Font.PLAIN, 11));

        progressBarTrack.add(progressBarFill);
        progressBarContainer.add(progressBarTrack);
        progressBarContainer.add(progressLabel);
        progressBarContainer.add(progressFileName);

        progressBarTrack.setVisible(false);
        progressLabel.setVisible(false);
        progressFileName.setVisible(false);

        // ── ACTION JOUER ──────────────────────────────────────────────────────
        playButton.addActionListener(e -> {
            if (!isUpdating && currentStatus == ServerStatus.OPERATIONAL) startUpdate();
        });

        // ── ASSEMBLAGE ────────────────────────────────────────────────────────
        centralContent.add(titleLabel);
        centralContent.add(Box.createRigidArea(new Dimension(0, 15)));
        centralContent.add(descArea);
        centralContent.add(Box.createRigidArea(new Dimension(0, 30)));
        centralContent.add(playButton);
        centralContent.add(Box.createRigidArea(new Dimension(0, 12)));
        centralContent.add(maintenanceBanner);
        centralContent.add(Box.createRigidArea(new Dimension(0, 8)));
        centralContent.add(progressBarContainer);

        this.add(centralContent);

        this.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) { responsive(); }
        });

        // ── POLLING MAINTENANCE ───────────────────────────────────────────────
        startMaintenancePolling();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // MAINTENANCE POLLING
    // ─────────────────────────────────────────────────────────────────────────

    private void startMaintenancePolling() {
        MaintenanceChecker.start(new MaintenanceChecker.MaintenanceCallback() {

            @Override
            public void onOperational() {
                SwingUtilities.invokeLater(() -> {
                    currentStatus = ServerStatus.OPERATIONAL;
                    maintenanceBanner.setVisible(false);
                    if (!isUpdating) {
                        playButton.setEnabled(true);
                        playButton.setText("JOUER");
                        playButton.setFont(new Font("Segoe UI", Font.BOLD, 20));
                        playButton.setCursor(new Cursor(Cursor.HAND_CURSOR));
                    }
                    repaint();
                });
            }

            @Override
            public void onMaintenance(String message, String endTime) {
                SwingUtilities.invokeLater(() -> {
                    currentStatus = ServerStatus.MAINTENANCE;
                    maintenanceIcon.setText("⚠");
                    maintenanceMessage.setText(message.isEmpty() ? "Serveur en maintenance" : message);
                    maintenanceEndTime.setText(endTime.isEmpty() ? "" : "Reprise estimée : " + endTime);
                    maintenanceBanner.setVisible(true);
                    maintenanceBanner.repaint();
                    if (!isUpdating) {
                        playButton.setEnabled(false);
                        playButton.setText("MAINTENANCE");
                        playButton.setFont(new Font("Segoe UI", Font.BOLD, 15));
                        playButton.setCursor(new Cursor(Cursor.DEFAULT_CURSOR));
                    }
                    layoutMaintenanceBanner();
                    repaint();
                });
            }

            @Override
            public void onApiDown() {
                SwingUtilities.invokeLater(() -> {
                    currentStatus = ServerStatus.API_DOWN;
                    maintenanceIcon.setText("✘");
                    maintenanceMessage.setText("Serveur inaccessible — Vérification en cours...");
                    maintenanceEndTime.setText("");
                    maintenanceBanner.setVisible(true);
                    maintenanceBanner.repaint();
                    if (!isUpdating) {
                        playButton.setEnabled(false);
                        playButton.setText("INDISPONIBLE");
                        playButton.setFont(new Font("Segoe UI", Font.BOLD, 14));
                        playButton.setCursor(new Cursor(Cursor.DEFAULT_CURSOR));
                    }
                    layoutMaintenanceBanner();
                    repaint();
                });
            }
        });
    }

    // ─────────────────────────────────────────────────────────────────────────
    // UPDATE
    // ─────────────────────────────────────────────────────────────────────────

    private void startUpdate() {
        isUpdating = true;
        playButton.setText("VÉRIFICATION...");
        playButton.setEnabled(false);
        progressBarTrack.setVisible(true);

        // On lance tout dans un thread pour que FlowUpdater ne freeze pas la fenêtre
        new Thread(() -> {
            try {
                Launcher.update(new UpdateManager.UpdateCallback() {
                    @Override
                    public void onProgress(int percent, String currentFile) {
                        SwingUtilities.invokeLater(() -> {
                            currentProgress = percent;
                            playButton.setText("MISE À JOUR...");
                            progressFileName.setText(currentFile);
                            progressLabel.setText(percent + "%");
                            repaint();
                        });
                    }

                    @Override
                    public void onSuccess(String serverAddress) {
                        handleLaunch();
                    }

                    @Override
                    public void onAlreadyUpToDate(String serverAddress) {
                        handleLaunch();
                    }

                    @Override
                    public void onError(String message) {
                        SwingUtilities.invokeLater(() -> {
                            isUpdating = false;
                            playButton.setText("RÉESSAYER");
                            playButton.setEnabled(true);
                            progressFileName.setText("✘ " + message);
                        });
                    }
                }, Launcher.userPreferences.get_download_shader());
            } catch (Exception e) {
                e.printStackTrace();
                // Erreur durant FlowUpdater
                SwingUtilities.invokeLater(() -> {
                    isUpdating = false;
                    playButton.setEnabled(true);
                    progressFileName.setText("Erreur Moteur (Forge)");
                });
            }
        }).start();
    }

    // Pour éviter de dupliquer le code de lancement
    private void handleLaunch() {
        SwingUtilities.invokeLater(() -> {
            playButton.setText("LANCEMENT...");
            currentProgress = 100;
            progressLabel.setText("100%");
        });

        MaintenanceChecker.stop();

        new Thread(() -> {
            try {
                Launcher.crack();
                Process gameProcess = Launcher.launch();
                gameProcess.waitFor();

                // Reset UI après fermeture
                SwingUtilities.invokeLater(() -> {
                    isUpdating = false;
                    playButton.setEnabled(true);
                    playButton.setText("JOUER");
                    progressBarTrack.setVisible(false);
                    startMaintenancePolling();
                });
            } catch (Exception e) {
                e.printStackTrace();
            }
        }, "LaunchThread").start();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // LAYOUT
    // ─────────────────────────────────────────────────────────────────────────

    private void layoutProgressBar() {
        int barWidth  = 300;
        int barHeight = 14;
        int barY = Math.max(0, (progressBarContainer.getHeight() / 2) - barHeight - 2);
        progressBarTrack.setBounds(0, barY, barWidth, barHeight);
        progressBarFill.setBounds(0, 0, (int)(barWidth * currentProgress / 100.0), barHeight);
        progressLabel.setBounds(barWidth + 10, barY, 45, barHeight);
        progressFileName.setBounds(0, barY + barHeight + 4, 400, 16);
    }

    private void layoutMaintenanceBanner() {
        int w = maintenanceBanner.getWidth();
        int h = maintenanceBanner.getHeight();
        if (w == 0 || h == 0) return;
        maintenanceIcon.setBounds(14, (h - 24) / 2, 24, 24);
        maintenanceMessage.setBounds(48, 8, w - 58, 20);
        maintenanceEndTime.setBounds(48, 28, w - 58, 16);
    }

    private void responsive() {
        int sidebarWidth  = (int)(this.getWidth() * sidebar_percentage);
        int sidebarHeight = this.getHeight();
        sidebar.setBounds(0, 0, sidebarWidth, sidebarHeight);

        int squareSize = (int)(sidebarWidth * square_percentage);
        sidebar_top.setBounds((sidebarWidth - squareSize) / 2, 10, squareSize, squareSize);
        sidebar_bottom.setBounds((sidebarWidth - squareSize) / 2, sidebarHeight - squareSize - 10, squareSize, squareSize);

        bottomButton.setBounds(0, 0, squareSize, squareSize);
        if (buttonIcon != null) {
            bottomButton.setIcon(new ImageIcon(buttonIcon.getImage().getScaledInstance(squareSize, squareSize, Image.SCALE_SMOOTH)));
        }

        int headSize = (int)(squareSize * 0.7);
        if (skinImage != null) {
            skinHead.setIcon(new ImageIcon(skinImage.getScaledInstance(headSize, headSize, Image.SCALE_SMOOTH)));
        }

        int x = sidebarWidth + 80;
        int y = (this.getHeight() - 480) / 2;
        centralContent.setBounds(x, y, 600, 480);

        if (playIcon != null) {
            playButton.setIcon(new ImageIcon(playIcon.getImage().getScaledInstance(120, 120, Image.SCALE_SMOOTH)));
            playButton.setText("");
        }

        layoutProgressBar();
        layoutMaintenanceBanner();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // BACKGROUND BLUR
    // ─────────────────────────────────────────────────────────────────────────

    private BufferedImage createBlurredImage(BufferedImage img, int radius) {
        if (radius < 1) return img;
        int extra = radius * 2;
        BufferedImage tmp = new BufferedImage(img.getWidth() + extra * 2, img.getHeight() + extra * 2, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = tmp.createGraphics();
        g.drawImage(img, extra, extra, null);
        g.dispose();

        int size = radius * 2 + 1;
        float[] data = new float[size * size];
        float v = 1.0f / (size * size);
        for (int i = 0; i < data.length; i++) data[i] = v;

        BufferedImageOp op = new ConvolveOp(new Kernel(size, size, data), ConvolveOp.EDGE_NO_OP, null);
        BufferedImage current = tmp;
        for (int i = 0; i < 3; i++) current = op.filter(current, null);

        BufferedImage result = new BufferedImage(img.getWidth(), img.getHeight(), img.getType());
        Graphics2D g2 = result.createGraphics();
        g2.drawImage(current, -extra, -extra, null);
        g2.dispose();
        return result;
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        if (blurredBackground != null) g2.drawImage(blurredBackground, 0, 0, getWidth(), getHeight(), null);
        else if (backgroundImage != null) g2.drawImage(backgroundImage, 0, 0, getWidth(), getHeight(), null);
        else { g2.setColor(BACKGROUND_COLOR); g2.fillRect(0, 0, getWidth(), getHeight()); }
        g2.setColor(new Color(0, 0, 0, 150));
        g2.fillRect(0, 0, getWidth(), getHeight());
        g2.dispose();
    }
}