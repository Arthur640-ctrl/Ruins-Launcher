package fr.ruins.launcher;

import com.google.gson.JsonObject;
import fr.ruins.launcher.utils.ApiClient;
import fr.ruins.launcher.utils.Session;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.image.BufferedImage;
import java.awt.image.BufferedImageOp;
import java.awt.image.ConvolveOp;
import java.awt.image.Kernel;
import java.io.IOException;

public class AuthPanel extends JPanel {

    // UI
    private JPanel container;
    private JPanel loginForm;
    private JPanel registerForm;

    private CardLayout cardLayout;

    private JLabel statusLabel;

    // Couleurs
    private final Color BACKGROUND_COLOR = new Color(13, 13, 13);
    private final Color CARD_COLOR = new Color(89, 2, 2, 220);
    private final Color ACCENT_COLOR = new Color(217, 4, 4);
    private final Color ACCENT_HOVER_COLOR = new Color(140, 3, 3);
    private final Color TEXT_COLOR = new Color(191, 184, 184);
    private final Color PLACEHOLDER_COLOR = new Color(100, 100, 100);
    private final Color ERROR_COLOR = new Color(255, 100, 120);
    private final Color SUCCESS_COLOR = new Color(100, 220, 150);

    // Background
    private BufferedImage backgroundImage;
    private BufferedImage blurredBackground;

    public AuthPanel() throws IOException {
        this.setLayout(new BorderLayout());

        // Chargement des images
        backgroundImage = Frame.getBufferdImage("background_1.png");

        // Appliquer le flou à l'image
        if (backgroundImage != null) {
            blurredBackground = createBlurredImage(backgroundImage, 5);
        }

        // Definis le layout
        cardLayout = new CardLayout();
        container = new JPanel(cardLayout);
        container.setOpaque(false);

        // Centre le container avec des marges responsives
        JPanel centerContainer = new JPanel(new GridBagLayout());
        centerContainer.setOpaque(false);
        centerContainer.setBorder(new EmptyBorder(50, 50, 50, 50));
        centerContainer.add(container);

        // Formulaire de login
        loginForm = create_login_form();

        // Formulaire de register
        registerForm = createRegisterForm();

        // Mise en place du container avec login et register
        container.add(loginForm, "login");
        container.add(registerForm, "register");

        this.add(centerContainer, BorderLayout.CENTER);

        // Listener pour responsive
        this.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                resizeForms();
            }
        });

        // Auto login
        Session savedSession = Launcher.load_session();
        if (savedSession != null) {
            Launcher.set_session(savedSession);

            SwingWorker<Void, Void> worker = new SwingWorker<>() {
                private boolean sessionValid = false;

                @Override
                protected Void doInBackground() {
                    try {
                        sessionValid = ApiClient.checkSession(
                                savedSession.getUuid(),
                                savedSession.getUsername(),
                                savedSession.getSessionToken(),
                                savedSession.getEmail()
                        );
                    } catch (IOException e) {
                        e.printStackTrace();
                        sessionValid = false;
                    }
                    return null;
                }

                @Override
                protected void done() {
                    if (sessionValid) {
                        try {
                            Frame.getInstance().setContentPane(Frame.getHomePanel());
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    } else {
                        try {
                            Frame.getInstance().setContentPane(Frame.getLoginPanel());
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    }
                    Frame.getInstance().revalidate();
                    Frame.getInstance().repaint();
                }
            };
            worker.execute();
        }

    }

    // Func : create_login_form() -> Création du formulaire de login
    private JPanel create_login_form() {

        // Ajout du container de login global
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setOpaque(false);
        panel.setPreferredSize(new Dimension(500, 450));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridwidth = GridBagConstraints.REMAINDER;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(5, 50, 5, 50);

        // Titre
        JLabel titleLabel = new JLabel("Connexion à Ruins", SwingConstants.CENTER);
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 32));
        titleLabel.setForeground(TEXT_COLOR);
        titleLabel.setBorder(new EmptyBorder(20, 0, 20, 0));
        gbc.insets = new Insets(30, 50, 10, 50);
        panel.add(titleLabel, gbc);

        // Champs de texte
        gbc.insets = new Insets(5, 50, 5, 50);

        // Email
        JTextField emailField = createStyledTextField("exemple@email.com");
        panel.add(createStyledLabel("Adresse e-mail"), gbc);
        panel.add(emailField, gbc);

        // Email subtitle
        JLabel emailSubtitle = new JLabel("");
        emailSubtitle.setFont(new Font("Segoe UI", Font.ITALIC, 12));
        emailSubtitle.setForeground(ERROR_COLOR);
        panel.add(emailSubtitle, gbc);

        // Password
        JPasswordField passwordField = createStyledPasswordField("••••••••");
        panel.add(createStyledLabel("Mot de passe"), gbc);
        panel.add(passwordField, gbc);

        // Error subtitle
        JLabel errorSubtitle = new JLabel("");
        errorSubtitle.setFont(new Font("Segoe UI", Font.ITALIC, 12));
        errorSubtitle.setForeground(ERROR_COLOR);
        panel.add(errorSubtitle, gbc);

        // Boutons
        JPanel buttonPanel = new JPanel(new GridLayout(1, 2, 10, 0));
        buttonPanel.setOpaque(false);
        buttonPanel.setBorder(new EmptyBorder(20, 0, 30, 0));

        JButton loginBtn = createStyledButton("Entrer dans Ruins", ACCENT_COLOR);
        JButton switchBtn = createStyledButton("Nouveau joueur ?", new Color(60, 70, 85));

        buttonPanel.add(loginBtn);
        buttonPanel.add(switchBtn);

        gbc.insets = new Insets(10, 50, 30, 50);
        panel.add(buttonPanel, gbc);

        // Actions
        loginBtn.addActionListener(e -> {

            // Récuperation des champs de texte
            String email = emailField.getText();
            String password = new String(passwordField.getPassword());

            // Réinitialisation des messages d'erreur
            setFieldSubtitle(errorSubtitle, "", ERROR_COLOR);
            setFieldSubtitle(emailSubtitle, "", ERROR_COLOR);

            // Verification de la validité de l'email
            if (!isValidEmail(email)) {
                setFieldSubtitle(emailSubtitle, "Adresse email invalide !", ERROR_COLOR);
                return;
            }

            // Essai du login
            new Thread(() -> {
                try {
                    // Envoyer requette API
                    JsonObject res = ApiClient.login(email, password);

                    // Initialiser la session
                    Session session = new Session(
                            res.get("uuid").getAsString(),
                            res.get("username").getAsString(),
                            res.get("session_token").getAsString(),
                            res.get("email").getAsString()
                    );

                    Launcher.set_session(session);
                    Launcher.save_session(session);

                    SwingUtilities.invokeLater(() -> {
                        try {
                            // On récupère l'instance (qui sera créée puisque session n'est plus nulle)
                            Frame.getInstance().setContentPane(Frame.getHomePanel());
                            Frame.getInstance().revalidate();
                            Frame.getInstance().repaint();
                        } catch (IOException ex) {
                            ex.printStackTrace();
                        }
                    });
                } catch (Exception ex) {
                    String error_message = ex.getMessage();
                    SwingUtilities.invokeLater(() -> {

                        if ("Identifiants invalides".equals(error_message)) {
                            setFieldSubtitle(errorSubtitle, "Email ou mot de passe invalide !", ERROR_COLOR);
                        } else {
                            setFieldSubtitle(errorSubtitle, "Erreur durant le login (verifier tout les champs) : " + error_message, ERROR_COLOR);
                        }
                    });
                }
            }).start();
        });

        switchBtn.addActionListener(e -> {
            cardLayout.show(container, "register");
        });

        return panel;
    }

    private JPanel createRegisterForm() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setOpaque(false);
        panel.setPreferredSize(new Dimension(500, 500));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridwidth = GridBagConstraints.REMAINDER;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(5, 50, 5, 50);

        // Titre
        JLabel titleLabel = new JLabel("Créer ton compte Ruins", SwingConstants.CENTER);
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 32));
        titleLabel.setForeground(TEXT_COLOR);
        titleLabel.setBorder(new EmptyBorder(20, 0, 20, 0));
        gbc.insets = new Insets(30, 50, 10, 50);
        panel.add(titleLabel, gbc);

        // Champs de texte
        gbc.insets = new Insets(5, 50, 5, 50);

        // Email
        JTextField emailField = createStyledTextField("exemple@email.com");
        panel.add(createStyledLabel("Adresse e-mail"), gbc);
        panel.add(emailField, gbc);

        // Email subtitle
        JLabel emailSubtitle = new JLabel("");
        emailSubtitle.setFont(new Font("Segoe UI", Font.ITALIC, 12));
        emailSubtitle.setForeground(ERROR_COLOR);
        panel.add(emailSubtitle, gbc);

        // Pseudo
        JTextField pseudoField = createStyledTextField("Pseudo");
        panel.add(createStyledLabel("Ton pseudo en jeu"), gbc);
        panel.add(pseudoField, gbc);

        // Pseudo subtitle
        JLabel pseudoSubtitle = new JLabel("");
        pseudoSubtitle.setFont(new Font("Segoe UI", Font.ITALIC, 12));
        pseudoSubtitle.setForeground(ERROR_COLOR);
        panel.add(pseudoSubtitle, gbc);

        // Password
        JPasswordField passwordField = createStyledPasswordField("••••••••");
        panel.add(createStyledLabel("Mot de passe"), gbc);
        panel.add(passwordField, gbc);

        // Error subtitle
        JLabel errorSubtitle = new JLabel("");
        errorSubtitle.setFont(new Font("Segoe UI", Font.ITALIC, 12));
        errorSubtitle.setForeground(ERROR_COLOR);
        panel.add(errorSubtitle, gbc);

        // Panneau pour les boutons
        JPanel buttonPanel = new JPanel(new GridLayout(1, 2, 10, 0));
        buttonPanel.setOpaque(false);
        buttonPanel.setBorder(new EmptyBorder(20, 0, 30, 0));

        JButton registerBtn = createStyledButton("Rejoindre Ruins", ACCENT_COLOR);
        JButton switchBtn = createStyledButton("J'ai déjà un compte", new Color(60, 70, 85));

        buttonPanel.add(registerBtn);
        buttonPanel.add(switchBtn);

        gbc.insets = new Insets(10, 50, 30, 50);
        panel.add(buttonPanel, gbc);

        registerBtn.addActionListener(e -> {
            String email = emailField.getText().trim();
            String pseudo = pseudoField.getText().trim();
            String password = new String(passwordField.getPassword());

            setFieldSubtitle(emailSubtitle, "", ERROR_COLOR);
            setFieldSubtitle(pseudoSubtitle, "", ERROR_COLOR);
            setFieldSubtitle(errorSubtitle, "", ERROR_COLOR);

            if (!isValidEmail(email)) {
                setFieldSubtitle(emailSubtitle, "Adresse email invalide !", ERROR_COLOR);
                return;
            }

            new Thread(() -> {
                try {
                    JsonObject res = ApiClient.register(email, pseudo, password);
                    SwingUtilities.invokeLater(() -> {
                        cardLayout.show(container, "login");
                    });
                } catch (Exception ex) {
                    String error_message = ex.getMessage();
                    SwingUtilities.invokeLater(() -> {

                        if ("Email déjà utilisé".equals(error_message)) {
                            setFieldSubtitle(emailSubtitle, "Cette adresse mail est déjà utilisée !", ERROR_COLOR);
                        } else if ("Pseudo déjà utilisé".equals(error_message)) {
                            setFieldSubtitle(pseudoSubtitle, "Ce pseudo est déjà utilisé !", ERROR_COLOR);
                        } else {
                            setFieldSubtitle(errorSubtitle, "Erreur durant le login (verifier tout les champs) : " + error_message, ERROR_COLOR);
                        }
                    });
                }
            }).start();
        });

        switchBtn.addActionListener(e -> {
            cardLayout.show(container, "login");
        });

        return panel;
    }

    private JLabel createStyledLabel(String text) {
        JLabel label = new JLabel(text);
        label.setForeground(PLACEHOLDER_COLOR);
        label.setFont(new Font("Segoe UI", Font.BOLD, 14));
        label.setBorder(new EmptyBorder(5, 0, 0, 0));
        return label;
    }

    private void resizeForms() {
        int width = Math.min(550, (int)(this.getWidth() * 0.8));
        int loginHeight = Math.min(500, (int)(this.getHeight() * 0.7));
        int registerHeight = Math.min(550, (int)(this.getHeight() * 0.8));

        loginForm.setPreferredSize(new Dimension(width, loginHeight));
        registerForm.setPreferredSize(new Dimension(width, registerHeight));

        loginForm.revalidate();
        registerForm.revalidate();
    }

    private JTextField createStyledTextField(String placeholder) {
        JTextField field = new JTextField() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                g2.setColor(new Color(45, 50, 60));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 15, 15);

                g2.setColor(new Color(255, 255, 255, 30));
                g2.setStroke(new BasicStroke(1));
                g2.drawRoundRect(0, 0, getWidth()-1, getHeight()-1, 15, 15);

                super.paintComponent(g);
                g2.dispose();
            }
        };
        field.setOpaque(false);

        field.setPreferredSize(new Dimension(400, 40));
        field.setFont(new Font("Segoe UI", Font.PLAIN, 16));
        field.setBorder(BorderFactory.createEmptyBorder(12, 15, 12, 15));

        field.setForeground(TEXT_COLOR);
        field.setCaretColor(ACCENT_COLOR);
        field.setSelectionColor(new Color(100, 150, 255, 100));
        field.putClientProperty("JTextField.placeholderText", placeholder);

        return field;
    }

    private JPasswordField createStyledPasswordField(String placeholder) {
        JPasswordField field = new JPasswordField() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                g2.setColor(new Color(45, 50, 60));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 15, 15);

                g2.setColor(new Color(255, 255, 255, 30));
                g2.setStroke(new BasicStroke(1));
                g2.drawRoundRect(0, 0, getWidth()-1, getHeight()-1, 15, 15);

                super.paintComponent(g);
                g2.dispose();
            }
        };
        field.setOpaque(false);
        field.setBorder(BorderFactory.createEmptyBorder(10, 15, 10, 15));
        field.setForeground(TEXT_COLOR);
        field.setFont(new Font("Segoe UI", Font.PLAIN, 17));
        field.setCaretColor(ACCENT_COLOR);
        field.setSelectionColor(new Color(100, 150, 255, 100));
        field.putClientProperty("JTextField.placeholderText", placeholder);

        return field;
    }

    private JButton createStyledButton(String text, Color backgroundColor) {
        JButton button = new JButton(text) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                if (getModel().isPressed()) {
                    g2.setColor(backgroundColor.darker());
                } else if (getModel().isRollover()) {
                    g2.setColor(backgroundColor.brighter());
                } else {
                    g2.setColor(backgroundColor);
                }

                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 15, 15);

                g2.setColor(new Color(255, 255, 255, 50));
                g2.setStroke(new BasicStroke(1));
                g2.drawRoundRect(0, 0, getWidth()-1, getHeight()-1, 15, 15);

                g2.setFont(new Font("Segoe UI", Font.BOLD, 16));
                g2.setColor(TEXT_COLOR);
                FontMetrics fm = g2.getFontMetrics();
                int x = (getWidth() - fm.stringWidth(getText())) / 2;
                int y = (getHeight() + fm.getAscent() - fm.getDescent()) / 2;
                g2.drawString(getText(), x, y);

                g2.dispose();
            }
        };

        button.setOpaque(false);
        button.setContentAreaFilled(false);

        button.setBorder(new EmptyBorder(15, 40, 15, 40));
        button.setPreferredSize(new Dimension(240, 55));

        button.setBorderPainted(false);
        button.setFocusPainted(false);
        button.setCursor(new Cursor(Cursor.HAND_CURSOR));

        return button;
    }

    private void setFieldSubtitle(JLabel subtitleLabel, String message, Color color) {
        subtitleLabel.setText(message);
        subtitleLabel.setForeground(color);
    }

    private boolean isValidEmail(String email) {
        return email.matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$");
    }

    private BufferedImage createBlurredImage(BufferedImage img, int radius) {
        if (radius < 1) return img;

        // Créer une image temporaire plus grande pour éviter les bords
        int extraSize = radius * 2;
        int tempWidth = img.getWidth() + extraSize * 2;
        int tempHeight = img.getHeight() + extraSize * 2;

        BufferedImage tempImage = new BufferedImage(tempWidth, tempHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = tempImage.createGraphics();
        g.drawImage(img, extraSize, extraSize, null);
        g.dispose();

        // Appliquer le flou plusieurs fois pour un effet plus uniforme
        BufferedImage current = tempImage;
        int size = radius * 2 + 1;
        float[] data = new float[size * size];
        float value = 1.0f / (size * size);
        for (int i = 0; i < data.length; i++) {
            data[i] = value;
        }

        BufferedImageOp op = new ConvolveOp(new Kernel(size, size, data), ConvolveOp.EDGE_NO_OP, null);

        // Appliquer le flou plusieurs fois pour un meilleur résultat
        for (int i = 0; i < 3; i++) {
            current = op.filter(current, null);
        }

        // Recadrer l'image pour enlever les bords
        BufferedImage result = new BufferedImage(img.getWidth(), img.getHeight(), img.getType());
        Graphics2D g2 = result.createGraphics();
        g2.drawImage(current, -extraSize, -extraSize, null);
        g2.dispose();

        return result;
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();

        if (blurredBackground != null) {
            // L'image prend TOUTE la taille du panel
            g2.drawImage(blurredBackground, 0, 0, getWidth(), getHeight(), null);
        } else if (backgroundImage != null) {
            g2.drawImage(backgroundImage, 0, 0, getWidth(), getHeight(), null);
        } else {
            // Fallback si pas d'image
            g2.setColor(BACKGROUND_COLOR);
            g2.fillRect(0, 0, getWidth(), getHeight());
        }

        // Superposition sombre pour meilleure lisibilité
        g2.setColor(new Color(0, 0, 0, 150));
        g2.fillRect(0, 0, getWidth(), getHeight());

        g2.dispose();
    }
}