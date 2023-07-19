package org.moddingx.packdev;

import org.apache.commons.io.file.PathUtils;
import org.gradle.api.Project;
import org.moddingx.launcherlib.util.Side;

import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class PackPaths {
    
    private final Project project;

    public PackPaths(Project project) {
        this.project = project;
    }
    
    public Path getPath(Side side) {
        return this.project.file("data/" + side.id).toPath();
    }

    // Elements later in the list should overwrite
    // Null means everything
    public final List<Path> getOverridePaths(@Nullable Side side) {
        List<Path> list = new ArrayList<>();
        if (side != null) {
            list.add(this.getPath(Side.COMMON));
            if (side != Side.COMMON) list.add(this.getPath(side));
        } else {
            list.add(this.getPath(Side.COMMON));
            list.add(this.getPath(Side.SERVER));
            list.add(this.getPath(Side.CLIENT));
        }
        return Collections.unmodifiableList(list);
    }
    
    public void copyAllDataTo(Path target, @Nullable Side side) throws IOException {
        if (!Files.isDirectory(target)) {
            Files.createDirectories(target);
        }
        for (Path src : this.getOverridePaths(side)) {
            if (Files.isDirectory(src)) {
                PathUtils.copyDirectory(src, target, StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    public void copyOverrideDataTo(Path target, Side side) throws IOException {
        if (!Files.isDirectory(target)) {
            Files.createDirectories(target);
        }
        Path src = this.getPath(side);
        if (Files.isDirectory(src)) {
            PathUtils.copyDirectory(src, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
