package me.ray.nexahub.utils

import android.content.Context
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

object EmojiUtils {
    fun loadEmoji(context: Context): EmojiData {
        return context.assets.open("emoji/categories.with.modifiers.min.json")
            .bufferedReader()
            .use { inputStream ->
                val text = inputStream.readText()
                val json = JsonInstant.parseToJsonElement(text).jsonObject

                val version = json["@version"]?.jsonPrimitive?.content ?: "unknown"
                val categories = json["emojis"]!!.jsonObject.map { (categoryName, categoryObject) ->
                    val subCategories =
                        categoryObject.jsonObject.map { (subCategoryName, subCategoryObject) ->
                            val emojis =
                                subCategoryObject.jsonArray.map { emojiObject ->
                                    val name = emojiObject.jsonObject["name"]?.jsonPrimitive?.content ?: "unknown"
                                    val emoji = emojiObject.jsonObject["emoji"]?.jsonPrimitive?.content ?: "unknown"
                                    val code =
                                        emojiObject.jsonObject["code"]?.jsonArray?.map { it.jsonPrimitive.content }
                                            ?: emptyList()

                                    Emoji(
                                        name = name,
                                        emoji = emoji,
                                        code = code
                                    )
                                }

                            EmojiSubCategory(
                                name = subCategoryName,
                                emojis = emojis
                            )
                        }

                    EmojiCategory(
                        name = categoryName,
                        subCategories = subCategories
                    )
                }

                EmojiData(version, categories)
            }
    }

    /**
     * Convert Unicode code points to an emoji character
     */
    fun codeToEmoji(codes: List<String>): String {
        return codes.joinToString("") { code ->
            val codePoint = code.toInt(16)
            String(Character.toChars(codePoint))
        }
    }

    /**
     * Check whether two emoji are different variants of the same base emoji
     * by comparing their base code points (ignoring skin tone modifiers)
     */
    fun areEmojiVariants(emoji1: Emoji, emoji2: Emoji): Boolean {
        val skinToneModifiers = setOf("1F3FB", "1F3FC", "1F3FD", "1F3FE", "1F3FF")

        val baseCodes1 = emoji1.code.filter { it !in skinToneModifiers }
        val baseCodes2 = emoji2.code.filter { it !in skinToneModifiers }

        return baseCodes1 == baseCodes2 && baseCodes1.isNotEmpty()
    }

    /**
     * Group a list of emoji by variant
     * Returns a Map whose key is the base emoji (usually the first variant) and whose value is the list of all variants
     */
    fun groupEmojisByVariants(emojis: List<Emoji>): Map<Emoji, List<Emoji>> {
        val grouped = mutableMapOf<Emoji, MutableList<Emoji>>()
        val processed = mutableSetOf<Emoji>()

        for (emoji in emojis) {
            if (emoji in processed) continue

            val variants = mutableListOf(emoji)

            // Find all variants
            for (otherEmoji in emojis) {
                if (otherEmoji != emoji && areEmojiVariants(emoji, otherEmoji)) {
                    variants.add(otherEmoji)
                    processed.add(otherEmoji)
                }
            }

            // Sort by skin tone order (if there are skin tone modifiers)
            variants.sortBy { variant ->
                val skinToneOrder = listOf("1F3FB", "1F3FC", "1F3FD", "1F3FE", "1F3FF")
                val skinTone = variant.code.find { it in skinToneOrder }
                skinTone?.let { skinToneOrder.indexOf(it) } ?: -1
            }

            grouped[variants.first()] = variants
            processed.add(emoji)
        }

        return grouped
    }
}

data class EmojiData(
    val version: String,
    val categories: List<EmojiCategory>,
) {
    /**
     * Get the variant groups of all emoji
     */
    fun getAllEmojiVariants(): Map<Emoji, List<Emoji>> {
        val allEmojis = categories.flatMap { category ->
            category.subCategories.flatMap { subCategory ->
                subCategory.emojis
            }
        }
        return EmojiUtils.groupEmojisByVariants(allEmojis)
    }
}

data class EmojiCategory(
    val name: String,
    val subCategories: List<EmojiSubCategory>,
) {
    /**
     * Get the variant groups of all emoji in this category
     */
    fun getEmojiVariants(): Map<Emoji, List<Emoji>> {
        val allEmojis = subCategories.flatMap { it.emojis }
        return EmojiUtils.groupEmojisByVariants(allEmojis)
    }
}

data class EmojiSubCategory(
    val name: String,
    val emojis: List<Emoji>,
)

data class Emoji(
    val name: String,
    val emoji: String,
    val code: List<String>, // Unicode code points (for example, ["1F600"] for grinning face)
)
