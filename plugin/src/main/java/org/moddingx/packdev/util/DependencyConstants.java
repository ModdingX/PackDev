package org.moddingx.packdev.util;

import org.gradle.api.Project;

public class DependencyConstants {
    
    public static final String MOONSTONE = "org.moddingx:Moonstone:2.1.+";
    public static final String MOONSTONE_MAIN = "org.moddingx.moonstone.desktop.Main";
    public static final int MOONSTONE_JAVA = 21;
    
    public static void addRepositories(Project project) {
        // Required to resolve Moonstone
        project.getRepositories().maven(repo -> repo.setUrl("https://maven.moddingx.org/"));
        project.getRepositories().mavenCentral();
    }
}
