package fr.ruins.launcher;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.awt.image.RasterFormatException;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * SettingsPanel — Paramètres complets du launcher.
 *
 * Layout :
 *   - Sidebar gauche (280 px) : navigation entre les rubriques + Retour + Deconnexion
 *   - Zone droite : contenu centré horizontalement dans l'espace disponible
 *
 * Rubriques : General · Skin · Compte · Graphique · Captures d'ecran
 */
public class SettingsPanel extends JPanel {

    // =====================================
    //  COULEURS
    // =====================================

    private final Color BG            = new Color(13, 13, 13);
    private final Color SIDEBAR_BG    = new Color(16, 16, 16, 252);
    private final Color SIDEBAR_HOV   = new Color(38, 38, 38);
    private final Color SIDEBAR_ACT   = new Color(217, 4, 4, 170);
    private final Color ACCENT        = new Color(217, 4, 4);
    private final Color ACCENT_DK     = new Color(140, 3, 3);
    private final Color TEXT          = new Color(175, 168, 168);
    private final Color TEXT_DIM      = new Color(105, 105, 105);
    private final Color WHITE         = Color.WHITE;
    private final Color CARD_BG       = new Color(24, 24, 24, 235);
    private final Color BORDER        = new Color(48, 48, 48);
    private final Color INPUT_BG_C    = new Color(16, 16, 16);
    private final Color INPUT_FOCUS_C = new Color(217, 4, 4, 130);
    private final Color SUCCESS       = new Color(88, 210, 138);
    private final Color ERR           = new Color(255, 88, 108);
    private final Color SEP_COL       = new Color(38, 38, 38);
    private final Color DANGER_FG     = new Color(255, 108, 108);

    // ═══════════════════════════════════════════════════
    //  DIMENSIONS
    // ═══════════════════════════════════════════════════

    private static final int SIDEBAR_W = 280;   // largeur de la sidebar
    private static final int CONTENT_W = 740;   // largeur du panneau de contenu
    private static final int PAD       = 30;    // padding interne des cartes

    // =====================================
    //  POLICES
    // =====================================

    private static final Font F_SECTION = new Font("Segoe UI", Font.BOLD, 19);
    private static final Font F_LABEL   = new Font("Segoe UI", Font.BOLD, 15);
    private static final Font F_VALUE   = new Font("Segoe UI", Font.PLAIN, 15);
    private static final Font F_SMALL   = new Font("Segoe UI", Font.PLAIN, 12);
    private static final Font F_NAV     = new Font("Segoe UI", Font.PLAIN, 17);
    private static final Font F_NAV_ACT = new Font("Segoe UI", Font.BOLD,  17);
    private static final Font F_BTN     = new Font("Segoe UI", Font.BOLD,  15);
    private static final Font F_BTN_SM  = new Font("Segoe UI", Font.PLAIN, 13);

    // =====================================
    //  ETAT
    // =====================================

    private BufferedImage backgroundImage;
    private BufferedImage blurredBackground;

    private JPanel     sidebar;
    private JPanel     contentArea;
    private CardLayout cardLayout;

    private String    activeSection = "general";
    private JButton[] navButtons;

    private File            selectedSkinFile;
    private JLabel          skinPreviewLabel;

    // =====================================
    //  CONSTRUCTEUR
    // =====================================

    public SettingsPanel() throws IOException {
        this.setLayout(null);
        this.setOpaque(false);

        backgroundImage = Frame.getBufferdImage("background_1.png");
        if (backgroundImage != null) {
            final BufferedImage src = backgroundImage;
            new Thread(() -> {
                BufferedImage b = applyBlur(src, 5);
                SwingUtilities.invokeLater(() -> { blurredBackground = b; repaint(); });
            }, "BlurThread-Settings").start();
        }

        buildSidebar();
        buildContent();

        this.addComponentListener(new ComponentAdapter() {
            @Override public void componentResized(ComponentEvent e) { doLayout2(); }
        });
    }

    @Override
    public void addNotify() {
        super.addNotify();
        SwingUtilities.invokeLater(() -> {
            doLayout2();
            goTo(activeSection); // ← force le CardLayout à re-rendre la section active
            revalidate();
            repaint();
        });
    }

    // =====================================
    //  SIDEBAR
    // =====================================

    private void buildSidebar() {
        sidebar = new JPanel(null) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setColor(SIDEBAR_BG);
                g2.fillRect(0, 0, getWidth(), getHeight());
                g2.setColor(SEP_COL);
                g2.drawLine(getWidth() - 1, 0, getWidth() - 1, getHeight());
                g2.dispose();
            }
        };
        sidebar.setOpaque(false);

        // Titre
        JLabel logo = new JLabel("RUINS");
        logo.setFont(new Font("Segoe UI", Font.BOLD, 30));
        logo.setForeground(ACCENT);
        logo.setBounds(26, 30, 200, 38);
        sidebar.add(logo);

        JLabel sub = new JLabel("Parametres");
        sub.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        sub.setForeground(TEXT_DIM);
        sub.setBounds(26, 66, 200, 22);
        sidebar.add(sub);

        JSeparator s1 = sep(); s1.setBounds(14, 98, SIDEBAR_W - 28, 1); sidebar.add(s1);

        // Rubriques
        String[][] sections = {
                {"general",   "General"},
                // {"skin",      "Skin"},
                {"compte",    "Compte"},
                {"graphique", "Graphique"},
                {"captures",  "Captures d'ecran"},
        };

        navButtons = new JButton[sections.length];
        int y = 114;
        for (int i = 0; i < sections.length; i++) {
            final String id = sections[i][0];
            JButton b = navBtn(sections[i][1], id);
            b.setBounds(6, y, SIDEBAR_W - 12, 54);
            navButtons[i] = b;
            sidebar.add(b);
            y += 58;
        }

        // Boutons bas
        JSeparator s2 = sep();
        JButton backB   = sideBtn("Retour",        false);
        JButton logoutB = sideBtn("Deconnexion",   true);
        backB.addActionListener(e -> Frame.getInstance().showHome());
        logoutB.addActionListener(e -> { Launcher.session = null; Frame.getInstance().showAuth(); });

        sidebar.putClientProperty("s2",     s2);
        sidebar.putClientProperty("back",   backB);
        sidebar.putClientProperty("logout", logoutB);
        sidebar.add(s2); sidebar.add(backB); sidebar.add(logoutB);

        this.add(sidebar);
    }

    // =====================================
    //  ZONE DE CONTENU
    // =====================================

    private void buildContent() {
        cardLayout  = new CardLayout();
        contentArea = new JPanel(cardLayout);
        contentArea.setOpaque(false);

        contentArea.add(sectionGeneral(),   "general");
        // contentArea.add(sectionSkin(),      "skin");
        contentArea.add(section_compte(),    "compte");
        contentArea.add(sectionGraphique(), "graphique");
        contentArea.add(sectionCaptures(),  "captures");

        this.add(contentArea);
        goTo("general");
    }

    // =====================================
    //  SECTION - GENERAL
    // =====================================

    private JPanel sectionGeneral() {
        UserPreferences prefs = Launcher.userPreferences;

        JPanel root = scrollRoot();
        int y = 0;

        // Langue
//        y = secTitle(root, "Langue de l'application", y);
//        JPanel languageCard = card(root, y, CONTENT_W, 86);
//        y += 86 + 12;
//
//        lbl(languageCard, "Langue", PAD, 14, F_LABEL, TEXT);
//
//        JComboBox<String> languageComboBox = combo(new String[]{"Francais"});
//        languageComboBox.setEnabled(false);
//        languageComboBox.setBounds(PAD, 38, 270, 38);
//        languageCard.add(languageComboBox);
//
//        lbl(languageCard, "Seule langue disponible.", PAD + 290, 50, F_SMALL, TEXT_DIM);

        // Installation
        y = secTitle(root, "Installation", y);
        JPanel repairCard = card(root, y, CONTENT_W, 92);
        y += 92 + 12;

        lbl(repairCard, "Reparer l'installation de Ruins", PAD, 14, F_LABEL, WHITE);
        lbl(repairCard, "Supprime et re-telecharge tous les fichiers du modpack.", PAD, 38, F_SMALL, TEXT);

        JButton repairButton = btn("Reparer", false);
        repairButton.setBounds(CONTENT_W - PAD - 160, 24, 160, 44);
        repairCard.add(repairButton);

        JLabel repairFeedback = fb("");
        repairFeedback.setBounds(PAD, 70, 500, 18);
        repairCard.add(repairFeedback);

        repairButton.addActionListener(e -> {
            int confirm = JOptionPane.showConfirmDialog(
                    root,
                    "Voulez-vous vraiment TOUT réinstaller ? \n(Vos mondes et captures d'écran seront supprimés)",
                    "Confirmation de réinitialisation",
                    JOptionPane.YES_NO_OPTION
            );

            if (confirm == JOptionPane.YES_OPTION) {
                // 1. On vide le dossier (sauf les préférences)
                prefs.full_reset(Launcher.path);

                // 2. On affiche le feedback
                showFb(repairFeedback, "Nettoyage terminé. Relancez le jeu pour réinstaller.", true);

                // 3. Optionnel : On peut forcer la fermeture ou le rafraîchissement
                repairButton.setEnabled(false);
            }
        });

        // Mémoire RAM
        y = secTitle(root, "Mémoire RAM", y);

        final int ramCardHeight = 312;
        JPanel ramCard = card(root, y, CONTENT_W, ramCardHeight);
        y += ramCardHeight + 12;

        int totalRamMb = prefs.get_ram_max_device();
        int[] ramPalette = prefs.ram_pallette();

        int initialStartIndex = prefs.closest_index(ramPalette, prefs.get_ram_start_mb());
        int initialMaxIndex = prefs.closest_index(ramPalette, prefs.get_ram_max_mb());

        // Titres
        JLabel ramStartTitleLabel = new JLabel("RAM au démarrage");
        ramStartTitleLabel.setFont(new Font("Segoe UI", Font.BOLD, 14));
        ramStartTitleLabel.setForeground(TEXT);
        ramStartTitleLabel.setBounds(PAD, 12, 220, 20);
        ramCard.add(ramStartTitleLabel);

        JLabel ramMaxTitleLabel = new JLabel("RAM en jeu");
        ramMaxTitleLabel.setFont(new Font("Segoe UI", Font.BOLD, 14));
        ramMaxTitleLabel.setForeground(TEXT);
        ramMaxTitleLabel.setBounds(PAD, 154, 220, 20);
        ramCard.add(ramMaxTitleLabel);

        // Valeurs
        JLabel ramStartValueLabel = new JLabel(prefs.get_ram_start_display());
        ramStartValueLabel.setFont(new Font("Segoe UI", Font.BOLD, 28));
        ramStartValueLabel.setForeground(WHITE);
        ramStartValueLabel.setBounds(PAD, 34, 220, 36);
        ramCard.add(ramStartValueLabel);

        JLabel ramMaxValueLabel = new JLabel(prefs.get_ram_max_display());
        ramMaxValueLabel.setFont(new Font("Segoe UI", Font.BOLD, 28));
        ramMaxValueLabel.setForeground(WHITE);
        ramMaxValueLabel.setBounds(PAD, 176, 220, 36);
        ramCard.add(ramMaxValueLabel);

        // Sliders
        JSlider ramStartSlider = new JSlider(0, ramPalette.length - 1, initialStartIndex);
        JSlider ramMaxSlider = new JSlider(0, ramPalette.length - 1, initialMaxIndex);

        styleSlider(ramStartSlider);
        styleSlider(ramMaxSlider);

        ramStartSlider.setBounds(PAD, 78, CONTENT_W - PAD * 2, 30);
        ramMaxSlider.setBounds(PAD, 220, CONTENT_W - PAD * 2, 30);

        ramCard.add(ramStartSlider);
        ramCard.add(ramMaxSlider);

        // Libellés sous les sliders
        lbl(ramCard, "512 Mo", PAD, 112, F_SMALL, TEXT_DIM);
        lbl(ramCard, Launcher.userPreferences.format_ram(totalRamMb), CONTENT_W - PAD - 90, 112, F_SMALL, TEXT_DIM);

        lbl(ramCard, "512 Mo", PAD, 254, F_SMALL, TEXT_DIM);
        lbl(ramCard, Launcher.userPreferences.format_ram(totalRamMb), CONTENT_W - PAD - 90, 254, F_SMALL, TEXT_DIM);

        // Synchronisation des deux sliders
        final boolean[] isUpdatingRamSliders = {false};

        ramStartSlider.addChangeListener(e -> {
            if (isUpdatingRamSliders[0]) {
                return;
            }

            isUpdatingRamSliders[0] = true;
            try {
                int startMb = ramPalette[ramStartSlider.getValue()];
                int maxMb = ramPalette[ramMaxSlider.getValue()];

                // Sécurité : Xms <= Xmx
                if (startMb > maxMb) {
                    ramMaxSlider.setValue(ramStartSlider.getValue());
                    maxMb = startMb;
                }

                prefs.set_ram_start_mb(startMb);
                prefs.set_ram_max_mb(maxMb);

                ramStartValueLabel.setText(prefs.get_ram_start_display());
                ramMaxValueLabel.setText(prefs.get_ram_max_display());
            } finally {
                isUpdatingRamSliders[0] = false;
            }
        });

        ramMaxSlider.addChangeListener(e -> {
            if (isUpdatingRamSliders[0]) {
                return;
            }

            isUpdatingRamSliders[0] = true;
            try {
                int startMb = ramPalette[ramStartSlider.getValue()];
                int maxMb = ramPalette[ramMaxSlider.getValue()];

                // Sécurité : Xms <= Xmx
                if (maxMb < startMb) {
                    ramStartSlider.setValue(ramMaxSlider.getValue());
                    startMb = maxMb;
                }

                prefs.set_ram_start_mb(startMb);
                prefs.set_ram_max_mb(maxMb);

                ramStartValueLabel.setText(prefs.get_ram_start_display());
                ramMaxValueLabel.setText(prefs.get_ram_max_display());
            } finally {
                isUpdatingRamSliders[0] = false;
            }
        });

        y += 30;
        JButton applyButton = btn("Appliquer les changements", true);
        applyButton.setBounds(0, y, 310, 52);
        root.add(applyButton);

        JLabel applyFeedback = fb("");
        applyFeedback.setBounds(324, y + 16, 400, 20);
        root.add(applyFeedback);

        applyButton.addActionListener(e -> { prefs.save(Launcher.path); showFb(applyFeedback, "Parametres sauvegardes !", true); });

        root.setPreferredSize(new Dimension(CONTENT_W, y + 80));
        return wrap(root);
    }

    // =====================================
    //  SECTION — SKIN
    // =====================================

    private JPanel sectionSkin() {
        JPanel root = scrollRoot();
        int y = 0;

        y = secTitle(root, "Skin du joueur", y);

        JPanel skinCard = card(root, y, CONTENT_W, 310); y += 310 + 12;

        // Apercu
        JPanel prev = new JPanel(null) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(INPUT_BG_C); g2.fillRoundRect(0,0,getWidth(),getHeight(),12,12);
                g2.setColor(BORDER);    g2.drawRoundRect(0,0,getWidth()-1,getHeight()-1,12,12);
                g2.dispose();
            }
        };
        prev.setOpaque(false); prev.setBounds(PAD, PAD, 190, 260); skinCard.add(prev);

        skinPreviewLabel = new JLabel("Aucun skin", SwingConstants.CENTER);
        skinPreviewLabel.setForeground(TEXT_DIM); skinPreviewLabel.setFont(F_SMALL);
        skinPreviewLabel.setBounds(0, 0, 190, 260); prev.add(skinPreviewLabel);
        // if (prefs.getSkinPath() != null) loadSkinPrev(new File(prefs.getSkinPath()));

        // Droite
        int rx = PAD + 190 + 24, rw = CONTENT_W - rx - PAD;
        lbl(skinCard, "Modifier le skin", rx, 16, F_LABEL, WHITE);

//        JLabel skinName = new JLabel(prefs.getSkinPath() != null
//                ? new File(prefs.getSkinPath()).getName() : "Aucun skin selectionne");
//        skinName.setFont(F_VALUE); skinName.setForeground(TEXT);
//        skinName.setBounds(rx, 44, rw, 22); skinCard.add(skinName);

        JPanel drop = new JPanel(null) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new Color(26,26,26,200)); g2.fillRoundRect(0,0,getWidth(),getHeight(),12,12);
                g2.setColor(new Color(88,88,88));
                float[] dash = {7f,5f};
                g2.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 0, dash, 0));
                g2.drawRoundRect(1,1,getWidth()-2,getHeight()-2,12,12);
                g2.dispose();
            }
        };
        drop.setOpaque(false); drop.setBounds(rx, 74, rw, 120); drop.setCursor(new Cursor(Cursor.HAND_CURSOR));

        JLabel dTxt = new JLabel("Glisser un fichier PNG ici", SwingConstants.CENTER);
        dTxt.setFont(F_LABEL); dTxt.setForeground(WHITE); dTxt.setBounds(0, 26, rw, 24); drop.add(dTxt);
        JLabel dSub = new JLabel("ou cliquer pour parcourir", SwingConstants.CENTER);
        dSub.setFont(F_SMALL); dSub.setForeground(TEXT_DIM); dSub.setBounds(0, 56, rw, 18); drop.add(dSub);
        JLabel dFmt = new JLabel("Format : 64x64 ou 64x32 px", SwingConstants.CENTER);
        dFmt.setFont(F_SMALL); dFmt.setForeground(TEXT_DIM); dFmt.setBounds(0, 80, rw, 18); drop.add(dFmt);
        skinCard.add(drop);

        JLabel skinFb = fb(""); skinFb.setBounds(rx, 202, rw, 18); skinCard.add(skinFb);
        JButton applyS = btn("Appliquer", true); applyS.setBounds(rx, 226, 170, 48); skinCard.add(applyS);
        JButton resetS = btn("Reinitialiser", false); resetS.setBounds(rx + 178, 226, 160, 48); skinCard.add(resetS);

//        new DropTarget(drop, new DropTargetAdapter() {
//            @Override public void drop(DropTargetDropEvent ev) {
//                ev.acceptDrop(DnDConstants.ACTION_COPY);
//                try {
//                    @SuppressWarnings("unchecked")
//                    List<File> list = (List<File>) ev.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);
//                    if (!list.isEmpty()) handleSkin(list.get(0), skinName, skinFb);
//                } catch (Exception ignored) {}
//            }
//        });
//        drop.addMouseListener(new MouseAdapter() {
//            @Override public void mousePressed(MouseEvent e) { openSkinChooser(skinName, skinFb); }
//        });
//        applyS.addActionListener(e -> applySkin(skinFb));
//        resetS.addActionListener(e -> {
//            selectedSkinFile = null; prefs.setSkinPath(null); prefs.save();
//            skinPreviewLabel.setIcon(null); skinPreviewLabel.setText("Aucun skin");
//            skinName.setText("Aucun skin selectionne"); showFb(skinFb, "Skin reinitialise.", true);
//        });

        root.setPreferredSize(new Dimension(CONTENT_W, y + 40));
        return wrap(root);
    }

    // =====================================
    //  SECTION — COMPTE
    // =====================================

    private JPanel section_compte() {

        JPanel root_panel = scrollRoot();
        int current_y = 0;

        current_y = secTitle(root_panel, "Mon compte", current_y);

        // =========================
        // USERNAME
        // =========================
        JPanel username_card = card(root_panel, current_y, CONTENT_W, 114);
        current_y += 114 + 10;

        lbl(username_card, "Pseudo", PAD, 12, F_LABEL, TEXT);

        JLabel username_value_label = new JLabel(Launcher.session.getUsername());
        username_value_label.setFont(new Font("Segoe UI", Font.BOLD, 22));
        username_value_label.setForeground(WHITE);
        username_value_label.setBounds(PAD, 34, 440, 30);
        username_card.add(username_value_label);

//        JButton username_edit_button = btn("Modifier", false);
//        username_edit_button.setFont(F_BTN_SM);
//        username_edit_button.setBounds(CONTENT_W - PAD - 130, 30, 130, 38);
//        username_card.add(username_edit_button);
//
//        JTextField username_input_field = input("Nouveau pseudo...");
//        username_input_field.setBounds(PAD, 70, 340, 38);
//        username_input_field.setVisible(false);
//        username_card.add(username_input_field);
//
//        JLabel username_error_label = new JLabel("");
//        username_error_label.setFont(F_SMALL);
//        username_error_label.setForeground(ERR);
//        username_error_label.setBounds(PAD, 100, 440, 16);
//        username_error_label.setVisible(false);
//        username_card.add(username_error_label);
//
//        username_edit_button.addActionListener(e -> {
//            boolean is_editing = !username_input_field.isVisible();
//
//            username_input_field.setVisible(is_editing);
//            username_error_label.setVisible(is_editing);
//            username_edit_button.setText(is_editing ? "Valider" : "Modifier");
//
//            if (!is_editing) {
//                String new_username = username_input_field.getText().trim();
//
//                if (!new_username.isEmpty() && !new_username.equals("Nouveau pseudo...")) {
//
//
//                    username_error_label.setText("");
//                }
//            }
//        });


        // =========================
        // EMAIL
        // =========================
        JPanel email_card = card(root_panel, current_y, CONTENT_W, 114);
        current_y += 114 + 10;

        lbl(email_card, "Adresse e-mail", PAD, 12, F_LABEL, TEXT);

        JLabel email_value_label = new JLabel(Launcher.session.getEmail());
        email_value_label.setFont(new Font("Segoe UI", Font.BOLD, 20));
        email_value_label.setForeground(WHITE);
        email_value_label.setBounds(PAD, 34, 470, 28);
        email_card.add(email_value_label);

//        JButton email_edit_button = btn("Modifier", false);
//        email_edit_button.setFont(F_BTN_SM);
//        email_edit_button.setBounds(CONTENT_W - PAD - 130, 30, 130, 38);
//        email_card.add(email_edit_button);
//
//        JTextField email_input_field = input("Nouvel e-mail...");
//        email_input_field.setBounds(PAD, 70, 340, 38);
//        email_input_field.setVisible(false);
//        email_card.add(email_input_field);
//
//        JLabel email_error_label = new JLabel("");
//        email_error_label.setFont(F_SMALL);
//        email_error_label.setForeground(ERR);
//        email_error_label.setBounds(PAD, 100, 440, 16);
//        email_error_label.setVisible(false);
//        email_card.add(email_error_label);
//
//        email_edit_button.addActionListener(e -> {
//            boolean is_editing = !email_input_field.isVisible();
//
//            email_input_field.setVisible(is_editing);
//            email_error_label.setVisible(is_editing);
//            email_edit_button.setText(is_editing ? "Valider" : "Modifier");
//
//            if (!is_editing) {
//                String new_email = email_input_field.getText().trim();
//
//                if (!new_email.isEmpty() && !new_email.equals("Nouvel e-mail...")) {
//                    email_value_label.setText(new_email);
//                    email_error_label.setText("");
//                }
//            }
//        });


        // =========================
        // ACTIONS
        // =========================
        current_y += 20;
        current_y = secTitle(root_panel, "Actions", current_y);

        // DELETE SESSION
        JPanel delete_session_card = card(root_panel, current_y, CONTENT_W, 90);
        current_y += 90 + 10;

        lbl(delete_session_card, "Supprimer le fichier de session", PAD, 14, F_LABEL, WHITE);
        lbl(delete_session_card, "Force une reconnexion au prochain demarrage.", PAD, 40, F_SMALL, TEXT);

        JButton delete_session_button = dangerBtn("Supprimer");
        delete_session_button.setFont(F_BTN_SM);
        delete_session_button.setBounds(CONTENT_W - PAD - 150, 24, 150, 42);
        delete_session_card.add(delete_session_button);

        JLabel delete_session_feedback = fb("");
        delete_session_feedback.setBounds(PAD, 70, 500, 16);
        delete_session_card.add(delete_session_feedback);

        delete_session_button.addActionListener(e -> {
            File session_file = new File(Launcher.path.toFile(), "session.json");
            boolean deleted = session_file.delete();

            showFb(
                    delete_session_feedback,
                    deleted ? "Fichier de session supprime." : "Fichier introuvable.",
                    deleted
            );
        });


        root_panel.setPreferredSize(new Dimension(CONTENT_W, current_y + 40));

        return wrap(root_panel);
    }

    // =====================================
    //  SECTION — GRAPHIQUE
    // =====================================

    private JPanel sectionGraphique() {
        JPanel root = scrollRoot();
        int y = 0;

        y = secTitle(root, "Shaders", y);
        JPanel togCard = card(root, y, CONTENT_W, 80);
        y += 80 + 12;

        lbl(togCard, "Activer les shaders", PAD, 14, F_LABEL, WHITE);
        lbl(togCard, "Active ou désactive les shaders au lancement du jeu.", PAD, 40, F_SMALL, TEXT);

        boolean currentSetting = Launcher.userPreferences.get_download_shader();
        JToggleButton tog = toggle("Désactiver", "Activer", currentSetting);
        tog.setBounds(CONTENT_W - PAD - 140, 18, 140, 44);

        tog.addActionListener(e -> {
            boolean isSelected = tog.isSelected();

            Launcher.userPreferences.set_download_shader(isSelected);

            Launcher.userPreferences.save(Launcher.path);

            System.out.println("[UI] Shaders mis à jour : " + isSelected);
        });

        togCard.add(tog);

        root.setPreferredSize(new Dimension(CONTENT_W, y + 80));
        return wrap(root);
    }

    // =====================================
    //  SECTION — CAPTURES
    // =====================================

    private JPanel sectionCaptures() {
        JPanel root = scrollRoot();
        int y = 0;

        y = secTitle(root, "Captures d'ecran", y);

        JButton openDirBtn = btn("Ouvrir le dossier", false);
        openDirBtn.setBounds(0, y, 230, 50); root.add(openDirBtn);
        y += 66;

        Path dir = Launcher.path.resolve("screenshots");
        openDirBtn.addActionListener(e -> {
            try {
                if (!Files.exists(dir)) Files.createDirectories(dir);
                Desktop.getDesktop().open(dir.toFile());
            } catch (IOException ex) { ex.printStackTrace(); }
        });

        JPanel grid = new JPanel(new FlowLayout(FlowLayout.LEFT, 14, 14));
        grid.setOpaque(false); grid.setBounds(0, y, CONTENT_W, 700); root.add(grid);

        new Thread(() -> {
            try {
                if (!Files.exists(dir)) {
                    SwingUtilities.invokeLater(() -> emptyMsg(grid, "Aucune capture d'ecran."));
                    return;
                }
                File[] files = dir.toFile().listFiles(f ->
                        f.getName().toLowerCase().endsWith(".png")
                                || f.getName().toLowerCase().endsWith(".jpg"));
                if (files == null || files.length == 0) {
                    SwingUtilities.invokeLater(() -> emptyMsg(grid, "Aucune capture d'ecran."));
                    return;
                }
                for (File f : files) {
                    BufferedImage img = ImageIO.read(f);
                    if (img == null) continue;
                    Image thumb = img.getScaledInstance(212, 119, Image.SCALE_SMOOTH);
                    SwingUtilities.invokeLater(() -> {
                        JPanel tile = new JPanel(null) {
                            @Override protected void paintComponent(Graphics g) {
                                Graphics2D g2 = (Graphics2D) g.create();
                                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                                g2.setColor(CARD_BG);
                                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
                                g2.dispose();
                            }
                        };
                        tile.setOpaque(false); tile.setPreferredSize(new Dimension(212, 162));
                        JLabel imgL = new JLabel(new ImageIcon(thumb));
                        imgL.setBounds(0, 0, 212, 119); tile.add(imgL);
                        JButton openB = tileBtn("Ouvrir");
                        openB.setBounds(4, 124, 98, 32); tile.add(openB);
                        openB.addActionListener(ev -> {
                            try { Desktop.getDesktop().open(f); } catch (IOException ignored) {}
                        });
                        JButton delB = tileBtn("Supprimer"); delB.setForeground(DANGER_FG);
                        delB.setBounds(110, 124, 98, 32); tile.add(delB);
                        delB.addActionListener(ev -> {
                            if (f.delete()) { grid.remove(tile); grid.revalidate(); grid.repaint(); }
                        });
                        grid.add(tile); grid.revalidate();
                    });
                }
            } catch (IOException ignored) {}
        }, "ScreenLoader").start();

        root.setPreferredSize(new Dimension(CONTENT_W, y + 800));
        return wrap(root);
    }

    private void emptyMsg(JPanel g, String msg) {
        JLabel l = new JLabel(msg); l.setFont(F_VALUE); l.setForeground(TEXT_DIM);
        l.setPreferredSize(new Dimension(CONTENT_W, 40)); g.add(l); g.revalidate();
    }

    // =====================================
    //  NAVIGATION
    // =====================================

    private void goTo(String id) {
        activeSection = id;
        cardLayout.show(contentArea, id);
        for (JButton b : navButtons) { b.repaint(); b.setFont(F_NAV); }
        contentArea.revalidate(); // ← ajouter
        contentArea.repaint();    // ← ajouter
    }

    // =====================================
    //  SKIN HELPERS
    // =====================================

    private void openSkinChooser(JLabel n, JLabel fb2) {
        JFileChooser ch = new JFileChooser();
        ch.setFileFilter(new FileNameExtensionFilter("PNG", "png"));
        ch.setAcceptAllFileFilterUsed(false);
        if (ch.showOpenDialog(this) == JFileChooser.APPROVE_OPTION)
            handleSkin(ch.getSelectedFile(), n, fb2);
    }

    private void handleSkin(File f, JLabel n, JLabel fb2) {
        try {
            BufferedImage img = ImageIO.read(f);
            if (img == null) { showFb(fb2, "Fichier PNG invalide.", false); return; }
            if (img.getWidth() != 64 || (img.getHeight() != 64 && img.getHeight() != 32)) {
                showFb(fb2, "Dimensions invalides (64x64 ou 64x32).", false); return;
            }
            selectedSkinFile = f; n.setText(f.getName()); loadSkinPrev(f);
            showFb(fb2, "Fichier charge — cliquer sur Appliquer.", true);
        } catch (IOException ex) { showFb(fb2, "Erreur de lecture.", false); }
    }

    private void applySkin(JLabel fb2) {
        if (selectedSkinFile == null) { showFb(fb2, "Aucun fichier selectionne.", false); return; }
        try {
            Path d = Launcher.path.resolve("skins"); Files.createDirectories(d);
            Path dest = d.resolve(Launcher.session.getUsername() + ".png");
            Files.copy(selectedSkinFile.toPath(), dest, StandardCopyOption.REPLACE_EXISTING);
            // prefs.setSkinPath(dest.toString()); prefs.save();
            showFb(fb2, "Skin applique !", true);
        } catch (IOException ex) { showFb(fb2, "Erreur : " + ex.getMessage(), false); }
    }

    private void loadSkinPrev(File f) {
        try {
            BufferedImage full = ImageIO.read(f); if (full == null) return;
            BufferedImage head = full.getSubimage(8, 8, 8, 8);
            BufferedImage prev = new BufferedImage(170, 170, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2 = prev.createGraphics();
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            g2.drawImage(head, 0, 0, 170, 170, null); g2.dispose();
            SwingUtilities.invokeLater(() -> { skinPreviewLabel.setIcon(new ImageIcon(prev)); skinPreviewLabel.setText(""); });
        } catch (IOException | RasterFormatException ignored) {}
    }


    // =====================================
    //  HELPERS UI
    // =====================================

    private JPanel scrollRoot() { JPanel p = new JPanel(null); p.setOpaque(false); return p; }

    private JPanel wrap(JPanel content) {
        JScrollPane sc = new JScrollPane(content);
        sc.setOpaque(false); sc.getViewport().setOpaque(false); sc.setBorder(null);
        sc.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        sc.getVerticalScrollBar().setUnitIncrement(22);
        JPanel w = new JPanel(new BorderLayout()); w.setOpaque(false); w.add(sc, BorderLayout.CENTER); return w;
    }

    private int secTitle(JPanel p, String t, int y) {
        JLabel l = new JLabel(t); l.setFont(F_SECTION); l.setForeground(WHITE);
        l.setBounds(0, y, CONTENT_W, 28); p.add(l);
        JSeparator s = sep(); s.setBounds(0, y + 30, CONTENT_W, 1); p.add(s);
        return y + 48;
    }

    private JPanel card(JPanel par, int y, int w, int h) {
        JPanel c = new JPanel(null) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(CARD_BG); g2.fillRoundRect(0,0,getWidth(),getHeight(),14,14);
                g2.setColor(BORDER); g2.setStroke(new BasicStroke(1f));
                g2.drawRoundRect(0,0,getWidth()-1,getHeight()-1,14,14);
                g2.dispose();
            }
        };
        c.setOpaque(false); c.setBounds(0, y, w, h); par.add(c); return c;
    }

    private JLabel lbl(JPanel p, String t, int x, int y, Font f, Color c) {
        JLabel l = new JLabel(t); l.setFont(f); l.setForeground(c);
        l.setBounds(x, y, CONTENT_W - x - 8, 26); p.add(l); return l;
    }

    private JButton navBtn(String text, String id) {
        JButton b = new JButton(text) {
            private boolean hov = false;
            { addMouseListener(new MouseAdapter() {
                public void mouseEntered(MouseEvent e) { hov = true;  repaint(); }
                public void mouseExited (MouseEvent e) { hov = false; repaint(); }
            }); }
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                boolean act = id.equals(activeSection);
                if      (act) g2.setColor(SIDEBAR_ACT);
                else if (hov) g2.setColor(SIDEBAR_HOV);
                else          g2.setColor(new Color(0,0,0,0));
                g2.fillRoundRect(0,0,getWidth(),getHeight(),10,10);
                if (act) { g2.setColor(ACCENT); g2.setStroke(new BasicStroke(4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)); g2.drawLine(0,8,0,getHeight()-8); }
                super.paintComponent(g);
                g2.dispose();
            }
            @Override protected void paintBorder(Graphics g) {}
        };
        b.setOpaque(false); b.setContentAreaFilled(false); b.setBorderPainted(false); b.setFocusPainted(false);
        b.setForeground(WHITE); b.setFont(F_NAV); b.setHorizontalAlignment(SwingConstants.LEFT);
        b.setBorder(BorderFactory.createEmptyBorder(0, 20, 0, 0));
        b.setCursor(new Cursor(Cursor.HAND_CURSOR)); b.addActionListener(e -> goTo(id)); return b;
    }

    private JButton sideBtn(String text, boolean danger) {
        JButton b = new JButton(text) {
            private boolean hov = false;
            { addMouseListener(new MouseAdapter() {
                public void mouseEntered(MouseEvent e) { hov = true;  repaint(); }
                public void mouseExited (MouseEvent e) { hov = false; repaint(); }
            }); }
            @Override protected void paintComponent(Graphics g) {
                if (hov) { Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setColor(danger ? new Color(180,28,28,180) : SIDEBAR_HOV);
                    g2.fillRoundRect(0,0,getWidth(),getHeight(),10,10); g2.dispose(); }
                super.paintComponent(g);
            }
            @Override protected void paintBorder(Graphics g) {}
        };
        b.setOpaque(false); b.setContentAreaFilled(false); b.setBorderPainted(false); b.setFocusPainted(false);
        b.setForeground(danger ? DANGER_FG : TEXT); b.setFont(F_NAV);
        b.setHorizontalAlignment(SwingConstants.LEFT);
        b.setBorder(BorderFactory.createEmptyBorder(0, 20, 0, 0));
        b.setCursor(new Cursor(Cursor.HAND_CURSOR)); return b;
    }

    private JButton btn(String t, boolean primary) {
        JButton b = new JButton(t) {
            private boolean hov = false;
            { addMouseListener(new MouseAdapter() {
                public void mouseEntered(MouseEvent e) { hov = true;  repaint(); }
                public void mouseExited (MouseEvent e) { hov = false; repaint(); }
            }); }
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(primary ? (hov ? ACCENT_DK : ACCENT) : (hov ? new Color(64,64,64) : new Color(40,40,40)));
                g2.fillRoundRect(0,0,getWidth(),getHeight(),22,22); super.paintComponent(g); g2.dispose();
            }
            @Override protected void paintBorder(Graphics g) {}
        };
        b.setOpaque(false); b.setContentAreaFilled(false); b.setBorderPainted(false); b.setFocusPainted(false);
        b.setForeground(WHITE); b.setFont(F_BTN); b.setCursor(new Cursor(Cursor.HAND_CURSOR)); return b;
    }

    private JButton dangerBtn(String t) { JButton b = btn(t, false); b.setForeground(DANGER_FG); return b; }

    private JButton tileBtn(String t) {
        JButton b = new JButton(t) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new Color(42,42,42)); g2.fillRoundRect(0,0,getWidth(),getHeight(),8,8);
                super.paintComponent(g); g2.dispose();
            }
            @Override protected void paintBorder(Graphics g) {}
        };
        b.setOpaque(false); b.setContentAreaFilled(false); b.setBorderPainted(false); b.setFocusPainted(false);
        b.setForeground(WHITE); b.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        b.setCursor(new Cursor(Cursor.HAND_CURSOR)); return b;
    }

    private JToggleButton toggle(String off, String on, boolean init) {
        JToggleButton t = new JToggleButton(init ? on : off, init) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(isSelected() ? ACCENT : INPUT_BG_C);
                g2.fillRoundRect(0,0,getWidth(),getHeight(),24,24);
                g2.setColor(isSelected() ? ACCENT_DK : BORDER); g2.setStroke(new BasicStroke(1f));
                g2.drawRoundRect(0,0,getWidth()-1,getHeight()-1,24,24);
                super.paintComponent(g); g2.dispose();
            }
            @Override protected void paintBorder(Graphics g) {}
        };
        t.setOpaque(false); t.setContentAreaFilled(false); t.setBorderPainted(false); t.setFocusPainted(false);
        t.setForeground(WHITE); t.setFont(F_BTN_SM); t.setCursor(new Cursor(Cursor.HAND_CURSOR));
        t.addItemListener(e -> t.setText(t.isSelected() ? on : off)); return t;
    }

    private JTextField input(String ph) {
        JTextField f = new JTextField() {
            private boolean foc = false;
            { addFocusListener(new FocusAdapter() {
                public void focusGained(FocusEvent e) { foc = true;  repaint(); }
                public void focusLost (FocusEvent e) { foc = false; repaint(); }
            }); }
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(INPUT_BG_C); g2.fillRoundRect(0,0,getWidth(),getHeight(),10,10);
                g2.setColor(foc ? INPUT_FOCUS_C : BORDER);
                g2.setStroke(new BasicStroke(foc ? 1.5f : 1f));
                g2.drawRoundRect(0,0,getWidth()-1,getHeight()-1,10,10);
                g2.dispose(); super.paintComponent(g);
            }
        };
        f.setOpaque(false); f.setBorder(BorderFactory.createEmptyBorder(0,14,0,14));
        f.setForeground(TEXT_DIM); f.setCaretColor(WHITE); f.setFont(F_VALUE); f.setText(ph);
        f.addFocusListener(new FocusAdapter() {
            public void focusGained(FocusEvent e) { if (f.getText().equals(ph)) { f.setText(""); f.setForeground(WHITE); } }
            public void focusLost (FocusEvent e) { if (f.getText().isEmpty()) { f.setText(ph); f.setForeground(TEXT_DIM); } }
        }); return f;
    }

    private JComboBox<String> combo(String[] opts) {
        JComboBox<String> cb = new JComboBox<>(opts);
        cb.setFont(F_VALUE); cb.setForeground(WHITE); cb.setBackground(INPUT_BG_C); return cb;
    }

    private JSeparator sep() { JSeparator s = new JSeparator(); s.setForeground(SEP_COL); return s; }

    private JLabel fb(String t) { JLabel l = new JLabel(t); l.setFont(F_SMALL); l.setForeground(SUCCESS); return l; }

    private void showFb(JLabel l, String t, boolean ok) { l.setText(t); l.setForeground(ok ? SUCCESS : ERR); }

    // =====================================
    //  LAYOUT
    // =====================================

    private void doLayout2() {
        int w = getWidth(), h = getHeight();
        sidebar.setBounds(0, 0, SIDEBAR_W, h);

        // Centrer le contenu dans l'espace disponible à droite
        int avail   = w - SIDEBAR_W;
        int contentX = SIDEBAR_W + Math.max(24, (avail - CONTENT_W) / 2);
        contentArea.setBounds(contentX, 28, CONTENT_W, h - 56);

        // Positionner les boutons du bas de la sidebar
        JSeparator s2  = (JSeparator) sidebar.getClientProperty("s2");
        JButton back   = (JButton)    sidebar.getClientProperty("back");
        JButton logout = (JButton)    sidebar.getClientProperty("logout");
        if (s2     != null) s2.setBounds(14, h - 122, SIDEBAR_W - 28, 1);
        if (back   != null) back.setBounds(6, h - 114, SIDEBAR_W - 12, 54);
        if (logout != null) logout.setBounds(6, h - 58, SIDEBAR_W - 12, 54);
    }

    // =====================================
    //  FLOU
    // =====================================

    private BufferedImage applyBlur(BufferedImage img, int r) {
        if (r < 1) return img;
        int e = r * 2;
        BufferedImage tmp = new BufferedImage(img.getWidth()+e*2, img.getHeight()+e*2, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = tmp.createGraphics(); g.drawImage(img, e, e, null); g.dispose();
        int sz = r*2+1; float[] k = new float[sz*sz]; float v = 1f/(sz*sz);
        for (int i = 0; i < k.length; i++) k[i] = v;
        java.awt.image.BufferedImageOp op = new java.awt.image.ConvolveOp(
                new java.awt.image.Kernel(sz, sz, k), java.awt.image.ConvolveOp.EDGE_NO_OP, null);
        BufferedImage cur = tmp; for (int i = 0; i < 3; i++) cur = op.filter(cur, null);
        BufferedImage res = new BufferedImage(img.getWidth(), img.getHeight(), img.getType());
        Graphics2D g2 = res.createGraphics(); g2.drawImage(cur, -e, -e, null); g2.dispose(); return res;
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        if      (blurredBackground != null) g2.drawImage(blurredBackground, 0,0,getWidth(),getHeight(),null);
        else if (backgroundImage   != null) g2.drawImage(backgroundImage,   0,0,getWidth(),getHeight(),null);
        else { g2.setColor(BG); g2.fillRect(0,0,getWidth(),getHeight()); }
        g2.setColor(new Color(0,0,0,168)); g2.fillRect(0,0,getWidth(),getHeight());
        g2.dispose();
    }

    private void styleSlider(JSlider slider) {
        slider.setOpaque(false);
        slider.setPaintTicks(false);
        slider.setPaintLabels(false);
        slider.setForeground(ACCENT);
        slider.setBackground(new Color(0,0,0,0));
    }
}