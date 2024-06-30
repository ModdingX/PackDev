package org.moddingx.packdev.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.Strictness;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.UnknownTaskException;
import org.gradle.api.plugins.JavaPluginExtension;

import java.util.Locale;

public class Util {
    
    public static final Gson GSON;
    
    static {
        GsonBuilder builder = new GsonBuilder();
        builder.disableHtmlEscaping();
        builder.setStrictness(Strictness.LENIENT);
        builder.setPrettyPrinting();
        GSON = builder.create();
    }
    
    public static String capitalize(String str) {
        if (str.isEmpty()) {
            return "";
        } else {
            return str.substring(0, 1).toUpperCase(Locale.ROOT) + str.substring(1).toLowerCase(Locale.ROOT);
        }
    }
    
    public static <T extends Task> T findTask(Project project, String name, Class<T> cls) {
        try {
            Task task = project.getTasks().getByName(name);
            if (cls.isAssignableFrom(task.getClass())) {
                //noinspection unchecked
                return (T) task;
            } else {
                return null;
            }
        } catch (UnknownTaskException | ClassCastException e) {
            return null;
        }
    }

    public static JavaPluginExtension getJavaExtension(Project project) {
        JavaPluginExtension ext = project.getExtensions().findByType(JavaPluginExtension.class);
        if (ext == null) throw new IllegalStateException("Java plugin extension not found. Is the java plugin applied?");
        return ext;
    }
    
    public static <T> T getExtension(Project project, String name, Class<T> cls) {
        Object ext = project.getExtensions().findByName(name);
        if (ext == null) {
            throw new IllegalStateException(name + " extension not found.");
        } else if (cls.isAssignableFrom(ext.getClass())) {
            //noinspection unchecked
            return (T) ext;
        } else {
            throw new ClassCastException("Extension has wrong type, expected " + cls + ", got " + ext.getClass().getName());
        }
    }
}
