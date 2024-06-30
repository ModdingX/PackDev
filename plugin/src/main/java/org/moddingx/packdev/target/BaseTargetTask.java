package org.moddingx.packdev.target;

import jakarta.annotation.Nonnull;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.file.copy.CopyAction;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.bundling.AbstractArchiveTask;
import org.gradle.work.InputChanges;
import org.moddingx.launcherlib.launcher.Launcher;
import org.moddingx.launcherlib.util.Side;
import org.moddingx.packdev.PackPaths;
import org.moddingx.packdev.PackSettings;
import org.moddingx.packdev.platform.ModFile;
import org.moddingx.packdev.platform.ModdingPlatform;

import javax.inject.Inject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public abstract class BaseTargetTask<T extends ModFile> extends AbstractArchiveTask {

    protected final ModdingPlatform<T> platform;
    protected final PackSettings settings;
    protected final PackPaths paths;
    protected final Launcher launcher;
    protected final List<T> files;

    private final Property<FileCollection> inputData = this.getProject().getObjects().property(FileCollection.class);

    @Inject
    public BaseTargetTask(ModdingPlatform<T> platform, PackSettings settings, List<T> files) {
        this.platform = platform;
        this.settings = settings;
        this.paths = settings.paths();
        this.launcher = settings.launcher();
        this.files = files;

        this.getArchiveExtension().convention(this.getProject().provider(() -> "zip"));

        this.inputData.convention(this.getProject().provider(() -> this.getProject().files(
                this.getProject().file("modlist.json"),
                this.getProject().file("data/" + Side.COMMON.id),
                this.getProject().file("data/" + Side.CLIENT.id),
                this.getProject().file("data/" + Side.SERVER.id)
        )));
        // We need dummy sources, or it will always skip with NO-SOURCE
        this.from(this.inputData);
    }

    @InputFiles
    public FileCollection getInputData() {
        return this.inputData.get();
    }

    public void setInputData(FileCollection inputMods) {
        this.inputData.set(inputMods);
    }

    @Nonnull
    @Override
    protected CopyAction createCopyAction() {
        // Do nothing
        return copy -> () -> true;
    }

    @TaskAction
    public void generateOutput(InputChanges inputs) throws IOException {
        Path target = this.getArchiveFile().get().getAsFile().toPath().toAbsolutePath().normalize();
        if (!Files.exists(target.getParent())) Files.createDirectories(target.getParent());
        if (Files.exists(target)) Files.delete(target);
        this.generate(target);
    }

    protected abstract void generate(Path target) throws IOException;
}
