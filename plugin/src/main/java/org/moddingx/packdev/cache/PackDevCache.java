package org.moddingx.packdev.cache;

import com.google.gson.*;
import jakarta.annotation.Nullable;
import org.gradle.api.Project;
import org.moddingx.launcherlib.launcher.Launcher;
import org.moddingx.packdev.platform.ModdingPlatform;
import org.moddingx.packdev.util.hash.ComputedHash;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class PackDevCache {
    
    public static final Gson GSON;

    static {
        GsonBuilder builder = new GsonBuilder();
        builder.disableHtmlEscaping();
        GSON = builder.create();
    }
    
    private static final int VERSION = 2;
    
    private final Path basePath;
    private final Path path;
    private final Launcher launcher;
    
    private boolean loaded;
    private boolean saved;
    
    private final Map<String, Integer> javaVersions;
    private final Map<String, Map<String, ComputedHash>> hashes;
    
    public PackDevCache(Project project, ModdingPlatform<?> platform) {
        this.basePath = project.getGradle().getGradleUserHomeDir().toPath()
                .resolve("caches").resolve("packdev").resolve("platform_v" + VERSION)
                .resolve(platform.id())
                .toAbsolutePath().normalize();
        this.path = this.basePath.resolve("index.json").toAbsolutePath().normalize();
        this.launcher = new Launcher(this.basePath.resolve("launcher"));
        
        this.loaded = false;
        this.saved = false;
        
        this.javaVersions = new HashMap<>();
        this.hashes = new HashMap<>();
    }
    
    public Launcher launcher() {
        return this.launcher;
    }
    
    public int getJavaVersion(String minecraft) {
        return this.javaVersions.computeIfAbsent(minecraft, k -> this.launcher.version(minecraft).java());
    }
    
    @Nullable
    public ComputedHash getHash(String fileKey, String algorithm) {
        this.load();
        Map<String, ComputedHash> map = this.hashes.get(fileKey);
        if (map == null) return null;
        return map.getOrDefault(algorithm.toLowerCase(Locale.ROOT), null);
    }
    
    public void updateHash(String fileKey, String algorithm, ComputedHash hash) {
        this.load();
        this.hashes.computeIfAbsent(fileKey, k -> new HashMap<>()).put(algorithm.toLowerCase(Locale.ROOT), hash);
        this.modify();
    }
    
    public Path getCachePath(String... groups) throws IOException {
        Path groupPath = this.basePath.getFileSystem().getPath("", groups);
        if (groupPath.isAbsolute()) {
            throw new IllegalArgumentException("Absolute path in PackDev cache");
        }
        Path target = this.basePath.resolve(groupPath).toAbsolutePath().normalize();
        if (!Files.isDirectory(target.getParent())) {
            Files.createDirectories(target.getParent());
        }
        return target;
    }
    
    private synchronized void load() {
        if (!this.loaded) {
            try {
                if (Files.isRegularFile(this.path)) {
                    String data = Files.readString(this.path, StandardCharsets.UTF_8);
                    JsonObject json = GSON.fromJson(data, JsonObject.class);
                    
                    this.javaVersions.clear();
                    if (json.has("java")) {
                        for (Map.Entry<String, JsonElement> entry : json.getAsJsonObject("java").entrySet()) {
                            this.javaVersions.put(entry.getKey(), entry.getValue().getAsInt());
                        }
                    }
                    
                    this.hashes.clear();
                    if (json.has("hashes")) {
                        for (Map.Entry<String, JsonElement> entry : json.getAsJsonObject("hashes").entrySet()) {
                            Map<String, ComputedHash> map = new HashMap<>();
                            entry.getValue().getAsJsonObject().entrySet().forEach(e -> map.put(e.getKey(), ComputedHash.load(new BigInteger(e.getValue().getAsString(), 36))));
                            this.hashes.put(entry.getKey(), map);
                        }
                    }
                }
            } catch (IOException | JsonParseException e) {
                e.printStackTrace();
            } finally {
                // If a load fails, the cache is dropped.
                this.loaded = true;
            }
        }
    }
    
    private synchronized void modify() {
        this.saved = false;
    }
    
    public synchronized void save() {
        if (this.loaded && !this.saved) {
            try {
                if (!Files.isDirectory(this.basePath)) {
                    Files.createDirectories(this.basePath);
                }
                JsonObject json = new JsonObject();

                JsonObject java = new JsonObject();
                for (Map.Entry<String, Integer> entry : this.javaVersions.entrySet()) {
                    java.addProperty(entry.getKey(), entry.getValue());
                }
                json.add("java", java);
                
                JsonObject hashes = new JsonObject();
                for (Map.Entry<String, Map<String, ComputedHash>> entry : this.hashes.entrySet()) {
                    JsonObject map = new JsonObject();
                    entry.getValue().forEach((k, v) -> map.add(k, new JsonPrimitive(v.store().toString(36))));
                    hashes.add(entry.getKey(), map);
                }
                json.add("hashes", hashes);
                
                String data = GSON.toJson(json) + "\n";
                Files.writeString(this.path, data, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                this.saved = true;
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
