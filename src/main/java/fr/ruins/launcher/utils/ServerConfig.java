package fr.ruins.launcher.utils;

import com.google.gson.JsonArray;

public class ServerConfig {
    private JsonArray mods;
    private String address;

    public ServerConfig(JsonArray mods, String address) {
        this.mods = mods;
        this.address = address;
    }

    public JsonArray getMods() {
        return mods;
    }

    public String getAddress() {
        return address;
    }
}
