package com.ga.airdrop.feature.more

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ga.airdrop.core.designsystem.theme.AirdropTheme
import com.ga.airdrop.core.designsystem.theme.Spacing
import com.ga.airdrop.core.designsystem.theme.ThemeController
import com.ga.airdrop.core.session.FakeAuthenticatedSessionBoundary
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PreferencesParityScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun preferencesSelectFieldsUseSwiftGeometryLight() {
        setPreferencesRows(mode = ThemeController.Mode.LIGHT)

        assertSwiftSelectFieldGeometry()
        assertNoRequiredAsterisksLikeSwift()
        saveRootScreenshot("preferences_select_field_swift_light.png")
    }

    @Test
    fun preferencesSelectFieldsUseSwiftGeometryDark() {
        setPreferencesRows(mode = ThemeController.Mode.DARK)

        assertSwiftSelectFieldGeometry()
        assertNoRequiredAsterisksLikeSwift()
        saveRootScreenshot("preferences_select_field_swift_dark.png")
    }

    @Test
    fun selectablePreferencesRowsRemainClickableLikeSwiftActions() {
        val pickupClicks = AtomicInteger()
        val currencyClicks = AtomicInteger()
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            ThemeController.set(ThemeController.Mode.LIGHT)
        }
        compose.setContent {
            AirdropTheme {
                Column(
                    modifier = Modifier
                        .width(335.dp)
                        .background(AirdropTheme.colors.gray200),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm1),
                ) {
                    MoreSelectField(
                        label = "Set Pickup Location",
                        value = "Montego Bay",
                        placeholder = "Select a pickup location",
                        onClick = { pickupClicks.incrementAndGet() },
                        testTagPrefix = "preferences-pickup",
                    )
                    MoreSelectField(
                        label = "Set Default Currency",
                        value = "USD",
                        placeholder = "Select a payment currency",
                        onClick = { currencyClicks.incrementAndGet() },
                        testTagPrefix = "preferences-currency",
                    )
                }
            }
        }

        compose.onNodeWithTag("preferences-pickup-card").performClick()
        compose.onNodeWithTag("preferences-currency-card").performClick()

        assertEquals(1, pickupClicks.get())
        assertEquals(1, currencyClicks.get())
    }

    @Test
    fun preferencesSaveMirrorsSwiftDefaultsAndSparsePayload() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.getSharedPreferences(PreferencesViewModel.PREFS, android.content.Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        val repository = RecordingMoreProfileRepository(
            user = MoreUser(
                id = 91,
                email = "swift-user@example.com",
                pickupLocation = "",
                paymentCurrency = "",
            ),
        )
        val holder = AtomicReference<PreferencesViewModel>()

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            holder.set(
                PreferencesViewModel(
                    repository,
                    FakeAuthenticatedSessionBoundary(initialAccountId = 91),
                )
                    .also { it.start(context) },
            )
        }
        val viewModel = holder.get()

        compose.waitUntil(timeoutMillis = 5_000) {
            repository.currentUserCalls.get() == 1 &&
                viewModel.state.value.email == "swift-user@example.com"
        }

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            viewModel.applyPickup(context, "Kingston")
            viewModel.applyCurrency(context, "USD")
            viewModel.save()
        }

        compose.waitUntil(timeoutMillis = 5_000) {
            repository.updateProfileCalls.get() == 1 && !viewModel.state.value.saving
        }

        val prefs = context.getSharedPreferences(
            PreferencesViewModel.PREFS,
            android.content.Context.MODE_PRIVATE,
        )
        assertEquals(
            "Kingston",
            prefs.getString(PreferencesViewModel.KEY_PICKUP, null),
        )
        assertEquals(
            "USD",
            prefs.getString(PreferencesViewModel.KEY_CURRENCY, null),
        )

        val fields = repository.lastProfileUpdate.get().orEmpty()
        assertEquals("91", fields["user_id"])
        assertEquals("swift-user@example.com", fields["email"])
        assertEquals("Kingston", fields["pickup_location"])
        assertEquals("USD", fields["payment_currency"])
        assertEquals(
            "Preferences should keep Swift sparse ProfileUpdateRequest scope",
            setOf("user_id", "email", "pickup_location", "payment_currency"),
            fields.keys,
        )
        assertEquals("Success" to "Preferences updated successfully", viewModel.state.value.alert)
    }

    private fun setPreferencesRows(mode: ThemeController.Mode) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            ThemeController.set(mode)
        }
        compose.setContent {
            AirdropTheme {
                Column(
                    modifier = Modifier
                        .width(335.dp)
                        .background(AirdropTheme.colors.gray200),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm1),
                ) {
                    MoreSelectField(
                        label = "Email Address",
                        value = "Chasecash@focus.com",
                        placeholder = "your.email@example.com",
                        required = false,
                        enabled = false,
                        onClick = null,
                        trailingIconRes = null,
                        testTagPrefix = "preferences-email",
                    )
                    MoreSelectField(
                        label = "Set Pickup Location",
                        value = "Montego Bay",
                        placeholder = "Select a pickup location",
                        required = false,
                        onClick = {},
                        testTagPrefix = "preferences-pickup",
                    )
                    MoreSelectField(
                        label = "Set Default Currency",
                        value = "USD",
                        placeholder = "Select a payment currency",
                        required = false,
                        onClick = {},
                        testTagPrefix = "preferences-currency",
                    )
                }
            }
        }
        compose.waitForIdle()
    }

    private fun assertSwiftSelectFieldGeometry() {
        val emailCard = compose.onNodeWithTag("preferences-email-card")
            .getUnclippedBoundsInRoot()
        val pickupCard = compose.onNodeWithTag("preferences-pickup-card")
            .getUnclippedBoundsInRoot()
        val currencyCard = compose.onNodeWithTag("preferences-currency-card")
            .getUnclippedBoundsInRoot()
        val pickupChevron = compose.onNodeWithTag(
            "preferences-pickup-chevron",
            useUnmergedTree = true,
        )
            .getUnclippedBoundsInRoot()
        val currencyChevron = compose.onNodeWithTag(
            "preferences-currency-chevron",
            useUnmergedTree = true,
        )
            .getUnclippedBoundsInRoot()

        assertClose(335f, boundsWidth(emailCard), "Email field width")
        assertClose(335f, boundsWidth(pickupCard), "Pickup field width")
        assertClose(335f, boundsWidth(currencyCard), "Currency field width")
        assertClose(52f, boundsHeight(emailCard), "Email field height")
        assertClose(52f, boundsHeight(pickupCard), "Pickup field height")
        assertClose(52f, boundsHeight(currencyCard), "Currency field height")
        assertClose(12f, boundsWidth(pickupChevron), "Pickup chevron width")
        assertClose(12f, boundsHeight(pickupChevron), "Pickup chevron height")
        assertClose(12f, boundsWidth(currencyChevron), "Currency chevron width")
        assertClose(12f, boundsHeight(currencyChevron), "Currency chevron height")
        assertEquals(
            "Disabled email row should not render a chevron",
            0,
            compose.onAllNodesWithTag(
                "preferences-email-chevron",
                useUnmergedTree = true,
            ).fetchSemanticsNodes().size,
        )
    }

    private fun assertNoRequiredAsterisksLikeSwift() {
        assertEquals(0, compose.onAllNodesWithText("*").fetchSemanticsNodes().size)
    }

    private fun saveRootScreenshot(filename: String) {
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        val output = File(screenshotDir(), filename)
        FileOutputStream(output).use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    private fun screenshotDir(): File {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        return File(context.getExternalFilesDir(null), "screenshots").also { it.mkdirs() }
    }

    private fun assertClose(expected: Float, actual: Float, label: String) {
        assertEquals(label, expected, actual, 0.75f)
    }

    private fun boundsWidth(bounds: androidx.compose.ui.unit.DpRect): Float =
        (bounds.right - bounds.left).value

    private fun boundsHeight(bounds: androidx.compose.ui.unit.DpRect): Float =
        (bounds.bottom - bounds.top).value
}
