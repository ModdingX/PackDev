package org.moddingx.packdev.loader;

@FunctionalInterface
public interface LoaderSettingsConsumer {
    
    void accept(LoaderSettingsFactory factory);
    
    @FunctionalInterface
    interface LoaderSettingsFactory {
        
        LoaderSettings create(String minecraftVersion);
    }
}
