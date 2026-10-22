package com.garfiec.librechat.feature.skills.di

import com.garfiec.librechat.feature.skills.SkillFileChanges
import com.garfiec.librechat.feature.skills.SkillSaveNotices
import com.garfiec.librechat.feature.skills.viewmodel.SkillAclViewModel
import com.garfiec.librechat.feature.skills.viewmodel.SkillDetailViewModel
import com.garfiec.librechat.feature.skills.viewmodel.SkillEditorViewModel
import com.garfiec.librechat.feature.skills.viewmodel.SkillFileViewModel
import com.garfiec.librechat.feature.skills.viewmodel.SkillFilesViewModel
import com.garfiec.librechat.feature.skills.viewmodel.SkillsListViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val skillsFeatureModule = module {
    // One per app: the editor posts a save's warnings, the page it returns to takes them.
    single { SkillSaveNotices() }
    // One per app: the file screen posts a save, the detail page's file list reloads for it.
    single { SkillFileChanges() }
    viewModelOf(::SkillsListViewModel)
    viewModelOf(::SkillAclViewModel)

    // SkillDetail/SkillEditor VMs receive skillId via parametersOf from the nav
    // layer, so the lambda-form viewModel { params -> ... } DSL is required
    // (viewModelOf can't read parametersOf).
    viewModel { params ->
        SkillDetailViewModel(
            skillsRepository = get(),
            roleRepository = get(),
            userRepository = get(),
            configRepository = get(),
            saveNotices = get(),
            skillId = params.get(),
        )
    }
    viewModel { params ->
        SkillEditorViewModel(
            skillsRepository = get(),
            configRepository = get(),
            saveNotices = get(),
            initialSkillId = params.getOrNull(),
        )
    }
    viewModel { params ->
        SkillFilesViewModel(
            skillsRepository = get(),
            fileChanges = get(),
            skillId = params.get(),
        )
    }
    // Two String params (skill id, then file path): read by index, since get() matches by type
    // and would hand the skill id to both.
    viewModel { params ->
        SkillFileViewModel(
            skillsRepository = get(),
            userRepository = get(),
            fileChanges = get(),
            skillId = params[0],
            relativePath = params[1],
        )
    }
}
