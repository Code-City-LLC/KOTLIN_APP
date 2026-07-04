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
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.ga.airdrop.R
import com.ga.airdrop.core.designsystem.components.AirdropHeader
import com.ga.airdrop.core.designsystem.components.AirdropHeaderStyle
import com.ga.airdrop.core.designsystem.theme.AirdropTheme
import com.ga.airdrop.core.designsystem.theme.AirdropType
import com.ga.airdrop.core.designsystem.theme.Radius
import com.ga.airdrop.core.designsystem.theme.Spacing
import com.ga.airdrop.core.navigation.Routes
import kotlinx.coroutines.delay

/**
 * Help/Contacts tab — FigmaContactsViewController parity: six section cards
 * (Contact Number, WhatsApp, Email, Location, Business Hours, Social Media)
 * with copy + open actions and the "information is copied" toast.
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
            // Swift FigmaContactsViewController.swift:114 — first card 126 below top.
            Spacer(Modifier.height(126.dp))
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.md),
                // Swift FigmaContactsViewController.swift:85 — 20 between cards.
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                // Swift FigmaContactsViewController.swift:354-367 — two phone
                // rows separated by a divider.
                SectionCard(iconRes = R.drawable.ic_contact_number, title = "Contact Number") {
                    ValueRow(text = "+876-676-6999", onOpen = { open("tel:+8766766999") }, onCopy = ::copy)
                    RowDivider()
                    ValueRow(text = "+833-676-6999", onOpen = { open("tel:+8336766999") }, onCopy = ::copy)
                }
                // Swift :371-381.
                SectionCard(iconRes = R.drawable.ic_whatsapp, title = "WhatsApp") {
                    ValueRow(text = "+876-566-9339", onOpen = { open("https://wa.me/8765669339") }, onCopy = ::copy)
                }
                // Swift :385-395.
                SectionCard(iconRes = R.drawable.ic_message, title = "Email Address") {
                    ValueRow(
                        text = "support@airdropja.com",
                        onOpen = { open("mailto:support@airdropja.com") },
                        onCopy = ::copy,
                    )
                }
                // Swift :399-422 — three addresses with dividers.
                SectionCard(iconRes = R.drawable.ic_location, title = "Location") {
                    val addresses = listOf(
                        "Unit #1, Toma Place, 9-11 Phoenix Avenue, Kingston 10",
                        "Unit #14, Annex Complex, Fairview Shopping Center, Montego Bay",
                        "Unit 8, Beckford Plaza, 33-35 Beckford Street, Savanna La Mar, Westmoreland",
                    )
                    addresses.forEachIndexed { index, address ->
                        ValueRow(text = address, onOpen = null, onCopy = ::copy)
                        if (index != addresses.lastIndex) RowDivider()
                    }
                }
                // Swift :426-434 — plain multiline block, no copy affordance.
                SectionCard(iconRes = R.drawable.ic_clock, title = "Business Hours") {
                    Text(
                        text = "Monday-Friday: 9am-6pm\nSaturday: 10am-4pm\nSunday: Closed",
                        style = AirdropType.subtitle1,
                        color = colors.iconSelected,
                    )
                }
                // Swift :438-480 — icon + "Title: " (dark) + handle (iconSelected),
                // copy copies the handle only.
                SectionCard(iconRes = R.drawable.ic_social_media, title = "Social Media") {
                    val socials = listOf(
                        SocialEntry(R.drawable.ic_instagram, "Instagram: ", "@airdrop.ja", "https://www.instagram.com/airdrop.ja/"),
                        SocialEntry(R.drawable.ic_facebook, "Facebook: ", "@airdrop.jamaica", "https://www.facebook.com/airdropja-2235323533226290/"),
                        SocialEntry(R.drawable.ic_x, "X: ", "@airdropja", "https://twitter.com/airdropja"),
                        SocialEntry(R.drawable.ic_tic_tok, "Tiktok: ", "@airdropja", "https://www.tiktok.com/@airdropja"),
                    )
                    socials.forEachIndexed { index, social ->
                        SocialRow(entry = social, onOpen = { open(social.url) }, onCopy = ::copy)
                        if (index != socials.lastIndex) RowDivider()
                    }
                }
                // Swift :137-139 — 130 tail clears the floating tab bar.
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

        // "All the information is copied" toast — Swift
        // FigmaContactsViewController.swift:536-556: textDarkTitle @ 0.92,
        // radius 20, SubTitle2 white label, 52 below the safe area.
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
                    color = androidx.compose.ui.graphics.Color.White,
                )
            }
        }
    }
}

private data class SocialEntry(
    val iconRes: Int,
    val title: String,
    val handle: String,
    val url: String,
)

/**
 * Outer rounded card with title row — Swift FigmaContactsViewController.swift:145-214:
 * gray100 fill, radius 15, 1dp iconShape border, 15dp padding, 10dp row spacing,
 * icon 24 + Title2 header.
 */
@Composable
private fun SectionCard(
    iconRes: Int,
    title: String,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    val colors = AirdropTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.s))
            .background(colors.gray100)
            .border(1.dp, colors.iconShape, RoundedCornerShape(Radius.s))
            .padding(Spacing.sm1),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                painter = painterResource(iconRes),
                contentDescription = null,
                colorFilter = ColorFilter.tint(colors.iconSelected),
                modifier = Modifier.size(24.dp),
            )
            Text(title, style = AirdropType.title2, color = colors.textDarkTitle)
        }
        content()
    }
}

/** Divider between rows inside a card — Swift :177-183 (iconShape, 1pt). */
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

/** Value row — Swift :240-259: SubTitle1 in iconSelected + trailing copy. */
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
                .then(if (onOpen != null) Modifier.clickable(onClick = onOpen) else Modifier)
                .padding(vertical = 6.dp),
        )
        CopyButton(text, onCopy)
    }
}

/**
 * Social row — Swift :244-253 combined label ("Instagram: " textDarkTitle +
 * "@handle" iconSelected, both SubTitle1); copy copies the handle (:472).
 */
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
                .clickable(onClick = onOpen)
                .padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                painter = painterResource(entry.iconRes),
                contentDescription = null,
                modifier = Modifier.size(24.dp),
            )
            Text(
                text = buildAnnotatedString {
                    withStyle(AirdropType.subtitle1.toSpanStyle().copy(color = colors.textDarkTitle)) {
                        append(entry.title)
                    }
                    withStyle(AirdropType.subtitle1.toSpanStyle().copy(color = colors.iconSelected)) {
                        append(entry.handle)
                    }
                },
                style = AirdropType.subtitle1,
            )
        }
        CopyButton(entry.handle, onCopy)
    }
}
