package com.projectapex.feature.race

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.projectapex.R

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
        RaceHeader()

        NextSessionCard(
            eventName = uiState.session.eventName,
            sessionType = uiState.sessionType,
            sessionTime = uiState.sessionTime,
            status = uiState.session.status
        )

        Button(
            // Live session entry point is not implemented yet; this is a
            // placeholder for future live-timing functionality.
            onClick = {},
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.race_enter_live_session))
        }

        RaceIntelligenceCard(capabilities = uiState.upcomingCapabilities)
    }
}

@Composable
private fun RaceHeader() {
    Column {
        Text(
            text = stringResource(R.string.race_header_title),
            style = MaterialTheme.typography.headlineMedium
        )
        Text(
            text = stringResource(R.string.race_header_subtitle),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
