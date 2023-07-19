package org.moddingx.packdev.platform.modrinth;

import org.gradle.api.Project;
import org.moddingx.launcherlib.util.Artifact;
import org.moddingx.launcherlib.util.Side;
import org.moddingx.packdev.cache.PackDevCache;
import org.moddingx.packdev.platform.BaseModFile;
import org.moddingx.packdev.platform.modrinth.api.ModrinthAPI;
import org.moddingx.packdev.platform.modrinth.api.ProjectInfo;
import org.moddingx.packdev.platform.modrinth.api.VersionInfo;
import org.moddingx.packdev.util.hash.ComputedHash;

import javax.annotation.Nullable;
import java.io.IOException;
import java.net.URI;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.stream.Collectors;

public class ModrinthFile extends BaseModFile {

    public final String projectId;
    public final String versionId;
    public final Side side;

    @Nullable private ProjectInfo projectInfo;
    @Nullable private VersionInfo versionInfo;
    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    @Nullable private Optional<Owner> owner;
    
    public ModrinthFile(Project project, PackDevCache cache, String projectId, String versionId, Side side) {
        super(project, cache);
        this.projectId = projectId;
        this.versionId = versionId;
        this.side = side;
    }

    @Override
    protected String fileKey() {
        return this.projectId + "-" + this.versionId;
    }

    @Override
    public String projectSlug() {
        return this.projectInfo().slug();
    }

    @Override
    public String projectName() {
        return this.projectInfo().title();
    }

    @Override
    public String fileName() {
        return this.versionInfo().fileName();
    }

    @Override
    public Side fileSide() {
        return this.side;
    }

    @Override
    public URI downloadURL() {
        return this.versionInfo().url();
    }

    @Override
    public URI projectURL() {
        return this.projectInfo().projectPage();
    }

    @Override
    public Optional<Owner> projectOwner() {
        //noinspection OptionalAssignedToNull
        if (this.owner == null) {
            this.owner = ModrinthAPI.owner(this.projectId);
        }
        return this.owner;
    }

    @Override
    public Artifact createDependency() {
        return Artifact.from("maven.modrinth", this.projectSlug(), this.versionInfo().versionNumber());
    }

    @Override
    protected Map<String, ComputedHash> computeHashes(Set<String> hashes) throws NoSuchAlgorithmException, IOException {
        Map<String, ComputedHash> computed = new HashMap<>();
        if (hashes.contains("size")) {
            computed.put("size", ComputedHash.ofSignedLong(this.versionInfo().fileSize()));
        } else if (hashes.contains("sha1") && this.versionInfo().hashes().containsKey("sha1")) {
            computed.put("sha1", ComputedHash.of(this.versionInfo().hashes().get("sha1"), 160));
        } else if (hashes.contains("sha512") && this.versionInfo().hashes().containsKey("sha512")) {
            computed.put("sha512", ComputedHash.of(this.versionInfo().hashes().get("sha512"), 512));
        }
        computed.putAll(super.computeHashes(hashes.stream().filter(hash -> !computed.containsKey(hash)).collect(Collectors.toUnmodifiableSet())));
        return Collections.unmodifiableMap(computed);
    }

    @Override
    public String toString() {
        return "ModrinthFile[" + this.projectSlug() + "," + this.fileName() + "]";
    }

    public ProjectInfo projectInfo() {
        if (this.projectInfo == null) {
            this.projectInfo = ModrinthAPI.project(this.projectId);
        }
        return this.projectInfo;
    }

    public VersionInfo versionInfo() {
        if (this.versionInfo == null) {
            this.versionInfo = ModrinthAPI.version(this.versionId);
        }
        return this.versionInfo;
    }
}
