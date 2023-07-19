package org.moddingx.packdev.platform.modrinth.api;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public record ProjectInfo(
        String slug,
        String projectType,
        String title
) {
    
    public URI projectPage() {
        return URI.create("https://modrinth.com/" + URLEncoder.encode(this.projectType(), StandardCharsets.UTF_8) + "/" + URLEncoder.encode(this.slug(), StandardCharsets.UTF_8));
    }
}
