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

private data class TermsSubItem(val id: String, val title: String, val content: String?)
private data class TermsSection(
    val id: String,
    val number: String,
    val title: String,
    val content: String?,
    val subItems: List<TermsSubItem>,
)

// Verbatim copy of the RN `sectionsData` constant (TermsConditionsView) —
// the cold-start fallback rendered before /content/terms-conditions responds.
private val TERMS_SECTIONS = listOf(
    TermsSection(
        id = "1", number = "1",
        title = "Acceptance of Terms and Conditions",
        content = "By accessing and using the etoy app mobile platform, you acknowledge that " +
            "you have read, understood, and agree to be bound by these Terms and Conditions. " +
            "The etoy app is designed to facilitate the exchange or giving away of toys to " +
            "promote recycling, save money, reduce waste, and help declutter your home. Your " +
            "use of this service constitutes your acceptance of these terms. You must click " +
            "the “Accept” button to proceed with using the platform.",
        subItems = emptyList(),
    ),
    TermsSection(
        id = "2", number = "2",
        title = "Intellectual Property Rights",
        content = null,
        subItems = listOf(
            TermsSubItem("2.1", "Ownership", null),
            TermsSubItem(
                "2.2", "Authorized and Prohibited Uses",
                "You are granted a limited, non-exclusive, non-transferable, revocable license " +
                    "to access and use the etoy app for personal, non-commercial purposes in " +
                    "accordance with these Terms and Conditions. You may not: copy, modify, " +
                    "distribute, sell, or lease any part of our services or included software; " +
                    "reverse engineer or attempt to extract the source code of our software; use " +
                    "our services to transmit any viruses, malware, or other harmful code; " +
                    "attempt to gain unauthorized access to our services, user accounts, or " +
                    "computer systems; use automated systems or bots to access our services " +
                    "without permission; remove, obscure, or alter any proprietary notices or " +
                    "labels; or use our services for any illegal or unauthorized purpose. Any " +
                    "unauthorized use of the platform or its content may violate copyright, " +
                    "trademark, and other applicable laws and may result in criminal or civil " +
                    "penalties.",
            ),
            TermsSubItem(
                "2.3", "Registration and Passwords",
                "To access certain features of the etoy app, you may be required to register " +
                    "and create an account. During registration, you agree to provide accurate, " +
                    "current, and complete information about yourself. You are responsible for " +
                    "maintaining the confidentiality of your account credentials, including your " +
                    "username and password. You agree to accept responsibility for all " +
                    "activities that occur under your account. You must immediately notify us " +
                    "of any unauthorized use of your account or any other breach of security. " +
                    "We reserve the right to refuse service, terminate accounts, or remove or " +
                    "edit content at our sole discretion. You may not use another user’s " +
                    "account without express written permission. You are responsible for " +
                    "ensuring that your password is strong and secure, and you agree not to " +
                    "share your account credentials with any third party. We are not liable " +
                    "for any loss or damage arising from your failure to comply with these " +
                    "security obligations.",
            ),
        ),
    ),
    TermsSection(
        id = "3", number = "3",
        title = "Website and Registration Administration",
        content = null,
        subItems = listOf(
            TermsSubItem("3.1", "Website and Other Information", null),
            TermsSubItem("3.2", "Linking", null),
            TermsSubItem("3.3", "Third Party Sites and Other Information", null),
            TermsSubItem("3.4", "Authority of Website Administrator", null),
        ),
    ),
    TermsSection(
        id = "4", number = "4",
        title = "User Requirements and Obligations",
        content = null,
        subItems = listOf(
            TermsSubItem("4.1", "Compliance", null),
            TermsSubItem("4.2", "Unsuitable Conduct", null),
            TermsSubItem("4.3", "User Cooperation and Notification", null),
            TermsSubItem(
                "4.4",
                "Warranty Disclaimers, Limitations on Liability, and Remedies",
                null,
            ),
            TermsSubItem("4.5", "Indemnification", null),
        ),
    ),
)

/**
 * Terms & Conditions — Figma node 40001383:9894, behavior from
 * FigmaTermsConditionsViewController: intro line + expandable sections 1–4
 * (sub-items visible while collapsed), swapped for the live
 * /content/terms-conditions body when it loads (colors stripped, dynamic
 * theme colors applied).
 */
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
            if (live != null) {
                More2OuterCard {
                    Column(Modifier.padding(Spacing.md)) {
                        LegalHtmlContent(live)
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    TERMS_SECTIONS.forEach { section ->
                        val expanded = section.id in state.expandedIds
                        TermsSectionCard(
                            section = section,
                            expanded = expanded,
                            onToggle = { viewModel.toggle(section.id) },
                        )
                    }
                }
            }
            Spacer(Modifier.height(Spacing.lg))
        }
    }
}

@Composable
private fun TermsSectionCard(
    section: TermsSection,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    val colors = AirdropTheme.colors
    More2OuterCard {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "${section.number}. ${section.title}",
                style = AirdropType.title2,
                color = colors.textDarkTitle,
                modifier = Modifier
                    .weight(1f)
                    .padding(end = Spacing.xs),
            )
            Image(
                painter = painterResource(R.drawable.ic_chevron),
                contentDescription = null,
                colorFilter = ColorFilter.tint(colors.textDarkTitle),
                modifier = Modifier
                    .size(15.dp)
                    .rotate(if (expanded) 0f else 180f),
            )
        }

        if (expanded && !section.content.isNullOrEmpty()) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(colors.iconShape)
            )
            Text(
                text = section.content,
                style = AirdropType.body2,
                color = colors.textDescription,
                modifier = Modifier.padding(
                    start = Spacing.md,
                    end = Spacing.md,
                    top = Spacing.sm,
                    bottom = Spacing.md,
                ),
            )
        } else if (!expanded && section.subItems.isNotEmpty()) {
            // RN renders sub-items only while the parent section is collapsed.
            Column(
                Modifier.padding(
                    start = Spacing.md,
                    end = Spacing.md,
                    top = Spacing.sm,
                    bottom = Spacing.md,
                ),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                section.subItems.forEach { sub ->
                    More2OuterCard {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(Spacing.md),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = sub.title,
                                style = AirdropType.body2,
                                color = colors.textDarkTitle,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(end = Spacing.sm),
                            )
                            Image(
                                painter = painterResource(R.drawable.ic_chevron),
                                contentDescription = null,
                                colorFilter = ColorFilter.tint(colors.textDarkTitle),
                                modifier = Modifier
                                    .size(14.dp)
                                    .rotate(180f),
                            )
                        }
                    }
                }
            }
        }
    }
}
