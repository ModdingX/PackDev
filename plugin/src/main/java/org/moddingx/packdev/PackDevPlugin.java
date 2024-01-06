package org.moddingx.packdev;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.ExternalModuleDependency;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.bundling.AbstractArchiveTask;
import org.gradle.build.event.BuildEventsListenerRegistry;
import org.gradle.jvm.toolchain.JavaLanguageVersion;
import org.moddingx.launcherlib.util.Side;
import org.moddingx.packdev.cache.PackDevCache;
import org.moddingx.packdev.loader.LoaderSettingsConsumer;
import org.moddingx.packdev.loader.ModLoader;
import org.moddingx.packdev.platform.ModFile;
import org.moddingx.packdev.platform.ModdingPlatform;
import org.moddingx.packdev.util.DependencyConstants;
import org.moddingx.packdev.util.MoonstoneTask;
import org.moddingx.packdev.util.Util;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.inject.Inject;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.concurrent.TimeUnit;

public abstract class PackDevPlugin implements Plugin<Project> {

    @Inject
    public abstract BuildEventsListenerRegistry getEventRegistry();
    
    @Override
    public void apply(@Nonnull Project project) {
        this.doApply(project);
    }
    
    public <T> void doApply(@Nonnull Project project) {
        PackPaths paths = new PackPaths(project);
        try {
            for (Side side : Side.values()) {
                if (!Files.exists(paths.getPath(side))) {
                    Files.createDirectories(paths.getPath(side));
                }
            }

            if (!Files.exists(project.file("modlist.json").toPath())) {
                Writer writer = Files.newBufferedWriter(project.file("modlist.json").toPath(), StandardOpenOption.CREATE_NEW);
                writer.write("{}\n");
                writer.close();
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        ModdingPlatform<?> platform;
        String loaderId;
        String minecraftVersion;
        List<JsonElement> fileData;
        try (Reader in = Files.newBufferedReader(project.file("modlist.json").toPath())) {
            JsonObject json = Util.GSON.fromJson(in, JsonObject.class);

            int api = Objects.requireNonNull(json.get("api"), "No modlist.json API version found.").getAsInt();
            if (api != 2) {
                throw new IllegalStateException("Unsupported modlist.json API: " + api + ". This version of PackDev requires api version 2.");
            }

            platform = PackDevRegistry.getPlatform(Objects.requireNonNull(json.get("platform"), "No modding platform set.").getAsString());
            loaderId = Objects.requireNonNull(json.get("loader"), "No mod loader set.").getAsString();
            minecraftVersion = Objects.requireNonNull(json.get("minecraft"), "No minecraft version set.").getAsString();

            fileData = new ArrayList<>();
            if (json.has("installed")) {
                json.get("installed").getAsJsonArray().forEach(fileData::add);
            }
            if (json.has("dependencies")) {
                json.get("dependencies").getAsJsonArray().forEach(fileData::add);
            }
            fileData = List.copyOf(fileData);
        } catch (JsonSyntaxException e) {
            throw new IllegalStateException("Invalid modlist.json: " + e.getMessage(), e);
        } catch (NullPointerException e) {
            throw new IllegalStateException("Failed to read modlist.json: " + e.getMessage(), e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        
        PackDevCache cache = new PackDevCache(project, platform);
        this.getEventRegistry().onTaskCompletion(project.provider(() -> e -> cache.save()));
        
        if (!project.getPlugins().hasPlugin("java")) project.getPlugins().apply("java");
        int javaVersion = cache.getJavaVersion(minecraftVersion);
        Util.getJavaExtension(project).getToolchain().getLanguageVersion().set(JavaLanguageVersion.of(javaVersion));

        DependencyConstants.addRepositories(project);
        Configuration moonstone = project.getConfigurations().create("moonstone", c -> {
            c.setCanBeResolved(true);
            c.setCanBeConsumed(false);
            c.resolutionStrategy(rs -> {
                rs.cacheChangingModulesFor(30, TimeUnit.MINUTES);
                rs.cacheDynamicVersionsFor(30, TimeUnit.MINUTES);
            });
        });
        Dependency moonstoneDependency = project.getDependencies().add(moonstone.getName(), DependencyConstants.MOONSTONE);
        if (moonstoneDependency instanceof ExternalModuleDependency emd) emd.setChanging(true);
        
        platform.initialise(project);
        List<ModFile> files = List.copyOf(platform.readModList(project, cache, fileData));
        
        @SuppressWarnings("unchecked")
        ModLoader<T> loaderInstance = (ModLoader<T>) PackDevRegistry.getAndApplyLoader(project, loaderId);
        T loaderData = loaderInstance.initialise(project, paths, files);

        // SourceSet for better IDE integration
        SourceSetContainer sourceSets = Util.getJavaExtension(project).getSourceSets();
        sourceSets.create("data", set -> {
            set.getJava().setSrcDirs(List.of());
            set.getResources().setSrcDirs(Arrays.stream(Side.values()).map(paths::getPath).map(Path::toFile).toList());
        });
        
        LoaderSettingsConsumer loaderSettingsAcceptor = factory -> loaderInstance.setLoaderVersion(project, loaderData, factory.create(minecraftVersion));
        PackDevExtension ext = project.getExtensions().create(PackDevExtension.EXTENSION_NAME, PackDevExtension.class, loaderSettingsAcceptor);
        
        project.afterEvaluate(p -> {
            project.getTasks().register("moonstone", MoonstoneTask.class, t -> t.classpath(project.provider(moonstone::resolve)));
            
            PackSettings settings = new PackSettings(
                    Objects.requireNonNull(p.getName(), "Project name not set"),
                    Objects.requireNonNull(p.getVersion(), "Project version not set").toString(),
                    minecraftVersion, loaderId, ext.getLoaderVersion(), javaVersion,
                    Optional.ofNullable(ext.getAuthor()),
                    paths, cache.launcher()
            );
            
            loaderInstance.afterEvaluate(p, settings, loaderData);

            Task buildTargetsTask = project.getTasks().create("buildTargets");
            Task buildTask = project.getTasks().findByName("build");
            if (buildTask != null) buildTask.dependsOn(buildTargetsTask);
            
            Map<String, Optional<Object>> targets = ext.getAllTargets();
            if (targets.isEmpty()) {
                System.err.println("Warning: No modpack targets defined.");
            } else {
                targets.entrySet().stream()
                        .sorted(Map.Entry.comparingByKey())
                        .forEach(target -> addBuildTask(project, target.getKey(), platform, settings, files, target.getValue().orElse(null), buildTargetsTask));
            }
        });
    }

    private static void addBuildTask(Project project, String id, ModdingPlatform<?> platform, PackSettings settings, List<ModFile> files, @Nullable Object properties, Task buildTargetsTask) {
        Task task = PackDevRegistry.createTargetTask(project, id, platform, settings, files, properties);
        if (task instanceof AbstractArchiveTask archive) {
            archive.getDestinationDirectory().set(project.file("build").toPath().resolve("target").toFile());
            archive.getArchiveBaseName().convention(project.provider(project::getName));
            archive.getArchiveVersion().convention(project.provider(() -> project.getVersion().toString()));
            archive.getArchiveClassifier().convention(id.toLowerCase(Locale.ROOT));
        }
        buildTargetsTask.dependsOn(task);
    }
}
