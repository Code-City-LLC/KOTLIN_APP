package com.ga.airdrop.feature.more2

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ga.airdrop.core.designsystem.theme.AirdropTheme
import com.ga.airdrop.core.designsystem.theme.AirdropType
import com.ga.airdrop.core.designsystem.theme.Spacing

/**
 * FAQs — Figma node 40001387:8896, behavior from FigmaFAQViewController:
 * accordion cards (question Title2 + chevron, Body2 answer under a divider),
 * fallback list swapped for GET /faqs.
 */
@Composable
fun FaqScreen(
    onBack: () -> Unit,
    viewModel: FaqViewModel = viewModel(),
) {
    val colors = AirdropTheme.colors
    val state by viewModel.state.collectAsState()

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
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                state.faqs.forEach { faq ->
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
            Spacer(Modifier.height(Spacing.lg))
        }
    }
}
