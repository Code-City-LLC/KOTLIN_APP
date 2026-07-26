package com.ga.airdrop.feature.more2

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ga.airdrop.R
import com.ga.airdrop.core.designsystem.theme.AirdropTheme
import com.ga.airdrop.core.designsystem.theme.AirdropType
import com.ga.airdrop.core.designsystem.theme.Spacing

// The hardcoded TERMS_SECTIONS fallback was deleted on 2026-07-26. It was a
// verbatim copy of a DIFFERENT product's legal text — it named "the etoy app"
// four times and described a toy-swapping platform — and it rendered silently
// under AirDrop's own header whenever GET /content/terms-conditions failed,
// indistinguishable from a successful load. A client must not author a legal
// document. The screen now shows the live document or an honest error.

@Composable
fun TermsScreen(
    onBack: () -> Unit,
    viewModel: TermsViewModel = viewModel(),
) {
    val colors = AirdropTheme.colors
    val state by viewModel.state.collectAsState()

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.gray100)
    ) {
        More2InnerHeader(title = "Terms & Conditions", onBack = onBack)

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.md),
        ) {
            Spacer(Modifier.height(Spacing.md))
            Text(
                text = "Please Read Carefully Prior To Using This Website/Service",
                style = AirdropType.body3,
                color = colors.textDescription,
                modifier = Modifier.padding(bottom = Spacing.xs),
            )
            Spacer(Modifier.height(Spacing.sm))

            val live = state.liveContent
            when {
                live != null -> More2OuterCard {
                    Column(Modifier.padding(Spacing.md)) {
                        LegalHtmlContent(live)
                    }
                }

                state.loading -> Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = Spacing.xl),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = colors.orangeMain)
                }

                else -> LegalLoadFailed(
                    message = state.error ?: "We couldn't load the Terms & Conditions.",
                    onRetry = viewModel::load,
                )
            }
            Spacer(Modifier.height(Spacing.lg))
        }
    }
}
