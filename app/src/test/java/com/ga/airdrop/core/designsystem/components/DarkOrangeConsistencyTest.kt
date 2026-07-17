package com.ga.airdrop.core.designsystem.components

import androidx.compose.ui.graphics.toArgb
import com.ga.airdrop.core.designsystem.theme.darkAirdropColors
import com.ga.airdrop.core.designsystem.theme.lightAirdropColors
import java.nio.file.Files
import java.nio.file.Path
import org.junit.Assert.assertEquals
import org.junit.Test

class DarkOrangeConsistencyTest {
    private val approvedStaticOrangeUses = mapOf(
        // The palette is the single source that wires fixed light tokens into
        // lightAirdropColors. Dark colors use OrangeMainDark/OrangeFunctionDark.
        "app/src/main/java/com/ga/airdrop/core/designsystem/theme/Color.kt" to 4,
        // These two fixed uses are the approved light-theme referral gradient.
        "app/src/main/java/com/ga/airdrop/feature/more2/InviteFriendScreen.kt" to 2,
    )
    private val approvedFixedLightOrangeUses = mapOf(
        "app/src/main/java/com/ga/airdrop/core/designsystem/theme/Color.kt" to 3,
        // Express is a service-brand category tint, not the semantic app accent.
        "app/src/main/java/com/ga/airdrop/feature/homedetails/WarehousesScreen.kt" to 1,
        "app/src/main/java/com/ga/airdrop/feature/shipments/ShipmentsUi.kt" to 1,
        // These are approved fixed light-gradient stops.
        "app/src/main/java/com/ga/airdrop/feature/more2/More2Components.kt" to 1,
        "app/src/main/java/com/ga/airdrop/feature/more2/ReferAFriendScreen.kt" to 1,
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
    fun `Kotlin UI cannot bypass the semantic dark orange`() {
        val projectRoot = findProjectRoot()
        val sourceRoot = projectRoot.resolve("app/src/main/java")
        val staticOrange = Regex(
            """BrandPalette\.(OrangeMain|OrangeDark|ButtonStatic|ButtonLoading)\b""",
        )
        val fixedLightOrange = Regex(
            """(?i)(?:Color\(\s*0xFFF15114\s*\)|parseColor\(\s*"#F15114"\s*\))""",
        )
        val observedStaticUses = mutableMapOf<String, Int>()
        val observedFixedLightUses = mutableMapOf<String, Int>()

        Files.walk(sourceRoot).use { paths ->
            paths
                .filter { Files.isRegularFile(it) && it.fileName.toString().endsWith(".kt") }
                .forEach { path ->
                    val relativePath = projectRoot.relativize(path).toString().replace('\\', '/')
                    val source = String(Files.readAllBytes(path), Charsets.UTF_8)
                    val actualStaticUses = staticOrange.findAll(source).count()
                    val expectedStaticUses = approvedStaticOrangeUses[relativePath] ?: 0
                    val actualFixedLightUses = fixedLightOrange.findAll(source).count()
                    val expectedFixedLightUses = approvedFixedLightOrangeUses[relativePath] ?: 0
                    observedStaticUses[relativePath] = actualStaticUses
                    observedFixedLightUses[relativePath] = actualFixedLightUses

                    assertEquals(
                        "$relativePath has a non-semantic orange reference",
                        expectedStaticUses,
                        actualStaticUses,
                    )
                    assertEquals(
                        "$relativePath has a fixed light orange outside the approved brand gradients",
                        expectedFixedLightUses,
                        actualFixedLightUses,
                    )
                }
        }

        approvedStaticOrangeUses.forEach { (relativePath, expectedUses) ->
            assertEquals(
                "$relativePath allowlist entry is stale or missing",
                expectedUses,
                observedStaticUses[relativePath],
            )
        }
        approvedFixedLightOrangeUses.forEach { (relativePath, expectedUses) ->
            assertEquals(
                "$relativePath fixed-light allowlist entry is stale or missing",
                expectedUses,
                observedFixedLightUses[relativePath],
            )
        }
    }

    @Test
    fun `dark vector assets cannot use the light orange`() {
        val drawableRoot = findProjectRoot().resolve("app/src/main/res/drawable")
        val staleLightOrange = Regex("""(?i)#(?:FF)?F15114\b""")

        Files.walk(drawableRoot).use { paths ->
            paths
                .filter {
                    Files.isRegularFile(it) &&
                        it.fileName.toString().endsWith("_dark.xml")
                }
                .forEach { path ->
                    val source = String(Files.readAllBytes(path), Charsets.UTF_8)
                    assertEquals(
                        "${path.fileName} has the light accent in a dark-only asset",
                        0,
                        staleLightOrange.findAll(source).count(),
                    )
                }
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
