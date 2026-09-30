package me.ray.nexahub.data.files

import android.content.Context
import android.content.res.AssetManager
import java.io.File
import java.io.FileNotFoundException

/**
 * Built-in skills: shipped with the APK as `assets/builtin_skills/<name>/`, with the same layout as user skills (SKILL.md + supporting files).
 *
 * After the app is installed/updated they are extracted to `filesDir/builtin_skills/` and mounted at `/builtin_skills` in the workspace,
 * so supporting scripts can run in the workspace. Built-in skills are read-only; a user skill with the same name takes precedence.
 *
 * Note: aapt ignores files starting with `.` and directories starting with `_` by default, so don't use such names.
 */
internal object BuiltinSkills {
    private const val ASSETS_ROOT = "builtin_skills"
    private const val VERSION_FILE = ".version"

    /**
     * When the app install/update time changes, fully extract the built-in skills from assets to [targetDir], replacing the old content.
     *
     * Uses lastUpdateTime rather than versionCode so every reinstall during development refreshes them.
     */
    fun extractIfNeeded(context: Context, targetDir: File) {
        val stamp = context.packageManager
            .getPackageInfo(context.packageName, 0)
            .lastUpdateTime
            .toString()
        val versionFile = targetDir.resolve(VERSION_FILE)
        if (versionFile.exists() && versionFile.readText() == stamp) return

        // Fully extract to a temp directory first and then swap, so a failure midway doesn't leave a partial set of files
        val staging = targetDir.resolveSibling(".${targetDir.name}.staging")
        staging.deleteRecursively()
        staging.mkdirs()
        val assets = context.assets
        assets.list(ASSETS_ROOT).orEmpty().forEach { name ->
            assets.copyTree("$ASSETS_ROOT/$name", staging.resolve(name))
        }
        staging.resolve(VERSION_FILE).writeText(stamp)

        targetDir.deleteRecursively()
        if (!staging.renameTo(targetDir)) {
            staging.deleteRecursively()
            error("Failed to move builtin skills into ${targetDir.absolutePath}")
        }
    }

    private fun AssetManager.copyTree(assetPath: String, target: File) {
        val children = list(assetPath).orEmpty()
        if (children.isNotEmpty()) {
            target.mkdirs()
            children.forEach { copyTree("$assetPath/$it", target.resolve(it)) }
            return
        }
        // AssetManager can't tell files from empty directories: when list is empty, try opening it as a file, and treat it as an empty directory if that fails
        try {
            open(assetPath).use { input ->
                target.parentFile?.mkdirs()
                target.outputStream().use { input.copyTo(it) }
            }
        } catch (_: FileNotFoundException) {
            target.mkdirs()
            return
        }
        // Assets don't keep the executable bit; scripts with a shebang need it restored so they can be run directly as ./script in the workspace
        if (target.startsWithShebang()) {
            target.setExecutable(true, false)
        }
    }

    private fun File.startsWithShebang(): Boolean = inputStream().use { input ->
        input.read() == '#'.code && input.read() == '!'.code
    }
}

/**
 * Merge user skills and built-in skills, user skills first; built-in skills with the same name as a user skill are hidden.
 */
internal fun mergeWithBuiltinSkills(
    local: List<SkillMetadata>,
    builtin: List<SkillMetadata>,
): List<SkillMetadata> {
    val localNames = local.mapTo(HashSet()) { it.name }
    return local + builtin.filter { it.name !in localNames }
}
