package com.alauncher.olauncher

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.jar.Attributes
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import java.util.jar.Manifest

class OEngineInstallerAgentTest {
    private fun makeAgent(file: File, premain: String = "git.artdeell.installer_agent.Agent") {
        val manifest = Manifest().apply {
            mainAttributes[Attributes.Name.MANIFEST_VERSION] = "1.0"
            mainAttributes.putValue("Premain-Class", premain)
        }
        JarOutputStream(file.outputStream(), manifest).use { jar ->
            jar.putNextEntry(JarEntry("git/artdeell/installer_agent/Agent.class"))
            jar.write(byteArrayOf(1, 2, 3))
            jar.closeEntry()
        }
    }

    @Test fun acceptsOnlyExpectedPremainClassAndAgentBytecode() {
        val temp = Files.createTempDirectory("installer-agent").toFile()
        try {
            val valid = File(temp, "valid.jar")
            makeAgent(valid)
            assertTrue(OEngineInstallerAgent.isValidAgentJar(valid))
            val wrong = File(temp, "wrong.jar")
            makeAgent(wrong, "example.WrongAgent")
            assertFalse(OEngineInstallerAgent.isValidAgentJar(wrong))
            assertFalse(OEngineInstallerAgent.isValidAgentJar(File(temp, "missing.jar")))
        } finally {
            temp.deleteRecursively()
        }
    }

    @Test fun buildsMojoAwtIntentArgumentsWithJarPathSeparateFromVmOptions() {
        val temp = Files.createTempDirectory("installer-args").toFile()
        try {
            val agent = File(temp, "agent.jar")
            val installer = File(temp, "installer.jar")
            makeAgent(agent)
            installer.writeBytes(byteArrayOf(1))
            val invocation = OEngineInstallerAgent.buildAwtInvocation(agent, installer)
            assertEquals(listOf("-javaagent:${agent.absolutePath}"), invocation.javaArgs)
            assertFalse(invocation.javaArgs.any { it == "-jar" || it == installer.absolutePath })
            assertEquals(installer.canonicalPath, invocation.installerPath)

            val optiFine = OEngineInstallerAgent.buildAwtInvocation(agent, installer, optiFine = true)
            assertEquals(listOf("-javaagent:${agent.absolutePath}=OF"), optiFine.javaArgs)
            assertEquals(installer.canonicalPath, optiFine.installerPath)
        } finally {
            temp.deleteRecursively()
        }
    }
}
