package com.alauncher.olauncher
import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import kotlinx.coroutines.delay
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

private const val MS_CLIENT_ID = "00000000402b5328"
private const val MS_SCOPE = "service::user.auth.xboxlive.com::MBI_SSL offline_access"
private const val VAULT_ALIAS = "o_launcher_microsoft_refresh"

data class MinecraftAccount(val name: String, val uuid: String, val refreshToken: String)
data class MicrosoftDeviceCode(val deviceCode: String, val userCode: String, val verificationUri: String, val expiresIn: Int, val interval: Int)

object MinecraftAuth {
    suspend fun beginDeviceLogin(): MicrosoftDeviceCode = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val j = form("https://login.live.com/oauth20_devicecode.srf", mapOf("client_id" to MS_CLIENT_ID, "scope" to MS_SCOPE))
        MicrosoftDeviceCode(j.getString("device_code"), j.getString("user_code"), j.optString("verification_uri", "https://www.microsoft.com/link"), j.optInt("expires_in", 900), j.optInt("interval", 5))
    }

    suspend fun completeDeviceLogin(device: MicrosoftDeviceCode): MinecraftAccount = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val started = System.currentTimeMillis()
        var interval = device.interval.coerceAtLeast(1)
        var msa: JSONObject? = null
        while (msa == null) {
            if ((System.currentTimeMillis() - started) / 1000L >= device.expiresIn) error("The Microsoft sign-in code expired. Please try again.")
            delay(interval * 1000L)
            val response = formAllowOAuthErrors("https://login.live.com/oauth20_token.srf", mapOf(
                "client_id" to MS_CLIENT_ID,
                "grant_type" to "urn:ietf:params:oauth:grant-type:device_code",
                "device_code" to device.deviceCode
            ))
            when (response.optString("error")) {
                "authorization_pending" -> Unit
                "slow_down" -> interval += 5
                "authorization_declined" -> error("Microsoft sign-in was declined.")
                "expired_token" -> error("The Microsoft sign-in code expired. Please try again.")
                "" -> msa = response
                else -> error(response.optString("error_description", "Microsoft sign-in failed."))
            }
        }
        val msaData = msa ?: error("Microsoft sign-in did not complete.")
        val msaToken = msaData.getString("access_token")
        val refresh = msaData.optString("refresh_token")
        val xbl = json("https://user.auth.xboxlive.com/user/authenticate", JSONObject()
            .put("Properties", JSONObject().put("AuthMethod", "RPS").put("SiteName", "user.auth.xboxlive.com").put("RpsTicket", "d=$msaToken"))
            .put("RelyingParty", "http://auth.xboxlive.com").put("TokenType", "JWT"))
        val xblToken = xbl.getString("Token")
        val xsts = json("https://xsts.auth.xboxlive.com/xsts/authorize", JSONObject()
            .put("Properties", JSONObject().put("SandboxId", "RETAIL").put("UserTokens", org.json.JSONArray().put(xblToken)))
            .put("RelyingParty", "rp://api.minecraftservices.com/").put("TokenType", "JWT"))
        val xstsToken = xsts.getString("Token")
        val uhs = xsts.getJSONObject("DisplayClaims").getJSONArray("xui").getJSONObject(0).getString("uhs")
        val mc = json("https://api.minecraftservices.com/authentication/login_with_xbox", JSONObject().put("identityToken", "XBL3.0 x=$uhs;$xstsToken"))
        val profile = get("https://api.minecraftservices.com/minecraft/profile", "Bearer ${mc.getString("access_token")}")
        val name = profile.optString("name").ifBlank { "Minecraft Player" }
        val uuid = profile.optString("id")
        if (uuid.isBlank()) error("Minecraft returned an empty player profile.")
        MinecraftAccount(name, uuid, refresh)
    }

    private fun form(url: String, fields: Map<String,String>) = request(url, "POST", encode(fields), "application/x-www-form-urlencoded")
    private fun formAllowOAuthErrors(url: String, fields: Map<String,String>) = request(url, "POST", encode(fields), "application/x-www-form-urlencoded", allowOAuthErrors = true)
    private fun json(url: String, body: JSONObject) = request(url, "POST", body.toString(), "application/json")
    private fun get(url: String, authorization: String) = request(url, "GET", null, null, authorization)
    private fun encode(fields: Map<String,String>) = fields.entries.joinToString("&") { URLEncoder.encode(it.key,"UTF-8")+"="+URLEncoder.encode(it.value,"UTF-8") }

    private fun request(url: String, method: String, body: String?, type: String?, authorization: String? = null, allowOAuthErrors: Boolean = false): JSONObject {
        val c = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method; connectTimeout = 15000; readTimeout = 20000
            setRequestProperty("Accept","application/json")
            authorization?.let { setRequestProperty("Authorization",it) }
            type?.let { setRequestProperty("Content-Type",it) }
            if (body != null) { doOutput = true; outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) } }
        }
        try {
            val status = c.responseCode
            val raw = (if (status in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
            val j = try { JSONObject(raw) } catch (_: Exception) { JSONObject().put("error_description", raw.ifBlank { "HTTP $status" }) }
            if (status !in 200..299 && !(allowOAuthErrors && j.has("error"))) {
                error(j.optString("error_description").ifBlank { j.optString("Message").ifBlank { "Request failed (HTTP $status)." } })
            }
            return j
        } finally { c.disconnect() }
    }
}

object MicrosoftTokenVault {
    private const val PREFS = "microsoft_account"
    fun save(context: Context, token: String) {
        if (token.isBlank()) return
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val packed = cipher.iv + cipher.doFinal(token.toByteArray(Charsets.UTF_8))
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("refresh_token", Base64.encodeToString(packed, Base64.NO_WRAP)).apply()
    }
    fun load(context: Context): String? {
        val encoded = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("refresh_token", null) ?: return null
        return try {
            val packed = Base64.decode(encoded, Base64.NO_WRAP)
            // Android Keystore AES-GCM uses a 12-byte IV; the remaining bytes are authenticated ciphertext.
            if (packed.size <= 12) return null
            val iv = packed.copyOfRange(0, 12)
            val encrypted = packed.copyOfRange(12, packed.size)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(), javax.crypto.spec.GCMParameterSpec(128, iv))
            cipher.doFinal(encrypted).toString(Charsets.UTF_8).takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            // A reset/invalidated Keystore key makes the cached token unusable; never expose ciphertext.
            clear(context)
            null
        }
    }
    fun clear(context: Context) { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply() }
    private fun key(): SecretKey {
        val store = java.security.KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(VAULT_ALIAS, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(KeyGenParameterSpec.Builder(VAULT_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setRandomizedEncryptionRequired(true).build())
        return gen.generateKey()
    }
}
