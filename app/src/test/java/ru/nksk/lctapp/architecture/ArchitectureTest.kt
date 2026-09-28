package ru.nksk.lctapp.architecture

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Early feedback for package boundaries across app and the pure Kotlin game module.
 * Checks declared packages and explicit imports, including aliases and wildcards; it does
 * not resolve fully qualified references, same-package access, or transitive dependencies.
 * The game module additionally enforces its dependency boundary through Gradle.
 */
class ArchitectureTest {
    @Test
    fun productionSourcesRespectPackageBoundaries() {
        val sourceRoots = listOf("lctapp.mainSourceDir", "lctapp.domainSourceDir",
            "lctapp.onboardingSourceDir", "lctapp.parentsSourceDir").map { property ->
            File(requireNotNull(System.getProperty(property)) { "Configure $property for the JVM test task" })
        }
        val sources = sourceRoots.flatMap { root ->
            val files = root.walkTopDown().filter { it.isFile && it.extension == "kt" }.sortedBy { it.path }.toList()
            assertTrue("No production Kotlin sources found in $root", files.isNotEmpty())
            files.map { root to it }
        }

        val violations = buildList {
            for ((sourceRoot, file) in sources) {
                val path = file.relativeTo(sourceRoot).invariantSeparatorsPath
                // Exclude comments and string examples from the package/import scan.
                val code = nonCode.replace(file.readText()) { match ->
                    match.value.map { if (it == '\n' || it == '\r') it else ' ' }
                        .joinToString("")
                }
                val packageName = packageDeclaration.find(code)?.groupValues?.get(1)
                if (packageName == null) {
                    add("$path: missing package declaration")
                    continue
                }
                if (path.substringBeforeLast('/') != packageName.replace('.', '/')) {
                    add("$path: path does not match package $packageName")
                }
                if (!isApprovedPackage(packageName, file.name)) {
                    add("$path: unapproved production package $packageName")
                    continue
                }
                for (match in importDeclaration.findAll(code)) {
                    val imported = match.groupValues[1]
                    if (!isAllowedImport(packageName, imported)) {
                        add("$path: $packageName must not import $imported")
                    }
                }
            }
        }
        assertTrue(violations.joinToString("\n"), violations.isEmpty())
    }

    private fun isApprovedPackage(packageName: String, fileName: String): Boolean =
        when {
            packageName == base -> fileName == "MainActivity.kt"
            packageName.within("$base.app") -> true
            featurePackage(packageName) != null -> true
            packageName.within("$base.core.ui") -> true
            packageName.within("$base.domain") -> true
            packageName.within("$base.data") -> true
            else -> false
        }

    private fun isAllowedImport(
        packageName: String,
        imported: String,
    ): Boolean {
        val feature = featurePackage(packageName)
        if (feature != null && packageName.within("$feature.ui") &&
            imported.split('.').any { it == "navigation" || it == "navigation3" }
        ) {
            return false
        }
        if (packageName.within("$base.domain")) {
            return imported.within("$base.domain") || imported.within("kotlin") ||
                imported.within("java") ||
                (imported.within("kotlinx.coroutines") &&
                    !imported.within("kotlinx.coroutines.android"))
        }
        // Android and other library APIs are allowed outside the pure Kotlin domain.
        if (!imported.within(base)) return true

        return when {
            packageName == base -> imported == "$base.app.LctApp" ||
                imported == "$base.domain.game.GameRepository"
            packageName.within("$base.app") -> true
            feature != null -> imported.within(feature) ||
                imported.within("$base.domain") || imported.within("$base.core.ui") ||
                imported.within("$base.R")
            packageName.within("$base.core.ui") -> imported.within("$base.core.ui") ||
                imported.within("$base.R") || (packageName.within("$base.core.ui.game") && imported.within("$base.domain"))
            packageName.within("$base.data") -> imported.within("$base.data") ||
                imported.within("$base.domain")
            else -> false
        }
    }

    private fun featurePackage(packageName: String): String? {
        val prefix = "$base.feature."
        if (!packageName.startsWith(prefix)) return null
        val name = packageName.removePrefix(prefix).substringBefore('.')
        return name.takeIf { it.isNotEmpty() }?.let { "$prefix$it" }
    }

    private fun String.within(packageName: String): Boolean =
        this == packageName || startsWith("$packageName.")

    private companion object {
        const val base = "ru.nksk.lctapp"
        val packageDeclaration = Regex("(?m)^[ \\t]*package[ \\t]+([\\w.]+)")
        val importDeclaration = Regex("(?m)^[ \\t]*import[ \\t]+([\\w.*]+)")
        val nonCode = Regex(
            "\"\"\"[\\s\\S]*?\"\"\"|\"(?:\\\\.|[^\"\\\\])*\"|" +
                "'(?:\\\\.|[^'\\\\])*'|//[^\\r\\n]*|/\\*[\\s\\S]*?\\*/"
        )
    }
}
