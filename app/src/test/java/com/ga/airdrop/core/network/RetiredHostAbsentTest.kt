package com.ga.airdrop.core.network

import com.ga.airdrop.BuildConfig
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The retired host must not exist anywhere in this repo.
 *
 * Kemar's ruling, relayed verbatim by BronzeMountain (ORC 88486): *"THAT IS NO
 * LONGER BEING USED ONLY: airdropja.com"*. No DNS record, no CNAME, no 301, no
 * deprecation window. The host does not come back, so it must not appear in any
 * code path, config, asset URL, **test fixture** or stored value — not as a
 * fallback, not as an allow-list entry, not "for reference".
 *
 * Why a source scan and not just a BuildConfig assertion: the flavor config was
 * only ONE of fourteen references. The rest were fixtures, a regex, a live
 * request in an androidTest, and comments. A config-only check would have
 * passed while the repo was still full of them — and did, because the connected
 * gate is diff-selective and never ran the androidTest that made a live request
 * to the dead host.
 *
 * ⚠️ This test FAILS if it cannot locate the source tree. A guard that silently
 * passes when it cannot do its job is worse than no guard: it reports safety it
 * never checked.
 */
class RetiredHostAbsentTest {

    /** Assembled at runtime so this file does not itself contain the string. */
    private val retiredHost = "app" + "." + "airdropja" + "." + "com"

    private val scannedExtensions = setOf("kt", "java", "kts", "xml", "json", "pro", "md", "yml", "yaml", "txt", "properties", "cfg", "env")

    /**
     * Build output and VCS internals only. Deliberately does NOT skip docs,
     * fixtures, scripts or CI config — those are where it kept hiding.
     *
     * `worktrees` is excluded because a git worktree is a SEPARATE checkout at
     * its own commit, often pre-fix. It is not this tree's source and its own
     * repo runs its own guard; scanning it would fail this build for a
     * different commit's content.
     */
    private val skippedDirectories = setOf(".git", "build", ".gradle", ".idea", "worktrees")

    private fun repoRoot(): File {
        var dir: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
        while (dir != null) {
            if (File(dir, "app/src/main").isDirectory && File(dir, "settings.gradle.kts").isFile) {
                return dir
            }
            dir = dir.parentFile
        }
        throw AssertionError(
            "Could not locate the repo root from ${System.getProperty("user.dir")}. " +
                "This guard cannot verify anything, so it fails rather than reporting a pass it did not earn.",
        )
    }

    @Test
    fun `the retired host appears nowhere in tracked source, config, fixtures or docs`() {
        val root = repoRoot()
        // ⚠️ THE WHOLE REPO, not a hand-picked list of directories.
        //
        // This used to scan only app/src, docs/ and app/build.gradle.kts — and
        // that scope was the bug. BronzeMountain traced the root cause of the
        // host keeps-coming-back problem (ORC 88775) to AGENT-FACING DOCS that
        // still taught it as the production base URL: an AI primer, a backend
        // source map, and worst of all an iOS RELEASE-CONFIGURATION table. An
        // agent reads those first and reproduces the exact defect that put a
        // build in the store pointing at a dead host.
        //
        // Every one of those lives at the REPO ROOT, which this guard did not
        // look at. A guard that scans where you already looked cannot find
        // what you missed.
        val roots = listOf(root)

        var filesScanned = 0
        val offenders = mutableListOf<String>()

        roots.forEach { start ->
            start.walkTopDown()
                .onEnter { dir -> dir.name !in skippedDirectories }
                .filter { it.isFile && (it.extension.lowercase() in scannedExtensions || it.name.endsWith(".gradle.kts")) }
                .forEach { file ->
                    filesScanned++
                    if (file.absolutePath == thisSourceFile(root)) return@forEach
                    file.readLines().forEachIndexed { i, line ->
                        if (line.contains(retiredHost)) {
                            offenders += "${file.relativeTo(root)}:${i + 1}: ${line.trim().take(140)}"
                        }
                    }
                }
        }

        // A scan that read almost nothing did not prove absence.
        assertTrue(
            "only $filesScanned files scanned under $root — the walk is not reaching the source tree",
            filesScanned > 200,
        )

        assertTrue(
            "The retired host is permanently dead and must not appear anywhere " +
                "(ORC 88486). $filesScanned files scanned, ${offenders.size} reference(s):\n" +
                offenders.joinToString("\n"),
            offenders.isEmpty(),
        )
    }

    @Test
    fun `neither build config base url points at the retired host`() {
        assertFalse(
            "API_BASE_URL points at the retired host: ${BuildConfig.API_BASE_URL}",
            BuildConfig.API_BASE_URL.contains(retiredHost),
        )
        assertFalse(
            "WEB_BASE_URL points at the retired host: ${BuildConfig.WEB_BASE_URL}",
            BuildConfig.WEB_BASE_URL.contains(retiredHost),
        )
    }

    private fun thisSourceFile(root: File) =
        File(root, "app/src/test/java/com/ga/airdrop/core/network/RetiredHostAbsentTest.kt").absolutePath
}
