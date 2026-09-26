package com.garfiec.librechat.feature.schedules.di

import com.garfiec.librechat.feature.schedules.viewmodel.ScheduleEditorViewModel
import com.garfiec.librechat.feature.schedules.viewmodel.SchedulesListViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val schedulesFeatureModule = module {
    viewModelOf(::SchedulesListViewModel)

    // The editor's scheduleId arrives via parametersOf from the nav layer, so the lambda form is
    // required — viewModelOf cannot read parametersOf. Detekt's DeprecatedKoinApi is a blanket
    // stylistic rule here, not a real @Deprecated API.
    @Suppress("DeprecatedKoinApi")
    viewModel { params ->
        ScheduleEditorViewModel(
            scheduleRepository = get(),
            agentRepository = get(),
            projectRepository = get(),
            scheduleId = params.getOrNull(),
        )
    }
}
