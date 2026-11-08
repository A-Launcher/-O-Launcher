package com.alauncher.olauncher

import java.io.File

/** Validates Mojang asset-index hashes and keeps object/mapped paths inside their roots. */
internal object OEngineAssetObjectPath {
    private val sha1Pattern = Regex("[0-9a-f]{40}")

    fun resolveRoot(gameRoot: File, relativePath: String): File {
        require(relativePath.isNotBlank() && !File(relativePath).isAbsolute) {
            "Asset directory must be relative to the selected game folder."
        }
        val root = gameRoot.canonicalFile
        val requested = File(root, relativePath).toPath().normalize()
        require(requested.startsWith(root.toPath()) && requested != root.toPath()) {
            "Asset directory escapes the selected game folder."
        }
        var current = root
        val relative = root.toPath().relativize(requested)
        for (part in relative) {
            current = File(current, part.toString())
            require(!java.nio.file.Files.isSymbolicLink(current.toPath())) {
                "Asset directory contains an unsafe symbolic link."
            }
        }
        val canonical = current.canonicalFile
        require(canonical.toPath().startsWith(root.toPath()) && canonical != root) {
            "Asset directory resolves outside the selected game folder."
        }
        return canonical
    }

    fun resolve(objectRoot: File, hash: String): File {
        require(sha1Pattern.matches(hash)) { "Asset index contains an invalid SHA-1 hash." }
        val root = objectRoot.canonicalFile
        val target = File(root, hash.substring(0, 2) + "/" + hash).canonicalFile
        require(target.toPath().startsWith(root.toPath()) && target.parentFile?.parentFile == root) {
            "Asset object path escapes the asset object directory."
        }
        return target
    }

    fun resolveMapped(destinationRoot: File, assetName: String): File {
        require(assetName.isNotBlank() && !assetName.startsWith("/") && !assetName.startsWith("\\\\")) {
            "Asset index contains an invalid mapped asset name."
        }
        val root = destinationRoot.canonicalFile
        val target = File(root, assetName).canonicalFile
        require(target.toPath().startsWith(root.toPath()) && target != root) {
            "Mapped asset path escapes its destination directory."
        }
        return target
    }
}
