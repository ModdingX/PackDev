package org.moddingx.packdev.platform.curse;

import jakarta.annotation.Nullable;
import org.gradle.api.Project;
import org.moddingx.cursewrapper.api.response.FileInfo;
import org.moddingx.cursewrapper.api.response.ProjectInfo;
import org.moddingx.launcherlib.util.Artifact;
import org.moddingx.launcherlib.util.Side;
import org.moddingx.packdev.cache.PackDevCache;
import org.moddingx.packdev.platform.BaseModFile;
import org.moddingx.packdev.util.curse.CurseUtil;
import org.moddingx.packdev.util.hash.ComputedHash;

import java.io.IOException;
import java.net.URI;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.stream.Collectors;

public class CurseFile extends BaseModFile {

    public final int projectId;
    public final int fileId;
    public final Side side;
    
    @Nullable private String slug;
    @Nullable private ProjectInfo projectInfo;
    @Nullable private FileInfo fileInfo;
    
    public CurseFile(Project project, PackDevCache cache, int projectId, int fileId, Side side) {
        super(project, cache);
        this.projectId = projectId;
        this.fileId = fileId;
        this.side = side;
    }

    @Override
    protected String fileKey() {
        return this.projectId + "-" + this.fileId;
    }

    @Override
    public String projectSlug() {
        if (this.slug == null) {
            try {
                this.slug = CurseUtil.API.getSlug(this.projectId);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        return this.slug;
    }

    @Override
    public String projectName() {
        return this.projectInfo().name();
    }

    @Override
    public String fileName() {
        return this.fileInfo().name();
    }

    @Override
    public Side fileSide() {
        return this.side;
    }

    @Override
    public URI downloadURL() {
        // We use CurseMaven to download as it works more reliable than the cdn
        return CurseUtil.curseMaven("/curse/maven/O-" + this.projectId + "/" + this.fileId + "/O-" + this.projectId + "-" + this.fileId + ".jar");
    }

    @Override
    public URI projectURL() {
        return this.projectInfo().website();
    }

    @Override
    public Optional<Owner> projectOwner() {
        return Optional.of(new Owner(this.projectInfo().owner(), URI.create("https://www.curseforge.com/members/" + this.projectInfo().owner() + "/projects")));
    }

    @Override
    public Artifact createDependency() {
        return Artifact.from("curse.maven", this.projectSlug() + "-" + this.projectId, Integer.toString(this.fileId));
    }

    @Override
    protected Map<String, ComputedHash> computeHashes(Set<String> hashes) throws NoSuchAlgorithmException, IOException {
        Map<String, ComputedHash> computed = new HashMap<>();
        if (hashes.contains("size")) {
            computed.put("size", ComputedHash.ofSignedLong(this.fileInfo().fileSize()));
        } else if (hashes.contains("fingerprint")) {
            computed.put("fingerprint", ComputedHash.of(this.fileInfo().fingerprint(), 32));
        } else if (hashes.contains("sha1") && this.fileInfo().hashes().containsKey("sha1")) {
            computed.put("sha1", ComputedHash.of(this.fileInfo().hashes().get("sha1"), 160));
        } else if (hashes.contains("md5") && this.fileInfo().hashes().containsKey("md5")) {
            computed.put("md5", ComputedHash.of(this.fileInfo().hashes().get("md5"), 128));
        }
        computed.putAll(super.computeHashes(hashes.stream().filter(hash -> !computed.containsKey(hash)).collect(Collectors.toUnmodifiableSet())));
        return Collections.unmodifiableMap(computed);
    }

    @Override
    public String toString() {
        return "CurseFile[" + this.projectSlug() + "," + this.fileName() + "]";
    }

    public ProjectInfo projectInfo() {
        if (this.projectInfo == null) {
            try {
                this.projectInfo = CurseUtil.API.getProject(this.projectId);
                this.slug = this.projectInfo.slug();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        return this.projectInfo;
    }
    
    public FileInfo fileInfo() {
        if (this.fileInfo == null) {
            try {
                this.fileInfo = CurseUtil.API.getFile(this.projectId, this.fileId);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        return this.fileInfo;
    }
}
