package fr.ruins.launcher;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.management.OperatingSystemMXBean;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class UserPreferences {

    public static int RAM_MIN_MB = 512;

    private int ramStartMb = 1024; // Xms
    private int ramMaxMb = 2048;   // Xmx

    private boolean download_shaders = false;

    // =========================
    // LOAD / SAVE
    // =========================
    public void load(Path folder_path) {
        Path file = folder_path.resolve("preferences.json");

        if (!Files.exists(file)) {
            System.out.println("Preferences file not found, using defaults.");
            set_default();
            return;
        }

        try {
            String json = Files.readString(file);
            JsonObject data = JsonParser.parseString(json).getAsJsonObject();

            int sysRam = detect_max_ram();

            // XMS (start)
            if (data.has("ram_start_mb")) {
                set_ram_start_mb(data.get("ram_start_mb").getAsInt());
            }

            // XMX (max)
            if (data.has("ram_max_mb")) {
                set_ram_max_mb(data.get("ram_max_mb").getAsInt());
            }

            if (data.has("download_shaders")) {
                set_download_shader(data.get("download_shaders").getAsBoolean());
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void save(Path folder_path) {
        Path file = folder_path.resolve("preferences.json");
        JsonObject data = new JsonObject();
        data.addProperty("ram_start_mb", ramStartMb);
        data.addProperty("ram_max_mb", ramMaxMb);

        try {
            Files.writeString(file, data.toString());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // =========================
    // Full reset
    // =========================

    public void full_reset(Path game_path) {
        try {
            if (!Files.exists(game_path)) return;

            Files.walk(game_path)
                    .sorted((a, b) -> b.compareTo(a))
                    .forEach(p -> {
                        String name = p.getFileName().toString();

                        // Liste des fichiers/dossiers à ne pas supprimer
                        boolean isProtected = name.equals("preferences.json")
                                || name.equals("session.json")
                                || name.equals("screenshots")
                                || name.equals("saves")
                                || p.equals(game_path); // Ne pas supprimer le dossier racine

                        try {
                            // On supprime que si ce n'est pas protégé
                            if (!isProtected) {
                                // Vérification supplémentaire : on ne supprime pas si le fichier est dans un dossier protégé
                                if (!is_in_protected_folder(p)) {
                                    Files.deleteIfExists(p);
                                }
                            }
                        } catch (IOException e) {
                            System.err.println("Erreur sur : " + name + " -> " + e.getMessage());
                        }
                    });
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private boolean is_in_protected_folder(Path path) {
        String fullPath = path.toString();
        return fullPath.contains("saves") || fullPath.contains("screenshots");
    }

    // =========================
    // DEFAULT (auto adaptatif)
    // =========================
    public void set_default() {

        ramStartMb = 1024; // Xms
        ramMaxMb = 2048;

        download_shaders = false;
    }

    // =========================
    // JVM ARGS
    // =========================
    public List<String> getJvmArgs() {
        return List.of(
                "-Xms" + ramStartMb + "M",
                "-Xmx" + ramMaxMb + "M"
        );
    }

    // =========================
    // GETTERS / SETTERS
    // =========================
    public void set_ram_start_mb(int ramStartMb) {
        this.ramStartMb = clamp_ram(ramStartMb, get_ram_max_device());
    }

    public void set_ram_max_mb(int ramMaxMb) {
        this.ramMaxMb = clamp_ram(ramMaxMb, get_ram_max_device());
    }

    public int get_ram_start_mb() {
        return ramStartMb;
    }

    public int get_ram_max_mb() {
        return ramMaxMb;
    }

    public String get_ram_start_display() {
        return format_ram(ramStartMb);
    }

    public String get_ram_max_display() {
        return format_ram(ramMaxMb);
    }

    public int get_ram_max_device() {
        return detect_max_ram();
    }

    public Boolean get_download_shader() {
        return download_shaders;
    }

    public void set_download_shader(boolean download_shaders) {
        this.download_shaders = download_shaders;
    }

    // =========================
    // Ram fr.ruins.launcher.utils
    // =========================
    private int clamp_ram(int value, int maxSysRam) {
        return Math.max(RAM_MIN_MB, Math.min(maxSysRam, value));
    }

    public static int detect_max_ram() {
        OperatingSystemMXBean os =
                (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();

        long total_ram_bytes = os.getTotalMemorySize();
        return (int) (total_ram_bytes / (1024 * 1024));
    }

    public String format_ram(int mb) {
        if (mb >= 1024) {
            return (mb / 1024) + " Go";
        }
        return mb + " Mo";
    }

    public int[] ram_pallette() {
        int step = 512;

        int max = get_ram_max_device();

        int size = (int) Math.ceil(max / (double) step);
        int[] values = new int[size];

        for (int i = 0; i < size; i++) {
            values[i] = Math.min((i + 1) * step, max);
        }

        return values;
    }

    public int closest_index(int[] array, int value) {
        int closestIndex = 0;
        int minDiff = Math.abs(array[0] - value);

        for (int i = 1; i < array.length; i++) {
            int diff = Math.abs(array[i] - value);
            if (diff < minDiff) {
                minDiff = diff;
                closestIndex = i;
            }
        }
        return closestIndex;
    }

}