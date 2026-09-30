package me.ray.nexahub.di

import android.content.Context
import me.ray.nexahub.data.files.FileFolders
import me.ray.nexahub.data.files.FilesManager
import me.ray.nexahub.data.files.SkillManager
import me.ray.nexahub.data.repository.ConversationRepository
import me.ray.nexahub.data.repository.FavoriteRepository
import me.ray.nexahub.data.repository.FolderRepository
import me.ray.nexahub.data.repository.FilesRepository
import me.ray.nexahub.data.repository.GenMediaRepository
import me.ray.nexahub.data.repository.MemoryRepository
import me.ray.nexahub.data.repository.WorkspaceRepository
import me.ray.nexahub.workspace.ProotShellRunner
import me.ray.nexahub.workspace.RootfsInstaller
import me.ray.nexahub.workspace.WorkspaceBindMount
import me.ray.nexahub.workspace.WorkspaceManager
import org.koin.dsl.module
import java.io.File

val repositoryModule = module {
    single {
        ConversationRepository(get(), get(), get(), get(), get(), get())
    }

    single {
        FolderRepository(get(), get())
    }

    single {
        MemoryRepository(get())
    }

    single {
        GenMediaRepository(get())
    }

    single {
        FilesRepository(get())
    }

    single {
        FavoriteRepository(get())
    }

    single {
        val context: Context = get()
        WorkspaceManager(
            baseDir = File(context.filesDir, "workspaces"),
            shellRunner = ProotShellRunner(
                nativeLibraryDir = File(context.applicationInfo.nativeLibraryDir),
            ),
            // The same mount table is used for PRoot's -b arguments and for path resolution in file tools, so the two can't drift apart
            bindMounts = listOf(
                WorkspaceBindMount(
                    source = File(context.filesDir, FileFolders.SKILLS).apply { mkdirs() },
                    target = "/skills",
                ),
                WorkspaceBindMount(
                    source = File(context.filesDir, FileFolders.BUILTIN_SKILLS).apply { mkdirs() },
                    target = "/builtin_skills",
                ),
                WorkspaceBindMount(
                    source = File(context.filesDir, FileFolders.TOOL_OUTPUTS).apply { mkdirs() },
                    target = "/tool_outputs",
                ),
                WorkspaceBindMount(
                    source = File(context.filesDir, FileFolders.UPLOAD).apply { mkdirs() },
                    target = "/upload",
                ),
            ),
        )
    }

    single {
        RootfsInstaller(get())
    }

    single {
        WorkspaceRepository(get(), get(), get(), get())
    }

    single {
        FilesManager(get(), get(), get())
    }

    single {
        SkillManager(get(), get())
    }
}
