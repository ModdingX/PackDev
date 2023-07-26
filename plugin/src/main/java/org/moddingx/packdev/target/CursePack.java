package org.moddingx.packdev.target;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.moddingx.cursewrapper.api.response.FileInfo;
import org.moddingx.launcherlib.util.Side;
import org.moddingx.packdev.PackSettings;
import org.moddingx.packdev.api.CurseProperties;
import org.moddingx.packdev.platform.ModFile;
import org.moddingx.packdev.platform.ModdingPlatform;
import org.moddingx.packdev.platform.curse.CurseFile;
import org.moddingx.packdev.util.LoaderConstants;
import org.moddingx.packdev.util.Util;
import org.moddingx.packdev.util.curse.CurseUtil;
import org.moddingx.packdev.util.hash.ComputedHash;

import javax.inject.Inject;
import java.io.IOException;
import java.io.Writer;
import java.net.URI;
import java.nio.file.*;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public class CursePack<T extends ModFile> extends BaseTargetTask<T> {

    private final CurseProperties properties;

    @Inject
    public CursePack(ModdingPlatform<T> platform, PackSettings settings, List<T> files, CurseProperties properties) {
        super(platform, settings, files);
        this.properties = properties;
    }

    @Override
    protected void generate(Path target) throws IOException {
        List<ResolvedFile> clientFiles = this.resolveClientFiles();
        try (FileSystem fs = FileSystems.newFileSystem(URI.create("jar:" + target.toUri()), Map.of(
                "create", String.valueOf(!Files.exists(target))
        ))) {
            this.paths.copyAllDataTo(fs.getPath("/overrides"), Side.CLIENT);
            this.generateManifest(fs.getPath("/manifest.json"), clientFiles);
            this.generateModList(fs.getPath("/modlist.html"));
        }
    }

    private void generateManifest(Path target, List<ResolvedFile> clientFiles) throws IOException {
        JsonObject json = new JsonObject();

        JsonObject minecraftBlock = new JsonObject();
        minecraftBlock.addProperty("version", this.settings.minecraft());

        JsonArray modLoaders = new JsonArray();
        JsonObject modLoader = new JsonObject();
        switch (this.settings.loader()) {
            case LoaderConstants.FORGE -> modLoader.addProperty("id", "forge-" + this.settings.loaderVersion());
            case LoaderConstants.FABRIC -> modLoader.addProperty("id", "fabric-" + this.settings.loaderVersion());
            case LoaderConstants.QUILT -> modLoader.addProperty("id", "quilt-" + this.settings.loaderVersion());
            default -> throw new IllegalStateException("Loader not supported by CurseForge: " + this.settings.loader());
        }
        modLoader.addProperty("primary", true);
        modLoaders.add(modLoader);

        minecraftBlock.add("modLoaders", modLoaders);
        json.add("minecraft", minecraftBlock);

        json.addProperty("manifestType", "minecraftModpack");
        json.addProperty("manifestVersion", 1);
        json.addProperty("overrides", "overrides");
        json.addProperty("name", this.settings.name());
        json.addProperty("version", this.settings.version());
        this.settings.author().ifPresent(author -> json.addProperty("author", author));
        json.addProperty("projectID", this.properties.projectId());

        JsonArray fileArray = new JsonArray();
        for (ResolvedFile file : clientFiles.stream().sorted(Comparator.comparing(ResolvedFile::projectId)).toList()) {
            if (file.file().fileSide().client) {
                JsonObject fileObj = new JsonObject();
                fileObj.addProperty("projectID", file.projectId());
                fileObj.addProperty("fileID", file.fileId());
                fileArray.add(fileObj);
            }
        }
        json.add("files", fileArray);

        Writer writer = Files.newBufferedWriter(target, StandardOpenOption.CREATE_NEW);
        writer.write(Util.GSON.toJson(json) + "\n");
        writer.close();
    }

    private void generateModList(Path target) throws IOException {
        Map<String, String> linesBySlug = new HashMap<>();
        for (ModFile file : this.files) {
            String authorPart = "";
            if (file.projectOwner().isPresent()) {
                ModFile.Owner owner = file.projectOwner().get();
                authorPart = " (by <a href=\"" + owner.website().normalize() + "\">" + owner.name() + "</a>)";
            }
            linesBySlug.put(file.projectSlug(), "<li><a href=\"" + file.projectURL().normalize() + "\">" + file.projectName() + "</a>" + authorPart + "</li>");
        }
        Writer writer = Files.newBufferedWriter(target, StandardOpenOption.CREATE_NEW);
        writer.write("<h2>" + this.settings.name() + " - " + this.settings.version() + "</h2>\n");
        writer.write("\n");
        writer.write("<ul>\n");
        for (String line : linesBySlug.entrySet().stream().sorted(Map.Entry.comparingByKey()).map(Map.Entry::getValue).toList()) {
            writer.write(line + "\n");
        }
        writer.write("</ul>\n");
        writer.close();
    }
    
    private List<ResolvedFile> resolveClientFiles() throws IOException {
        try {
            // CurseForge only needs to resolve client files.
            // Server only mods that are not on curseforge can be ignored
            // They show up in the modlist but don't need a project / file id.
            List<T> clientFiles = this.files.stream().filter(f -> f.fileSide().client).toList();
            
            List<ResolvedFile> files = new ArrayList<>();
            List<ResolvableFile> missing = new ArrayList<>();
            List<ModFile> failed = new ArrayList<>();
            for (ModFile file : clientFiles) {
                if (file instanceof CurseFile cf) {
                    files.add(new ResolvedFile(cf.projectId, cf.fileId, cf));
                } else {
                    missing.add(new ResolvableFile(file.hash("fingerprint"), file));
                }
            }
            if (!missing.isEmpty()) {
                Map<Long, FileInfo> resolved = CurseUtil.API.matchFingerprints(missing.stream()
                        .map(ResolvableFile::fingerprint)
                        .map(ComputedHash::longValue)
                        .collect(Collectors.toUnmodifiableSet())
                ).stream().collect(Collectors.toUnmodifiableMap(FileInfo::fingerprint, Function.identity()));
                for (ResolvableFile file : missing) {
                    FileInfo info = resolved.get(file.fingerprint().longValue());
                    if (info != null) {
                        files.add(new ResolvedFile(info.projectId(), info.fileId(), file.file()));
                    } else {
                        failed.add(file.file());
                    }
                }
            }
            if (files.size() != clientFiles.size() || !failed.isEmpty()) {
                throw new IOException("Can't build curse pack: Not all files found on curse platform: Missing files: " + failed.stream().map(ModFile::toString).collect(Collectors.joining(", ")));
            }
            return Collections.unmodifiableList(files);
        } catch (NoSuchAlgorithmException e) {
            throw new IOException("Can't resolve files for curse platform: Fingerprint not supported", e);
        }
    }
    
    private record ResolvableFile(ComputedHash fingerprint, ModFile file) {}
    private record ResolvedFile(int projectId, int fileId, ModFile file) {}
}
