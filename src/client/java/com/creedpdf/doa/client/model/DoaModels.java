package com.creedpdf.doa.client.model;

import com.creedpdf.doa.DoaMod;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;

import java.io.Reader;
import java.util.Optional;

/** Loads the Blender-built pillar and relic models on every resource reload. */
public class DoaModels implements SimpleSynchronousResourceReloadListener {
    public static DoaModel PILLAR;
    public static DoaModel RELIC;

    @Override
    public Identifier getFabricId() {
        return DoaMod.id("models");
    }

    @Override
    public void reload(ResourceManager manager) {
        PILLAR = load(manager, "models/entity/pillar.json");
        RELIC = load(manager, "models/entity/relic.json");
    }

    private static DoaModel load(ResourceManager manager, String path) {
        Identifier id = DoaMod.id(path);
        Optional<Resource> res = manager.getResource(id);
        if (res.isEmpty()) {
            DoaMod.LOG.error("Missing DOA model {}", id);
            return null;
        }
        try (Reader r = res.get().getReader()) {
            JsonObject o = JsonParser.parseReader(r).getAsJsonObject();
            return DoaModel.parse(o);
        } catch (Exception e) {
            DoaMod.LOG.error("Failed to load DOA model {}", id, e);
            return null;
        }
    }
}
