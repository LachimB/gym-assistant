package dev.gymassistant.drive

import kotlinx.coroutines.runBlocking
import kotlin.test.*

class GoogleDeliveryRemoteTest {
    private val marker = SYNTHETIC_MARKER
    @Test fun simplifiedNameWithoutProjectVerificationIsNotALegacyFallback()=runBlocking<Unit> {
        val remote=GoogleDeliveryRemote {DeliveryHttpResponse(200,"""{"name":"SYNTHETIC M1","mimeType":"application/vnd.google-apps.folder","appProperties":{"gymSyntheticM1":"$marker"}}""".toByteArray())}
        assertEquals("missingRoot",assertFailsWith<DeliveryFailure>{remote.folder(SYNTHETIC_PARENT)}.reason)
    }
    @Test fun exact404IsAbsenceBut403NeverCreatesOrReadsContent() = runBlocking {
        for (status in listOf(404,403)) {
            var calls = 0
            val remote = GoogleDeliveryRemote { calls++; DeliveryHttpResponse(status) }
            if (status == 404) assertNull(remote.inspect("reserved",SYNTHETIC_PARENT))
            else try { remote.inspect("reserved",SYNTHETIC_PARENT); fail("Expected permission error") }
                catch (failure: DeliveryFailure) { assertEquals("permission",failure.reason) }
            assertEquals(1,calls)
        }
    }
    @Test fun invalidRemoteMetadataStopsBeforeReadingAnyFileContents() = runBlocking {
        var calls = 0
        val remote = GoogleDeliveryRemote { calls++; DeliveryHttpResponse(200,"""{"id":"reserved","name":"private.json","mimeType":"application/json","parents":["$SYNTHETIC_PARENT"],"appProperties":{"gymSyntheticM1":"$marker"}}""".toByteArray()) }
        try { remote.inspect("reserved",SYNTHETIC_PARENT); fail("Expected conflict") }
        catch (failure: DeliveryFailure) { assertEquals("conflict",failure.reason) }
        assertEquals(1,calls)
    }
    @Test fun canonicalBytesAreReadOnlyAfterExactSyntheticMetadata() = runBlocking {
        val requests = mutableListOf<DeliveryHttpRequest>()
        val remote = GoogleDeliveryRemote { request ->
            requests += request
            DeliveryHttpResponse(200,if (request.path.endsWith("alt=media")) "exact bytes".toByteArray() else
                """{"id":"reserved","name":"SYNTHETIC-session-sample.json","mimeType":"application/json","parents":["$SYNTHETIC_PARENT"],"appProperties":{"gymSyntheticM1":"$marker"}}""".toByteArray())
        }
        assertContentEquals("exact bytes".toByteArray(),remote.inspect("reserved",SYNTHETIC_PARENT)?.content)
        assertEquals(2,requests.size)
    }
    @Test fun duplicateCreateIsOnlyAReasonToVerifyTheSameSavedId() = runBlocking {
        var sent: DeliveryHttpRequest? = null
        val remote = GoogleDeliveryRemote { request -> sent = request; DeliveryHttpResponse(409) }
        remote.create("reserved",SYNTHETIC_PARENT,"SYNTHETIC-session-sample.json","canonical bytes".toByteArray())
        val request = assertNotNull(sent)
        assertEquals("POST",request.method)
        assertEquals("/upload/drive/v3/files?uploadType=multipart&fields=id",request.path)
        val payload = assertNotNull(request.body).toString(Charsets.UTF_8)
        assertTrue(payload.contains("\"id\":\"reserved\"")); assertTrue(payload.contains("canonical bytes"))
        assertTrue(payload.contains(marker))
    }
    @Test fun forbiddenIdsParentsAndNamesCannotReachTheTransport() = runBlocking {
        var calls=0
        val remote = GoogleDeliveryRemote { calls++; DeliveryHttpResponse(200) }
        for (operation in listOf<suspend () -> Unit>(
            { remote.inspect("https://other.example/",SYNTHETIC_PARENT) },
            { remote.inspect("reserved","different-parent") },
            { remote.folder("different-parent") },
            { remote.create("reserved",SYNTHETIC_PARENT,"personal.json",byteArrayOf()) },
        )) {
            try { operation(); fail("Expected rejection") } catch (failure: DeliveryFailure) { assertEquals("contract",failure.reason) }
        }
        assertEquals(0,calls)
    }
    @Test fun transportRejectsCredentialRedirectPathsAndClosedTokenWithoutNetwork() {
        var reserved=0
        val http=GoogleDeliveryHttp("synthetic-token") { reserved++ }
        for (path in listOf("https://other.example/drive/v3/files", "/drive/v3/files/../private", "/oauth2/v3/userinfo")) {
            assertFailsWith<IllegalArgumentException> { http.request(DeliveryHttpRequest("GET",path)) }
        }
        http.close()
        assertFailsWith<IllegalStateException> { http.request(DeliveryHttpRequest("GET",ACCOUNT_PATH)) }
        assertEquals(0,reserved)
    }
    @Test fun retryAfterSupportsBothSecondsAndHttpDatesWithinTheBatchBound() {
        val now=java.time.Instant.parse("2026-11-03T10:00:00Z")
        assertEquals(12000L,retryAfterDelayMs("12",now))
        assertEquals(15000L,retryAfterDelayMs("Tue, 03 Nov 2026 10:00:15 GMT",now))
        assertEquals(30000L,retryAfterDelayMs("999999",now))
        assertEquals(0L,retryAfterDelayMs("Tue, 03 Nov 2026 09:00:00 GMT",now))
        assertNull(retryAfterDelayMs("-1",now));assertNull(retryAfterDelayMs("invalid",now))
    }
    @Test fun failedRequestReservationPreventsAnyNetworkTransmission() {
        val http=GoogleDeliveryHttp("synthetic-token") { throw DeliveryFailure("budget") }
        val failure=assertFailsWith<DeliveryFailure> { http.request(DeliveryHttpRequest("GET",ACCOUNT_PATH)) }
        assertEquals("budget",failure.reason);http.close()
    }
    @Test fun providerFailureBodyNeverAppearsInReportedError() = runBlocking {
        val remote = GoogleDeliveryRemote { DeliveryHttpResponse(401,"credential-looking-provider-error".toByteArray()) }
        try { remote.identity(); fail("Expected authorization error") }
        catch (failure: DeliveryFailure) { assertEquals("authorization",failure.message) }
    }
}
