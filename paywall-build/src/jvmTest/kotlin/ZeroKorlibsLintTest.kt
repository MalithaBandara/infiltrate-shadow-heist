package com.infiltrate.test

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class ZeroKorlibsLintTest {

    @Test
    fun testZeroKorlibsImportInGameModel() {
        val modelDir = File("../src/game/model")
        assertTrue(modelDir.exists() && modelDir.isDirectory, "model directory ../src/game/model must exist")

        val ktFiles = modelDir.listFiles { _, name -> name.endsWith(".kt") } ?: emptyArray()
        assertTrue(ktFiles.isNotEmpty(), "model directory must contain .kt files")

        for (file in ktFiles) {
            val lines = file.readLines()
            for ((index, line) in lines.withIndex()) {
                val trimmed = line.trim()
                assertTrue(
                    !trimmed.startsWith("import korlibs"),
                    "File ${file.name}:${index + 1} imports korlibs ('$trimmed'). src/game/model must remain 100% engine-agnostic."
                )
            }
        }
    }
}
