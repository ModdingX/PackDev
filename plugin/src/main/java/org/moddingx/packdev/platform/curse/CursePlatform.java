package org.moddingx.packdev.platform.curse;

import com.google.gson.JsonElement;
import org.gradle.api.Project;
import org.moddingx.launcherlib.util.Side;
import org.moddingx.packdev.cache.PackDevCache;
import org.moddingx.packdev.platform.ModdingPlatform;
import org.moddingx.packdev.util.curse.CurseUtil;

import java.util.Comparator;
import java.util.List;

public class CursePlatform implements ModdingPlatform<CurseFile> {

    public static final CursePlatform INSTANCE = new CursePlatform();

    private CursePlatform() {

    }

    @Override
    public String id() {
        return "curseforge";
    }

    @Override
    public void initialise(Project project) {
        project.afterEvaluate((ignored) ->
                project.getRepositories().maven(r -> {
                    r.setUrl(CurseUtil.CURSE_MAVEN);
                    r.content(c -> c.includeGroup("curse.maven"));
                }));
    }

    @Override
    public List<CurseFile> readModList(Project project, PackDevCache cache, List<JsonElement> files) {
        return files.stream().map(JsonElement::getAsJsonObject).map(json -> new CurseFile(
                project, cache, json.get("project").getAsInt(), json.get("file").getAsInt(),
                Side.byId(json.get("side").getAsString())
        )).toList();
    }

    @Override
    public Comparator<CurseFile> internalOrder() {
        return Comparator.comparing((CurseFile cf) -> cf.projectId).thenComparing((CurseFile cf) -> cf.fileId);
    }
}
