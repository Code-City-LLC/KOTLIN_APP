package com.ga.airdrop.feature.appupdate

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ga.airdrop.R
import com.ga.airdrop.core.designsystem.components.GradientButton
import com.ga.airdrop.feature.more.MoreDetailHeader
import com.ga.airdrop.core.designsystem.theme.AirdropTheme
import com.ga.airdrop.core.designsystem.theme.AirdropType
import com.ga.airdrop.core.designsystem.theme.Radius
import com.ga.airdrop.core.designsystem.theme.Spacing

object AppUpdateTags {
    const val ROOT = "app-update-root"
    const val HEADLINE = "app-update-headline"
    const val VERSION = "app-update-version"
    const val NOTES = "app-update-notes"
    const val UPDATE = "app-update-primary"
    const val LATER = "app-update-later"
}

/**
 * "Update Available" — the screen six routes already pointed at and which did
 * not exist.
 *
 * `NotificationsViewModel` maps `app_update`, `app_update_available`,
 * `force_update`, `AppUpdateView` and `AppUpdateScreen` to an app-update
 * destination, and until now that destination bounced straight out to the Play
 * listing. The customer got no say, no version, and no idea what changed.
 *
 * Copy and structure are **taken from iOS `FigmaAppUpdateViewController`**
 * rather than invented, so the two platforms say the same thing:
 *   header "Update Available" · headline "A new version of AirDrop is ready" ·
 *   "What's New" · primary "Update Now" · secondary "Not Now".
 *
 * ⚠️ **We show a build number, not a marketing version, and that is deliberate.**
 * Play's In-App Update API exposes `availableVersionCode()` — an integer — and
 * has no API for the *name* ("3.1.3"). iOS can print "Version 3.1.3" because the
 * iTunes lookup returns it; Android cannot. Rather than fabricate a name or
 * scrape it out of the store page, this prints the build number when we have one
 * and simply omits the line when we do not. **A wrong version string is worse
 * than no version string.**
 *
 * [releaseNotes] is likewise optional: Play does not hand release notes to the
 * client, so this is populated only when the backend push carried a description.
 * When it is null the whole "What's New" block is omitted rather than rendered
 * empty.
 */
@Composable
fun AppUpdateScreen(
    availableVersionCode: Int?,
    installedVersionName: String,
    releaseNotes: String?,
    onUpdate: () -> Unit,
    onLater: () -> Unit,
    onBack: () -> Unit,
) {
    val colors = AirdropTheme.colors
    Column(
        Modifier
            .fillMaxSize()
            .background(colors.gray200)
            .testTag(AppUpdateTags.ROOT),
    ) {
        MoreDetailHeader(title = "Update Available", onBack = onBack)

        Column(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.md),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(Spacing.lg))

            // The real launcher icon, so the customer recognises WHICH app is
            // being updated — same choice iOS made.
            Image(
                painter = painterResource(R.mipmap.ic_launcher),
                contentDescription = null,
                modifier = Modifier
                    .size(88.dp)
                    .clip(RoundedCornerShape(Radius.m)),
            )

            Spacer(Modifier.height(Spacing.md))

            Text(
                text = "A new version of AirDrop is ready",
                style = AirdropType.title1,
                color = colors.textDarkTitle,
                textAlign = TextAlign.Center,
                modifier = Modifier.testTag(AppUpdateTags.HEADLINE),
            )

            if (availableVersionCode != null) {
                Spacer(Modifier.height(Spacing.xs))
                Text(
                    text = "Build $availableVersionCode — you have $installedVersionName",
                    style = AirdropType.body2,
                    color = colors.textDescription,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.testTag(AppUpdateTags.VERSION),
                )
            }

            if (!releaseNotes.isNullOrBlank()) {
                Spacer(Modifier.height(Spacing.lg))
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(colors.gray100, RoundedCornerShape(Radius.s))
                        .padding(Spacing.md)
                        .testTag(AppUpdateTags.NOTES),
                ) {
                    Text(
                        text = "What's New",
                        style = AirdropType.subtitle2,
                        color = colors.textDarkTitle,
                    )
                    Spacer(Modifier.height(Spacing.xs))
                    Text(
                        text = releaseNotes,
                        style = AirdropType.body2,
                        color = colors.textDescription,
                    )
                }
            }

            Spacer(Modifier.height(Spacing.lg))
        }

        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.md)
                .padding(bottom = Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            GradientButton(
                text = "Update Now",
                onClick = onUpdate,
                modifier = Modifier.testTag(AppUpdateTags.UPDATE),
            )
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .clickable(onClick = onLater)
                    .testTag(AppUpdateTags.LATER),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Not Now",
                    style = AirdropType.subtitle2,
                    color = colors.textDescription,
                    modifier = Modifier,
                )
            }
        }
    }
}
