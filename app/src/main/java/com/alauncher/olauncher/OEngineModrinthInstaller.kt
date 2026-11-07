package com.alauncher.olauncher

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest

/** Verified individual-project installer. Modpacks use a separate archive import flow. */
object OEngineModrinthInstaller {
 data class ProjectVersion(val id:String,val name:String,val versionNumber:String,val gameVersions:List<String>,val loaders:List<String>,val files:List<VersionFile>,val dependencies:List<Dependency>)
 data class VersionFile(val filename:String,val url:String,val size:Long,val sha1:String,val sha512:String,val primary:Boolean)
 data class Dependency(val projectId:String?,val versionId:String?,val type:String)
 data class InstallResult(val projectId:String,val versionId:String,val filename:String,val destination:File,val bytes:Long,val requiredDependencies:List<Dependency>)
 data class DependencyInstallResult(val primary:InstallResult,val installedDependencies:List<InstallResult>,val unresolvedDependencies:List<String>)

 fun targetDirectory(root:File,type:OEngineModrinthCatalog.ProjectType,worldName:String?=null):File = when(type) {
  OEngineModrinthCatalog.ProjectType.MOD -> File(root,"mods")
  OEngineModrinthCatalog.ProjectType.RESOURCE_PACK -> File(root,"resourcepacks")
  OEngineModrinthCatalog.ProjectType.SHADER -> File(root,"shaderpacks")
  OEngineModrinthCatalog.ProjectType.DATA_PACK -> {
   val name=requireNotNull(worldName) { "Choose a world before installing a data pack." }
   require(name.isNotBlank() && name!="." && name!=".." && !name.contains("/") && !name.contains("\\"))
   val canonicalRoot=root.canonicalFile
   val saves=File(canonicalRoot,"saves")
   val world=File(saves,name)
   require(!java.nio.file.Files.isSymbolicLink(saves.toPath()) && saves.isDirectory &&
    saves.canonicalFile.parentFile==canonicalRoot &&
    !java.nio.file.Files.isSymbolicLink(world.toPath()) && world.isDirectory &&
    world.canonicalFile.parentFile==saves.canonicalFile && File(world,"level.dat").isFile) {
     "The selected Minecraft world is unavailable or unsafe."
    }
   val folder=File(world,"datapacks")
   require(!java.nio.file.Files.isSymbolicLink(folder.toPath()))
   folder
  }
  else -> throw IllegalArgumentException("Only individual mods, resource packs, shaders and data packs can be installed here.")
 }
 fun safeFilename(value:String):String {
  val name=value.substringAfterLast('/').substringAfterLast('\\').trim()
  require(name.isNotBlank() && name!="." && name!="..")
  require(name.length<=180 && name.matches(Regex("[A-Za-z0-9._() +\\-]+"))) { "Unsafe Modrinth filename." }
  return name
 }
 /** URL-encodes Modrinth's JSON-array filters without changing their JSON meaning. */
 fun buildVersionQuery(gameVersion:String?,loader:String?):String = buildList {
  if(!gameVersion.isNullOrBlank()) {
   val json=JSONArray().put(gameVersion.trim()).toString()
   add("game_versions="+URLEncoder.encode(json,"UTF-8"))
  }
  if(!loader.isNullOrBlank()) {
   val json=JSONArray().put(loader.trim().lowercase()).toString()
   add("loaders="+URLEncoder.encode(json,"UTF-8"))
  }
 }.joinToString("&")

 fun parseVersions(body:String):List<ProjectVersion> {
  val trimmed = body.trimStart()
  val array = if (trimmed.startsWith("{")) JSONArray().put(JSONObject(body)) else JSONArray(body)
  return buildList {
   for(i in 0 until array.length()) {
    val item=array.optJSONObject(i) ?: continue
    val id=item.optString("id").takeIf { it.matches(Regex("[A-Za-z0-9_-]{1,80}")) } ?: continue
    val files=item.optJSONArray("files") ?: JSONArray()
    val parsed=buildList {
     for(j in 0 until files.length()) {
      val f=files.optJSONObject(j) ?: continue
      val hashes=f.optJSONObject("hashes") ?: JSONObject()
      val url=f.optString("url")
      if(!url.startsWith("https://")) continue
      val h1=hashes.optString("sha1").lowercase()
      val h512=hashes.optString("sha512").lowercase()
      if(!h1.matches(Regex("[0-9a-f]{40}")) && !h512.matches(Regex("[0-9a-f]{128}"))) continue
      add(VersionFile(safeFilename(f.optString("filename")),url,f.optLong("size"),h1,h512,f.optBoolean("primary")))
     }
    }
    if(parsed.isEmpty()) continue
    val deps=item.optJSONArray("dependencies") ?: JSONArray()
    val dependencies=buildList {
     for(j in 0 until deps.length()) {
      val d=deps.optJSONObject(j) ?: continue
      add(Dependency(d.optString("project_id").takeIf(String::isNotBlank),d.optString("version_id").takeIf(String::isNotBlank),d.optString("dependency_type")))
     }
    }
    add(ProjectVersion(id,item.optString("name"),item.optString("version_number"),item.optJSONArray("game_versions").strings(),item.optJSONArray("loaders").strings(),parsed,dependencies))
   }
  }
 }
 suspend fun versions(projectId:String,gameVersion:String?=null,loader:String?=null):List<ProjectVersion> = withContext(Dispatchers.IO) {
  require(projectId.matches(Regex("[A-Za-z0-9_-]{1,64}")))
  val query=buildVersionQuery(gameVersion,loader)
  parseVersions(request("https://api.modrinth.com/v2/project/"+projectId+"/version"+if(query.isBlank()) "" else "?"+query))
 }
 suspend fun install(projectId:String,versionId:String,type:OEngineModrinthCatalog.ProjectType,gameRoot:File,worldName:String?=null,onProgress:(Long,Long)->Unit={_,_->}):InstallResult = withContext(Dispatchers.IO) {
  require(projectId.matches(Regex("[A-Za-z0-9_-]{1,64}")) && versionId.matches(Regex("[A-Za-z0-9_-]{1,80}")))
  val metadata = request("https://api.modrinth.com/v2/version/"+versionId)
  require(JSONObject(metadata).optString("project_id") == projectId) { "This version does not belong to the selected Modrinth project." }
  val version=parseVersions(metadata).firstOrNull { it.id==versionId } ?: error("Invalid Modrinth version metadata.")
  val file=version.files.firstOrNull { it.primary } ?: version.files.first()
  require(file.size in 1..(512L*1024L*1024L)) { "File exceeds 512 MiB or has invalid size." }
  val root=gameRoot.canonicalFile
  require(root.exists() || root.mkdirs())
  val requestedFolder=targetDirectory(root,type,worldName)
  require(!java.nio.file.Files.isSymbolicLink(requestedFolder.toPath())) { "The content destination cannot be a symbolic link." }
  require(requestedFolder.exists() || requestedFolder.mkdirs()) { "Could not create the content destination." }
  require(!java.nio.file.Files.isSymbolicLink(requestedFolder.toPath())) { "The content destination changed unexpectedly." }
  val folder=requestedFolder.canonicalFile
  require(folder.toPath().startsWith(root.toPath()) && folder.isDirectory)
  val destination=File(folder,safeFilename(file.filename)).canonicalFile
  require(destination.parentFile==folder)
  val temp=File(folder,destination.name+".oengine-part")
  temp.delete()
  download(file,temp,onProgress)
  val backup = File(folder, destination.name + ".oengine-backup")
  backup.delete()
  if (destination.exists() && !destination.renameTo(backup)) {
   temp.delete()
   error("Could not safely stage the existing file for replacement.")
  }
  if (!temp.renameTo(destination)) {
   if (backup.exists()) backup.renameTo(destination)
   temp.delete()
   error("Could not atomically move the verified download into place.")
  }
  backup.delete()
  InstallResult(projectId,version.id,file.filename,destination,destination.length(),version.dependencies.filter { it.type=="required" })
 }
 /**
  * Installs required Modrinth dependencies after the selected project. Optional and incompatible
  * dependencies are never installed automatically; unresolved requirements are returned to UI.
  */
 suspend fun installWithRequiredDependencies(
  projectId:String,
  versionId:String,
  type:OEngineModrinthCatalog.ProjectType,
  gameRoot:File,
  gameVersion:String?,
  loader:String?,
  onProgress:(Long,Long)->Unit={_,_->}
 ):DependencyInstallResult {
  val primary=install(projectId,versionId,type,gameRoot,onProgress=onProgress)
  val installed=mutableListOf<InstallResult>()
  val unresolved=mutableListOf<String>()
  val visited=linkedSetOf(projectId)
  suspend fun resolve(dependency:Dependency,parentGameVersion:String?,parentLoader:String?,depth:Int) {
   if(dependency.type!="required") return
   val id=dependency.projectId
   if(id.isNullOrBlank()) {
    unresolved += "Required dependency has no project ID"+(dependency.versionId?.let { " (version $it)" } ?: "")
    return
   }
   if(id in visited) return
   if(depth>8 || visited.size>=40) {
    unresolved += "$id (dependency resolution limit reached)"
    return
   }
   visited += id
   try {
    val candidates=if(!dependency.versionId.isNullOrBlank()) {
     val body=request("https://api.modrinth.com/v2/version/"+dependency.versionId)
     parseVersions(body).filter { it.id==dependency.versionId && JSONObject(body).optString("project_id")==id }
    } else versions(id,parentGameVersion,parentLoader)
    val chosen=candidates.firstOrNull { candidate ->
     (parentGameVersion.isNullOrBlank() || candidate.gameVersions.isEmpty() || parentGameVersion in candidate.gameVersions) &&
      (parentLoader.isNullOrBlank() || candidate.loaders.isEmpty() || parentLoader.lowercase() in candidate.loaders.map { it.lowercase() })
    } ?: error("No compatible release for required dependency $id")
    chosen.dependencies.filter { it.type=="required" }.forEach { resolve(it,parentGameVersion ?: chosen.gameVersions.firstOrNull(),parentLoader ?: chosen.loaders.firstOrNull(),depth+1) }
    val result=install(id,chosen.id,OEngineModrinthCatalog.ProjectType.MOD,gameRoot)
    installed += result
   } catch(failure:Exception) {
    unresolved += "$id: "+(failure.message ?: "could not resolve/install")
   }
  }
  primary.requiredDependencies.forEach { resolve(it,gameVersion,loader,1) }
  return DependencyInstallResult(primary,installed,unresolved)
 }
 private fun download(file:VersionFile,destination:File,onProgress:(Long,Long)->Unit) {
  val c=openHttpsGet(file.url, 15000, 60000)
  var count=0L
  val d1=MessageDigest.getInstance("SHA-1"); val d512=MessageDigest.getInstance("SHA-512")
  try {
   c.connect()
   if(c.responseCode !in 200..299) error("Modrinth download failed (HTTP "+c.responseCode+").")
   c.inputStream.use { input -> destination.outputStream().buffered().use { output ->
    val buffer=ByteArray(65536)
    while(true) { val n=input.read(buffer);if(n<0) break;count+=n
     if(count>file.size || count>512L*1024L*1024L) error("Download exceeded declared size.")
     d1.update(buffer,0,n);d512.update(buffer,0,n);output.write(buffer,0,n);onProgress(count,file.size)
    }
   } }
  } catch(e:Exception) { destination.delete();throw e } finally { c.disconnect() }
  val h1=d1.digest().joinToString("") { "%02x".format(it) };val h512=d512.digest().joinToString("") { "%02x".format(it) }
  if(count!=file.size || (file.sha1.isNotBlank() && !h1.equals(file.sha1,true)) || (file.sha512.isNotBlank() && !h512.equals(file.sha512,true))) {
   destination.delete();error("Modrinth file integrity verification failed.")
  }
 }
 private fun openHttpsGet(address:String,connectTimeout:Int=12000,readTimeout:Int=30000):HttpURLConnection {
  var current=URL(address)
  require(current.protocol.equals("https",true) && current.host.isNotBlank() && current.userInfo==null) { "Modrinth requests must use a valid HTTPS URL." }
  repeat(6) {
   val connection=(current.openConnection() as HttpURLConnection).apply {
    requestMethod="GET"; this.connectTimeout=connectTimeout; this.readTimeout=readTimeout
    instanceFollowRedirects=false
    setRequestProperty("User-Agent","O-Launcher/1.0 (Android)")
    setRequestProperty("Accept","application/json")
   }
   val status=connection.responseCode
   if(status !in setOf(301,302,303,307,308)) return connection
   val location=connection.getHeaderField("Location")
   connection.disconnect()
   require(!location.isNullOrBlank()) { "Modrinth returned a redirect without a destination." }
   current=URL(current,location)
   require(current.protocol.equals("https",true) && current.host.isNotBlank() && current.userInfo==null) { "Modrinth attempted an insecure or invalid redirect." }
  }
  error("Modrinth exceeded the allowed redirect count.")
 }
 private fun readMetadataBody(stream:java.io.InputStream?):String {
  if(stream==null) return ""
  stream.use { input ->
   val output=java.io.ByteArrayOutputStream(); val buffer=ByteArray(8192); var total=0
   while(true) { val count=input.read(buffer); if(count<0) break; total+=count
    require(total<=8*1024*1024) { "Modrinth metadata response exceeded the 8 MiB limit." }
    output.write(buffer,0,count)
   }
   return output.toString(Charsets.UTF_8.name())
  }
 }
 private fun request(address:String):String {
  val c=openHttpsGet(address, 12000, 30000)
  try {
   val status=c.responseCode;val stream=if(status in 200..299)c.inputStream else c.errorStream
   val body=readMetadataBody(stream)
   if(status !in 200..299) error(runCatching { JSONObject(body).optString("description") }.getOrNull()?.takeIf(String::isNotBlank) ?: "Modrinth returned HTTP "+status)
   return body
  } finally { c.disconnect() }
 }
 private fun JSONArray?.strings():List<String> { if(this==null)return emptyList();return buildList { for(i in 0 until length()) optString(i).takeIf(String::isNotBlank)?.let(::add) } }
}