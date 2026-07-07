package com.projectapex.feature.race.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.projectapex.R
import com.projectapex.core.ui.ApexCard
import com.projectapex.domain.model.RaceState

/**
 * Position / Driver / Tyre / Gap standings, driven entirely by [raceState] -
 * no simulator dependency, no business logic beyond sorting by position.
 */
@Composable
fun RaceLeaderboard(
    raceState: RaceState,
    modifier: Modifier = Modifier
) {
    val cars = raceState.cars.sortedBy { it.position }

    ApexCard(modifier = modifier.fillMaxWidth()) {
        SectionHeader(title = stringResource(R.string.race_leaderboard_title))

        if (cars.isEmpty()) {
            Text(
                text = stringResource(R.string.race_track_no_session),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 12.dp)
            )
        } else {
            Column(modifier = Modifier.padding(top = 12.dp)) {
                cars.forEachIndexed { index, car ->
                    key(car.driver.id) {
                        LeaderboardRow(car = car)
                    }
                    if (index != cars.lastIndex) {
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}
