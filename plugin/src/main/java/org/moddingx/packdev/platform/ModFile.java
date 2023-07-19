package org.moddingx.packdev.platform;

import org.moddingx.launcherlib.util.Artifact;
import org.moddingx.launcherlib.util.Side;
import org.moddingx.packdev.util.hash.ComputedHash;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public interface ModFile {

    String projectSlug();
    String projectName();
    String fileName();
    Side fileSide();
    URI downloadURL();
    URI projectURL();
    Optional<Owner> projectOwner();
    Artifact createDependency();
    
    Map<String, ComputedHash> hashes(Set<String> hashes) throws NoSuchAlgorithmException, IOException;
    
    default InputStream openStream() throws IOException {
        return this.downloadURL().toURL().openStream();
    }
    
    default ComputedHash hash(String hash) throws NoSuchAlgorithmException, IOException {
        Map<String, ComputedHash> map = this.hashes(Set.of(hash));
        if (!map.containsKey(hash)) {
            throw new IOException("Can't compute " + hash + " hash for file: " + this + ": File returned an empty map.");
        } else {
            return Objects.requireNonNull(map.get(hash), "ModFile returned a null hash.");
        }
    }
    
    record Owner(String name, URI website) {}
}
