package org.moddingx.packdev;

import groovy.lang.Closure;
import groovy.lang.DelegatesTo;
import groovy.lang.GroovyObjectSupport;
import groovy.transform.Internal;
import jakarta.annotation.Nullable;
import org.gradle.api.Action;
import org.moddingx.packdev.api.CurseProperties;
import org.moddingx.packdev.loader.LoaderSettings;
import org.moddingx.packdev.loader.LoaderSettingsConsumer;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public class PackDevExtension extends GroovyObjectSupport {

    public static final String EXTENSION_NAME = "modpack";

    private final TargetBuilder targets;
    private final LoaderSettingsConsumer loaderSettingsAcceptor;
    
    @Nullable private String loaderVersion;
    @Nullable private String author;

    public PackDevExtension(LoaderSettingsConsumer loaderSettingsAcceptor) {
        this.targets = new TargetBuilder();
        this.loaderSettingsAcceptor = loaderSettingsAcceptor;
        this.loaderVersion = null;
        this.author = null;
    }

    public void loader(String loaderVersion) {
        this.setLoader(loaderVersion, new LoaderBuilder());
    }
    
    public void loader(String loaderVersion, @DelegatesTo(value = LoaderBuilder.class, strategy = Closure.DELEGATE_FIRST) Closure<?> closure) {
        LoaderBuilder builder = new LoaderBuilder();
        closure.setDelegate(builder);
        closure.setResolveStrategy(Closure.DELEGATE_FIRST);
        if (closure.getMaximumNumberOfParameters() == 0) {
            closure.call();
        } else {
            closure.call(builder);
        }
        this.setLoader(loaderVersion, builder);
    }
    
    private void setLoader(String loaderVersion, LoaderBuilder builder) {
        if (this.loaderVersion == null) {
            this.loaderVersion = loaderVersion;
            this.loaderSettingsAcceptor.accept(minecraftVersion -> new LoaderSettings(minecraftVersion, loaderVersion, builder.officialMappings));
        } else {
            throw new IllegalStateException("Loader version has already been set.");
        }
    }
    
    public void author(String author) {
        this.author = author;
    }

    public void targets(@DelegatesTo(value = TargetBuilder.class, strategy = Closure.DELEGATE_FIRST) Closure<?> closure) {
        closure.setDelegate(this.targets);
        closure.setResolveStrategy(Closure.DELEGATE_FIRST);
        if (closure.getMaximumNumberOfParameters() == 0) {
            closure.call();
        } else {
            closure.call(this.targets);
        }
    }

    public void targets(Action<TargetBuilder> action) {
        action.execute(this.targets);
    }
    
    @Internal
    public String getLoaderVersion() {
        return Objects.requireNonNull(this.loaderVersion, "Loader version not set.");
    }

    @Nullable
    @Internal
    public String getAuthor() {
        return this.author;
    }

    @Internal
    public Map<String, Optional<Object>> getAllTargets() {
        return Map.copyOf(this.targets.targets);
    }
    
    public static class TargetBuilder {
        
        private final Map<String, Optional<Object>> targets;

        private TargetBuilder() {
            this.targets = new HashMap<>();
        }

        public void curse(int projectId) {
            this.target("curse", new CurseProperties(projectId));
        }
        
        public void modrinth() {
            this.target("modrinth");
        }
        
        public void server() {
            this.target("server");
        }
        
        public void multimc() {
            this.target("multimc");
        }

        public void target(String id) {
            this.target(id, null);
        }
        
        public void target(String id, Object properties) {
            if (this.targets.containsKey(id)) {
                throw new IllegalArgumentException("Each target can only be built once: " + id);
            } else {
                this.targets.put(id, Optional.ofNullable(properties));
            }
        }
    }
    
    public static class LoaderBuilder {
        
        private boolean officialMappings = false;
        
        public void useOfficialMappings() {
            System.err.println("""
                               Using official mojang names in PackDev.
                               This can cause problems importing the gradle project or running the game from the IDE.
                               Try disabling them before reporting any issues.
                               """);
            this.officialMappings = true;
        }
    }
}
