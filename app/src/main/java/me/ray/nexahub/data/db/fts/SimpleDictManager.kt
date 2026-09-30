package me.ray.nexahub.data.db.fts

import android.content.Context
import java.io.File

object SimpleDictManager {

    private const val DICT_ASSET_DIR = "simple_dict"
    private const val VERSION_FILE = "version.txt"

    // Matches the version of the dictionary in assets; bump it when the dictionary is updated
    private const val CURRENT_VERSION = 1

    /**
     * Extracts assets/simple_dict to files/simple_dict and returns the dictionary directory.
     * Returns immediately if it's already the latest version, without copying again.
     * Can be called from any thread.
     */
    fun extractDict(context: Context): File {
        val destDir = File(context.filesDir, DICT_ASSET_DIR)
        val versionFile = File(destDir, VERSION_FILE)

        if (versionFile.exists() && versionFile.readText().trim().toIntOrNull() == CURRENT_VERSION) {
            return destDir
        }

        destDir.deleteRecursively()
        destDir.mkdirs()
        copyAssetDir(context, DICT_ASSET_DIR, destDir)
        versionFile.writeText(CURRENT_VERSION.toString())
        return destDir
    }

    private fun copyAssetDir(context: Context, assetPath: String, destDir: File) {
        val assets = context.assets.list(assetPath) ?: return
        for (name in assets) {
            val childAsset = "$assetPath/$name"
            val destFile = File(destDir, name)
            val children = context.assets.list(childAsset)
            if (!children.isNullOrEmpty()) {
                destFile.mkdirs()
                copyAssetDir(context, childAsset, destFile)
            } else {
                context.assets.open(childAsset).use { input ->
                    destFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            }
        }
    }
}
