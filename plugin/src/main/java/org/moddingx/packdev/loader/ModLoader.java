package org.moddingx.packdev.loader;

import org.gradle.api.Project;
import org.moddingx.packdev.PackPaths;
import org.moddingx.packdev.PackSettings;
import org.moddingx.packdev.platform.ModFile;

import java.util.List;

public interface ModLoader<T> {
    
    T initialise(Project project, PackPaths paths, List<ModFile> files);
    void setLoaderVersion(Project project, T data, LoaderSettings settings);
    void afterEvaluate(Project project, PackSettings settings, T data);
}
