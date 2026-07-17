package com.ga.airdrop.core.designsystem.components

import androidx.compose.ui.graphics.toArgb
import com.ga.airdrop.core.designsystem.theme.darkAirdropColors
import com.ga.airdrop.core.designsystem.theme.lightAirdropColors
import java.nio.file.Files
import java.nio.file.Path
import org.junit.Assert.assertEquals
import org.junit.Test

class DarkOrangeConsistencyTest {
    private val semanticOrangeFiles = listOf(
        "app/src/main/java/com/ga/airdrop/core/designsystem/components/TypeInputField.kt",
        "app/src/main/java/com/ga/airdrop/feature/auth/AuthComponents.kt",
        "app/src/main/java/com/ga/airdrop/feature/auth/SignUpScreen.kt",
        "app/src/main/java/com/ga/airdrop/feature/calculator/CalculatorResultsScreen.kt",
        "app/src/main/java/com/ga/airdrop/feature/calculator/CalculatorScreen.kt",
        "app/src/main/java/com/ga/airdrop/feature/calculator/CalculatorUi.kt",
        "app/src/main/java/com/ga/airdrop/feature/calculator/GovernmentChargesScreen.kt",
        "app/src/main/java/com/ga/airdrop/feature/common/AirdropUploadSourcePicker.kt",
        "app/src/main/java/com/ga/airdrop/feature/dropalert/DropAlertScreen.kt",
        "app/src/main/java/com/ga/airdrop/feature/homedetails/NotificationsScreen.kt",
        "app/src/main/java/com/ga/airdrop/feature/more/BackgroundImagesScreen.kt",
        "app/src/main/java/com/ga/airdrop/feature/more/DocumentsScreen.kt",
        "app/src/main/java/com/ga/airdrop/feature/more/MoreComponents.kt",
        "app/src/main/java/com/ga/airdrop/feature/more/NotificationSettingsScreen.kt",
        "app/src/main/java/com/ga/airdrop/feature/more/ProfileScreen.kt",
        "app/src/main/java/com/ga/airdrop/feature/more/SettingsScreen.kt",
        "app/src/main/java/com/ga/airdrop/feature/more2/AboutScreen.kt",
        "app/src/main/java/com/ga/airdrop/feature/more2/AccountDeletionReasonScreen.kt",
        "app/src/main/java/com/ga/airdrop/feature/more2/AccountDeletionScreen.kt",
        "app/src/main/java/com/ga/airdrop/feature/more2/AuthorizedUsersScreen.kt",
        "app/src/main/java/com/ga/airdrop/feature/more2/FaqScreen.kt",
        "app/src/main/java/com/ga/airdrop/feature/more2/InviteFriendScreen.kt",
        "app/src/main/java/com/ga/airdrop/feature/more2/LegalContent.kt",
        "app/src/main/java/com/ga/airdrop/feature/more2/More2Components.kt",
        "app/src/main/java/com/ga/airdrop/feature/more2/PromotionsScreen.kt",
        "app/src/main/java/com/ga/airdrop/feature/more2/ReferredFriendsScreen.kt",
        "app/src/main/java/com/ga/airdrop/feature/more2/RestrictedItemsScreen.kt",
        "app/src/main/java/com/ga/airdrop/feature/more2/ShippingRatesScreen.kt",
        "app/src/main/java/com/ga/airdrop/feature/security/BiometricLockScreen.kt",
    )

    @Test
    fun `dark primary controls use the approved F46427 orange`() {
        val expected = 0xFFF46427.toInt()

        assertEquals(expected, darkAirdropColors.orangeMain.toArgb())
        assertEquals(expected, darkAirdropColors.buttonStatic.toArgb())
        assertEquals(
            listOf(expected, expected),
            primaryButtonGradient(darkAirdropColors).map { it.toArgb() },
        )
    }

    @Test
    fun `light primary controls preserve the approved gradient`() {
        assertEquals(
            listOf(0xFFFF783E.toInt(), 0xFFF15114.toInt()),
            primaryButtonGradient(lightAirdropColors).map { it.toArgb() },
        )
    }

    @Test
    fun `migrated screens cannot bypass the semantic dark orange`() {
        val projectRoot = findProjectRoot()
        val staticOrange = Regex(
            """BrandPalette\.(OrangeMain|OrangeDark|ButtonStatic|ButtonLoading)\b""",
        )

        semanticOrangeFiles.forEach { relativePath ->
            val source = String(
                Files.readAllBytes(projectRoot.resolve(relativePath)),
                Charsets.UTF_8,
            )
            val expectedStaticUses =
                if (relativePath.endsWith("/InviteFriendScreen.kt")) 2 else 0

            assertEquals(
                "$relativePath has a non-semantic orange reference",
                expectedStaticUses,
                staticOrange.findAll(source).count(),
            )
        }
    }

    private fun findProjectRoot(): Path {
        var current: Path? = Path.of(System.getProperty("user.dir")).toAbsolutePath()
        while (current != null) {
            val candidate = current
            if (Files.isDirectory(candidate.resolve("app/src/main/java"))) return candidate
            current = candidate.parent
        }
        error("Could not locate the Kotlin project root from ${System.getProperty("user.dir")}")
    }
}
