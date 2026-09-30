package me.ray.nexahub.di

import kotlinx.serialization.json.Json
import me.ray.nexahub.AppScope
import me.ray.nexahub.data.ai.tools.local.LocalTools
import me.ray.nexahub.data.ai.tools.ChatToolFactory
import me.ray.nexahub.data.event.AppEventBus
import me.ray.nexahub.service.ChatNotificationManager
import me.ray.nexahub.service.ChatService
import me.ray.nexahub.ui.pages.extensions.workspace.WorkspaceTerminalSessionManager
import me.ray.nexahub.utils.EmojiData
import me.ray.nexahub.utils.EmojiUtils
import me.ray.nexahub.utils.JsonInstant
import me.ray.nexahub.utils.SoundEffectPlayer
import me.ray.nexahub.web.WebServerManager
import me.ray.nexahub.tts.provider.TTSManager
import org.koin.dsl.module

val appModule = module {
    single<Json> { JsonInstant }

    single {
        AppEventBus()
    }

    single {
        LocalTools(get(), get(), get(), get())
    }

    single {
        AppScope()
    }

    single<EmojiData> {
        EmojiUtils.loadEmoji(get())
    }

    single {
        TTSManager(get())
    }

    single {
        SoundEffectPlayer(get())
    }

    single {
        WorkspaceTerminalSessionManager(get(), get())
    }

    // Generation notifications are decoupled from business logic: ChatService only emits events, which are consumed here;
    // createdAtStart ensures the subscription starts with the process, otherwise events from background generation would be lost with no subscriber
    single(createdAtStart = true) {
        ChatNotificationManager(
            context = get(),
            appScope = get(),
            eventBus = get(),
            settingsStore = get(),
        )
    }

    single {
        ChatToolFactory(
            json = get(),
            memoryRepository = get(),
            conversationRepository = get(),
            localTools = get(),
            mcpManager = get(),
            skillManager = get(),
            workspaceRepository = get(),
        )
    }

    single {
        ChatService(
            context = get(),
            appScope = get(),
            appEventBus = get(),
            settingsStore = get(),
            conversationRepo = get(),
            memoryRepository = get(),
            generationLoop = get(),
            translationHandler = get(),
            templateTransformer = get(),
            providerManager = get(),
            chatToolFactory = get(),
            mcpManager = get(),
            filesManager = get(),
            workspaceRepository = get(),
            folderRepository = get()
        )
    }

    single {
        WebServerManager(
            context = get(),
            appScope = get(),
            chatService = get(),
            conversationRepo = get(),
            folderRepo = get(),
            settingsStore = get(),
            filesManager = get()
        )
    }
}
