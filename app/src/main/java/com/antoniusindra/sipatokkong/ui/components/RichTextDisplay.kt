package com.antoniusindra.sipatokkong.ui.components

import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import com.antoniusindra.sipatokkong.core.util.RichTextUtil

@Composable
fun RichTextDisplay(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyLarge,
    maxLines: Int = Int.MAX_VALUE
) {
    SelectionContainer(modifier = modifier) {
        Text(
            text = RichTextUtil.parseMarkdown(text),
            style = style,
            maxLines = maxLines,
            overflow = if (maxLines != Int.MAX_VALUE) androidx.compose.ui.text.style.TextOverflow.Ellipsis else androidx.compose.ui.text.style.TextOverflow.Clip
        )
    }
}
