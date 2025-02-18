/*
 * This source file was generated by the Gradle 'init' task
 */
package org.example

import java.io.File
import kotlin.test.assertTrue
import kotlin.test.Test
import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.io.TempDir

/**
 * A simple functional test for the 'org.example.greeting' plugin.
 */
class GradleDockerPluginFunctionalTest {

    @field:TempDir
    lateinit var projectDir: File

    private val buildFile by lazy { projectDir.resolve("build.gradle") }
    private val settingsFile by lazy { projectDir.resolve("settings.gradle") }

    @Test fun `can apply plugin`() {
        // Set up the test build
        settingsFile.writeText("")
        buildFile.writeText("""
            import eu.nisoft.gradle.docker.StartContainerTask
            
            plugins {
                id('eu.nisoft.docker')
            }
            
            tasks.register("startMysql", StartContainerTask) {
                containerName = "mysql"
                image = "mysql:8.0"
                pullImage = true
            }
        """.trimIndent())

        // Run the build
        val runner = GradleRunner.create()
        runner.forwardOutput()
        runner.withPluginClasspath()
        runner.withArguments("tasks")
        runner.withProjectDir(projectDir)
        assertDoesNotThrow {
            runner.build().output.contains("pullForStartMysql")
        }
    }
}
