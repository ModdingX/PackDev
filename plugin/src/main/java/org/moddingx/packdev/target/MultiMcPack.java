package org.moddingx.packdev.target;

import com.google.gson.JsonObject;
import groovy.json.StringEscapeUtils;
import org.moddingx.launcherlib.util.Side;
import org.moddingx.packdev.PackSettings;
import org.moddingx.packdev.platform.ModFile;
import org.moddingx.packdev.platform.ModdingPlatform;
import org.moddingx.packdev.util.Util;
import org.moddingx.packdev.util.multimc.MultiMcAPI;

import javax.inject.Inject;
import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.net.URI;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;

public class MultiMcPack<T extends ModFile> extends BaseTargetTask<T> {

    @Inject
    public MultiMcPack(ModdingPlatform<T> platform, PackSettings settings, List<T> files) {
        super(platform, settings, files);
    }

    @Override
    protected void generate(Path target) throws IOException {
        try (FileSystem fs = FileSystems.newFileSystem(URI.create("jar:" + target.toUri()), Map.of(
                "create", String.valueOf(!Files.exists(target))
        ))) {
            JsonObject meta = MultiMcAPI.buildLoaderPack(this.settings.loader(), this.settings.minecraft(), this.settings.loaderVersion());
            try (Writer writer = Files.newBufferedWriter(fs.getPath("/mmc-pack.json"), StandardOpenOption.CREATE_NEW)) {
                writer.write(Util.GSON.toJson(meta) + "\n");
            }
            
            this.generateInstanceConfig(fs.getPath("/instance.cfg"));
            this.paths.copyAllDataTo(fs.getPath("/minecraft"), Side.CLIENT);
            
            Files.createDirectories(fs.getPath("/minecraft/mods"));
            this.downloadMods(fs.getPath("/minecraft/mods"));
        }
    }
    
    private void generateInstanceConfig(Path target) throws IOException {
        String meta = this.settings.name() + " - " + this.settings.version() + this.settings.author().map(name -> " (by " + name + ")").orElse("") + "\n\n";
        
        List<ModListEntry> entries = new ArrayList<>();
        for (ModFile file : this.files.stream().sorted(Comparator.comparing(ModFile::projectSlug)).toList()) {
            entries.add(new ModListEntry(file.projectName(), file.projectOwner().map(ModFile.Owner::name), file.fileName(), file.projectURL()));
        }
        int namePadding = entries.stream().mapToInt(ModListEntry::namePadding).max().orElse(3);
        int authorPadding = entries.stream().mapToInt(ModListEntry::authorPadding).max().orElse(0);
        int fileNamePadding = entries.stream().mapToInt(ModListEntry::fileNamePadding).max().orElse(3);
        String modlist = entries.stream().map(e -> e.string(namePadding, authorPadding, fileNamePadding)).collect(Collectors.joining("\n"));
        
        try (Writer writer = Files.newBufferedWriter(target, StandardOpenOption.CREATE_NEW)) {
            writer.write("InstanceType=OneSix\n");
            writer.write("name=" + StringEscapeUtils.escapeJava(this.settings.name() + " - " + this.settings.version()) + "\n");
            writer.write("iconKey=grass\n");
            writer.write("notes=" + StringEscapeUtils.escapeJava(meta + modlist) + "\n");
        }
    }
    
    private void downloadMods(Path base) throws IOException {
        for (ModFile file : this.files) {
            if (file.fileSide().client) {
                Path dest = base.resolve(file.fileName());
                try (InputStream in = file.openStream()) {
                    Files.copy(in, dest);
                }
            }
        }
    }
    
    private record ModListEntry(String name, Optional<String> author, String fileName, URI url) {
        
        public int namePadding() {
            return this.name().length() + 3;
        }
        
        public int authorPadding() {
            return this.author().map(name -> name.length() + 9).orElse(0);
        }
        
        public int fileNamePadding() {
            return this.fileName().length() + 3;
        }
        
        public String string(int namePadding, int authorPadding, int fileNamePadding) {
            String authorStr = this.author().map(name -> " (by " + name + ")").orElse("");
            return this.name() + " ".repeat(Math.max(namePadding - this.name().length(), 0)) + authorStr + " ".repeat(Math.max(authorPadding - authorStr.length(), 0)) + this.fileName() + " ".repeat(Math.max(fileNamePadding - this.fileName().length(), 0)) + this.url.normalize();
        }
    }
}
