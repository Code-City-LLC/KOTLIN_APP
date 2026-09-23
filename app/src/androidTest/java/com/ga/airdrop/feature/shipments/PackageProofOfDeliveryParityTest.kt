package com.ga.airdrop.feature.shipments

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ga.airdrop.core.designsystem.theme.AirdropThemeProvider
import com.ga.airdrop.core.designsystem.theme.ThemeController
import java.io.File
import java.io.FileOutputStream
import java.io.ByteArrayInputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PackageProofOfDeliveryParityTest {

    @get:Rule
    val compose = createComposeRule()

    private lateinit var signatureFile: File

    @Before
    fun writeSignatureImage() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        signatureFile = File(context.cacheDir, "proof-of-delivery-test.png")
        val bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.BLACK)
        }
        FileOutputStream(signatureFile).use { output ->
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
        }
        bitmap.recycle()
    }

    @After
    fun deleteSignatureImage() {
        if (::signatureFile.isInitialized) signatureFile.delete()
    }

    @Test
    fun signedForPanelUsesTheSameWhitePlateInLightTheme() {
        render(ThemeController.Mode.LIGHT)
        assertSignedForPlate()
        saveScreenshot("signed_for_light.png")
    }

    @Test
    fun signedForPanelUsesTheSameWhitePlateInDarkTheme() {
        render(ThemeController.Mode.DARK)
        assertSignedForPlate()
        saveScreenshot("signed_for_dark.png")
    }

    @Test
    fun invalidSignatureDoesNotAddSectionSpacingInLightTheme() {
        assertInvalidSignatureHasNoLayout(ThemeController.Mode.LIGHT)
    }

    @Test
    fun invalidSignatureDoesNotAddSectionSpacingInDarkTheme() {
        assertInvalidSignatureHasNoLayout(ThemeController.Mode.DARK)
    }

    @Test
    fun canceledOlderDownloadCannotReplaceOrDeleteTheNewSignature() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val oldStarted = CountDownLatch(1)
        val releaseOld = CountDownLatch(1)
        val oldFile = AtomicReference<File>()
        var newFile: File? = null
        val old = launch(Dispatchers.Default) {
            prepareProofOfDeliveryFile(context, 42, "https://airdropja.com/proof.png") { name ->
                oldFile.set(File(context.cacheDir, "invoices/$name"))
                withContext(Dispatchers.IO) {
                    val stream = object : ByteArrayInputStream("older signature".toByteArray()) {
                        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                            oldStarted.countDown()
                            check(releaseOld.await(10, TimeUnit.SECONDS))
                            return super.read(buffer, offset, length)
                        }
                    }
                    copyInvoiceStreamToCache(context, name, stream)
                }
            }
        }
        try {
            assertTrue("Old response body must be in flight", oldStarted.await(5, TimeUnit.SECONDS))
            old.cancel()
            val newerBytes = "newer signature".toByteArray()
            newFile = prepareProofOfDeliveryFile(context, 42, "https://airdropja.com/proof.png") { name ->
                withContext(Dispatchers.IO) {
                    copyInvoiceStreamToCache(context, name, ByteArrayInputStream(newerBytes))
                }
            }
            releaseOld.countDown()
            old.join()

            assertArrayEquals(newerBytes, newFile.readBytes())
            assertNotEquals(oldFile.get().absolutePath, newFile.absolutePath)
            assertFalse("Canceled signature bytes must be removed", oldFile.get().exists())
        } finally {
            releaseOld.countDown()
            old.cancel()
            old.join()
            oldFile.get()?.delete()
            newFile?.delete()
        }
    }

    private fun assertInvalidSignatureHasNoLayout(mode: ThemeController.Mode) {
        signatureFile.writeText("not an image")
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            ThemeController.set(mode)
        }
        compose.setContent {
            AirdropThemeProvider {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Box(Modifier.width(100.dp).height(20.dp).testTag("before-signature"))
                    SignedForPanel(
                        presentation = SignedForPresentation("https://airdropja.com/proof.png"),
                        signatureFile = signatureFile,
                    )
                    Box(Modifier.width(100.dp).height(20.dp).testTag("after-signature"))
                }
            }
        }
        compose.waitForIdle()
        compose.onNodeWithTag("package-details-section-signed-for").assertDoesNotExist()
        val before = compose.onNodeWithTag("before-signature").getUnclippedBoundsInRoot()
        val after = compose.onNodeWithTag("after-signature").getUnclippedBoundsInRoot()
        assertEquals(
            "Absent signatures must not create a second section gap",
            16.dp,
            after.top - before.bottom,
        )
    }

    private fun render(mode: ThemeController.Mode) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            ThemeController.set(mode)
        }
        compose.setContent {
            AirdropThemeProvider {
                SignedForPanel(
                    presentation = SignedForPresentation(
                        imageUrl = "https://airdropja.com/proof.png",
                        meta = "Received by D. Grant \u00B7 10 Aug 2026",
                    ),
                    signatureFile = signatureFile,
                )
            }
        }
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodesWithTag("package-details-section-signed-for")
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun assertSignedForPlate() {
        compose.onNodeWithTag("package-details-section-signed-for").assertIsDisplayed()
        compose.onNodeWithTag("package-details-signed-for-plate").assertIsDisplayed()
        compose.onNodeWithText("Received by D. Grant \u00B7 10 Aug 2026").assertIsDisplayed()

        val plate = compose.onNodeWithTag("package-details-signed-for-plate")
            .captureToImage()
            .asAndroidBitmap()
        // The ink image is centered; this left inset is intentionally the fixed
        // white plate surface in both modes.
        assertEquals(Color.WHITE, plate.getPixel(20, 20))
    }

    private fun saveScreenshot(name: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.getExternalFilesDir(null), "screenshots/proof_of_delivery")
            .also { it.mkdirs() }
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        FileOutputStream(File(directory, name)).use {
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
        }
    }
}
