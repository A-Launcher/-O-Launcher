package com.alauncher.olauncher

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/** Verified individual-project installer. Modpacks use a separate archive import flow. */
object OEngineModrinthInstaller {
 data class ProjectVersion(val id:String,val name:String,val versionNumber:String,val gameVersions:List<String>,val loaders:List<String>,val files:List<VersionFile>,val dependencies:List<Dependency>)
 data class VersionFile(val filename:String,val url:String,val size:Long,val sha1:String,val sha512:String,val primary:Boolean)
 data class Dependency(val projectId:String?,val versionId:String?,val type:String)
 data class InstallResult(val projectId:String,val versionId:String,val filename:String,val destination:File,val bytes:Long,val requiredDependencies:List<Dependency>)

 fun targetDirectory(root:File,type:OEngineModrinthCatalog.ProjectType):File = when(type) {
  OEngineModrinthCatalog.ProjectType.MOD -> File(root,"mods")
  OEngineModrinthCatalog.ProjectType.RESOURCE_PACK -> File(root,"resourcepacks")
  OEngineModrinthCatalog.ProjectType.SHADER -> File(root,"shaderpacks")
  OEngineModrinthCatalog.ProjectType.DATA_PACK -> File(root,"datapacks")
  else -> throw IllegalArgumentException("Only individual mods, resource packs, shaders and data packs can be installed here.")
 }
 fun safeFilename(value:String):String {
  val name=value.substringAfterLast('/').substringAfterLast('\\').trim()
  require(name.isNotBlank() && name!="." && name!="..")
  require(name.length<=180 && name.matches(Regex("[A-Za-z0-9._() +\\-]+"))) { "Unsafe Modrinth filename." }
  return name
 }
 fun parseVersions(body:String):List<ProjectVersion> {
  val array=JSONArray(body)
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
  val query=buildList {
   if(!gameVersion.isNullOrBlank()) add("game_versions=[%22"+gameVersion.trim()+"%22]")
   if(!loader.isNullOrBlank()) add("loaders=[%22"+loader.trim().lowercase()+"%22]")
  }.joinToString("&")
  parseVersions(request("https://api.modrinth.com/v2/project/"+projectId+"/version"+if(query.isBlank()) "" else "?"+query))
 }
 suspend fun install(projectId:String,versionId:String,type:OEngineModrinthCatalog.ProjectType,gameRoot:File,onProgress:(Long,Long)->Unit={_,_->}):InstallResult = withContext(Dispatchers.IO) {
  require(projectId.matches(Regex("[A-Za-z0-9_-]{1,64}")) && versionId.matches(Regex("[A-Za-z0-9_-]{1,80}")))
  val version=parseVersions(request("https://api.modrinth.com/v2/version/"+versionId)).firstOrNull { it.id==versionId } ?: error("Invalid Modrinth version metadata.")
  val file=version.files.firstOrNull { it.primary } ?: version.files.first()
  require(file.size in 1..(512L*1024L*1024L)) { "File exceeds 512 MiB or has invalid size." }
  val root=gameRoot.canonicalFile
  require(root.exists() || root.mkdirs())
  val folder=targetDirectory(root,type).canonicalFile
  require(folder.toPath().startsWith(root.toPath()) && (folder.exists() || folder.mkdirs()))
  val destination=File(folder,safeFilename(file.filename)).canonicalFile
  require(destination.parentFile==folder)
  val temp=File(folder,destination.name+".oengine-part")
  temp.delete()
  download(file,temp,onProgress)
  if(destination.exists() && !destination.delete()) { temp.delete(); error("Could not replace existing file.") }
  if(!temp.renameTo(destination)) { temp.copyTo(destination,overwrite=true); temp.delete() }
  InstallResult(projectId,version.id,file.filename,destination,destination.length(),version.dependencies.filter { it.type=="required" })
 }
 private fun download(file:VersionFile,destination:File,onProgress:(Long,Long)->Unit) {
  val c=(URL(file.url).openConnection() as HttpURLConnection).apply { connectTimeout=15000;readTimeout=60000;instanceFollowRedirects=true;setRequestProperty("User-Agent","O-Launcher/1.0 (Android)") }
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
 private fun request(address:String):String {
  val c=(URL(address).openConnection() as HttpURLConnection).apply { connectTimeout=12000;readTimeout=30000;instanceFollowRedirects=true;setRequestProperty("User-Agent","O-Launcher/1.0 (Android)");setRequestProperty("Accept","application/json") }
  try {
   val status=c.responseCode;val stream=if(status in 200..299)c.inputStream else c.errorStream
   val body=stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
   if(status !in 200..299) error(runCatching { JSONObject(body).optString("description") }.getOrNull()?.takeIf(String::isNotBlank) ?: "Modrinth returned HTTP "+status)
   return body
  } finally { c.disconnect() }
 }
 private fun JSONArray?.strings():List<String> { if(this==null)return emptyList();return buildList { for(i in 0 until length()) optString(i).takeIf(String::isNotBlank)?.let(::add) } }
}