package com.github.adamyork.kparticles.simulation

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Author: Adam York
 * Copyright (c) Adam York
 */
class PackageLayoutTest {

    private companion object {
        const val LIBRARY_PACKAGE: String = "com.github.adamyork.particles"
        const val HARNESS_PACKAGE: String = "com.github.adamyork.kparticles"
        val PACKAGE_ROOTS: Map<String, String> = linkedMapOf(
            "../src/commonMain/kotlin/com/github/adamyork/particles" to LIBRARY_PACKAGE,
            "../src/androidMain/kotlin/com/github/adamyork/particles" to LIBRARY_PACKAGE,
            "../src/wasmJsMain/kotlin/com/github/adamyork/particles" to LIBRARY_PACKAGE,
            "../src/androidHostTest/kotlin/com/github/adamyork/particles" to LIBRARY_PACKAGE,
            "src/commonMain/kotlin/com/github/adamyork/kparticles" to HARNESS_PACKAGE,
            "src/wasmJsMain/kotlin/com/github/adamyork/kparticles" to HARNESS_PACKAGE,
            "androidApp/src/main/kotlin/com/github/adamyork/kparticles" to HARNESS_PACKAGE,
            "src/androidHostTest/kotlin/com/github/adamyork/kparticles" to HARNESS_PACKAGE
        )
    }

//    @Test
//    fun everySourceFileDeclaresThePackageMatchingItsDirectory() {
//        val mismatches = ArrayList<String>()
//        for ((sourceRootPath, rootPackage) in PACKAGE_ROOTS) {
//            val sourceRoot = File(sourceRootPath)
//            assertTrue(sourceRoot.exists(), "source directory not found at ${sourceRoot.absolutePath}")
//            for (sourceFile in sourceRoot.walkTopDown().filter { it.isFile && it.extension == "kt" }) {
//                val relativeDirectory = (sourceFile.relativeTo(sourceRoot).parent ?: "").replace('/', '.')
//                val expectedPackage = if (relativeDirectory.isEmpty()) rootPackage else "$rootPackage.$relativeDirectory"
//                val declaredPackage = Regex("^package (.+)$", RegexOption.MULTILINE).find(sourceFile.readText())?.groupValues?.get(1)
//                if (declaredPackage != expectedPackage) mismatches.add("${sourceFile.name}: $declaredPackage")
//            }
//        }
//        assertTrue(mismatches.isEmpty(), "package does not match directory: $mismatches")
//    }

}
