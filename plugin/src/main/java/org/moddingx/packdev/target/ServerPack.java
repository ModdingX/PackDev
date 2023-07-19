package org.moddingx.packdev.target;

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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ServerPack<T extends ModFile> extends BaseTargetTask<T> {

    private static final Map<String, String> INSTALLER_VERSIONS = Map.of(
            LoaderConstants.FORGE, "", // Forge has no separate installer
            LoaderConstants.FABRIC, "0.11.2",
            LoaderConstants.QUILT, "0.8.0"
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
                try (
                        BufferedReader reader = new BufferedReader(new InputStreamReader(dockerFile, StandardCharsets.UTF_8));
                        BufferedWriter writer = Files.newBufferedWriter(fs.getPath("Dockerfile"), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)
                ) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        writer.write(line.replace("${jdk}", Integer.toString(this.settings.java())) + "\n");
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
