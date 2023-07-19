package org.moddingx.packdev;

import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.plugins.UnknownPluginException;
import org.moddingx.packdev.api.CurseProperties;
import org.moddingx.packdev.loader.ModLoader;
import org.moddingx.packdev.platform.ModFile;
import org.moddingx.packdev.platform.ModdingPlatform;
import org.moddingx.packdev.platform.curse.CursePlatform;
import org.moddingx.packdev.platform.modrinth.ModrinthPlatform;
import org.moddingx.packdev.target.CursePack;
import org.moddingx.packdev.target.ModrinthPack;
import org.moddingx.packdev.target.MultiMcPack;
import org.moddingx.packdev.target.ServerPack;
import org.moddingx.packdev.util.LoaderConstants;
import org.moddingx.packdev.util.Util;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PackDevRegistry {
    
    private static final Map<String, ModdingPlatform<?>> platforms = new HashMap<>();
    private static final Map<String, LoaderData> loaders = new HashMap<>();
    private static final Map<String, ConfiguredTarget> targets = new HashMap<>();
    
    static {
        registerPlatform(CursePlatform.INSTANCE);
        registerPlatform(ModrinthPlatform.INSTANCE);
        
        registerLoader(LoaderConstants.FORGE, "net.minecraftforge.gradle", "org.moddingx.packdev.forge.ForgeLoader");
        registerLoader(LoaderConstants.FABRIC, "fabric-loom", "org.moddingx.packdev.fabric.FabricLoader");
        registerLoader(LoaderConstants.QUILT, "org.quiltmc.loom", "org.moddingx.packdev.fabric.QuiltLoader");
        
        registerTarget("curse", CursePack.class, CurseProperties.class);
        registerTarget("modrinth", ModrinthPack.class);
        registerTarget("server", ServerPack.class);
        registerTarget("multimc", MultiMcPack.class);
    }
    
    public static synchronized void registerPlatform(ModdingPlatform<?> platform) {
        String id = platform.id();
        if (platforms.containsKey(id)) {
            throw new IllegalStateException("Duplicate modding platform: " + id);
        } else {
            platforms.put(id, platform);
        }
    }

    public static synchronized void registerLoader(String id, String loaderPlugin, String loaderClass) {
        if (loaders.containsKey(id)) {
            throw new IllegalStateException("Duplicate modpack loader: " + id);
        } else {
            loaders.put(id, new LoaderData(List.of(loaderPlugin), loaderClass));
        }
    }

    public static synchronized void registerLoader(String id, List<String> loaderPlugins, String loaderClass) {
        if (loaders.containsKey(id)) {
            throw new IllegalStateException("Duplicate modpack loader: " + id);
        } else {
            loaders.put(id, new LoaderData(List.copyOf(loaderPlugins), loaderClass));
        }
    }
    
    public static synchronized void registerTarget(String id, Class<? extends Task> taskClass) {
        registerTarget(id, taskClass, null);
    }
    
    public static synchronized void registerTarget(String id, Class<? extends Task> taskClass, Class<?> propertiesClass) {
        if (targets.containsKey(id)) {
            throw new IllegalStateException("Duplicate modpack target: " + id);
        } else {
            targets.put(id, new ConfiguredTarget(taskClass, propertiesClass));
        }
    }
    
    public static synchronized ModdingPlatform<?> getPlatform(String id) {
        if (platforms.containsKey(id)) {
            return platforms.get(id);
        } else {
            throw new IllegalArgumentException("Unknown modding platform: PackDev can't handle modpacks for platform: " + id);
        }
    }
    
    public static synchronized ModLoader<?> getAndApplyLoader(Project project, String id) {
        if (loaders.containsKey(id)) {
            LoaderData data = loaders.get(id);
            for (String plugin : data.loaderPlugins()) {
                if (project.getPlugins().hasPlugin(plugin)) continue;
                try {
                    project.getPlugins().apply(plugin);
                } catch (UnknownPluginException e) {
                    throw new IllegalStateException("The " + id + " loader needs the " + plugin + " gradle plugin. Make sure, it is present on the classpath.", e);
                }
            }
            try {
                Class<?> cls = Class.forName(data.loaderClass());
                return (ModLoader<?>) cls.getConstructor().newInstance();
            } catch (ReflectiveOperationException | ClassCastException e) {
                throw new IllegalStateException("Failed to instantiate loader support for " + id, e);
            }
        } else {
            throw new IllegalArgumentException("Unknown modpack loader: PackDev can't handle modpacks for loader: " + id);
        }
    }
    
    public static synchronized Task createTargetTask(Project project, String id, ModdingPlatform<?> platform, PackSettings settings, List<? extends ModFile> files, @Nullable Object properties) {
        if (targets.containsKey(id)) {
            ConfiguredTarget target = targets.get(id);
            if (properties == null && target.propertiesClass() != null) {
                throw new IllegalArgumentException("Missing properties for target " + id + ", expected " + target.propertiesClass());
            } else if (target.propertiesClass() != null && !target.propertiesClass().isAssignableFrom(properties.getClass())) {
                throw new IllegalArgumentException("Invalid properties for target " + id + ", expected " + target.propertiesClass() + " got " + properties.getClass());
            } else if (target.propertiesClass() != null) {
                return project.getTasks().create("build" + Util.capitalize(id) + "Pack", target.taskClass(), platform, settings, List.copyOf(files), properties);
            } else {
                return project.getTasks().create("build" + Util.capitalize(id) + "Pack", target.taskClass(), platform, settings, List.copyOf(files));
            }
        } else {
            throw new IllegalArgumentException("Unknown modpack target: PackDev can't build targets of type: " + id);
        }
    }

    private record LoaderData(List<String> loaderPlugins, String loaderClass) {}
    private record ConfiguredTarget(Class<? extends Task> taskClass, @Nullable Class<?> propertiesClass) {}
}
