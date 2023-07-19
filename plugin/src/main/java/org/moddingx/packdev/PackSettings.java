package org.moddingx.packdev;

import org.moddingx.launcherlib.launcher.Launcher;

import java.util.Optional;

public record PackSettings(
        String name,
        String version,
        String minecraft,
        String loader,
        String loaderVersion,
        int java,
        Optional<String> author,
        PackPaths paths,
        Launcher launcher
) {}
