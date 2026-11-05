package dev.gymassistant.drive

import dev.gymassistant.session.*
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import kotlin.test.*

class PersonalDelivery4Test {
    @Test fun immutablePayloadRejectsTraversalCorruptionAndOversize() {
        val root=Files.createTempDirectory("personal-payload-test").toFile()
        try {
            val f=DeliveryPayloadFiles4(root);f.write("valid.json","synthetic")
            assertEquals("synthetic",f.read("valid.json",9,sha256Utf8("synthetic")))
            assertFails {f.write("../outside.json","secret")};assertFails {f.write("valid.json","different")}
            assertFails {f.write("huge.json","x".repeat(DeliveryPayloadFiles4.LIMIT+1))}
            root.resolve("valid.json").writeText("corrupt");assertFails {f.read("valid.json",9,sha256Utf8("synthetic"))}
        }finally {root.deleteRecursively()}
    }
    @Test fun setupIdentityCannotChangeAndVerifiedRootCannotReopen() {
        val root=Files.createTempDirectory("personal-setup-test").toFile()
        try {
            val files=PersonalSetupFiles4(root.resolve("setup.json"));val reserved=PersonalSetupIntent4("id","fixture@example.test","root","personal-m1-test")
            files.save(null,reserved);assertEquals(reserved,files.read())
            assertFails {files.save(reserved,reserved.copy(folderId="other"))}
            val verified=reserved.copy(state="verified");files.save(reserved,verified)
            assertFails {files.save(verified,reserved)};assertEquals(verified,files.read())
        }finally {root.deleteRecursively()}
    }
    private class SetupHttp:DeliveryHttp {
        var folderExists=false;var creates=0;var reservations=0;var loseAck=false;var wrongAccount=false
        var folderId="reserved-root";var marker=""
        override fun request(request:DeliveryHttpRequest):DeliveryHttpResponse {
            fun ok(s:String)=DeliveryHttpResponse(200,s.toByteArray())
            return when {
                request.path==ACCOUNT_PATH->ok("""{"user":{"permissionId":"${if(wrongAccount)"other" else "id"}","emailAddress":"fixture@example.test"}}""")
                request.path.contains("generateIds")-> {reservations++;ok("""{"ids":["$folderId"]}""")}
                request.method=="POST"->{creates++;val metadata=kotlinx.serialization.json.Json.parseToJsonElement(checkNotNull(request.body).toString(Charsets.UTF_8)).toString();marker=Regex("personal-m1-[a-zA-Z0-9-]+").find(metadata)!!.value;folderExists=true;if(loseAck){loseAck=false;throw DeliveryFailure("network")};ok("{}")}
                !folderExists->DeliveryHttpResponse(404)
                else->ok("""{"id":"$folderId","name":"Gym Assistant — PERSONAL M1 DEV","mimeType":"application/vnd.google-apps.folder","appProperties":{"gymPersonalM1":"$marker"},"trashed":false}""")
            }
        }
    }
    @Test fun capturedRootRecoveryNeverCreatesOrRecreatesATopLevelArea()=runBlocking {
        val root=Files.createTempDirectory("personal-root-test").toFile()
        try {
            val files=PersonalSetupFiles4(root.resolve("setup.json"));val http=SetupHttp()
            assertEquals("missingRoot",assertFailsWith<DeliveryFailure>{ensurePersonalRoot4(files,http,"fixture@example.test")}.reason)
            assertNull(files.read());assertEquals(0,http.reservations);assertEquals(0,http.creates)
            // An earlier uncertain create can be read back, but it cannot recreate a missing folder outside the common root.
            files.save(null,PersonalSetupIntent4("id","fixture@example.test",http.folderId,"personal-m1-test"))
            assertEquals("missingRoot",assertFailsWith<DeliveryFailure>{ensurePersonalRoot4(files,http,"fixture@example.test")}.reason)
            assertEquals("reserved",files.read()?.state)
            http.folderExists=true;http.marker="personal-m1-test"
            ensurePersonalRoot4(files,http,"fixture@example.test")
            assertEquals("verified",files.read()?.state);assertEquals(0,http.reservations);assertEquals(0,http.creates)
            http.folderExists=false
            assertEquals("missingRoot",assertFailsWith<DeliveryFailure>{ensurePersonalRoot4(files,http,"fixture@example.test")}.reason)
            assertEquals(0,http.creates)
            http.wrongAccount=true
            assertEquals("accountMismatch",assertFailsWith<DeliveryFailure>{ensurePersonalRoot4(files,http,"fixture@example.test")}.reason)
        }finally {root.deleteRecursively()}
    }
}
