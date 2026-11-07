package com.alauncher.olauncher
import org.junit.Assert.*
import org.junit.Test
import java.io.File
class OEngineModrinthInstallerTest {
 @Test fun targetFoldersAreCorrect() {
  val root=File("/tmp/oengine-test")
  assertEquals("mods",OEngineModrinthInstaller.targetDirectory(root,OEngineModrinthCatalog.ProjectType.MOD).name)
  assertEquals("resourcepacks",OEngineModrinthInstaller.targetDirectory(root,OEngineModrinthCatalog.ProjectType.RESOURCE_PACK).name)
  assertEquals("shaderpacks",OEngineModrinthInstaller.targetDirectory(root,OEngineModrinthCatalog.ProjectType.SHADER).name)
  assertEquals("datapacks",OEngineModrinthInstaller.targetDirectory(root,OEngineModrinthCatalog.ProjectType.DATA_PACK).name)
 }
 @Test fun rejectsUnsafeNames() {
  listOf("../evil.jar","/tmp/evil.jar","..","bad:name.jar").forEach { try { OEngineModrinthInstaller.safeFilename(it);fail("accepted "+it) } catch(_:IllegalArgumentException){} }
 }
 @Test fun parsesHashVerifiedFilesAndDependencies() {
  val body="""[{"id":"ver_123","files":[{"filename":"mod.jar","url":"https://cdn.modrinth.com/a.jar","size":12,"primary":true,"hashes":{"sha1":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"}}],"dependencies":[{"project_id":"dep123","dependency_type":"required"}]}]"""
  val v=OEngineModrinthInstaller.parseVersions(body).single()
  assertEquals("ver_123",v.id);assertEquals("mod.jar",v.files.single().filename);assertEquals("dep123",v.dependencies.single().projectId)
 }
 @Test fun ignoresHttpOrUnhashedFiles() {
  val body="""[{"id":"ver_123","files":[{"filename":"bad.jar","url":"http://x/a.jar","size":1,"hashes":{}},{"filename":"good.jar","url":"https://x/a.jar","size":1,"hashes":{"sha1":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"}}]}]"""
  assertEquals(listOf("good.jar"),OEngineModrinthInstaller.parseVersions(body).single().files.map { it.filename })
 }

 @Test fun parsesSingleVersionEndpointObject() {
  val body="""{"id":"v1","project_id":"p1","name":"Release","version_number":"1.0","game_versions":["1.20.1"],"loaders":["fabric"],"files":[{"filename":"mod.jar","url":"https://cdn.example/mod.jar","size":12,"primary":true,"hashes":{"sha1":"0123456789012345678901234567890123456789"}}],"dependencies":[]}"""
  val parsed=OEngineModrinthInstaller.parseVersions(body)
  assertEquals(1,parsed.size);assertEquals("v1",parsed.single().id);assertEquals("mod.jar",parsed.single().files.single().filename)
 }
}