package dev.gymassistant.drive

import java.io.Closeable
import java.io.IOException
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import kotlinx.serialization.json.*

internal fun retryAfterDelayMs(value: String?, now: java.time.Instant): Long? {
    if (value == null) return null
    val seconds=value.toLongOrNull()
    if (seconds != null) return if (seconds >= 0) seconds.coerceAtMost(30)*1000 else null
    return try {
        val instant=java.time.ZonedDateTime.parse(value,java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME).toInstant()
        java.time.Duration.between(now,instant).toMillis().coerceIn(0,30000)
    } catch (_: Exception) { null }
}

internal data class DeliveryHttpRequest(val method: String, val path: String, val contentType: String? = null, val body: ByteArray? = null)
internal data class DeliveryHttpResponse(val status: Int, val body: ByteArray = byteArrayOf(), val retryAfterMs: Long? = null)
internal fun interface DeliveryHttp { fun request(request: DeliveryHttpRequest): DeliveryHttpResponse }

/** Token never goes in URL, saved rows, logs or redirected requests. One foreground batch. */
internal class GoogleDeliveryHttp(private var token: String, private val reserveRequest: () -> Unit) : DeliveryHttp, Closeable {
    override fun close() { token = "" }
    override fun request(request: DeliveryHttpRequest): DeliveryHttpResponse {
        check(token.isNotBlank() && '\r' !in token && '\n' !in token)
        val path = request.path
        require(path == ACCOUNT_PATH || path == "/drive/v3/files/generateIds?count=1&space=drive" ||
            Regex("/drive/v3/files/[-A-Za-z0-9_]+\\?fields=[A-Za-z0-9,()]+").matches(path) ||
            Regex("/drive/v3/files/[-A-Za-z0-9_]+\\?alt=media").matches(path) ||
            path == "/upload/drive/v3/files?uploadType=multipart&fields=id")
        require(request.method in setOf("GET", "POST"))
        reserveRequest()
        val connection = URL("https://www.googleapis.com$path").openConnection() as HttpsURLConnection
        connection.instanceFollowRedirects = false
        connection.useCaches = false
        connection.connectTimeout = 10000
        connection.readTimeout = 10000
        connection.requestMethod = request.method
        connection.setRequestProperty("Authorization", "Bearer $token")
        try {
            request.body?.let { body ->
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", checkNotNull(request.contentType))
                connection.setFixedLengthStreamingMode(body.size)
                connection.outputStream.use { it.write(body) }
            }
            val code = connection.responseCode
            val retry = retryAfterDelayMs(connection.getHeaderField("Retry-After"), java.time.Instant.now())
            if (code != 200) return DeliveryHttpResponse(code, retryAfterMs = retry)
            val limit = if (path.endsWith("?alt=media")) 8 * 1024 * 1024 else 65536
            val body = connection.inputStream.use { input ->
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (output.size() <= limit) {
                    val count = input.read(buffer, 0, minOf(buffer.size, limit + 1 - output.size()))
                    if (count < 0) break
                    output.write(buffer, 0, count)
                }
                output.toByteArray()
            }
            if (body.size > limit) throw DeliveryFailure("contract")
            return DeliveryHttpResponse(code, body, retry)
        } catch (_: IOException) {
            throw DeliveryFailure("network")
        } finally { connection.disconnect() }
    }
}

internal class GoogleDeliveryRemote(private val http: DeliveryHttp) : DeliveryRemote {
    private fun id(value: String): String { if (!value.matches(Regex("[-A-Za-z0-9_]+"))) throw DeliveryFailure("contract"); return value }
    private fun checked(request: DeliveryHttpRequest, allow: Set<Int> = setOf(200)): DeliveryHttpResponse {
        val result = http.request(request)
        if (result.status in allow) return result
        throw DeliveryFailure(when (result.status) {
            401 -> "authorization"; 403 -> "permission"; 404 -> "missingRoot"; 429 -> "rateLimited"
            in 500..599 -> "providerUnavailable"; else -> "contract"
        }, result.retryAfterMs)
    }
    private fun json(response: DeliveryHttpResponse): JsonObject = try {
        Json.parseToJsonElement(response.body.toString(Charsets.UTF_8)).jsonObject
    } catch (_: Exception) { throw DeliveryFailure("contract") }
    private fun JsonObject.text(key: String) = this[key]?.jsonPrimitive?.content ?: throw DeliveryFailure("contract")
    override suspend fun identity(): DeliveryIdentity {
        val user = json(checked(DeliveryHttpRequest("GET", ACCOUNT_PATH)))["user"]?.jsonObject ?: throw DeliveryFailure("contract")
        return DeliveryIdentity(user.text("permissionId"), user.text("emailAddress"))
    }
    override suspend fun folder(id: String): DeliveryFolder {
        if (id != SYNTHETIC_PARENT) throw DeliveryFailure("contract")
        val data = json(checked(DeliveryHttpRequest("GET", PARENT_PATH)))
        val marker = data["appProperties"]?.jsonObject?.text("gymSyntheticM1") ?: throw DeliveryFailure("contract")
        return DeliveryFolder(data.text("name"), marker, data.text("mimeType"))
    }
    override suspend fun reserveFileId(): String {
        val data = json(checked(DeliveryHttpRequest("GET", "/drive/v3/files/generateIds?count=1&space=drive")))
        val ids = data["ids"]?.jsonArray ?: throw DeliveryFailure("contract")
        if (ids.size != 1) throw DeliveryFailure("contract")
        return id(ids.single().jsonPrimitive.content)
    }
    override suspend fun inspect(id: String, expectedParent: String): RemoteDelivery? {
        if (expectedParent != SYNTHETIC_PARENT) throw DeliveryFailure("contract")
        val path = "/drive/v3/files/${id(id)}"
        val response = checked(DeliveryHttpRequest("GET", path+"?fields=id,name,mimeType,parents,appProperties"), setOf(200,404))
        if (response.status == 404) return null
        val metadata = json(response)
        val parents = metadata["parents"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList()
        val marker = metadata["appProperties"]?.jsonObject?.get("gymSyntheticM1")?.jsonPrimitive?.content
        if (metadata.text("id") != id || metadata.text("mimeType") != "application/json" || parents != listOf(expectedParent) ||
            marker != SYNTHETIC_MARKER || !metadata.text("name").startsWith("SYNTHETIC-session-")) throw DeliveryFailure("conflict")
        val content = checked(DeliveryHttpRequest("GET", path+"?alt=media")).body
        return RemoteDelivery(id, "application/json", parents, content)
    }
    override suspend fun create(id: String, parent: String, name: String, canonical: ByteArray) {
        id(id)
        if (parent != SYNTHETIC_PARENT || !name.matches(Regex("SYNTHETIC-session-[-A-Za-z0-9_]+\\.json")) || canonical.size > 8 * 1024 * 1024) throw DeliveryFailure("contract")
        val boundary = "gym-synthetic-${java.util.UUID.randomUUID()}"
        val metadata = buildJsonObject {
            put("id", id); put("name", name); put("mimeType", "application/json")
            put("parents", buildJsonArray { add(parent) })
            put("appProperties", buildJsonObject { put("gymSyntheticM1", SYNTHETIC_MARKER) })
        }.toString()
        val prefix = "--$boundary\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n$metadata\r\n--$boundary\r\nContent-Type: application/json\r\n\r\n".toByteArray(Charsets.UTF_8)
        val body = prefix + canonical + "\r\n--$boundary--\r\n".toByteArray(Charsets.UTF_8)
        checked(DeliveryHttpRequest("POST", "/upload/drive/v3/files?uploadType=multipart&fields=id", "multipart/related; boundary=$boundary", body), setOf(200,409))
        // Caller must verify the exact saved provider ID even after200; never trust only this acknowledgement.
    }
}
