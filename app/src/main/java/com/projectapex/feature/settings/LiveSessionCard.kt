package com.projectapex.feature.settings

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.projectapex.R
import com.projectapex.core.ui.ApexCard

/**
 * Controls for [com.projectapex.domain.livedata.OpenF1LiveDataSource]. Takes
 * plain state and callbacks only, mirroring [DeveloperModeCard] - it has no
 * idea the data source exists, that's [SettingsViewModel]'s job.
 */
@Composable
fun LiveSessionCard(
    isLiveSessionRunning: Boolean,
    statusText: String,
    isError: Boolean,
    onStartLiveSession: (totalLaps: Int) -> Unit,
    onStopLiveSession: () -> Unit,
    modifier: Modifier = Modifier
) {
    var totalLapsInput by rememberSaveable { mutableStateOf("") }

    ApexCard(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.settings_live_session_title),
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            text = statusText,
            style = MaterialTheme.typography.bodyMedium,
            color = if (isError) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
        )
        OutlinedTextField(
            value = totalLapsInput,
            onValueChange = { input -> totalLapsInput = input.filter(Char::isDigit) },
            label = { Text(stringResource(R.string.settings_live_session_total_laps_label)) },
            placeholder = { Text(stringResource(R.string.settings_live_session_total_laps_hint)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            enabled = !isLiveSessionRunning,
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp)
        )
        Button(
            onClick = { onStartLiveSession(totalLapsInput.toIntOrNull() ?: 0) },
            enabled = !isLiveSessionRunning,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.settings_start_live_session))
        }
        OutlinedButton(
            onClick = onStopLiveSession,
            enabled = isLiveSessionRunning,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 12.dp)
        ) {
            Text(stringResource(R.string.settings_stop_live_session))
        }
        Text(
            text = stringResource(R.string.settings_live_session_disclaimer),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
