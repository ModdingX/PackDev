package org.moddingx.packdev.platform.modrinth.api;

import com.google.gson.*;
import org.moddingx.launcherlib.util.Either;
import org.moddingx.packdev.platform.ModFile;
import org.moddingx.packdev.util.hash.ComputedHash;

import javax.annotation.Nullable;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public class ModrinthAPI {
    
    // Trailing slash is important, so URI#resolve works properly
    private static final URI BASE_URL = URI.create("https://api.modrinth.com/v2/");
    private static final HttpClient client = HttpClient.newHttpClient();
    
    public static final Gson GSON;

    static {
        GsonBuilder builder = new GsonBuilder();
        builder.disableHtmlEscaping();
        GSON = builder.create();
    }
    
    public static Map<ComputedHash, VersionInfo> files(Set<ComputedHash> hashes) {
        try {
            JsonArray array = new JsonArray();
            for (ComputedHash hash : hashes) array.add(hash.hexDigest());
            JsonObject request = new JsonObject();
            request.addProperty("algorithm", "sha512");
            request.add("hashes", array);
            JsonObject json = request("version_files", Map.of(), request).getAsJsonObject();
            Map<ComputedHash, VersionInfo> map = new HashMap<>();
            for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
                ComputedHash hash = ComputedHash.of(entry.getKey(), 512);
                map.put(hash, version(entry.getValue().getAsJsonObject()));
            }
            return Collections.unmodifiableMap(map);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
    
    public static ProjectInfo project(String projectId) {
        try {
            JsonObject json = request("project/" + URLEncoder.encode(projectId, StandardCharsets.UTF_8)).getAsJsonObject();
            return new ProjectInfo(
                    json.get("slug").getAsString(),
                    json.get("project_type").getAsString(),
                    json.get("title").getAsString()
            );
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
    
    public static VersionInfo version(String versionId) {
        try {
            JsonObject json = request("version/" + URLEncoder.encode(versionId, StandardCharsets.UTF_8)).getAsJsonObject();
            return version(json);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
    
    private static VersionInfo version(JsonObject json) {
        try {
            JsonObject file = null;
            for (JsonElement elem : json.get("files").getAsJsonArray()) {
                if (elem.getAsJsonObject().get("primary").getAsBoolean()) {
                    file = elem.getAsJsonObject();
                    break;
                }
            }
            if (file == null) {
                throw new JsonSyntaxException("No primary file in version.");
            }
            Map<String, String> hashes = new HashMap<>();
            for (Map.Entry<String, JsonElement> entry : file.get("hashes").getAsJsonObject().entrySet()) {
                hashes.put(entry.getKey(), entry.getValue().getAsString());
            }
            return new VersionInfo(
                    file.get("filename").getAsString(),
                    json.get("version_number").getAsString(),
                    file.get("size").getAsLong(),
                    new URI(file.get("url").getAsString()),
                    Collections.unmodifiableMap(hashes)
            );
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }
    
    public static Optional<ModFile.Owner> owner(String projectId) {
        try {
            JsonArray array = request("project/" + URLEncoder.encode(projectId, StandardCharsets.UTF_8) + "/members").getAsJsonArray();
            for (JsonElement elem : array) {
                JsonObject json = elem.getAsJsonObject();
                if (json.get("role").getAsString().toLowerCase(Locale.ROOT).equals("owner")) {
                    JsonObject user = json.get("user").getAsJsonObject();
                    String username = json.get("user").getAsJsonObject().get("username").getAsString();
                    String name = username;
                    if (user.has("name") && !user.get("name").isJsonNull()) {
                        name = user.get("name").getAsString();
                    }
                    return Optional.of(new ModFile.Owner(name, new URI("https://modrinth.com/user/" + URLEncoder.encode(username, StandardCharsets.UTF_8))));
                }
            }
            return Optional.empty();
        } catch (IOException | URISyntaxException e) {
            return Optional.empty();
        }
    }
    
    private static JsonElement request(String route) throws IOException {
        return request(route, Map.of());
    }

    private static JsonElement request(String route, Map<String, String> query) throws IOException {
        return request(route, query, null);
    }
    
    private static JsonElement request(String route, Map<String, String> query, @Nullable JsonElement body) throws IOException {
        String routeStr = route.startsWith("/") ? route.substring(1) : route;
        String queryStr = "";
        if (!query.isEmpty()) {
            queryStr = query.entrySet().stream()
                    .map(entry -> URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8) + "=" + URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8))
                    .collect(Collectors.joining("&", "?", ""));
        }
        URI req = BASE_URL.resolve(routeStr + queryStr);
        try {
            HttpRequest.Builder builder;
            if (body == null) {
                builder = HttpRequest.newBuilder().GET();
            } else {
                String bodyStr = GSON.toJson(body);
                builder = HttpRequest.newBuilder().POST(HttpRequest.BodyPublishers.ofString(bodyStr));
                builder = builder.header("Content-Type", "application/json");
            }
            return client.<Either<JsonElement, IOException>>send(
                    builder.uri(req)
                            .header("Accept", "application/json")
                            .header("User-Agent", "ModdingX/UpdateCheckerGenerator")
                            .build(),
                    resp -> {
                        if ((resp.statusCode() / 100) == 2 && resp.statusCode() != 204) {
                            return HttpResponse.BodySubscribers.mapping(
                                    HttpResponse.BodySubscribers.ofString(StandardCharsets.UTF_8),
                                    str -> Either.tryWith(() -> GSON.fromJson(str, JsonElement.class))
                                            .mapRight(ex -> new IOException("Failed to parse json response from modrinth api", ex))
                            );
                        } else {
                            return HttpResponse.BodySubscribers.replacing(Either.right(new IOException("HTTP Status Code: " + resp.statusCode())));
                        }
                    }
            ).body().getOrThrow(Function.identity(), Function.identity());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted", e);
        }
    }
}
