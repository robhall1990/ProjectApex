package com.projectapex.feature.race

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.projectapex.feature.race.components.RaceIntelligenceSection
import com.projectapex.feature.race.components.RaceLeaderboard
import com.projectapex.feature.race.components.ReplayControls
import com.projectapex.feature.race.components.UnwrappedTrackView

@Composable
fun RaceScreen(
    viewModel: RaceViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        ReplayControls(
            isReplayMode = uiState.isReplayMode,
            onPreviousClick = viewModel::onPreviousClicked,
            onPlayPauseClick = viewModel::onPlayPauseClicked,
            onNextClick = viewModel::onNextClicked
        )

        UnwrappedTrackView(raceState = uiState.raceState)

        RaceIntelligenceSection(insights = uiState.insights)

        RaceLeaderboard(raceState = uiState.raceState)
    }
}
