package com.ga.airdrop.feature.more2

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ga.airdrop.R
import com.ga.airdrop.core.designsystem.components.GradientButton
import com.ga.airdrop.core.designsystem.theme.AirdropTheme
import com.ga.airdrop.core.designsystem.theme.AirdropType
import com.ga.airdrop.core.designsystem.theme.Radius
import com.ga.airdrop.core.designsystem.theme.Spacing
import com.ga.airdrop.core.navigation.Routes
import com.ga.airdrop.feature.shipments.ShipmentsSearchField

/**
 * FAQs — Figma node 40001387:8896, behavior from FigmaFAQViewController:
 * a "Search the FAQs" pill that instantly filters the accordion cards
 * (question Title2 + chevron, Body2 answer), a "No matches" empty state, and a
 * "Still need help?" support footer whose Contact Support CTA opens Contacts.
 */
@Composable
fun FaqScreen(
    onBack: () -> Unit,
    onNavigate: (String) -> Unit,
    viewModel: FaqViewModel = viewModel(),
) {
    val colors = AirdropTheme.colors
    val state by viewModel.state.collectAsState()
    val visible = filterFaqs(state.faqs, state.searchQuery)

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.gray100)
    ) {
        More2InnerHeader(title = "FAQs", onBack = onBack)

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.md),
        ) {
            Spacer(Modifier.height(Spacing.md))
            ShipmentsSearchField(
                value = state.searchQuery,
                onValueChange = viewModel::onSearchChange,
                placeholder = "Search the FAQs",
                testTag = "faq-search",
            )
            Spacer(Modifier.height(Spacing.md))

            if (visible.isEmpty()) {
                FaqEmptyState(query = state.searchQuery)
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    visible.forEach { faq ->
                        AccordionCard(
                            title = faq.question,
                            expanded = faq.id in state.expandedIds,
                            onToggle = { viewModel.toggle(faq.id) },
                            titleEndGap = Spacing.sm,
                            testTagPrefix = "faq-${faq.id}",
                        ) {
                            Text(
                                text = faq.answer,
                                style = AirdropType.body2,
                                color = colors.textDescription,
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(Spacing.md))
            FaqSupportFooter(onContact = { onNavigate(Routes.CONTACTS) })
            Spacer(Modifier.height(Spacing.lg))
        }
    }
}

/** Swift makeEmptyState — question glyph + "No matches for “query”". */
@Composable
private fun FaqEmptyState(query: String) {
    val colors = AirdropTheme.colors
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(
            painter = painterResource(R.drawable.ic_help),
            contentDescription = null,
            colorFilter = ColorFilter.tint(colors.textDescription),
            modifier = Modifier.size(40.dp),
        )
        Spacer(Modifier.height(Spacing.sm))
        Text(
            text = "No matches for “${query.trim()}”",
            style = AirdropType.title2,
            color = colors.textDarkTitle,
            textAlign = TextAlign.Center,
        )
    }
}

/** Swift makeSupportFooter — "Still need help?" card + Contact Support CTA. */
@Composable
private fun FaqSupportFooter(onContact: () -> Unit) {
    val colors = AirdropTheme.colors
    Column(
        Modifier
            .fillMaxWidth()
            .background(colors.gray100, RoundedCornerShape(Radius.s))
            .border(1.dp, colors.iconShape, RoundedCornerShape(Radius.s))
            .padding(20.dp),
    ) {
        Text(text = "Still need help?", style = AirdropType.title2, color = colors.textDarkTitle)
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Our support team is ready when you are. Reach out and we’ll get back to you fast.",
            style = AirdropType.body2,
            color = colors.textDescription,
        )
        Spacer(Modifier.height(16.dp))
        GradientButton(text = "Contact Support", onClick = onContact)
    }
}
