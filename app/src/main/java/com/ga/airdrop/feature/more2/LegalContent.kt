package com.ga.airdrop.feature.more2

import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.method.LinkMovementMethod
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.util.TypedValue
import android.widget.TextView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.res.ResourcesCompat
import androidx.core.text.HtmlCompat
import com.ga.airdrop.R
import com.ga.airdrop.core.designsystem.theme.AirdropTheme
import com.ga.airdrop.core.designsystem.theme.AirdropType
import com.ga.airdrop.core.designsystem.theme.BrandPalette
import com.ga.airdrop.core.designsystem.theme.Spacing

/*
 * Shared rendering for the Terms & Conditions / Privacy Policy live CMS
 * content, porting the Swift attributedLegalContent approach:
 *   - content starting with "<" is HTML, anything else is markdown that gets
 *     converted (headings/bold/em/links/paragraphs);
 *   - inline CSS/hex colors are STRIPPED so the text renders with the
 *     dynamic theme colors in both light and dark mode (Swift recolors the
 *     parsed string post-hoc for the same reason).
 */

/** RN convertMarkdownToHtml port (same rules as the Swift markdownToHTML). */
internal fun markdownToHtml(value: String): String {
    val normalized = value.replace("\r\n", "\n")
    return normalized.split("\n\n").mapNotNull { rawBlock ->
        val trimmed = rawBlock.trim()
        if (trimmed.isEmpty()) return@mapNotNull null
        val escaped = escapeHtml(trimmed)
        when {
            escaped.startsWith("#### ") -> "<h4>${inlineMarkdownToHtml(escaped.drop(5))}</h4>"
            escaped.startsWith("### ") -> "<h3>${inlineMarkdownToHtml(escaped.drop(4))}</h3>"
            escaped.startsWith("## ") -> "<h2>${inlineMarkdownToHtml(escaped.drop(3))}</h2>"
            escaped.startsWith("# ") -> "<h1>${inlineMarkdownToHtml(escaped.drop(2))}</h1>"
            else -> "<p>${inlineMarkdownToHtml(escaped).replace("\n", "<br/>")}</p>"
        }
    }.joinToString("")
}

private fun escapeHtml(value: String): String = value
    .replace("&", "&amp;")
    .replace("<", "&lt;")
    .replace(">", "&gt;")

private fun inlineMarkdownToHtml(value: String): String = value
    .replace(Regex("\\[([^\\]]+)\\]\\(([^)]+)\\)"), "<a href=\"$2\">$1</a>")
    .replace(Regex("\\*\\*([^*]+)\\*\\*"), "<strong>$1</strong>")
    .replace(Regex("(?<!\\*)\\*([^*]+)\\*(?!\\*)"), "<em>$1</em>")

/**
 * Strip frozen colors from CMS HTML so theme colors apply:
 * `color:`/`background(-color):` declarations inside style attributes,
 * `<font color=…>` and bgcolor attributes.
 */
internal fun stripInlineColors(html: String): String = html
    .replace(Regex("(?i)(background-color|background|color)\\s*:\\s*[^;\"']+;?"), "")
    .replace(Regex("(?i)\\s(color|bgcolor)\\s*=\\s*(\"[^\"]*\"|'[^']*'|[^\\s>]+)"), "")

/** Prepare raw CMS content for HtmlCompat rendering. */
internal fun prepareLegalHtml(raw: String): String {
    val trimmed = raw.trim()
    // RN parity: only treat as HTML when the content STARTS with "<".
    val body = if (trimmed.startsWith("<")) trimmed else markdownToHtml(trimmed)
    return stripInlineColors(body)
}

internal fun colorLegalHeadings(source: Spanned, headingColor: Int): SpannableStringBuilder {
    val builder = SpannableStringBuilder(source)
    builder.getSpans(0, builder.length, RelativeSizeSpan::class.java)
        .filter { it.sizeChange > 1f }
        .forEach { span ->
            val start = builder.getSpanStart(span)
            val end = builder.getSpanEnd(span)
            if (start >= 0 && end > start) {
                builder.setSpan(
                    ForegroundColorSpan(headingColor),
                    start,
                    end,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                )
            }
        }
    return builder
}

/** Live CMS content body — TextView so HTML lists/headings/links render. */
@Composable
internal fun LegalHtmlContent(html: String, modifier: Modifier = Modifier) {
    val colors = AirdropTheme.colors
    val bodyColor = colors.textDescription.toArgb()
    val headingColor = colors.textDarkTitle.toArgb()
    val linkColor = BrandPalette.OrangeMain.toArgb()
    val prepared = remember(html, bodyColor, headingColor) {
        colorLegalHeadings(
            HtmlCompat.fromHtml(prepareLegalHtml(html), HtmlCompat.FROM_HTML_MODE_LEGACY),
            headingColor,
        )
    }
    AndroidView(
        modifier = modifier.fillMaxWidth(),
        factory = { context ->
            TextView(context).apply {
                movementMethod = LinkMovementMethod.getInstance()
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                typeface = runCatching {
                    ResourcesCompat.getFont(context, R.font.cairo_regular)
                }.getOrNull() ?: Typeface.DEFAULT
                setLineSpacing(0f, 1.25f)
            }
        },
        update = { view ->
            view.setTextColor(bodyColor)
            view.setLinkTextColor(linkColor)
            view.text = prepared
        },
    )
}

/**
 * Accordion card shared by Terms/Privacy/FAQs: Title2 header + 15dp chevron
 * (points down when expanded, up when collapsed — RN ChevronIcon), 1dp
 * divider, Body2 textDescription content.
 */
@Composable
internal fun AccordionCard(
    title: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    titleEndGap: Dp = Spacing.xs,
    testTagPrefix: String? = null,
    content: (@Composable () -> Unit)? = null,
) {
    val colors = AirdropTheme.colors
    More2OuterCard(
        modifier = modifier.then(
            if (testTagPrefix != null) {
                Modifier.testTag("$testTagPrefix-card")
            } else {
                Modifier
            },
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .then(
                    if (testTagPrefix != null) {
                        Modifier.testTag("$testTagPrefix-header")
                    } else {
                        Modifier
                    },
                )
                .padding(Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = AirdropType.title2,
                color = colors.textDarkTitle,
                modifier = Modifier
                    .weight(1f)
                    .then(
                        if (testTagPrefix != null) {
                            Modifier.testTag("$testTagPrefix-title")
                        } else {
                            Modifier
                        },
                    ),
            )
            Spacer(
                Modifier
                    .width(titleEndGap)
                    .then(
                        if (testTagPrefix != null) {
                            Modifier.testTag("$testTagPrefix-title-chevron-gap")
                        } else {
                            Modifier
                        },
                    ),
            )
            Image(
                painter = painterResource(R.drawable.ic_chevron),
                contentDescription = null,
                colorFilter = ColorFilter.tint(colors.textDarkTitle),
                modifier = Modifier
                    .size(15.dp)
                    .rotate(if (expanded) 0f else 180f)
                    .then(
                        if (testTagPrefix != null) {
                            Modifier.testTag("$testTagPrefix-chevron")
                        } else {
                            Modifier
                        },
                    ),
            )
        }
        if (expanded && content != null) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(colors.iconShape)
            )
            Column(
                Modifier.padding(
                    start = Spacing.md,
                    end = Spacing.md,
                    top = Spacing.sm,
                    bottom = Spacing.md,
                )
            ) {
                content()
            }
        }
    }
}
