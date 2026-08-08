package com.antoniusindra.sipatokkong.core.util

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.sp

object RichTextUtil {
    fun parseMarkdown(text: String): AnnotatedString {
        return buildAnnotatedString {
            val lines = text.lines()
            var currentListNumber = 0
            
            lines.forEachIndexed { index, line ->
                val trimmed = line.trim()
                
                // Determine if this is a numbered list item
                val isNumbered = trimmed.firstOrNull()?.isDigit() == true && trimmed.contains(". ")
                if (!isNumbered && trimmed.isNotEmpty()) {
                    currentListNumber = 0
                }

                when {
                    trimmed.startsWith("### ") -> {
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = 16.sp)) {
                            append(trimmed.removePrefix("### "))
                        }
                    }
                    trimmed.startsWith("## ") -> {
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = 20.sp)) {
                            append(trimmed.removePrefix("## "))
                        }
                    }
                    trimmed.startsWith("# ") -> {
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = 22.sp)) {
                            append(trimmed.removePrefix("# "))
                        }
                    }
                    trimmed.startsWith("- ") || trimmed.startsWith("* ") || trimmed.startsWith("+ ") -> {
                        append("  • ")
                        parseInline(trimmed.substring(2))
                    }
                    isNumbered -> {
                        val dotIndex = trimmed.indexOf(". ")
                        val suffix = trimmed.substring(dotIndex + 2)
                        currentListNumber++
                        append("  $currentListNumber. ")
                        parseInline(suffix)
                    }
                    else -> {
                        parseInline(line)
                    }
                }
                if (index < lines.size - 1) {
                    append("\n")
                }
            }
        }
    }

    private fun AnnotatedString.Builder.parseInline(text: String) {
        val boldRegex = Regex("""\*\*(.*?)\*\*""")
        var lastIndex = 0
        boldRegex.findAll(text).forEach { match ->
            append(text.substring(lastIndex, match.range.first))
            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                append(match.groupValues[1])
            }
            lastIndex = match.range.last + 1
        }
        append(text.substring(lastIndex))
    }
}
