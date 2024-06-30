package org.moddingx.packdev.util.multimc;

import com.google.common.collect.ImmutableList;
import com.google.gson.*;
import jakarta.annotation.Nullable;
import org.moddingx.packdev.util.LoaderConstants;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.*;
import java.util.function.Function;

public class MultiMcAPI {
    
    public static final String ENDPOINT = "https://meta.multimc.org/v1";
    
    public static final String MC_UID = "net.minecraft";
    public static final Map<String, LoaderData> LOADER_UIDS = Map.of(
            LoaderConstants.FORGE, new LoaderData("net.minecraftforge", null),
            LoaderConstants.FABRIC, new LoaderData("net.fabricmc.fabric-loader", "net.fabricmc.intermediary"),
            LoaderConstants.QUILT, new LoaderData("org.quiltmc.quilt-loader", "net.fabricmc.intermediary"),
            LoaderConstants.NEOFORGE, new LoaderData("net.neoforged", null)
    );
    
    public static final Gson GSON;

    static {
        GsonBuilder builder = new GsonBuilder();
        builder.disableHtmlEscaping();
        GSON = builder.create();
    }

    public static JsonObject buildLoaderPack(String loader, String minecraftVersion, String loaderVersion) throws IOException {
        LoaderData data = LOADER_UIDS.getOrDefault(loader, null);
        if (data == null) throw new IllegalArgumentException("Loader not supported in MultiMC: " + loader);

        Map<String, String> initial = new HashMap<>();
        if (data.loaderUid() != null) initial.put(data.loaderUid(), loaderVersion);
        if (data.minecraftUid() != null) initial.put(data.minecraftUid(), minecraftVersion);
        
        List<Component> components = resolveAll(initial).stream().sorted(Comparator.comparing(Component::order)).toList();

        JsonArray array = new JsonArray();
        for (Component c : components) {
            boolean important = MC_UID.equals(c.uid());
            boolean dependency = !MC_UID.equals(c.uid()) && !Objects.equals(data.loaderUid(), c.uid()) && !Objects.equals(data.minecraftUid(), c.uid());
            array.add(c.toJson(important, dependency));
        }
        JsonObject json = new JsonObject();
        json.addProperty("formatVersion", 1);
        json.add("components", array);
        return json;
    }

    public static Component resolve(String uid, String version) throws IOException {
        JsonObject json = fetch("/" + uid + "/" + version + ".json");
        String name = json.has("name") ? json.get("name").getAsString() : uid;
        boolean isVolatile = json.has("volatile") && json.get("volatile").getAsBoolean();
        int order = json.has("order") ? json.get("order").getAsInt() : 0;
        ImmutableList.Builder<Dependency> requires = ImmutableList.builder();
        if (json.has("requires")) {
            for (JsonElement elem : json.get("requires").getAsJsonArray()) {
                String dUid = elem.getAsJsonObject().get("uid").getAsString();
                String dVer = elem.getAsJsonObject().has("equals") ? elem.getAsJsonObject().get("equals").getAsString() : null;
                String dSuggest = elem.getAsJsonObject().has("suggests") ? elem.getAsJsonObject().get("suggests").getAsString() : null;
                requires.add(new Dependency(dUid, dVer, dSuggest));
            }
        }
        return new Component(uid, version, name, isVolatile, order, requires.build());
    }
    
    public static List<Component> resolveAll(Map<String, String> initial) throws IOException {
        Map<String, Component> components = new HashMap<>();
        Map<String, Dependency> unresolvedDependencies = new HashMap<>();
        for (Map.Entry<String, String> entry : initial.entrySet()) {
            addToComponents(components, unresolvedDependencies, resolve(entry.getKey(), entry.getValue()));
        }
        // Remove dependencies that were already covered by initial
        unresolvedDependencies.keySet().removeIf(components::containsKey);
        while (!unresolvedDependencies.isEmpty()) {
            boolean stale = dependencyResolutionRound(components, unresolvedDependencies, Dependency::version);
            if (stale) stale = dependencyResolutionRound(components, unresolvedDependencies, Dependency::suggest);
            if (stale) {
                throw new IllegalStateException("Failed to build MultiMC pack: Failed to resolve all dependencies: Missing: " + String.join(", ", unresolvedDependencies.values().stream().map(Dependency::uid).toList()));
            }
        }
        return components.values().stream().toList();
    }
    
    private static void addToComponents(Map<String, Component> components, Map<String, Dependency> unresolvedDependencies, Component component) {
        if (components.containsKey(component.uid())) return;
        components.put(component.uid(), component);
        for (Dependency dependency : component.requires()) {
            if (!components.containsKey(dependency.uid())) {
                if (unresolvedDependencies.containsKey(dependency.uid())) {
                    unresolvedDependencies.put(dependency.uid(), unresolvedDependencies.get(dependency.uid()).mergeTo(dependency));
                } else {
                    unresolvedDependencies.put(dependency.uid(), dependency);
                }
            }
        }
    }

    private static boolean dependencyResolutionRound(Map<String, Component> components, Map<String, Dependency> unresolvedDependencies, Function<Dependency, String> versionGetter) throws IOException {
        Set<Component> componentsToAddThisRound = new HashSet<>();
        Iterator<Dependency> itr = unresolvedDependencies.values().iterator();
        while (itr.hasNext()) {
            Dependency dependency = itr.next();
            String version = versionGetter.apply(dependency);
            if (version == null) continue;
            componentsToAddThisRound.add(resolve(dependency.uid(), version));
            itr.remove();
        }
        for (Component component : componentsToAddThisRound) {
            addToComponents(components, unresolvedDependencies, component);
        }
        return componentsToAddThisRound.isEmpty();
    }

    private static JsonObject fetch(String endpoint) throws IOException {
        try {
            URL url = new URI(ENDPOINT + endpoint).toURL();
            Reader reader = new InputStreamReader(url.openStream());
            JsonObject json = GSON.fromJson(reader, JsonObject.class);
            reader.close();
            return json;
        } catch (URISyntaxException e) {
            throw new IOException(e);
        }
    }

    public record LoaderData(@Nullable String loaderUid, @Nullable String minecraftUid) {}
    
    public record Component(String uid, String version, String name, boolean isVolatile, int order, ImmutableList<Dependency> requires) {

        public JsonObject toJson(boolean important, boolean dependency) {
            JsonObject json = new JsonObject();
            json.addProperty("uid", this.uid);
            json.addProperty("version", this.version);
            json.addProperty("cachedName", this.name);
            json.addProperty("cachedVersion", this.version);
            if (this.isVolatile) json.addProperty("cachedVolatile", true);
            if (important) json.addProperty("important", true);
            if (dependency) json.addProperty("dependencyOnly", true);
            if (!this.requires.isEmpty()) {
                JsonArray array = new JsonArray();
                for (Dependency dep : this.requires) {
                    array.add(dep.toJson());
                }
                json.add("cachedRequires", array);
            }
            return json;
        }
    }

    public record Dependency(String uid, @Nullable String version, @Nullable String suggest) {

        public JsonObject toJson() {
            JsonObject json = new JsonObject();
            json.addProperty("uid", this.uid());
            if (this.version() != null) json.addProperty("equals", this.version());
            if (this.suggest() != null) json.addProperty("suggests", this.suggest());
            return json;
        }
        
        public Dependency mergeTo(Dependency other) {
            return new Dependency(this.uid,
                    this.version() != null ? this.version() : other.version(),
                    this.suggest() != null ? this.suggest() : other.suggest()
            );
        }
    }
}
