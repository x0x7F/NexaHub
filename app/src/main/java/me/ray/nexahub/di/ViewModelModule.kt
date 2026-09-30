package me.ray.nexahub.di

import me.ray.nexahub.ui.pages.assistant.AssistantVM
import me.ray.nexahub.ui.pages.assistant.detail.AssistantDetailVM
import me.ray.nexahub.ui.pages.backup.BackupVM
import me.ray.nexahub.ui.pages.chat.ChatDrawerVM
import me.ray.nexahub.ui.pages.chat.ChatVM
import me.ray.nexahub.ui.pages.debug.DebugVM
import me.ray.nexahub.ui.pages.favorite.FavoriteVM
import me.ray.nexahub.ui.pages.search.SearchVM
import me.ray.nexahub.ui.pages.history.HistoryVM
import me.ray.nexahub.ui.pages.stats.StatsVM
import me.ray.nexahub.ui.pages.imggen.ImgGenVM
import me.ray.nexahub.ui.pages.extensions.PromptVM
import me.ray.nexahub.ui.pages.extensions.QuickMessagesVM
import me.ray.nexahub.ui.pages.extensions.skills.SkillDetailVM
import me.ray.nexahub.ui.pages.extensions.skills.SkillsVM
import me.ray.nexahub.ui.pages.extensions.workspace.WorkspaceDetailVM
import me.ray.nexahub.ui.pages.extensions.workspace.WorkspaceVM
import me.ray.nexahub.ui.pages.setting.SettingVM
import me.ray.nexahub.ui.pages.share.handler.ShareHandlerVM
import me.ray.nexahub.ui.pages.translator.TranslatorVM
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val viewModelModule = module {
    viewModel<ChatVM> { params ->
        ChatVM(
            id = params.get(),
            context = get(),
            settingsStore = get(),
            conversationRepo = get(),
            chatService = get(),
            filesManager = get(),
            favoriteRepository = get(),
        )
    }
    viewModelOf(::ChatDrawerVM)
    viewModelOf(::SettingVM)
    viewModelOf(::DebugVM)
    viewModelOf(::HistoryVM)
    viewModelOf(::AssistantVM)
    viewModel<AssistantDetailVM> {
        AssistantDetailVM(
            id = it.get(),
            settingsStore = get(),
            memoryRepository = get(),
            filesManager = get(),
            skillManager = get(),
            workspaceRepository = get(),
        )
    }
    viewModelOf(::TranslatorVM)
    viewModel<ShareHandlerVM> {
        ShareHandlerVM(
            text = it.get(),
            settingsStore = get(),
        )
    }
    viewModelOf(::BackupVM)
    viewModelOf(::ImgGenVM)
    viewModelOf(::PromptVM)
    viewModelOf(::QuickMessagesVM)
    viewModelOf(::SkillsVM)
    viewModelOf(::SkillDetailVM)
    viewModelOf(::WorkspaceVM)
    viewModel<WorkspaceDetailVM> {
        WorkspaceDetailVM(
            id = it.get(),
            repository = get(),
            terminalSessionManager = get(),
        )
    }
    viewModelOf(::FavoriteVM)
    viewModelOf(::SearchVM)
    viewModelOf(::StatsVM)
}
