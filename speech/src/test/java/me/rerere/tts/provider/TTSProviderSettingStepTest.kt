package me.rerere.tts.provider

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TTSProviderSettingStepTest {
    @Test
    fun step_defaults_are_expected() {
        val setting = TTSProviderSetting.Step()

        assertEquals("Step TTS", setting.name)
        assertEquals("https://api.stepfun.com", setting.baseUrl)
        assertEquals("step-tts-mini", setting.model)
        assertEquals("elegantgentle-female", setting.voice)
        assertEquals("mp3", setting.responseFormat)
        assertEquals(1.0f, setting.speed)
        assertEquals(1.0f, setting.volume)
        assertEquals(24000, setting.sampleRate)
        assertEquals("", setting.instruction)
        assertEquals("", setting.apiKey)
    }

    @Test
    fun step_is_registered_in_provider_types() {
        // If it's not registered in the Types list, the Step option won't appear in the settings dropdown and can't be created
        assertTrue(TTSProviderSetting.Types.contains(TTSProviderSetting.Step::class))
    }

    @Test
    fun step_copyProvider_preserves_id_and_name() {
        val original = TTSProviderSetting.Step(
            apiKey = "sk-test",
            model = "stepaudio-2.5-tts",
            voice = "cixingnansheng",
        )
        val copied = original.copyProvider(
            id = original.id,
            name = "My Step"
        ) as TTSProviderSetting.Step

        assertEquals(original.id, copied.id)
        assertEquals("My Step", copied.name)
        // All other fields must stay unchanged
        assertEquals("sk-test", copied.apiKey)
        assertEquals("stepaudio-2.5-tts", copied.model)
        assertEquals("cixingnansheng", copied.voice)
    }
}
