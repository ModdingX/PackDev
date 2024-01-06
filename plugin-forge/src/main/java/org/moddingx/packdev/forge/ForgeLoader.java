package org.moddingx.packdev.forge;

import net.minecraftforge.gradle.mcp.ChannelProvidersExtension;
import net.minecraftforge.gradle.mcp.tasks.GenerateSRG;
import net.minecraftforge.gradle.userdev.DependencyManagementExtension;
import net.minecraftforge.gradle.userdev.UserDevExtension;
import org.gradle.api.Action;
import org.gradle.api.DefaultTask;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ExternalModuleDependency;
import org.gradle.api.tasks.Copy;
import org.gradle.api.tasks.Delete;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.compile.JavaCompile;
import org.moddingx.launcherlib.util.Artifact;
import org.moddingx.launcherlib.util.Side;
import org.moddingx.packdev.PackPaths;
import org.moddingx.packdev.PackSettings;
import org.moddingx.packdev.loader.LoaderSettings;
import org.moddingx.packdev.loader.ModLoader;
import org.moddingx.packdev.platform.ModFile;
import org.moddingx.packdev.util.Util;

import javax.annotation.Nonnull;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class ForgeLoader implements ModLoader<Void> {

    @Override
    public Void initialise(Project project, PackPaths paths, List<ModFile> files) {
        ChannelProvidersExtension channelProviders = Util.getExtension(project, ChannelProvidersExtension.EXTENSION_NAME, ChannelProvidersExtension.class);
        channelProviders.addProvider(new SrgChannelProvider());
        
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
        
        DependencyManagementExtension fgExt = Util.getExtension(project, DependencyManagementExtension.EXTENSION_NAME, DependencyManagementExtension.class);
        for (ModFile file : files) {
            String cfg = switch (file.fileSide()) {
                case COMMON -> "implementation";
                case CLIENT -> "clientMods";
                case SERVER -> "serverMods";
            };
            Artifact artifact = file.createDependency();
            ExternalModuleDependency dependency = (ExternalModuleDependency) project.getDependencies().create(artifact.getDescriptor());
            project.getDependencies().add(cfg, fgExt.deobf(dependency));
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
        
        UserDevExtension mcExt = Util.getExtension(project, UserDevExtension.EXTENSION_NAME, UserDevExtension.class);
        addRunConfig(project, paths, mcExt, "client", Side.CLIENT, mainSources, clientDepSources);
        addRunConfig(project, paths, mcExt, "server", Side.SERVER, mainSources, serverDepSources);
        
        return null;
    }

    @Override
    public void setLoaderVersion(Project project, Void data, LoaderSettings settings) {
        UserDevExtension mcExt = Util.getExtension(project, UserDevExtension.EXTENSION_NAME, UserDevExtension.class);
        
        if (settings.officialMappings()) {
            mcExt.mappings("official", settings.minecraftVersion());
        } else {
            mcExt.mappings(SrgChannelProvider.CHANNEL, settings.minecraftVersion());
        }
        
        Artifact artifact = Artifact.from("net.minecraftforge", "forge", settings.minecraftVersion() + "-" + settings.loaderVersion());
        project.getDependencies().add("minecraft", artifact.getDescriptor());
    }

    @Override
    public void afterEvaluate(Project project, PackSettings settings, Void data) {
        //
    }

    private static void addRunConfig(Project project, PackPaths paths, UserDevExtension ext, String name, Side side, SourceSet commonMods, SourceSet additionalMods) {
        String capitalized = Util.capitalize(name);
        File workingDir = project.file("runs/" + name);
        ext.getRuns().create(name, run -> {
            run.workingDirectory(workingDir);
            run.property("forge.logging.console.level", "info");
            GenerateSRG generateMappings = Util.findTask(project, "createMcpToSrg", GenerateSRG.class);
            if (generateMappings != null) {
                run.property("mixin.env.remapRefMap", "true");
                run.property("mixin.env.refMapRemappingFile", generateMappings.getOutput().get().getAsFile().toPath().toAbsolutePath().normalize().toString());
            }
            run.jvmArg("-Dproduction=true");
            run.jvmArg("-Dforge.enableGameTest=false");
            run.jvmArg("-Dforge.gameTestServer=false");
            run.getMods().create("packdev_dummy_mod", mod -> {
                mod.source(commonMods);
                mod.source(additionalMods);
            });
        });

        // Required if mods are provided through the mods folder (because they don't exist on the platform)
        // Delete old mods from the mods folder, so we correctly handle mod removals.
        Delete deleteTask = project.getTasks().create("delete" + capitalized + "Data", Delete.class);
        deleteTask.delete(new File(workingDir, "mods"));
        
        Copy copyTask = project.getTasks().create("copy" + capitalized + "Data", Copy.class);
        copyTask.dependsOn(deleteTask);
        copyTask.setDestinationDir(workingDir);
        for (Path path : paths.getOverridePaths(side)) {
            copyTask.from(project.fileTree(path.toFile()));
        }

        // Create some directories because Forge 1.17+ requires it
        JavaCompile jc = Util.findTask(project, "compileJava", JavaCompile.class);
        if (jc == null) throw new IllegalStateException("Cannot set up PackDev run config: compileJava task not found");
        Task createDirTask = project.getTasks().create("prepare" + capitalized + "Data", DefaultTask.class);
        //noinspection Convert2Lambda
        createDirTask.doLast(new Action<>() {
            
            @Override
            public void execute(@Nonnull Task task) {
                try {
                    Files.createDirectories(jc.getDestinationDirectory().getAsFile().get().toPath());
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        });
        createDirTask.getOutputs().upToDateWhen(t -> false);

        project.getGradle().projectsEvaluated(g -> {
            Task prepareRunsTask = project.getTasks().getByName("prepareRuns");
            prepareRunsTask.dependsOn(copyTask);
            prepareRunsTask.dependsOn(createDirTask);
        });
    }
}
