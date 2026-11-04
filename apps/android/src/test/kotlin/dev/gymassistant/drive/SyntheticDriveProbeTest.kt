package dev.gymassistant.drive

import kotlin.test.*

class SyntheticDriveProbeTest {
    private val email = "synthetic-owner@example.invalid"
    private val identity = """{"user":{"emailAddress":"$email","permissionId":"synthetic-id"}}"""
    private val parent = """{"name":"Gym Assistant — SYNTHETIC M1 TEST — fixture","mimeType":"application/vnd.google-apps.folder","appProperties":{"gymSyntheticM1":"fixture"}}"""
    private val child = """{"id":"$SYNTHETIC_CHILD","name":"SYNTHETIC-external-child.json","mimeType":"application/json","parents":["$SYNTHETIC_PARENT"]}"""
    private fun probe(responses: List<ProbeResponse>, seen: MutableList<String> = mutableListOf()): String =
        runSyntheticDriveProbe("fake-token", email, ProbeTransport { token, path ->
            assertEquals("fake-token", token)
            seen += path
            responses[seen.size - 1]
        })

    @Test fun onlyExactFileScopeIsAccepted() {
        assertTrue(exactDriveScope(listOf(DRIVE_FILE_SCOPE)))
        for (scope in listOf(emptyList(), listOf("openid"), listOf(DRIVE_FILE_SCOPE, "profile"))) assertFalse(exactDriveScope(scope))
    }
    @Test fun identityIsVerifiedBeforeReadingOnlySyntheticMetadata() {
        val seen = mutableListOf<String>()
        val result = probe(listOf(ProbeResponse(200, identity), ProbeResponse(200, parent), ProbeResponse(200, child, true)), seen)
        assertEquals(listOf(ACCOUNT_PATH, PARENT_PATH, CHILD_PATH), seen)
        assertTrue("Native client can read an ETag" in result)
        assertFalse("fake-token" in result)
        assertFalse("synthetic-id" in result)
        assertFalse(email in result)
    }
    @Test fun unavailableChildGrantDoesNotWidenScopeOrReadContents() {
        val seen = mutableListOf<String>()
        val result = probe(listOf(ProbeResponse(200, identity), ProbeResponse(200, parent), ProbeResponse(404)), seen)
        assertEquals(3, seen.size)
        assertTrue("not accessible on Android" in result)
    }
    @Test fun unexpectedAccountStopsBeforeAnyFileRequest() {
        val seen = mutableListOf<String>()
        assertFails { probe(listOf(ProbeResponse(200, identity.replace(email, "other@example.invalid"))), seen) }
        assertEquals(listOf(ACCOUNT_PATH), seen)
    }
    @Test fun unexpectedParentStopsBeforeChildRequest() {
        val seen = mutableListOf<String>()
        assertFails { probe(listOf(ProbeResponse(200, identity), ProbeResponse(200, parent.replace("Gym Assistant", "Other folder"))), seen) }
        assertEquals(listOf(ACCOUNT_PATH, PARENT_PATH), seen)
    }
    @Test fun unexpectedChildNameMimeOrParentIsRejected() {
        for (changed in listOf(child.replace("SYNTHETIC-external-child.json", "personal.pdf"),
                               child.replace("application/json", "application/pdf"),
                               child.replace(SYNTHETIC_PARENT, "other-parent"))) {
            assertFails { probe(listOf(ProbeResponse(200, identity), ProbeResponse(200, parent), ProbeResponse(200, changed))) }
        }
    }
    @Test fun authorizationOrRedirectFailureIsNotRetried() {
        for (code in listOf(401, 403, 302, 500)) {
            val seen = mutableListOf<String>()
            assertFails { probe(listOf(ProbeResponse(code)), seen) }
            assertEquals(1, seen.size)
        }
    }
    @Test fun transportRejectsOtherEndpointsBeforeBudgetOrNetwork() {
        var reserved = 0
        val transport = GoogleProbeTransport { reserved++ }
        for (path in listOf("https://other.example", "/drive/v3/files/private-file", "/oauth2/v3/userinfo", "/drive/v3/files/../about")) {
            assertFails { transport.get("fake-token", path) }
        }
        assertEquals(0, reserved)
    }
    @Test fun publicPlaceholderConfigurationCannotConsumeALiveRequest() {
        if (syntheticResourcesConfigured()) return // Private builds use separate owner-reviewed pins.
        var reserved = 0
        val transport = GoogleProbeTransport { reserved++ }
        assertFails { transport.get("fake-token", ACCOUNT_PATH) }
        assertEquals(0, reserved)
    }
}
