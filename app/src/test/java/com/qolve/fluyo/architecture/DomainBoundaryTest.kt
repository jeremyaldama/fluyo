package com.qolve.fluyo.architecture

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.readLines
import org.junit.Assert.assertTrue
import org.junit.Test

class DomainBoundaryTest {

    @Test
    fun `domain does not depend on Android or outer application layers`() {
        val domainRoot = Path.of("src/main/java/com/qolve/fluyo/domain")
        val forbidden = Regex(
            "^import (android\\.|androidx\\.|com\\.qolve\\.fluyo\\.(data|presentation|notifications|di)\\.)",
        )
        val violations = mutableListOf<String>()
        Files.walk(domainRoot).use { paths ->
            paths.filter { it.extension == "kt" }.forEach { file ->
                file.readLines()
                    .filter(forbidden::containsMatchIn)
                    .forEach { line ->
                        violations += "${domainRoot.relativize(file)}: $line"
                    }
            }
        }

        assertTrue("Domain dependency violations:\n${violations.joinToString("\n")}", violations.isEmpty())
    }
}
