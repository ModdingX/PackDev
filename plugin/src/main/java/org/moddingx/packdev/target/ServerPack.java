package org.moddingx.packdev.target;

import groovy.json.StringEscapeUtils;
import org.moddingx.launcherlib.util.Side;
import org.moddingx.packdev.PackDevPlugin;
import org.moddingx.packdev.PackSettings;
import org.moddingx.packdev.platform.ModFile;
import org.moddingx.packdev.platform.ModdingPlatform;
import org.moddingx.packdev.util.LoaderConstants;

import javax.inject.Inject;
import java.io.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermission;
import java.util.*;
import java.util.regex.Pattern;

public class ServerPack<T extends ModFile> extends BaseTargetTask<T> {

    private static final Pattern DOCKERFILE_TEMPLATE_PATTERN = Pattern.compile("\\$\\{(\\w+)(#?)\\}");
    private static final Map<String, String> INSTALLER_VERSIONS = Map.of(
            LoaderConstants.FORGE, "", // Forge has no separate installer
            LoaderConstants.FABRIC, "1.0.0",
            LoaderConstants.QUILT, "0.9.1",
            LoaderConstants.NEOFORGE, "" // NeoForge has no separate installer
    );
    
    @Inject
    public ServerPack(ModdingPlatform<T> platform, PackSettings settings, List<T> files) {
        super(platform, settings, files);
    }

    @Override
    protected void generate(Path target) throws IOException {
        try (FileSystem fs = FileSystems.newFileSystem(URI.create("jar:" + target.toUri()), Map.of(
                "create", String.valueOf(!Files.exists(target))
        ))) {
            this.paths.copyAllDataTo(fs.getPath("/"), Side.SERVER);

            try (InputStream installScript = PackDevPlugin.class.getResourceAsStream("/" + PackDevPlugin.class.getPackage().getName().replace('.', '/') + "/install_server.py")) {
                if (installScript == null) {
                    throw new IllegalStateException("Can't build server pack: Install script not found for loader: " + this.settings.loader());
                }
                Files.copy(installScript, fs.getPath("install.py"));
            }

            try {
                Set<PosixFilePermission> perms = new HashSet<>(Files.getPosixFilePermissions(fs.getPath("install.py")));
                perms.add(PosixFilePermission.OWNER_EXECUTE);
                perms.add(PosixFilePermission.GROUP_EXECUTE);
                perms.add(PosixFilePermission.OTHERS_EXECUTE);
                Files.setPosixFilePermissions(fs.getPath("install.py"), perms);
            } catch (Exception e) {
                //
            }

            try (InputStream dockerFile = PackDevPlugin.class.getResourceAsStream("/" + PackDevPlugin.class.getPackage().getName().replace('.', '/') + "/Dockerfile")) {
                if (dockerFile == null) {
                    throw new IllegalStateException("Can't build server pack: Dockerfile not found.");
                }
                Map<String, String> replaces = Map.of(
                        "jdk", Integer.toString(this.settings.java()),
                        "name", this.settings.name(),
                        "version", this.settings.version(),
                        "minecraft", this.settings.minecraft()
                );
                try (
                        BufferedReader reader = new BufferedReader(new InputStreamReader(dockerFile, StandardCharsets.UTF_8));
                        BufferedWriter writer = Files.newBufferedWriter(fs.getPath("Dockerfile"), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)
                ) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        String replacedLine = DOCKERFILE_TEMPLATE_PATTERN.matcher(line).replaceAll(r -> {
                            String key = r.group(1);
                            boolean quote = Objects.equals(r.group(2), "#");
                            String replacement = replaces.getOrDefault(key, null);
                            if (replacement == null) throw new IllegalStateException("Invalid replacement in Dockerfile template: " + key);
                            return quote ? StringEscapeUtils.escapeJava(replacement) : replacement;
                        });
                        writer.write(replacedLine + "\n");
                    }
                }
            }

            this.generateServerInfo(fs.getPath("server.txt"));
        }
    }

    private void generateServerInfo(Path target) throws IOException {
        String installerVersion = INSTALLER_VERSIONS.getOrDefault(this.settings.loader(), null);
        if (installerVersion == null) throw new IllegalStateException("The server pack target does not support loader " + this.settings.loader());
        Writer writer = Files.newBufferedWriter(target, StandardOpenOption.CREATE_NEW);
        writer.write(this.settings.loader() + "/" + installerVersion + "\n");
        writer.write(this.settings.minecraft() + "/" + this.settings.loaderVersion() + "\n");
        for (ModFile file : this.files.stream().sorted(this.platform.internalOrder()).toList()) {
            if (file.fileSide().server) {
                writer.write(file.fileName().replace("/", "") + "/" + file.downloadURL().normalize() + "\n");
            }
        }
        writer.close();
    }
}
