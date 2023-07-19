package org.moddingx.packdev.platform;

import com.google.gson.JsonElement;
import org.gradle.api.Project;
import org.moddingx.packdev.cache.PackDevCache;

import java.util.Comparator;
import java.util.List;

public interface ModdingPlatform<F extends ModFile> {
    
    String id();
    void initialise(Project project);
    List<F> readModList(Project project, PackDevCache cache, List<JsonElement> files);
    Comparator<F> internalOrder(); // Must not access any api and must be consistent.
}
