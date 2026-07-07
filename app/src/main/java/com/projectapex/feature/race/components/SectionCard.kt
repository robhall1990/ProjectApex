package com.projectapex.feature.race.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.projectapex.core.ui.ApexCard

/**
 * The recurring "titled card" pattern behind every Race screen section: an
 * [ApexCard] with a [PanelHeader] title followed by content. Extracted so no
 * section hand-rolls this structure itself.
 */
@Composable
fun SectionCard(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    ApexCard(modifier = modifier.fillMaxWidth()) {
        PanelHeader(title = title)
        Column(
            modifier = Modifier.padding(top = 12.dp),
            content = content
        )
    }
}
