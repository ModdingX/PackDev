package org.moddingx.packdev.platform.modrinth.api;

import java.net.URI;
import java.util.Map;

public record VersionInfo(
        String fileName,
        String versionNumber,
        long fileSize,
        URI url,
        Map<String, String> hashes
) {}
