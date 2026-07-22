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
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
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
 * Swift is the implementation-precedence guide where it conflicts with Figma:
 * the Figma node still includes a Live Chat row, groups Contact/WhatsApp/Email
 * in one card, and shows a Business Hours copy affordance. The Swift app ships
 * separate section cards, 20pt card gaps, 15pt card padding, no Live Chat row,
 * and no Business Hours copy button, so Android follows Swift for those
 * conflicts.
 */
@Composable
fun ContactsScreen(
    onNavigate: (String) -> Unit,
    openExternal: ((String) -> Boolean)? = null,
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

    fun open(uri: String): Boolean {
        return openExternal?.invoke(uri) ?: runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(uri)))
            true
        }.getOrDefault(false)
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
                // Swift contentStack.spacing = 20.
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                SectionCard(
                    modifier = Modifier.testTag("contacts-card-contact-number"),
                    iconRes = R.drawable.ic_contact_number,
                    darkIconRes = R.drawable.ic_contacts_contact_number_dark,
                    title = "Contact Number",
                ) {
                    ValueRow("+876-676-6999", onOpen = { openPhone("+876-676-6999", ::open) }, onCopy = ::copy)
                    RowDivider()
                    ValueRow("+833-676-6999", onOpen = { openPhone("+833-676-6999", ::open) }, onCopy = ::copy)
                }

                SectionCard(
                    modifier = Modifier.testTag("contacts-card-whatsapp"),
                    iconRes = R.drawable.ic_whatsapp,
                    darkIconRes = R.drawable.ic_contacts_whatsapp_dark,
                    title = "WhatsApp",
                ) {
                    ValueRow("+876-566-9339", onOpen = { openWhatsApp("+876-566-9339", ::open) }, onCopy = ::copy)
                }

                SectionCard(
                    modifier = Modifier.testTag("contacts-card-email"),
                    iconRes = R.drawable.ic_mail,
                    darkIconRes = R.drawable.ic_contacts_mail_dark,
                    title = "Email Address",
                ) {
                    ValueRow(
                        "support@airdropja.com",
                        onOpen = { openEmail("support@airdropja.com", ::open) },
                        onCopy = ::copy,
                    )
                }

                SectionCard(
                    modifier = Modifier.testTag("contacts-card-location"),
                    iconRes = R.drawable.ic_location,
                    darkIconRes = R.drawable.ic_contacts_location_dark,
                    title = "Location",
                ) {
                    val addresses = listOf(
                        "Unit 19 Pristine Plaza, 15 Eastwood Park Rd, Kingston, Jamaica",
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

                // Swift copy and no trailing copy affordance.
                val hoursText = "Monday-Friday: 9am-6pm\nSaturday: 10am-4pm\nSunday: Closed"
                SectionCard(
                    modifier = Modifier.testTag("contacts-card-business-hours"),
                    iconRes = R.drawable.ic_clock,
                    darkIconRes = R.drawable.ic_contacts_clock_dark,
                    title = "Business Hours",
                ) {
                    Text(
                        text = hoursText,
                        style = AirdropType.body1,
                        color = colors.iconSelected,
                    )
                }

                SectionCard(
                    modifier = Modifier.testTag("contacts-card-social-media"),
                    iconRes = R.drawable.ic_social_media,
                    darkIconRes = R.drawable.ic_contacts_social_media_dark,
                    title = "Social Media",
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
            hasUnreadNotifications = headerInfo.hasUnreadNotifications,
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

private fun openPhone(phone: String, open: (String) -> Boolean) {
    val digits = phone
        .filter { it == '+' || it.isDigit() }
        .replace("+", "")
    open("tel:$digits")
}

private fun openWhatsApp(phone: String, open: (String) -> Boolean) {
    val digits = phone.filter { it.isDigit() }
    val native = "whatsapp://send?phone=$digits"
    if (!open(native)) {
        open("https://wa.me/$digits")
    }
}

private fun openEmail(address: String, open: (String) -> Boolean) {
    open("mailto:$address?subject=Contact%20from%20App")
}

/** Swift section card: gray100, radius 15, 1dp iconShape border, 15dp padding. */
@Composable
private fun ContactCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = AirdropTheme.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Spacing.sm1))
            .background(colors.gray100)
            .border(1.dp, colors.iconShape, RoundedCornerShape(Spacing.sm1))
            .padding(Spacing.sm1),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        content = content,
    )
}

@Composable
private fun SectionCard(
    modifier: Modifier = Modifier,
    iconRes: Int,
    darkIconRes: Int,
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = AirdropTheme.colors
    ContactCard(modifier = modifier) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                painter = painterResource(if (colors.isDark) darkIconRes else iconRes),
                contentDescription = null,
                modifier = Modifier.size(24.dp),
            )
            Text(title, style = AirdropType.title2, color = colors.textDarkTitle)
        }
        Column(
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            modifier = Modifier.fillMaxWidth(),
            content = content,
        )
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
    Box(
        modifier = Modifier
            .size(34.dp)
            .clickable { onCopy(value) },
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.ic_copy),
            contentDescription = "Copy",
            colorFilter = ColorFilter.tint(colors.iconSelected),
            modifier = Modifier.size(24.dp),
        )
    }
}

/** Value row — Swift: subtitle1 value + trailing 24 copy. */
@Composable
private fun ValueRow(text: String, onOpen: (() -> Unit)?, onCopy: (String) -> Unit) {
    val colors = AirdropTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 38.dp),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            style = AirdropType.body1,
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
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 38.dp),
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
                text = buildAnnotatedString {
                    withStyle(
                        SpanStyle(
                            color = colors.textDarkTitle,
                            fontWeight = FontWeight.SemiBold,
                        ),
                    ) {
                        append(entry.title)
                    }
                    withStyle(SpanStyle(color = colors.iconSelected)) {
                        append(entry.handle)
                    }
                },
                style = AirdropType.body1,
            )
        }
        CopyButton(entry.handle, onCopy)
    }
}
