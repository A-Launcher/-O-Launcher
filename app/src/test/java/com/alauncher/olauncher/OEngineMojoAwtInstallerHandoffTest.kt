package com.alauncher.olauncher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.json.JSONArray

class OEngineMojoAwtInstallerHandoffTest {
    @Test fun acceptsMojoAgentJvmArgument() {
        val path = "/instance/oengine/installers/agent/forge_installer.jar"
        val args = OEngineMojoAwtInstallerHandoff.parseJavaArgs(
            JSONArray().put("-javaagent:$path").toString(), path
        )
        assertEquals(listOf("-javaagent:$path"), args)
    }

    @Test fun rejectsArgumentsThatDoNotPointAtStagedAgent() {
        assertThrows(IllegalArgumentException::class.java) {
            OEngineMojoAwtInstallerHandoff.parseJavaArgs(
                JSONArray().put("-javaagent:/tmp/other.jar").toString(),
                "/instance/oengine/installers/agent/forge_installer.jar"
            )
        }
    }

    @Test fun rejectsNullAndOversizedArgumentLists() {
        assertThrows(IllegalArgumentException::class.java) {
            OEngineMojoAwtInstallerHandoff.parseJavaArgs(
                JSONArray().put(org.json.JSONObject.NULL).toString(), "/agent.jar"
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            OEngineMojoAwtInstallerHandoff.parseJavaArgs(
                JSONArray().put("x".repeat(5000)).put("-javaagent:/agent.jar").toString(),
                "/agent.jar"
            )
        }
    }
}
