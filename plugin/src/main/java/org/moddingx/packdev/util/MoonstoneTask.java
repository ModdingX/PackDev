package org.moddingx.packdev.util;

import org.gradle.api.tasks.JavaExec;
import org.gradle.jvm.toolchain.JavaLanguageVersion;

import java.util.ArrayList;
import java.util.List;

public class MoonstoneTask extends JavaExec {
    
    public MoonstoneTask() {
        this.getMainClass().set(DependencyConstants.MOONSTONE_MAIN);
        
        List<String> args = new ArrayList<>();
        if (this.getProject().getProperties().containsKey("moonstone.theme") && "light".equals(this.getProject().getProperties().get("moonstone.theme"))) {
            args.add("--light");
        }
        args.add("--");
        args.add(this.getProject().file("modlist.json").toPath().toAbsolutePath().normalize().toString());
        this.args(args.toArray());
        
        this.getJavaLauncher().set(this.getJavaToolchainService().launcherFor(spec -> spec.getLanguageVersion().set(JavaLanguageVersion.of(DependencyConstants.MOONSTONE_JAVA))));
    }
}
