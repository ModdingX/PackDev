package org.moddingx.packdev.platform.modrinth;

import com.google.gson.JsonElement;
import org.gradle.api.Project;
import org.moddingx.launcherlib.util.Side;
import org.moddingx.packdev.cache.PackDevCache;
import org.moddingx.packdev.platform.ModdingPlatform;

import java.util.Comparator;
import java.util.List;

public class ModrinthPlatform implements ModdingPlatform<ModrinthFile> {

    public static final ModrinthPlatform INSTANCE = new ModrinthPlatform();

    private ModrinthPlatform() {

    }

    @Override
    public String id() {
        return "modrinth";
    }

    @Override
    public void initialise(Project project) {
        project.getRepositories().maven(r -> {
            r.setUrl("https://api.modrinth.com/maven");
            r.content(c -> c.includeGroup("maven.modrinth"));
        });
    }

    @Override
    public List<ModrinthFile> readModList(Project project, PackDevCache cache, List<JsonElement> files) {
        return files.stream().map(JsonElement::getAsJsonObject).map(json -> new ModrinthFile(
                project, cache, json.get("project").getAsString(), json.get("file").getAsString(),
                Side.byId(json.get("side").getAsString())
        )).toList();
    }

    @Override
    public Comparator<ModrinthFile> internalOrder() {
        return Comparator.comparing((ModrinthFile mf) -> mf.projectId).thenComparing((ModrinthFile mf) -> mf.versionId);
    }
}
