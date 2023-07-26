package org.moddingx.packdev.fabric;

import net.fabricmc.loom.api.LoomGradleExtensionAPI;
import net.fabricmc.loom.configuration.ide.RunConfigSettings;
import net.fabricmc.loom.util.Constants;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.tasks.*;
import org.moddingx.launcherlib.util.Artifact;
import org.moddingx.launcherlib.util.Side;
import org.moddingx.packdev.PackPaths;
import org.moddingx.packdev.PackSettings;
import org.moddingx.packdev.loader.LoaderSettings;
import org.moddingx.packdev.loader.ModLoader;
import org.moddingx.packdev.platform.ModFile;
import org.moddingx.packdev.util.Util;

import java.io.File;
import java.nio.file.Path;
import java.util.List;

public abstract class LoomLoader implements ModLoader<Void> {
    
    private static final String REMAP_CONFIGURATION = "modpack";
    
    private final String loaderGroup;
    private final String loaderArtifact;

    public LoomLoader(String loaderGroup, String loaderArtifact) {
        this.loaderGroup = loaderGroup;
        this.loaderArtifact = loaderArtifact;
    }

    @Override
    public Void initialise(Project project, PackPaths paths, List<ModFile> files) {
        LoomGradleExtensionAPI loom = Util.getExtension(project, "loom", LoomGradleExtensionAPI.class);

        // These are resolved and copied into the mods folder
        // Required to load Jar-in-Jar
        Configuration commonMods = project.getConfigurations().create("commonMods", c -> {
            c.setCanBeConsumed(false);
            c.setCanBeResolved(true);
        });
        Configuration clientMods = project.getConfigurations().create("clientMods", c -> {
            c.extendsFrom(commonMods);
            c.setCanBeConsumed(false);
            c.setCanBeResolved(true);
        });
        Configuration serverMods = project.getConfigurations().create("serverMods", c -> {
            c.extendsFrom(clientMods);
            c.setCanBeConsumed(false);
            c.setCanBeResolved(true);
        });
        
        
        // Let loom remap all mods into a dummy source set for better IDE support
        // Allows to set breakpoints and such
        SourceSetContainer sourceSets = Util.getJavaExtension(project).getSourceSets();
        SourceSet depSources = sourceSets.create("modpack_dependency", set -> {
            set.getJava().setSrcDirs(List.of());
            set.getResources().setSrcDirs(List.of());
        });
        loom.addRemapConfiguration(REMAP_CONFIGURATION, settings -> {
            settings.getSourceSet().set(depSources);
            settings.getTargetConfigurationName().set(REMAP_CONFIGURATION);
            settings.getOnCompileClasspath().set(true);
            settings.getOnRuntimeClasspath().set(false); // We load the mods from the mod folder, so don't put anything on runtime classpath
            settings.getApplyDependencyTransforms().set(true);
        });
        
        for (ModFile file : files) {
            Artifact artifact = file.createDependency();
            switch (file.fileSide()) {
                case COMMON -> project.getDependencies().add(commonMods.getName(), artifact.getDescriptor());
                case CLIENT -> project.getDependencies().add(clientMods.getName(), artifact.getDescriptor());
                case SERVER -> project.getDependencies().add(serverMods.getName(), artifact.getDescriptor());
            }
            // Add to source set for remapping
            project.getDependencies().add(REMAP_CONFIGURATION, artifact.getDescriptor());
        }
        
        this.configureRun(project, paths, loom, "client", Side.CLIENT, clientMods);
        this.configureRun(project, paths, loom, "server", Side.SERVER, serverMods);
        
        return null;
    }

    @Override
    public void setLoaderVersion(Project project, Void data, LoaderSettings settings) {
        LoomGradleExtensionAPI loom = Util.getExtension(project, "loom", LoomGradleExtensionAPI.class);
        
        project.getDependencies().add(Constants.Configurations.MINECRAFT, Artifact.from("com.mojang", "minecraft", settings.minecraftVersion()).getDescriptor());
        
        if (settings.officialMappings()) {
            project.getDependencies().add(Constants.Configurations.MAPPINGS, loom.officialMojangMappings());
        } else {
            // Empty layers gives intermediary
            //noinspection UnstableApiUsage
            project.getDependencies().add(Constants.Configurations.MAPPINGS, loom.layered(builder -> {}));
        }
        
        project.getDependencies().add("modImplementation", Artifact.from(this.loaderGroup, this.loaderArtifact, settings.loaderVersion()).getDescriptor());
    }

    @Override
    public void afterEvaluate(Project project, PackSettings settings, Void data) {
        //
    }
    
    private void configureRun(Project project, PackPaths paths, LoomGradleExtensionAPI loom, String name, Side side, Configuration configuration) {
        String capitalized = Util.capitalize(name);
        String taskName = "run" + capitalized;

        RunConfigSettings settings = loom.getRunConfigs().maybeCreate(name);
        settings.environment(side.id);
        settings.defaultMainClass(side == Side.CLIENT ? Constants.Knot.KNOT_CLIENT : Constants.Knot.KNOT_SERVER);
        settings.runDir(taskName);

        // Delete old mods from the mods folder, so we correctly handle mod removals.
        Delete deleteTask = project.getTasks().create("delete" + capitalized + "Data", Delete.class);
        deleteTask.delete(new File(project.file(taskName), "mods"));
        
        Copy copyTask = project.getTasks().create("copy" + capitalized + "Data", Copy.class);
        copyTask.dependsOn(deleteTask);
        copyTask.setDestinationDir(project.file(taskName));
        for (Path path : paths.getOverridePaths(side)) {
            copyTask.from(project.fileTree(path.toFile()));
        }
        
        // Put all mods into the mods folder
        copyTask.from(configuration, spec -> spec.into("mods"));
        
        project.getGradle().projectsEvaluated(g -> {
            TaskProvider<Task> prepareTask = project.getTasks().named(side == Side.CLIENT ? "configureClientLaunch" : "configureLaunch");
            prepareTask.configure(t -> t.dependsOn(copyTask));
        });
    }
}
