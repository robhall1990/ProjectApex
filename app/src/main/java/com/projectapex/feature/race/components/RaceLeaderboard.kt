package com.projectapex.feature.race.components

import androidx.compose.foundation.layout.Column
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
import com.projectapex.domain.model.RaceState

/**
 * Position / Driver / Gap / Tyre standings, driven entirely by [raceState] -
 * no simulator dependency, no business logic beyond sorting by position.
 * The leader's row is visually highlighted by [LeaderboardRow] itself.
 */
@Composable
fun RaceLeaderboard(
    raceState: RaceState,
    modifier: Modifier = Modifier
) {
    val cars = raceState.cars.sortedBy { it.position }

    SectionCard(title = stringResource(R.string.race_leaderboard_title), modifier = modifier) {
        if (cars.isEmpty()) {
            Text(
                text = stringResource(R.string.race_track_no_session),
                style = MaterialTheme.typography.bodyMedium
            )
        } else {
            Column {
                LeaderboardColumnHeader()
                HorizontalDivider(modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
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
