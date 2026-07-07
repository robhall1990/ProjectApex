package com.projectapex.feature.home

import org.junit.Assert.assertEquals
import org.junit.Test

class HomeViewModelTest {

    @Test
    fun `initial state exposes the app title and tagline`() {
        val viewModel = HomeViewModel()

        val state = viewModel.uiState.value

        assertEquals("Project Apex", state.title)
        assertEquals("The smartest way to watch Formula 1.", state.tagline)
    }
}
