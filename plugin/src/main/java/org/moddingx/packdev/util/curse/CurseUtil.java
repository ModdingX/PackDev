package org.moddingx.packdev.util.curse;

import org.moddingx.cursewrapper.api.CurseWrapper;

import java.net.URI;

public class CurseUtil {

    public static final CurseWrapper API = new CurseWrapper(URI.create("https://curse.moddingx.org/"));
    public static final URI CURSE_MAVEN = URI.create("https://www.cursemaven.com");

    public static URI curseMaven(String endpoint) {
        return CURSE_MAVEN.resolve(endpoint.startsWith("/") ? endpoint : "/" + endpoint);
    }
}
