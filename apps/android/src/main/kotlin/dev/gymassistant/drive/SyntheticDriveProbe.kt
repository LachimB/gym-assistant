package dev.gymassistant.drive

import dev.gymassistant.m0.BuildConfig
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import kotlinx.serialization.json.*

internal const val DRIVE_FILE_SCOPE = "https://www.googleapis.com/auth/drive.file"
internal val SYNTHETIC_PARENT = BuildConfig.SYNTHETIC_DRIVE_PARENT
internal val SYNTHETIC_CHILD = BuildConfig.SYNTHETIC_DRIVE_CHILD
internal const val ACCOUNT_PATH = "/drive/v3/about?fields=user(emailAddress,permissionId)"
internal val PARENT_PATH = "/drive/v3/files/$SYNTHETIC_PARENT?fields=name,mimeType,appProperties"
internal val CHILD_PATH = "/drive/v3/files/$SYNTHETIC_CHILD?fields=id,name,mimeType,parents,version"

internal data class ProbeResponse(val code: Int, val body: String = "", val etagPresent: Boolean = false)
internal fun interface ProbeTransport { fun get(token: String, path: String): ProbeResponse }

/** Experiment-only read transport; never send a token to a supplied URL or follow a redirect. */
internal class GoogleProbeTransport(private val reserveRequest: () -> Unit) : ProbeTransport {
    override fun get(token: String, path: String): ProbeResponse {
        check(syntheticResourcesConfigured()) { "Private synthetic configuration required" }
        require(path in setOf(ACCOUNT_PATH, PARENT_PATH, CHILD_PATH)) { "Unsupported probe endpoint" }
        require(token.isNotBlank() && '\n' !in token && '\r' !in token)
        reserveRequest() // Persist the experiment request before transmission; failures consume budget.
        val connection = URL("https://www.googleapis.com$path").openConnection() as HttpsURLConnection
        connection.instanceFollowRedirects = false
        connection.useCaches = false
        connection.connectTimeout = 10000
        connection.readTimeout = 10000
        connection.requestMethod = "GET"
        connection.setRequestProperty("Authorization", "Bearer $token")
        try {
            val code = connection.responseCode
            if (code != 200) return ProbeResponse(code) // Do not log/read provider error bodies.
            val body = connection.inputStream.reader(Charsets.UTF_8).use { reader ->
                val buffer = CharArray(65537)
                var used = 0
                while (used < buffer.size) {
                    val count = reader.read(buffer, used, buffer.size - used)
                    if (count < 0) break
                    used += count
                }
                check(used <= 65536) { "Probe metadata exceeds limit" }
                String(buffer, 0, used)
            }
            return ProbeResponse(code, body, !connection.getHeaderField("ETag").isNullOrBlank())
        } finally {
            connection.disconnect()
        }
    }
}

internal fun exactDriveScope(scopes: Collection<String>) = scopes.toSet() == setOf(DRIVE_FILE_SCOPE)

/** Reads only identity metadata and two already-approved synthetic artifacts; never Workout Sessions. */
internal fun runSyntheticDriveProbe(token: String, expectedEmail: String, transport: ProbeTransport): String {
    fun read(path: String): Pair<ProbeResponse, JsonObject?> {
        val response = transport.get(token, path)
        return response to if (response.code == 200) Json.parseToJsonElement(response.body).jsonObject else null
    }
    val (identityResponse, identity) = read(ACCOUNT_PATH)
    check(identityResponse.code == 200 && identity != null) { "Personal account verification failed. No file read." }
    val user = identity["user"]?.jsonObject
    check(user?.get("emailAddress")?.jsonPrimitive?.content.equals(expectedEmail, ignoreCase = true) &&
        !user?.get("permissionId")?.jsonPrimitive?.content.isNullOrBlank()) {
        "The authorized account differs from the personal account entered. No file read."
    }
    val (parentResponse, parent) = read(PARENT_PATH)
    check(parentResponse.code == 200 && parent != null) { "The synthetic test folder is not accessible. No wider access requested." }
    val marker = parent["appProperties"]?.jsonObject?.get("gymSyntheticM1")?.jsonPrimitive?.content
    check(!marker.isNullOrBlank() && parent["name"]?.jsonPrimitive?.content == "Gym Assistant — SYNTHETIC M1 TEST — $marker" &&
        parent["mimeType"]?.jsonPrimitive?.content == "application/vnd.google-apps.folder") {
        "The folder is not the approved synthetic fixture. Probe stopped."
    }
    val (childResponse, child) = read(CHILD_PATH)
    if (childResponse.code in setOf(403, 404)) return "Personal account verified. Test folder accessible; selected test file is not accessible on Android. No scope expanded."
    check(childResponse.code == 200 && child != null) { "Synthetic file metadata read failed. No retry." }
    check(child["id"]?.jsonPrimitive?.content == SYNTHETIC_CHILD &&
        child["name"]?.jsonPrimitive?.content == "SYNTHETIC-external-child.json" &&
        child["mimeType"]?.jsonPrimitive?.content == "application/json" &&
        child["parents"]?.jsonArray?.map { it.jsonPrimitive.content } == listOf(SYNTHETIC_PARENT)) {
        "Unexpected synthetic metadata. Probe stopped without reading file content."
    }
    return "Personal account verified. Synthetic folder and selected file accessible. " +
        if (childResponse.etagPresent) "Native client can read an ETag; conditional writes remain untested. No workout uploaded."
        else "No ETag received from this metadata endpoint. This read-only probe does not test conditional writes. No workout uploaded."
}

internal val SYNTHETIC_MARKER: String get() = BuildConfig.SYNTHETIC_DRIVE_MARKER
internal fun syntheticResourcesConfigured() = listOf(SYNTHETIC_PARENT, SYNTHETIC_CHILD, SYNTHETIC_MARKER)
    .all { it.isNotBlank() && !it.startsWith("synthetic_fixture_") }
