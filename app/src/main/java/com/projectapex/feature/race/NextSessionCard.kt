package com.projectapex.feature.race

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.projectapex.core.model.SessionStatus
import com.projectapex.core.ui.ApexCard

@Composable
fun NextSessionCard(
    eventName: String,
    sessionType: String,
    sessionTime: String,
    status: SessionStatus,
    modifier: Modifier = Modifier
) {
    ApexCard(modifier = modifier.fillMaxWidth()) {
        Text(text = eventName, style = MaterialTheme.typography.titleLarge)
        Text(
            text = sessionType,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 4.dp)
        )
        Text(text = sessionTime, style = MaterialTheme.typography.bodyMedium)
        Text(
            text = status.name,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(top = 12.dp)
        )
    }
}
