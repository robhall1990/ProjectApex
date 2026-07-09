package com.projectapex.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.projectapex.domain.model.CarState
import com.projectapex.domain.model.RaceState

private val COLUMN_WEIGHTS = floatArrayOf(0.5f, 2f, 1.6f, 1f, 1.3f, 0.8f)

@Composable
fun LeaderboardTable(raceState: RaceState, modifier: Modifier = Modifier) {
    val cars = raceState.cars.sortedBy { it.position }

    Box(modifier = modifier.fillMaxSize().border(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        if (cars.isEmpty()) {
            Text(
                text = "No session running - start the simulator or a live session.",
                modifier = Modifier.padding(16.dp),
                style = MaterialTheme.typography.bodyMedium,
            )
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                item {
                    HeaderRow()
                    HorizontalDivider()
                }
                items(cars, key = { it.driver.id }) { car ->
                    LeaderboardDataRow(car)
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
}

@Composable
private fun HeaderRow() {
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
        Cell("POS", COLUMN_WEIGHTS[0], FontWeight.Bold)
        Cell("DRIVER", COLUMN_WEIGHTS[1], FontWeight.Bold)
        Cell("TEAM", COLUMN_WEIGHTS[2], FontWeight.Bold)
        Cell("GAP", COLUMN_WEIGHTS[3], FontWeight.Bold)
        Cell("TYRE", COLUMN_WEIGHTS[4], FontWeight.Bold)
        Cell("PIT", COLUMN_WEIGHTS[5], FontWeight.Bold)
    }
}

@Composable
private fun LeaderboardDataRow(car: CarState) {
    val background = if (car.position == 1) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(background)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Cell(car.position.toString(), COLUMN_WEIGHTS[0])
        Cell("${car.driver.name} (#${car.driver.number})", COLUMN_WEIGHTS[1])
        Cell(car.driver.team, COLUMN_WEIGHTS[2])
        Cell(if (car.position == 1) "Leader" else "+%.1fs".format(car.gapToLeaderSeconds), COLUMN_WEIGHTS[3])
        Cell("${car.tyreCompound} (${car.tyreAgeLaps})", COLUMN_WEIGHTS[4])
        Cell(if (car.isInPitLane) "IN" else "-", COLUMN_WEIGHTS[5])
    }
}

@Composable
private fun RowScope.Cell(
    text: String,
    weight: Float,
    fontWeight: FontWeight? = null,
) {
    Text(
        text = text,
        modifier = Modifier.weight(weight),
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = fontWeight,
        maxLines = 1,
    )
}
