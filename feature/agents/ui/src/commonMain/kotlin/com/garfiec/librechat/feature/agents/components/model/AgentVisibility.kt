package com.garfiec.librechat.feature.agents.components.model

import com.garfiec.librechat.feature.agents.resources.Res
import com.garfiec.librechat.feature.agents.resources.visibility_private
import com.garfiec.librechat.feature.agents.resources.visibility_public
import com.garfiec.librechat.feature.agents.resources.visibility_team
import org.jetbrains.compose.resources.StringResource

enum class AgentVisibility(val label: StringResource) {
    PRIVATE(Res.string.visibility_private),
    TEAM(Res.string.visibility_team),
    PUBLIC(Res.string.visibility_public),
}
