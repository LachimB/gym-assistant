package dev.gymassistant.drive

import kotlinx.coroutines.runBlocking
import kotlin.test.*

class PersonalDeliveryRemote4Test {
    private class Http(var responses:MutableList<DeliveryHttpResponse>):DeliveryHttp {
        val calls=mutableListOf<DeliveryHttpRequest>()
        override fun request(request:DeliveryHttpRequest):DeliveryHttpResponse {calls+=request;return responses.removeAt(0)}
    }
    private fun ok(value:String)=DeliveryHttpResponse(200,value.toByteArray())
    private fun file(parent:String="personal-root",marker:String="personal-m1-test",trashed:Boolean=false,mime:String="application/json")=
        """{"id":"saved-id","name":"PERSONAL-session-test.json","mimeType":"$mime","parents":["$parent"],"appProperties":{"gymPersonalM1":"$marker"},"trashed":$trashed}"""
    @Test fun wrongNamespaceCannotConstructAndChangedMetadataCannotReadContent()=runBlocking {
        assertFails {PersonalDeliveryRemote4(Http(mutableListOf()),SYNTHETIC_PARENT,"personal-m1-test")}
        for(metadata in listOf(file(parent="other"),file(marker="other"),file(trashed=true),file(mime="text/plain"))) {
            val http=Http(mutableListOf(ok(metadata)));val remote=PersonalDeliveryRemote4(http,"personal-root","personal-m1-test")
            assertEquals("conflict",assertFailsWith<DeliveryFailure>{remote.inspect("saved-id","personal-root")}.reason)
            assertEquals(1,http.calls.size)
        }
    }
    @Test fun onlyExactId404IsAbsenceAndPermissionsNeverCreateReplacement()=runBlocking {
        val http=Http(mutableListOf(DeliveryHttpResponse(404),DeliveryHttpResponse(403),DeliveryHttpResponse(401),DeliveryHttpResponse(429,retryAfterMs=2000)))
        val remote=PersonalDeliveryRemote4(http,"personal-root","personal-m1-test")
        assertNull(remote.inspect("saved-id","personal-root"))
        for(reason in listOf("permission","authorization","rateLimited"))assertEquals(reason,assertFailsWith<DeliveryFailure>{remote.inspect("saved-id","personal-root")}.reason)
        assertTrue(http.calls.all {it.method=="GET"})
    }
    @Test fun exactContentIsReadAndCreate409IsNotTreatedAsDeliveryAcknowledgement()=runBlocking {
        val http=Http(mutableListOf(ok(file()),ok("synthetic content"),DeliveryHttpResponse(409)))
        val remote=PersonalDeliveryRemote4(http,"personal-root","personal-m1-test")
        assertEquals("synthetic content",remote.inspect("saved-id","personal-root")?.content?.toString(Charsets.UTF_8))
        remote.create("saved-id","personal-root","PERSONAL-session-test.json","synthetic content".toByteArray())
        assertEquals(3,http.calls.size);val post=http.calls.last();assertEquals("POST",post.method)
        val body=checkNotNull(post.body).toString(Charsets.UTF_8)
        assertTrue(body.contains("gymPersonalM1") && body.contains("saved-id") && !body.contains("gymSyntheticM1"))
        assertFailsWith<DeliveryFailure>{remote.create("new","other","PERSONAL-session-test.json",byteArrayOf(1))}
        Unit
    }
}
