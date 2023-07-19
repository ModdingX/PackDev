package org.moddingx.packdev.forge;

import net.minecraftforge.gradle.mcp.ChannelProvider;
import net.minecraftforge.gradle.mcp.MCPRepo;
import org.gradle.api.Project;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class SrgChannelProvider implements ChannelProvider {
    
    public static final String CHANNEL = "packdevsrg";
    
    @Nonnull
    @Override
    public Set<String> getChannels() {
        return Set.of(CHANNEL);
    }

    @Nullable
    @Override
    public File getMappingsFile(@Nonnull MCPRepo mcpRepo, @Nonnull Project project, @Nonnull String channel, @Nonnull String version) throws IOException {
        Path path = project.file("build").toPath().resolve("packdev_mappings").resolve("srg.zip");
        if (!Files.exists(path) || Files.size(path) <= 0) {
            if (path.toAbsolutePath().normalize().getParent() != null && !Files.exists(path.toAbsolutePath().normalize().getParent())) {
                Files.createDirectories(path.toAbsolutePath().normalize().getParent());
            }
            
            try (
                    OutputStream out = Files.newOutputStream(path, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                    ZipOutputStream zipOut = new ZipOutputStream(out)
            ) {
                zipOut.putNextEntry(new ZipEntry("fields.csv"));
                zipOut.write("searge,name,side,desc\n".getBytes(StandardCharsets.UTF_8));
                zipOut.closeEntry();
                
                zipOut.putNextEntry(new ZipEntry("methods.csv"));
                zipOut.write("searge,name,side,desc\n".getBytes(StandardCharsets.UTF_8));
                zipOut.closeEntry();
                
                zipOut.putNextEntry(new ZipEntry("params.csv"));
                zipOut.write("param,name,side\n".getBytes(StandardCharsets.UTF_8));
                zipOut.closeEntry();
            }
        }
        return path.toFile();
    }
}
