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

    @Test fun buildsJavaAgentInvocationForForgeAndOptifine() {
        val temp = Files.createTempDirectory("installer-args").toFile()
        try {
            val agent = File(temp, "agent.jar")
            val installer = File(temp, "installer.jar")
            makeAgent(agent)
            installer.writeBytes(byteArrayOf(1))
            assertEquals(
                listOf("-javaagent:${agent.absolutePath}", "-jar", installer.absolutePath),
                OEngineInstallerAgent.buildLaunchArguments(agent, installer)
            )
            assertEquals(
                "-javaagent:${agent.absolutePath}=OF",
                OEngineInstallerAgent.buildLaunchArguments(agent, installer, optiFine = true).first()
            )
        } finally {
            temp.deleteRecursively()
        }
    }
}
