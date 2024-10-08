package org.moddingx.packdev.neoforge;

import net.neoforged.gradle.dsl.common.runs.run.Run;
import org.gradle.api.DefaultTask;
import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.file.DuplicatesStrategy;
import org.gradle.api.tasks.*;
import org.gradle.language.jvm.tasks.ProcessResources;
import org.moddingx.launcherlib.util.Artifact;
import org.moddingx.launcherlib.util.Side;
import org.moddingx.packdev.PackPaths;
import org.moddingx.packdev.PackSettings;
import org.moddingx.packdev.loader.LoaderSettings;
import org.moddingx.packdev.loader.ModLoader;
import org.moddingx.packdev.platform.ModFile;
import org.moddingx.packdev.util.Util;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;

public class NeoForgeLoader implements ModLoader<Void> {

    @Override
    public Void initialise(Project project, PackPaths paths, List<ModFile> files) {
        Configuration clientMods = project.getConfigurations().create("clientMods", c -> {
            c.setCanBeConsumed(false);
            c.setCanBeResolved(true);
        });
        Configuration serverMods = project.getConfigurations().create("serverMods", c -> {
            c.setCanBeConsumed(false);
            c.setCanBeResolved(true);
        });
        Configuration compileOnly = project.getConfigurations().getByName("compileOnly");
        compileOnly.extendsFrom(clientMods, serverMods);
        
        for (ModFile file : files) {
            String cfg = switch (file.fileSide()) {
                case COMMON -> "implementation";
                case CLIENT -> "clientMods";
                case SERVER -> "serverMods";
            };
            Artifact artifact = file.createDependency();
            project.getDependencies().add(cfg, artifact.getDescriptor());
        }
        
        SourceSetContainer sourceSets = Util.getJavaExtension(project).getSourceSets();
        SourceSet mainSources = sourceSets.getByName(SourceSet.MAIN_SOURCE_SET_NAME);
        SourceSet clientDepSources = sourceSets.create("modpack_dependency_client", set -> {
            set.getJava().setSrcDirs(List.of());
            set.getResources().setSrcDirs(List.of());
            set.setRuntimeClasspath(clientMods);
        });
        SourceSet serverDepSources = sourceSets.create("modpack_dependency_server", set -> {
            set.getJava().setSrcDirs(List.of());
            set.getResources().setSrcDirs(List.of());
            set.setRuntimeClasspath(serverMods);
        });

        setupDummyModMetadata(project);
        //noinspection unchecked
        NamedDomainObjectContainer<Run> runs = Util.getExtension(project, "runs", NamedDomainObjectContainer.class);
        addRun(project, paths, runs, "client", Side.CLIENT, mainSources, clientDepSources);
        addRun(project, paths, runs, "server", Side.SERVER, mainSources, serverDepSources);
        
        return null;
    }

    @Override
    public void setLoaderVersion(Project project, Void data, LoaderSettings settings) {
        if ("1.20.1".equals(settings.minecraftVersion())) {
            // NeoForge 1.20.1 is basically MinecraftForge 1.20.1 with a few modifications but an entirely different build system.
            System.err.println("PackDev doesn't support NeoForge 1.20.1. Please use MinecraftForge instead.");
            throw new IllegalStateException("PackDev doesn't support NeoForge 1.20.1. Please use MinecraftForge instead.");
        }
        
        if (settings.officialMappings()) {
            System.err.println("""
                               Redundant useOfficialMappings() with NeoForge.
                               NeoForge always uses official mappings at runtime.
                               """);
        }
        
        Artifact artifact = Artifact.from("net.neoforged", "neoforge", settings.loaderVersion());
        project.getDependencies().add("implementation", artifact.getDescriptor());
    }

    @Override
    public void afterEvaluate(Project project, PackSettings settings, Void data) {
        //
    }

    private static void setupDummyModMetadata(Project project) {
        // Recent NeoForge versions need an actual mod manifest to be present in order to load
        // Therefore we put a default manifest into the main resources.
        ProcessResources res = Util.findTask(project, "processResources", ProcessResources.class);
        if (res == null) throw new IllegalStateException("Cannot set up PackDev run: processResources task not found");
        Path manifestDir = project.getLayout().getBuildDirectory().get().getAsFile().toPath().resolve("createDummyManifest");
        Path manifestPath = manifestDir.resolve("neoforge.mods.toml");
        TaskProvider<DefaultTask> createManifest = project.getTasks().register("createDummyManifest", DefaultTask.class, task -> {
            task.doLast(t -> {
                try {
                    Files.createDirectories(manifestDir);
                    Files.writeString(manifestDir.resolve("neoforge.mods.toml"), """
                        modLoader="lowcodefml"
                        loaderVersion="(0,)"
                        license="packdev_dummy"
                        mods=[{modId="packdev_dummy"}]
                        """, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        });
        res.dependsOn(createManifest);
        res.from(manifestPath.toFile(), spec -> {
            spec.into("META-INF");
            spec.setDuplicatesStrategy(DuplicatesStrategy.EXCLUDE);
        });
    }
    
    private static void addRun(Project project, PackPaths paths, NamedDomainObjectContainer<Run> runs, String name, Side side, SourceSet commonMods, SourceSet additionalMods) {
        String capitalized = Util.capitalize(name);
        File workingDir = project.file("runs/" + name);

        // Required if mods are provided through the mods folder (because they don't exist on the platform)
        // Delete old mods from the mods folder, so we correctly handle mod removals.
        TaskProvider<Delete> deleteTask = project.getTasks().register("delete" + capitalized + "Data", Delete.class, task -> {
            task.delete(new File(workingDir, "mods"));
        });
        
        TaskProvider<Copy> copyTask = project.getTasks().register("copy" + capitalized + "Data", Copy.class, task -> {
            task.dependsOn(deleteTask);
            task.setDestinationDir(workingDir);
            for (Path path : paths.getOverridePaths(side)) {
                task.from(project.fileTree(path.toFile()));
            }
        });

        runs.create(name, run -> {
            run.modSources(commonMods, additionalMods);
            run.systemProperty("production", "true");
            run.workingDirectory(workingDir);
            run.getDependsOn().add(copyTask);
        });
    }
}
