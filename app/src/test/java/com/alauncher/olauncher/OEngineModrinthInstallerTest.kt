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
  try {
   OEngineModrinthInstaller.targetDirectory(root,OEngineModrinthCatalog.ProjectType.DATA_PACK)
   fail("data pack install must require a world")
  } catch(_:IllegalArgumentException) {}
 }
 @Test fun dataPackTargetUsesSelectedWorld() {
  val root=java.nio.file.Files.createTempDirectory("oengine-target-world").toFile()
  try {
   val world=File(root,"saves/Survival").apply { mkdirs() }
   File(world,"level.dat").writeText("world")
   assertEquals(File(world,"datapacks").path,OEngineModrinthInstaller.targetDirectory(root,OEngineModrinthCatalog.ProjectType.DATA_PACK,"Survival").path)
  } finally { root.deleteRecursively() }
 }
 @Test fun rejectsMissingOrUnsafeDataPackWorlds() {
  val root=java.nio.file.Files.createTempDirectory("oengine-target-invalid-world").toFile()
  try {
   try { OEngineModrinthInstaller.targetDirectory(root,OEngineModrinthCatalog.ProjectType.DATA_PACK,"Missing"); fail("missing world accepted") }
   catch(_:IllegalArgumentException) {}
   val outside=java.nio.file.Files.createTempDirectory("oengine-target-outside").toFile()
   try {
    File(outside,"level.dat").writeText("world")
    File(root,"saves").mkdirs()
    java.nio.file.Files.createSymbolicLink(File(root,"saves/Linked").toPath(),outside.toPath())
    try { OEngineModrinthInstaller.targetDirectory(root,OEngineModrinthCatalog.ProjectType.DATA_PACK,"Linked"); fail("linked world accepted") }
    catch(_:IllegalArgumentException) {}
   } finally { outside.deleteRecursively() }
  } finally { root.deleteRecursively() }
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

 @Test fun versionFiltersAreEncodedAsJsonArrays() {
  assertEquals("game_versions=%5B%221.20.1%22%5D&loaders=%5B%22fabric%22%5D",
   OEngineModrinthInstaller.buildVersionQuery("1.20.1","FABRIC"))
 }
 @Test fun versionFiltersOmitBlankValues() {
  assertEquals("",OEngineModrinthInstaller.buildVersionQuery(" ",""))
  assertEquals("loaders=%5B%22quilt%22%5D",OEngineModrinthInstaller.buildVersionQuery(null,"quilt"))
 }

 @Test fun parsesSingleVersionEndpointObject() {
  val body="""{"id":"v1","project_id":"p1","name":"Release","version_number":"1.0","game_versions":["1.20.1"],"loaders":["fabric"],"files":[{"filename":"mod.jar","url":"https://cdn.example/mod.jar","size":12,"primary":true,"hashes":{"sha1":"0123456789012345678901234567890123456789"}}],"dependencies":[]}"""
  val parsed=OEngineModrinthInstaller.parseVersions(body)
  assertEquals(1,parsed.size);assertEquals("v1",parsed.single().id);assertEquals("mod.jar",parsed.single().files.single().filename)
 }
}