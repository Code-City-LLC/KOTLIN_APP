package com.ga.airdrop.feature.contacts

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.ga.airdrop.R
import com.ga.airdrop.core.designsystem.components.AirdropHeader
import com.ga.airdrop.core.designsystem.components.AirdropHeaderStyle
import com.ga.airdrop.core.designsystem.theme.AirdropTheme
import com.ga.airdrop.core.designsystem.theme.AirdropType
import com.ga.airdrop.core.designsystem.theme.Spacing
import com.ga.airdrop.core.navigation.Routes
import kotlinx.coroutines.delay

/**
 * Help/Contacts tab — Figma node 40001617:20377 (visual truth), behavior from
 * FigmaContactsViewController + RN ContactsView.
 *
 * Figma layout, top-to-bottom (first card 20 below the 106 header, 10 gap
 * between cards, all cards gray100 / radius 15 / 1dp iconShape border):
 *  - "Live Chat" Card Page row (59 tall, px 20 / py 10) → LiveAgentChatView;
 *  - Component 36: Contact Number + WhatsApp + Email grouped in ONE card
 *    (padding 20; each sub-section = 24 duotone icon + column[Title2 title,
 *    SubTitle2 value rows with trailing copy], pb 10);
 *  - Component 37 x3: Location (3 addresses), Business Hours (multiline +
 *    copy), Social Media (4 handle rows).
 * Swift is the implementation-precedence guide when it conflicts with Figma:
 * Figma labels values as SubTitle2, but FigmaContactsViewController renders
 * value rows, business hours, and social rows with Typography.subtitle1().
 */
@Composable
fun ContactsScreen(
    onNavigate: (String) -> Unit,
) {
    val colors = AirdropTheme.colors
    val headerInfo by com.ga.airdrop.core.session.SessionStore.header.collectAsState()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var showCopiedToast by remember { mutableStateOf(false) }

    fun copy(value: String) {
        clipboard.setText(AnnotatedString(value))
        showCopiedToast = true
    }

    fun open(uri: String) {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(uri))) }
    }

    if (showCopiedToast) {
        LaunchedEffect(showCopiedToast) {
            delay(2000)
            showCopiedToast = false
        }
    }

    Box(Modifier.fillMaxSize().background(colors.gray200)) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            // Figma: first card 126 below top (header 106 + 20).
            Spacer(Modifier.height(126.dp))
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.md),
                // Figma: 10 between cards (y 20→89→393→713→871).
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                // ── Live Chat row — Figma "Card Page" 40001617:20379 ──
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(59.dp)
                        .clip(RoundedCornerShape(Spacing.sm1))
                        .background(colors.gray100)
                        .border(1.dp, colors.iconShape, RoundedCornerShape(Spacing.sm1))
                        .clickable { onNavigate(Routes.LIVE_CHAT) }
                        .padding(horizontal = Spacing.md, vertical = Spacing.sm),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Image(
                            painter = painterResource(
                                if (colors.isDark) R.drawable.ic_contacts_chat_dark else R.drawable.ic_chat
                            ),
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                        )
                        Text("Live Chat", style = AirdropType.subtitle1, color = colors.textDarkTitle)
                    }
                    Image(
                        painter = painterResource(R.drawable.ic_small_arrow_down),
                        contentDescription = null,
                        colorFilter = ColorFilter.tint(colors.textDarkTitle),
                        modifier = Modifier
                            .size(24.dp)
                            .rotate(-90f),
                    )
                }

                // ── Component 36: Contact Number / WhatsApp / Email ──
                ContactCard {
                    SubSection(
                        iconRes = R.drawable.ic_contact_number,
                        darkIconRes = R.drawable.ic_contacts_contact_number_dark,
                        title = "Contact Number",
                    ) {
                        ValueRow("+876-676-6999", onOpen = { open("tel:+8766766999") }, onCopy = ::copy)
                        ValueRow("+833-676-6999", onOpen = { open("tel:+8336766999") }, onCopy = ::copy)
                    }
                    SubSection(
                        iconRes = R.drawable.ic_whatsapp,
                        darkIconRes = R.drawable.ic_contacts_whatsapp_dark,
                        title = "WhatsApp",
                    ) {
                        ValueRow("+876-566-9339", onOpen = { open("https://wa.me/8765669339") }, onCopy = ::copy)
                    }
                    SubSection(
                        iconRes = R.drawable.ic_mail,
                        darkIconRes = R.drawable.ic_contacts_mail_dark,
                        title = "Email Address",
                        last = true,
                    ) {
                        ValueRow(
                            "support@airdropja.com",
                            onOpen = { open("mailto:support@airdropja.com") },
                            onCopy = ::copy,
                        )
                    }
                }

                // ── Component 37: Location ──
                ContactCard {
                    SubSection(
                        iconRes = R.drawable.ic_location,
                        darkIconRes = R.drawable.ic_contacts_location_dark,
                        title = "Location",
                        last = true,
                    ) {
                        val addresses = listOf(
                            "Unit #1, Toma Place, 9-11 Phoenix Avenue, Kingston 10",
                            "Unit #14, Annex Complex, Fairview Shopping Center, Montego Bay",
                            "Unit 8, Beckford Plaza, 33-35 Beckford Street, Savanna La Mar, Westmoreland",
                        )
                        addresses.forEachIndexed { index, address ->
                            ValueRow(
                                address,
                                onOpen = { open("geo:0,0?q=${Uri.encode(address)}") },
                                onCopy = ::copy,
                            )
                            if (index != addresses.lastIndex) RowDivider()
                        }
                    }
                }

                // ── Component 37: Business Hours (Figma copy + trailing copy
                // button that copies the whole block — 40001617:20382) ──
                val hoursText = "Monday - Friday: 9 AM - 6 PM\nSaturday: 10 AM - 4 PM\nSunday: Closed"
                ContactCard {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Row(
                            Modifier.weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                        ) {
                            Image(
                                painter = painterResource(
                                    if (colors.isDark) R.drawable.ic_contacts_clock_dark else R.drawable.ic_clock
                                ),
                                contentDescription = null,
                                modifier = Modifier.size(24.dp),
                            )
                            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                                Text("Business Hours", style = AirdropType.title2, color = colors.textDarkTitle)
                                Text(
                                    text = hoursText,
                                    style = AirdropType.subtitle1,
                                    color = colors.iconSelected,
                                )
                            }
                        }
                        CopyButton(hoursText, ::copy)
                    }
                }

                // ── Component 37: Social Media ──
                ContactCard {
                    SubSection(
                        iconRes = R.drawable.ic_social_media,
                        darkIconRes = R.drawable.ic_contacts_social_media_dark,
                        title = "Social Media",
                        last = true,
                    ) {
                        val socials = listOf(
                            SocialEntry(
                                iconRes = R.drawable.ic_instagram,
                                darkIconRes = R.drawable.ic_contacts_instagram_dark,
                                title = "Instagram: ",
                                handle = "@airdrop.ja",
                                url = "https://www.instagram.com/airdrop.ja/",
                            ),
                            SocialEntry(
                                iconRes = R.drawable.ic_facebook,
                                darkIconRes = R.drawable.ic_contacts_facebook_dark,
                                title = "Facebook: ",
                                handle = "@airdrop.jamaica",
                                url = "https://www.facebook.com/airdropja-2235323533226290/",
                            ),
                            SocialEntry(
                                iconRes = R.drawable.ic_x,
                                darkIconRes = R.drawable.ic_contacts_x_dark,
                                title = "X: ",
                                handle = "@airdropja",
                                url = "https://twitter.com/airdropja",
                            ),
                            SocialEntry(
                                iconRes = R.drawable.ic_tic_tok,
                                darkIconRes = R.drawable.ic_contacts_tic_tok_dark,
                                title = "Tiktok: ",
                                handle = "@airdropja",
                                url = "https://www.tiktok.com/@airdropja",
                            ),
                        )
                        socials.forEachIndexed { index, social ->
                            SocialRow(entry = social, onOpen = { open(social.url) }, onCopy = ::copy)
                            if (index != socials.lastIndex) RowDivider()
                        }
                    }
                }

                // Tail clears the tab bar (Swift :137-139).
                Spacer(Modifier.height(130.dp))
            }
        }

        AirdropHeader(
            greeting = listOf(headerInfo.greeting, headerInfo.firstName)
                .filter { it.isNotBlank() }
                .joinToString(" "),
            tierName = headerInfo.tierName,
            style = AirdropHeaderStyle.Solid,
            cartCount = headerInfo.cartCount,
            airCoins = headerInfo.airCoins,
            onTierClick = { onNavigate(Routes.GOLD_PRIORITY) },
            onBellClick = { onNavigate(Routes.NOTIFICATIONS) },
            onCartClick = { onNavigate(Routes.CART) },
            onAirCoinsClick = { onNavigate(Routes.AIRCOIN_HISTORY) },
            modifier = Modifier.align(Alignment.TopCenter),
        )

        // "All the information is copied" toast — Swift :536-556.
        if (showCopiedToast) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 52.dp)
                    .height(40.dp)
                    .background(colors.textDarkTitle.copy(alpha = 0.92f), RoundedCornerShape(20.dp))
                    .padding(horizontal = 18.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "All the information is copied",
                    style = AirdropType.subtitle2,
                    color = colors.gray100,
                )
            }
        }
    }
}

private data class SocialEntry(
    val iconRes: Int,
    val darkIconRes: Int,
    val title: String,
    val handle: String,
    val url: String,
)

/** Outer card — Figma: gray100, radius 15, 1dp iconShape border, padding 20. */
@Composable
private fun ContactCard(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    val colors = AirdropTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Spacing.sm1))
            .background(colors.gray100)
            .border(1.dp, colors.iconShape, RoundedCornerShape(Spacing.sm1))
            .padding(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        content = content,
    )
}

/**
 * Figma sub-section: 24 duotone icon (untinted — orange + night-aware dark
 * tones baked into the vector) + column [Title2 title, rows...] gap 10;
 * pb 10 except on the last sub-section.
 */
@Composable
private fun SubSection(
    iconRes: Int,
    darkIconRes: Int,
    title: String,
    last: Boolean = false,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    val colors = AirdropTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = if (last) 0.dp else Spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Image(
            painter = painterResource(if (colors.isDark) darkIconRes else iconRes),
            contentDescription = null,
            modifier = Modifier.size(24.dp),
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Text(title, style = AirdropType.title2, color = colors.textDarkTitle)
            content()
        }
    }
}

/** Divider between rows inside a card (iconShape, 1dp). */
@Composable
private fun RowDivider() {
    Box(
        Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(AirdropTheme.colors.iconShape)
    )
}

@Composable
private fun CopyButton(value: String, onCopy: (String) -> Unit) {
    val colors = AirdropTheme.colors
    Image(
        painter = painterResource(R.drawable.ic_copy),
        contentDescription = "Copy",
        colorFilter = ColorFilter.tint(colors.iconSelected),
        modifier = Modifier
            .size(24.dp)
            .clickable { onCopy(value) },
    )
}

/** Value row — Swift: subtitle1 value + trailing 24 copy. */
@Composable
private fun ValueRow(text: String, onOpen: (() -> Unit)?, onCopy: (String) -> Unit) {
    val colors = AirdropTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            style = AirdropType.subtitle1,
            color = colors.iconSelected,
            modifier = Modifier
                .weight(1f)
                .then(if (onOpen != null) Modifier.clickable(onClick = onOpen) else Modifier),
        )
        CopyButton(text, onCopy)
    }
}

/** Social row — icon + "Title: @handle" subtitle1; copy copies the handle. */
@Composable
private fun SocialRow(entry: SocialEntry, onOpen: () -> Unit, onCopy: (String) -> Unit) {
    val colors = AirdropTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onOpen),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                painter = painterResource(if (colors.isDark) entry.darkIconRes else entry.iconRes),
                contentDescription = null,
                modifier = Modifier.size(24.dp),
            )
            Text(
                text = entry.title + entry.handle,
                style = AirdropType.subtitle1,
                color = colors.iconSelected,
            )
        }
        CopyButton(entry.handle, onCopy)
    }
}
