package fr.ruins.launcher.utils;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fr.ruins.launcher.Launcher;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;

public class ApiClient {

    private static final String API_URL = "http://137.74.42.218:5400";
    private static final String DEV_URL = "http://0.0.0.0:5400";

    public static JsonObject register(String email, String pseudo, String password) throws IOException {

        URL url = null;

        if (Launcher.dev_mode == false) {
            url = new URL(API_URL + "/register");
        } else {
            url = new URL(DEV_URL + "/register");
        }

        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setDoOutput(true);

        // Corps JSON
        JsonObject body = new JsonObject();
        body.addProperty("email", email);
        body.addProperty("pseudo", pseudo);
        body.addProperty("password", password);

        try (OutputStream os = conn.getOutputStream()) {
            os.write(body.toString().getBytes());
        }

        InputStream stream;
        if (conn.getResponseCode() >= 400) {
            // si erreur HTTP, lire l'ErrorStream
            stream = conn.getErrorStream();
        } else {
            stream = conn.getInputStream();
        }

        BufferedReader reader = new BufferedReader(new InputStreamReader(stream));
        StringBuilder responseStr = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            responseStr.append(line);
        }

        JsonObject jsonResponse = new JsonParser()
                .parse(responseStr.toString()).getAsJsonObject();

        // Si erreur HTTP, lancer une exception avec le vrai message
        if (conn.getResponseCode() >= 400) {
            String msg = jsonResponse.has("detail") ? jsonResponse.get("detail").getAsString()
                    : "Erreur inconnue";
            throw new IOException(msg);
        }

        return jsonResponse;
    }

    public static JsonObject login(String email, String password) throws IOException {
        URL url = null;

        if (Launcher.dev_mode == false) {
            url = new URL(API_URL + "/login");
        } else {
            url = new URL(DEV_URL + "/login");
        }

        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setDoOutput(true);

        JsonObject body = new JsonObject();
        body.addProperty("email", email);
        body.addProperty("password", password);

        try (OutputStream os = conn.getOutputStream()) {
            os.write(body.toString().getBytes());
        }

        InputStream stream;
        if (conn.getResponseCode() >= 400) {
            stream = conn.getErrorStream();
        } else {
            stream = conn.getInputStream();
        }

        BufferedReader reader = new BufferedReader(new InputStreamReader(stream));
        StringBuilder responseStr = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            responseStr.append(line);
        }

        JsonObject jsonResponse = new JsonParser()
                .parse(responseStr.toString()).getAsJsonObject();

        if (conn.getResponseCode() >= 400) {
            String msg = jsonResponse.has("detail") ? jsonResponse.get("detail").getAsString()
                    : "Erreur inconnue";
            throw new IOException(msg);
        }

        return jsonResponse;
    }

    public static ServerConfig getUpdateConfig() throws IOException {
        URL url = null;

        if (Launcher.dev_mode == false) {
            url = new URL(API_URL + "/update/check");
        } else {
            url = new URL(DEV_URL + "/update/check");
        }

        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Content-Type", "application/json");

        InputStream stream = conn.getResponseCode() >= 400 ? conn.getErrorStream() : conn.getInputStream();

        BufferedReader reader = new BufferedReader(new InputStreamReader(stream));
        StringBuilder responseStr = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            responseStr.append(line);
        }

        JsonObject jsonResponse = JsonParser.parseString(responseStr.toString()).getAsJsonObject();

        if (conn.getResponseCode() >= 400) {
            String msg = jsonResponse.has("detail") ? jsonResponse.get("detail").getAsString() : "Erreur inconnue";
            throw new IOException(msg);
        }

        JsonArray mods = jsonResponse.getAsJsonArray("mods");
        String address = jsonResponse.get("address").getAsString();

        return new ServerConfig(mods, address);
    }

    public static JsonObject prejoin(String email, String token) throws IOException {
        URL url = null;

        if (Launcher.dev_mode == false) {
            url = new URL(API_URL + "/session/prejoin");
        } else {
            url = new URL(DEV_URL + "/session/prejoin");
        }

        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setDoOutput(true);

        // Corps JSON
        JsonObject body = new JsonObject();
        body.addProperty("email", email);
        body.addProperty("token", token);

        try (OutputStream os = conn.getOutputStream()) {
            os.write(body.toString().getBytes());
        }

        InputStream stream;
        if (conn.getResponseCode() >= 400) {
            stream = conn.getErrorStream();
        } else {
            stream = conn.getInputStream();
        }

        BufferedReader reader = new BufferedReader(new InputStreamReader(stream));
        StringBuilder responseStr = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            responseStr.append(line);
        }

        JsonObject jsonResponse = JsonParser.parseString(responseStr.toString()).getAsJsonObject();

        if (conn.getResponseCode() >= 400) {
            String msg = jsonResponse.has("detail") ? jsonResponse.get("detail").getAsString() : "Erreur inconnue";
            throw new IOException(msg);
        }

        return jsonResponse;
    }

    public static boolean checkSession(String uuid, String username, String sessionToken, String email) throws IOException {
        URL url = null;

        if (Launcher.dev_mode == false) {
            url = new URL(API_URL + "/session/check");
        } else {
            url = new URL(DEV_URL + "/session/check");
        }

        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setDoOutput(true);

        JsonObject body = new JsonObject();
        body.addProperty("uuid", uuid);
        body.addProperty("username", username);
        body.addProperty("session_token", sessionToken);
        body.addProperty("email", email);

        try (OutputStream os = conn.getOutputStream()) {
            os.write(body.toString().getBytes());
        }

        InputStream stream = conn.getResponseCode() >= 400 ? conn.getErrorStream() : conn.getInputStream();

        BufferedReader reader = new BufferedReader(new InputStreamReader(stream));
        StringBuilder responseStr = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            responseStr.append(line);
        }

        JsonObject jsonResponse = JsonParser.parseString(responseStr.toString()).getAsJsonObject();

        if (conn.getResponseCode() >= 400) {
            String msg = jsonResponse.has("reason") ? jsonResponse.get("reason").getAsString() : "Erreur inconnue";
            throw new IOException(msg);
        }

        return jsonResponse.has("valid") && jsonResponse.get("valid").getAsBoolean();
    }
}
