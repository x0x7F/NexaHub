package me.ray.nexahub.common.http

import java.util.Locale

/**
 * Utility for building Accept-Language header values.
 *
 * Key features:
 * 1) Reads the system languages on both Android and the JVM.
 * 2) Expands "zh-CN" into ["zh-CN", "zh"] (configurable).
 * 3) Deduplicates and generates decreasing q parameters by priority (IETF RFC 7231).
 * 4) The result looks like: "zh-CN, zh;q=0.9, en-US;q=0.8, en;q=0.7"
 */
class AcceptLanguageBuilder private constructor(
    private val localesInPreference: List<Locale>,
    private val options: Options
) {

    data class Options(
        /** Maximum number of language tags used (truncated after deduplication). */
        val maxLanguages: Int = 6,
        /** Step by which q decreases for each subsequent entry, starting from 1.0 (0 < step <= 1). */
        val qStep: Double = 0.1,
        /** Lower bound of q (inclusive); values that would go lower are clamped to it. */
        val minQ: Double = 0.1,
        /** Whether to add the "generic language code" for regional languages, e.g. append "zh" for "zh-CN". */
        val includeGenericLanguage: Boolean = true,
        /** Whether to deduplicate tags (keeping first-occurrence order). */
        val deduplicate: Boolean = true
    )

    companion object {
        /** Create directly from the JVM (desktop/server) system environment. */
        fun fromJvmSystem(options: Options = Options()): AcceptLanguageBuilder {
            val primary = Locale.getDefault()
            // The JVM usually provides only one primary Locale; pass withLocales yourself if you need a custom list.
            return AcceptLanguageBuilder(listOf(primary), options)
        }

        /**
         * Create from the Android system environment.
         * @param context preferably the application or current context, to get the user's "per-app language" / system language preference list
         */
        fun fromAndroid(context: android.content.Context, options: Options = Options()): AcceptLanguageBuilder {
            val locales = systemLocalesAndroid(context)
            return AcceptLanguageBuilder(locales, options)
        }

        /** Create from a caller-provided Locale list (in priority order). */
        fun withLocales(locales: List<Locale>, options: Options = Options()): AcceptLanguageBuilder {
            return AcceptLanguageBuilder(locales, options)
        }

        // Get Android's system Locale list
        private fun systemLocalesAndroid(context: android.content.Context): List<Locale> {
            val cfg = context.resources.configuration
            return if (android.os.Build.VERSION.SDK_INT >= 24) {
                val list = cfg.locales
                (0 until list.size()).map { idx -> list[idx] }
            } else {
                listOf(cfg.locale)
            }
        }
    }

    /** Generate the final Accept-Language header value (without the "Accept-Language:" prefix). */
    fun build(): String {
        // 1) Convert Locales to language tags, expanding "generic language codes" as needed
        val tags = mutableListOf<String>()
        for (loc in localesInPreference) {
            val full = toLanguageTagCompat(loc)
            if (full.isNotBlank()) tags += full

            if (options.includeGenericLanguage) {
                val generic = genericLanguageOf(full)
                if (generic != null) tags += generic
            }
        }

        // 2) Deduplicate (keeping first-occurrence order)
        val distinct = if (options.deduplicate) tags.distinct() else tags

        // 3) Truncate
        val limited = distinct.take(options.maxLanguages.coerceAtLeast(1))

        // 4) Assign q values: the first (1.0) has no q, the rest decrease by step down to minQ
        val parts = mutableListOf<String>()
        var q = 1.0
        for ((i, tag) in limited.withIndex()) {
            if (i == 0) {
                parts += tag
            } else {
                q = (1.0 - i * options.qStep).coerceAtLeast(options.minQ)
                parts += "$tag;q=${formatQ(q)}"
            }
        }

        return parts.joinToString(separator = ", ")
    }

    // --- Helpers ---

    private fun toLanguageTagCompat(locale: Locale): String {
        // JVM 7+ provides Locale#toLanguageTag; still build it by hand as a fallback to be safe
        val tag = locale.toLanguageTag()
        if (tag.isNotBlank()) return tag

        val language = locale.language ?: return ""
        val country = locale.country
        val variant = locale.variant

        return buildString {
            append(language)
            if (!country.isNullOrBlank()) append("-").append(country)
            if (!variant.isNullOrBlank()) append("-").append(variant)
        }
    }

    /** Get "zh" from "zh-CN"; returns null for "en" (no more generic level). */
    private fun genericLanguageOf(tag: String): String? {
        val idx = tag.indexOf('-')
        if (idx <= 0) return null
        val head = tag.substring(0, idx)
        // Ignore more complex cases such as "zh-Hans-CN"; just go up one level
        return if (head.isNotBlank()) head else null
    }

    /** q value format: at most 3 decimal places, dropping trailing zeros and the decimal point. */
    private fun formatQ(value: Double): String {
        val s = String.format(java.util.Locale.ROOT, "%.3f", value)
        return s.trimEnd('0').trimEnd('.')
    }
}

fun main() {
    val builder = AcceptLanguageBuilder.fromJvmSystem()
    println(builder.build())
}
