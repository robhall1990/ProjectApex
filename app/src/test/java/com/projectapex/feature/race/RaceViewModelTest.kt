package com.projectapex.feature.race

import com.projectapex.core.model.SessionStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class RaceViewModelTest {

    @Test
    fun `initial state reflects an offline placeholder session`() {
        val viewModel = RaceViewModel()

        val state = viewModel.uiState.value

        assertEquals(SessionStatus.OFFLINE, state.session.status)
        assertEquals("British Grand Prix", state.session.eventName)
        assertEquals("Race", state.sessionType)
        assertEquals("Sunday 14:00", state.sessionTime)
        assertEquals(
            listOf("Live gaps", "Strategy AI", "Track visualisation", "Race replay"),
            state.upcomingCapabilities
        )
    }
}
