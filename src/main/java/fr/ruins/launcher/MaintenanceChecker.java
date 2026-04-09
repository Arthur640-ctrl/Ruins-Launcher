package fr.ruins.launcher;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * MaintenanceChecker — Vérifie le statut de maintenance toutes les 2 minutes.
 *
 * Deux cas gérés :
 *   1. API DOWN      → onApiDown()       : serveur inaccessible, erreur réseau
 *   2. MAINTENANCE   → onMaintenance()   : API up mais maintenance: true dans game.json
 *   3. TOUT OK       → onOperational()   : ni maintenance ni panne
 *
 * Usage :
 *   MaintenanceChecker.start(callback);  // démarrer le polling
 *   MaintenanceChecker.stop();           // arrêter proprement
 */
public class MaintenanceChecker {

    private static final String API_BASE_URL    = "http://137.74.42.218:5400";
    private static final int    POLL_INTERVAL_S = 120; // 2 minutes
    private static final int    TIMEOUT_MS      = 5_000;

    // ---------------------------
    // CALLBACK
    // ---------------------------

    public interface MaintenanceCallback {
        /** L'API répond et maintenance = false → tout va bien. */
        void onOperational();

        /** L'API répond mais maintenance = true. */
        void onMaintenance(String message, String endTime);

        /** L'API est injoignable (serveur down, pas de réseau, etc.). */
        void onApiDown();
    }

    // ---------------------------
    // ÉTAT INTERNE
    // ---------------------------

    private static ScheduledExecutorService scheduler;
    private static ScheduledFuture<?>       task;

    // Dernier état connu — pour ne notifier le callback que si ça change
    private static Status lastStatus = null;

    private enum Status { OPERATIONAL, MAINTENANCE, API_DOWN }

    // ---------------------------
    // DÉMARRAGE / ARRÊT
    // ---------------------------

    /**
     * Démarre le polling immédiatement puis toutes les POLL_INTERVAL_S secondes.
     * Appeler une seule fois au chargement de HomePanel.
     */
    public static void start(MaintenanceCallback callback) {
        if (scheduler != null && !scheduler.isShutdown()) stop();

        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "MaintenanceChecker-Thread");
            t.setDaemon(true); // N'empêche pas le launcher de fermer
            return t;
        });

        task = scheduler.scheduleAtFixedRate(
                () -> check(callback),
                0,                  // 1ère vérif immédiate au démarrage
                POLL_INTERVAL_S,
                TimeUnit.SECONDS
        );

        System.out.println("[MaintenanceChecker] Polling démarré (intervalle : " + POLL_INTERVAL_S + "s)");
    }

    /**
     * Arrête proprement le polling.
     * Appeler quand HomePanel est démonté ou que le jeu se lance.
     */
    public static void stop() {
        if (task != null)      task.cancel(false);
        if (scheduler != null) scheduler.shutdownNow();
        lastStatus = null;
        System.out.println("[MaintenanceChecker] Polling arrêté.");
    }

    // ---------------------------
    // VÉRIFICATION
    // ---------------------------

    private static void check(MaintenanceCallback callback) {
        try {
            HttpURLConnection conn = (HttpURLConnection)
                    new URL(API_BASE_URL + "/maintenance/status").openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", "RuinsLauncher/1.0");
            conn.setConnectTimeout(TIMEOUT_MS);
            conn.setReadTimeout(TIMEOUT_MS);

            int code = conn.getResponseCode();

            if (code != 200) {
                // L'API répond mais avec une erreur inattendue → traité comme "down"
                notifyIfChanged(Status.API_DOWN, callback, null, null);
                return;
            }

            JsonObject body = new Gson().fromJson(
                    new InputStreamReader(conn.getInputStream()), JsonObject.class
            );

            boolean maintenance = body.get("maintenance").getAsBoolean();
            String  message     = body.has("message")  ? body.get("message").getAsString()  : "";
            String  endTime     = body.has("end_time") ? body.get("end_time").getAsString() : "";

            if (maintenance) {
                notifyIfChanged(Status.MAINTENANCE, callback, message, endTime);
            } else {
                notifyIfChanged(Status.OPERATIONAL, callback, null, null);
            }

        } catch (IOException e) {
            // Timeout ou connexion refusée → API inaccessible
            System.err.println("[MaintenanceChecker] API inaccessible : " + e.getMessage());
            notifyIfChanged(Status.API_DOWN, callback, null, null);
        }
    }

    /**
     * N'appelle le callback que si le statut a changé depuis la dernière vérification.
     * Évite de flooder l'UI avec des appels inutiles toutes les 2 minutes.
     */
    private static void notifyIfChanged(Status newStatus, MaintenanceCallback callback,
                                        String message, String endTime) {
        if (newStatus == lastStatus) return; // Rien de nouveau
        lastStatus = newStatus;

        switch (newStatus) {
            case OPERATIONAL  -> callback.onOperational();
            case MAINTENANCE  -> callback.onMaintenance(message, endTime);
            case API_DOWN     -> callback.onApiDown();
        }
    }
}