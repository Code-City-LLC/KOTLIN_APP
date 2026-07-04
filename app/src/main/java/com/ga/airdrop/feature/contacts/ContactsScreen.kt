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
import androidx.compose.ui.graphics.Color
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
import com.ga.airdrop.core.designsystem.theme.Radius
import com.ga.airdrop.core.designsystem.theme.Spacing
import com.ga.airdrop.core.navigation.Routes
import kotlinx.coroutines.delay

/**
 * Help/Contacts tab — Figma node 40001617:20377, behavior from
 * FigmaContactsViewController: Live Chat row, contact/WhatsApp/email cards
 * with copy + open actions, locations, business hours, social links, and
 * the "information is copied" toast pill.
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
            Spacer(Modifier.height(130.dp)) // clearance under the solid header
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                LiveChatRow(onClick = { /* Live chat via AutoPilot — same as Swift openChat */ })
                ContactCard(
                    entries = listOf(
                        ContactEntry(
                            iconRes = R.drawable.ic_contact_number,
                            title = "Contact Number",
                            rows = listOf(
                                ContactRow("+876-676-6999", onOpen = { open("tel:+8766766999") }),
                            ),
                        ),
                        ContactEntry(
                            iconRes = R.drawable.ic_whatsapp,
                            title = "WhatsApp",
                            rows = listOf(
                                ContactRow("+876-566-9339", onOpen = { open("https://wa.me/8765669339") }),
                            ),
                        ),
                        ContactEntry(
                            iconRes = R.drawable.ic_message,
                            title = "Email Address",
                            rows = listOf(
                                ContactRow("support@airdropja.com", onOpen = { open("mailto:support@airdropja.com") }),
                            ),
                        ),
                    ),
                    onCopy = ::copy,
                )
                SectionListCard(
                    iconRes = R.drawable.ic_location,
                    title = "Location",
                    rows = listOf(
                        "Unit #1, Toma Place, 9-11 Phoenix Avenue, Kingston 10",
                        "Unit #14, Annex Complex, Fairview Shopping Center, Montego Bay",
                        "Unit 8, Beckford Plaza, 33-35 Beckford Street, Savanna La Mar, Westmoreland",
                    ),
                    onCopy = ::copy,
                )
                BusinessHoursCard(onCopy = ::copy)
                SocialMediaCard(onCopy = ::copy, onOpen = ::open)
                Spacer(Modifier.height(90.dp))
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

        // "All the information is copied" pill — Figma 40001617:20650.
        if (showCopiedToast) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 126.dp)
                    .background(Color(0xCC292929), RoundedCornerShape(60.dp))
                    .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
            ) {
                Text(
                    text = "All the information is copied",
                    style = AirdropType.body2,
                    color = Color.White,
                )
            }
        }
    }
}

private data class ContactRow(val value: String, val onOpen: () -> Unit)
private data class ContactEntry(val iconRes: Int, val title: String, val rows: List<ContactRow>)

@Composable
private fun CardContainer(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    val colors = AirdropTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.s))
            .background(colors.gray100)
            .border(1.dp, colors.iconShape, RoundedCornerShape(Radius.s))
            .padding(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        content = content,
    )
}

@Composable
private fun LiveChatRow(onClick: () -> Unit) {
    val colors = AirdropTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(59.dp)
            .clip(RoundedCornerShape(Radius.s))
            .background(colors.gray100)
            .border(1.dp, colors.iconShape, RoundedCornerShape(Radius.s))
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                painter = painterResource(R.drawable.ic_chat),
                contentDescription = null,
                colorFilter = ColorFilter.tint(colors.iconSelected),
                modifier = Modifier.size(24.dp),
            )
            Text("Live Chat", style = AirdropType.subtitle1, color = colors.textDarkTitle)
        }
        Image(
            painter = painterResource(R.drawable.ic_small_arrow_down),
            contentDescription = null,
            colorFilter = ColorFilter.tint(colors.iconSelected),
            modifier = Modifier.size(24.dp).rotate(-90f),
        )
    }
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

@Composable
private fun ContactCard(entries: List<ContactEntry>, onCopy: (String) -> Unit) {
    val colors = AirdropTheme.colors
    CardContainer {
        entries.forEach { entry ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = Spacing.sm),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                Image(
                    painter = painterResource(entry.iconRes),
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(colors.iconSelected),
                    modifier = Modifier.size(24.dp),
                )
                Column(
                    Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    Text(entry.title, style = AirdropType.title2, color = colors.textDarkTitle)
                    entry.rows.forEach { row ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = row.value,
                                style = AirdropType.subtitle2,
                                color = colors.textDarkTitle,
                                modifier = Modifier.clickable(onClick = row.onOpen),
                            )
                            CopyButton(row.value, onCopy)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionListCard(
    iconRes: Int,
    title: String,
    rows: List<String>,
    onCopy: (String) -> Unit,
) {
    val colors = AirdropTheme.colors
    CardContainer {
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
        Column(Modifier.fillMaxWidth()) {
            rows.forEachIndexed { index, address ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = Spacing.sm1),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = address,
                        style = AirdropType.subtitle2,
                        color = colors.textDarkTitle,
                        modifier = Modifier.weight(1f),
                    )
                    CopyButton(address, onCopy)
                }
                if (index != rows.lastIndex) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(colors.divider)
                    )
                }
            }
        }
    }
}

@Composable
private fun BusinessHoursCard(onCopy: (String) -> Unit) {
    val colors = AirdropTheme.colors
    val hours = "Monday-Friday: 9am-6pm\nSaturday: 10am-4pm\nSunday: Closed"
    CardContainer {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Image(
                painter = painterResource(R.drawable.ic_clock),
                contentDescription = null,
                colorFilter = ColorFilter.tint(colors.iconSelected),
                modifier = Modifier.size(24.dp),
            )
            Column(
                Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                Text("Business Hours", style = AirdropType.title2, color = colors.textDarkTitle)
                Text(hours, style = AirdropType.subtitle2, color = colors.textDarkTitle)
            }
            CopyButton(hours, onCopy)
        }
    }
}

private data class SocialEntry(val iconRes: Int, val label: String, val url: String)

@Composable
private fun SocialMediaCard(onCopy: (String) -> Unit, onOpen: (String) -> Unit) {
    val colors = AirdropTheme.colors
    val socials = listOf(
        SocialEntry(R.drawable.ic_instagram, "Instagram: @airdrop.ja", "https://www.instagram.com/airdrop.ja/"),
        SocialEntry(R.drawable.ic_facebook, "Facebook: @airdrop.jamaica", "https://www.facebook.com/airdropja-2235323533226290/"),
        SocialEntry(R.drawable.ic_x, "X: @airdropja", "https://twitter.com/airdropja"),
        SocialEntry(R.drawable.ic_tic_tok, "Tiktok: @airdropja", "https://www.tiktok.com/@airdropja"),
    )
    CardContainer {
        Row(
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                painter = painterResource(R.drawable.ic_social_media),
                contentDescription = null,
                colorFilter = ColorFilter.tint(colors.iconSelected),
                modifier = Modifier.size(24.dp),
            )
            Text("Social Media", style = AirdropType.title2, color = colors.textDarkTitle)
        }
        Column(Modifier.fillMaxWidth()) {
            socials.forEachIndexed { index, social ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = Spacing.sm1),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onOpen(social.url) },
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Image(
                            painter = painterResource(social.iconRes),
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                        )
                        Text(
                            text = social.label,
                            style = AirdropType.subtitle2,
                            color = colors.textDarkTitle,
                        )
                    }
                    CopyButton(social.label, onCopy)
                }
                if (index != socials.lastIndex) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(colors.divider)
                    )
                }
            }
        }
    }
}
