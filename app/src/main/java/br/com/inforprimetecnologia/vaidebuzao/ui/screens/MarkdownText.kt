package br.com.inforprimetecnologia.vaidebuzao.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp

/** Renderizador simples de Markdown para os textos legais (títulos, citações, listas, tabelas, **negrito**). */
private sealed interface MdBlock {
    data class H(val level: Int, val text: String) : MdBlock
    data class P(val text: String) : MdBlock
    data class Quote(val lines: List<String>) : MdBlock
    data class Bullets(val items: List<String>) : MdBlock
    data class Table(val rows: List<List<String>>) : MdBlock
}

private fun parse(md: String): List<MdBlock> {
    val out = ArrayList<MdBlock>()
    val lines = md.lines()
    var i = 0
    while (i < lines.size) {
        val line = lines[i].trimEnd()
        when {
            line.isBlank() -> i++
            line.startsWith("#") -> {
                val level = line.takeWhile { it == '#' }.length
                out += MdBlock.H(level, line.drop(level).trim())
                i++
            }
            line.startsWith(">") -> {
                val q = ArrayList<String>()
                while (i < lines.size && lines[i].startsWith(">")) {
                    q += lines[i].removePrefix(">").trim()
                    i++
                }
                out += MdBlock.Quote(q.filter { it.isNotBlank() })
            }
            line.startsWith("- ") -> {
                val items = ArrayList<String>()
                while (i < lines.size && lines[i].startsWith("- ")) {
                    items += lines[i].removePrefix("- ").trim()
                    i++
                }
                out += MdBlock.Bullets(items)
            }
            line.startsWith("|") -> {
                val rows = ArrayList<List<String>>()
                while (i < lines.size && lines[i].startsWith("|")) {
                    val cells = lines[i].trim().trim('|').split("|").map { it.trim() }
                    if (!cells.all { it.matches(Regex("^:?-{2,}:?$")) }) rows += cells
                    i++
                }
                out += MdBlock.Table(rows)
            }
            else -> {
                val p = StringBuilder(line)
                i++
                while (i < lines.size && lines[i].isNotBlank() && !lines[i].startsWith("#") &&
                    !lines[i].startsWith("- ") && !lines[i].startsWith(">") && !lines[i].startsWith("|")
                ) {
                    p.append(' ').append(lines[i].trim())
                    i++
                }
                out += MdBlock.P(p.toString())
            }
        }
    }
    return out
}

private fun inline(text: String): AnnotatedString = buildAnnotatedString {
    val parts = text.split("**")
    parts.forEachIndexed { idx, part ->
        val clean = part.replace("\"", "\"")
        if (idx % 2 == 1) withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(clean) } else append(clean)
    }
}

@Composable
fun MarkdownText(markdown: String, modifier: Modifier = Modifier) {
    val blocks = remember(markdown) { parse(markdown) }
    val c = MaterialTheme.colorScheme
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        blocks.forEach { b ->
            when (b) {
                is MdBlock.H -> Text(
                    inline(b.text),
                    style = when (b.level) {
                        1 -> MaterialTheme.typography.headlineSmall
                        2 -> MaterialTheme.typography.titleLarge
                        else -> MaterialTheme.typography.titleMedium
                    },
                    modifier = Modifier.padding(top = if (b.level == 1) 0.dp else 8.dp).semantics { heading() },
                )
                is MdBlock.P -> Text(inline(b.text), style = MaterialTheme.typography.bodyMedium, color = c.onSurface)
                is MdBlock.Quote -> Surface(color = c.primaryContainer, shape = MaterialTheme.shapes.medium) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        b.lines.forEach { l ->
                            if (l.startsWith("- ")) {
                                Bullet(l.removePrefix("- "), c.onPrimaryContainer)
                            } else {
                                Text(inline(l), style = MaterialTheme.typography.titleSmall, color = c.onPrimaryContainer)
                            }
                        }
                    }
                }
                is MdBlock.Bullets -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    b.items.forEach { Bullet(it, c.onSurface) }
                }
                is MdBlock.Table -> Surface(
                    color = c.surfaceContainerLow,
                    shape = MaterialTheme.shapes.small,
                ) {
                    Column(Modifier.padding(vertical = 4.dp)) {
                        val header = b.rows.firstOrNull().orEmpty()
                        b.rows.drop(1).forEachIndexed { idx, row ->
                            if (idx > 0) HorizontalDivider(color = c.outlineVariant)
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                row.forEachIndexed { ci, cell ->
                                    val label = header.getOrNull(ci)
                                    if (ci == 0) {
                                        Text(inline(cell), style = MaterialTheme.typography.titleSmall)
                                    } else {
                                        Text(
                                            buildAnnotatedString {
                                                if (label != null) withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = c.onSurfaceVariant)) { append("$label: ") }
                                                append(inline(cell))
                                            },
                                            style = MaterialTheme.typography.bodySmall,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Bullet(text: String, color: androidx.compose.ui.graphics.Color) {
    Row {
        Box(Modifier.padding(top = 8.dp).width(6.dp).height(6.dp).background(MaterialTheme.colorScheme.secondary, MaterialTheme.shapes.extraSmall))
        Spacer(Modifier.width(10.dp))
        Text(inline(text), style = MaterialTheme.typography.bodyMedium, color = color)
    }
}
