package dev.gymassistant.drive

import dev.gymassistant.sync.*
import kotlinx.serialization.json.*
import org.junit.Test
import kotlin.test.*

class TemplateWorkingRemote5Test {
    private val before="name: synthetic before\n".toByteArray()
    private val after="name: synthetic after\n".toByteArray()
    private val folders=listOf(
        TemplateFolderPin5("project-fixture","account-root","Gym Assistant","gymProjectM1","gym-project-fixture"),
        TemplateFolderPin5("personal-fixture","project-fixture","Personal fixture","gymPersonalM1","personal-m1-fixture"),
        TemplateFolderPin5("working-fixture","personal-fixture","Current working copy","gymTemplateM1","gym-template-fixture"))
    private val namespace=TemplateNamespace5("synthetic-permission","project-fixture","working-fixture",folders)
    private fun step(existing:Boolean=true)=WorkingFileStep("file-fixture","working-fixture","gym-template-fixture","exercise.yaml","text/plain",if(existing)"\"before-tag\"" else null,if(existing)before else null,after)
    private fun response(value:JsonElement)=DeliveryHttpResponse(200,value.toString().toByteArray())
    private inner class Fake(existing:Boolean=true):TemplateHttp5 {
        val calls=mutableListOf<TemplateHttpRequest5>()
        var exists=existing;var bytes=before;var tag="\"before-tag\"";var updates=0;var creates=0;var metadataReads=0
        var account="synthetic-permission";var missingFolder:String?=null;var outsideFolder=false;var outsideFile=false
        var changeDuringRead=false;var changeBeforePut=false;var loseAck=false;var createConflict=false
        var badMetadata:ByteArray?=null
        var quotedFolderFlag=false;var quotedFileFlag=false
        override fun request(request:TemplateHttpRequest5):DeliveryHttpResponse {
            calls+=request
            if(request.path==ACCOUNT_PATH)return response(buildJsonObject {put("user",buildJsonObject {put("permissionId",account)})})
            for(folder in folders)if(request.path=="/drive/v3/files/${folder.id}?fields=$TEMPLATE_FOLDER_FIELDS_5") {
                if(missingFolder==folder.id)return DeliveryHttpResponse(404)
                return response(buildJsonObject {put("id",folder.id);put("name",folder.name);put("mimeType","application/vnd.google-apps.folder")
                    put("parents",buildJsonArray {add(if(outsideFolder && folder.id=="working-fixture")"outside-fixture" else folder.parent)})
                    put("ownedByMe",if(quotedFolderFlag)JsonPrimitive("true") else JsonPrimitive(true));put("trashed",false);put("appProperties",buildJsonObject {put(folder.markerKey,folder.marker)})})
            }
            if(request.method=="GET" && request.path.contains("?fields=$TEMPLATE_FILE_FIELDS_5")) {
                metadataReads++;if(!exists)return DeliveryHttpResponse(404)
                badMetadata?.let {return DeliveryHttpResponse(200,it)}
                if(changeDuringRead && metadataReads==2)tag="\"changed-during-read\""
                return response(buildJsonObject {put("id","file-fixture");put("title","exercise.yaml");put("mimeType","text/plain");put("ownedByMe",if(quotedFileFlag)JsonPrimitive("true") else JsonPrimitive(true))
                    put("parents",buildJsonArray {add(buildJsonObject {put("id",if(outsideFile)"outside-fixture" else "working-fixture")})})
                    put("labels",buildJsonObject {put("trashed",false)});put("etag",tag)
                    put("properties",buildJsonArray {add(buildJsonObject {put("key",TEMPLATE_MARKER_KEY_5);put("value","gym-template-fixture");put("visibility","PRIVATE")})})})
            }
            if(request.method=="GET" && request.path.endsWith("?alt=media"))return DeliveryHttpResponse(200,bytes)
            if(request.method=="PUT") {
                if(changeBeforePut){tag="\"external-tag\"";bytes="name: external edit\n".toByteArray()}
                if(request.ifMatch!=tag)return DeliveryHttpResponse(412)
                updates++;bytes=checkNotNull(request.body).copyOf();tag="\"after-tag\""
                if(loseAck)throw DeliveryFailure("network")
                return response(buildJsonObject {put("id","file-fixture")})
            }
            if(request.method=="POST") {
                val body=checkNotNull(request.body).toString(Charsets.UTF_8)
                val metadata=Json.parseToJsonElement(body.substringAfter("\r\n\r\n").substringBefore("\r\n--")).jsonObject
                assertEquals("file-fixture",metadata.getValue("id").jsonPrimitive.content)
                assertEquals(listOf("working-fixture"),metadata.getValue("parents").jsonArray.map {it.jsonPrimitive.content})
                assertTrue(body.contains(after.toString(Charsets.UTF_8)))
                creates++;exists=true;bytes=after.copyOf();tag="\"created-tag\""
                if(loseAck)throw DeliveryFailure("network")
                return if(createConflict)DeliveryHttpResponse(409) else response(buildJsonObject {put("id","file-fixture")})
            }
            error("Unexpected synthetic request")
        }
    }
    private class Journal:WorkingFileJournal {
        var acknowledged=0
        override fun requireDurable(step:WorkingFileStep){}
        override fun requireCurrentLease(){}
        override fun acknowledge(step:WorkingFileStep,resultTag:String,resultHash:String){assertEquals(step.intendedHash,resultHash);acknowledged++}
    }
    @Test fun conditionalYamlWriteHasExactTagAndLostAckReusesTheSameFile() {
        val fake=Fake();val pin=step();val remote=GoogleTemplateWorkingRemote5(fake,namespace,listOf(pin));val journal=Journal()
        fake.loseAck=true;assertFailsWith<DeliveryFailure> {GuardedWorkingFile(remote,journal).execute(pin)}
        assertEquals(0,journal.acknowledged);fake.loseAck=false;GuardedWorkingFile(remote,journal).execute(pin)
        assertEquals(1,fake.updates);assertEquals(1,journal.acknowledged);assertEquals(0,fake.creates)
        assertEquals("\"before-tag\"",fake.calls.single {it.method=="PUT"}.ifMatch)
    }
    @Test fun staleTagNeverFallsBackToAnUnconditionalWrite() {
        val fake=Fake();fake.changeBeforePut=true;val pin=step();val journal=Journal()
        val failure=assertFailsWith<DeliveryFailure> {GuardedWorkingFile(GoogleTemplateWorkingRemote5(fake,namespace,listOf(pin)),journal).execute(pin)}
        assertEquals("conflict",failure.reason);assertEquals(0,fake.updates);assertEquals(0,journal.acknowledged)
        assertEquals(1,fake.calls.count {it.method=="PUT"});assertTrue(fake.calls.none {it.method=="POST"})
        assertContentEquals("name: external edit\n".toByteArray(),fake.bytes)
    }
    @Test fun lostCreateOrDuplicateAcknowledgementAlwaysReadsTheReservedId() {
        for(uncertain in listOf(true,false)) {
            val fake=Fake(false);fake.loseAck=uncertain;fake.createConflict=!uncertain;val pin=step(false);val journal=Journal()
            val remote=GoogleTemplateWorkingRemote5(fake,namespace,listOf(pin))
            if(uncertain){assertFailsWith<DeliveryFailure> {GuardedWorkingFile(remote,journal).execute(pin)};fake.loseAck=false}
            GuardedWorkingFile(remote,journal).execute(pin)
            assertEquals(1,fake.creates);assertEquals(1,journal.acknowledged);assertEquals(0,fake.updates)
        }
    }
    @Test fun accountRootAndParentMismatchesStopBeforeMutationOrFallbackCreation() {
        for(reason in 0..3){
            val fake=Fake();when(reason){0->fake.account="another-synthetic-permission";1->fake.missingFolder="project-fixture";2->fake.outsideFolder=true;3->fake.outsideFile=true}
            val pin=step();assertFailsWith<DeliveryFailure> {GuardedWorkingFile(GoogleTemplateWorkingRemote5(fake,namespace,listOf(pin)),Journal()).execute(pin)}
            assertTrue(fake.calls.none {it.method in setOf("POST","PUT")})
        }
    }
    @Test fun changingReadOrMalformedMetadataCannotBecomeAnAcknowledgedSnapshot() {
        for(reason in 0..3){
            val fake=Fake();when(reason){0->fake.changeDuringRead=true;1->fake.badMetadata=byteArrayOf(0xff.toByte());2->fake.badMetadata="{\"id\":\"file-fixture\",\"id\":\"duplicate\"}".toByteArray();3->fake.bytes=ByteArray(1048577)}
            val remote=GoogleTemplateWorkingRemote5(fake,namespace,listOf(step()))
            remote.verifyNamespace()
            assertFailsWith<DeliveryFailure> {remote.read("file-fixture")};assertEquals(0,fake.updates)
        }
    }
    @Test fun unknownObjectsAndRetargetedConditionalTagsAreRejectedLocally() {
        val fake=Fake();val remote=GoogleTemplateWorkingRemote5(fake,namespace,listOf(step()))
        assertFailsWith<DeliveryFailure> {remote.read("unknown-fixture")}
        assertFailsWith<DeliveryFailure> {remote.replace("file-fixture","\"different-captured-tag\"",after)}
        assertTrue(fake.calls.isEmpty())
    }
    @Test fun namespacePinsCannotContainCyclesOrOmitTheCommonRoot() {
        assertFails {TemplateNamespace5("synthetic-permission","project-fixture","working-fixture",folders.map {if(it.id=="personal-fixture")it.copy(parent="working-fixture")else it})}
        assertFails {TemplateNamespace5("synthetic-permission","unknown-fixture","working-fixture",folders)}
    }
    @Test fun stringBooleansCannotMasqueradeAsVerifiedOwnership() {
        val folder=Fake();folder.quotedFolderFlag=true
        assertEquals("contract",assertFailsWith<DeliveryFailure> {GoogleTemplateWorkingRemote5(folder,namespace,listOf(step())).verifyNamespace()}.reason)
        val file=Fake();val remote=GoogleTemplateWorkingRemote5(file,namespace,listOf(step()));remote.verifyNamespace();file.quotedFileFlag=true
        assertEquals("contract",assertFailsWith<DeliveryFailure> {remote.read("file-fixture")}.reason)
        assertTrue(file.calls.none {it.path.endsWith("?alt=media") || it.method in setOf("PUT","POST")})
    }
    @Test fun directSourceAccessRequiresVerifiedNamespaceAndFailedRecheckRevokesIt() {
        val fake=Fake();val pin=step();val remote=GoogleTemplateWorkingRemote5(fake,namespace,listOf(pin))
        assertFailsWith<DeliveryFailure> {remote.read(pin.objectId)}
        assertFailsWith<DeliveryFailure> {remote.replace(pin.objectId,checkNotNull(pin.expectedTag),after)}
        assertTrue(fake.calls.isEmpty())
        remote.verifyNamespace();fake.outsideFolder=true;assertFailsWith<DeliveryFailure> {remote.verifyNamespace()}
        val count=fake.calls.size;assertFailsWith<DeliveryFailure> {remote.read(pin.objectId)};assertEquals(count,fake.calls.size)
    }
    @Test fun nativeTransportRejectsUnknownPathsWildcardTagsAndFolderWritesBeforeAnySocket() {
        var attempts=0
        val http=GoogleTemplateHttp5("synthetic",{attempts++},setOf("file-fixture","folder-fixture"),setOf("file-fixture"))
        for(request in listOf(
            TemplateHttpRequest5("GET","https://outside.example.test/"),
            TemplateHttpRequest5("GET","/drive/v2/files/unknown-fixture?alt=media"),
            TemplateHttpRequest5("PUT","/upload/drive/v2/files/file-fixture?uploadType=media&fields=id","text/plain",after),
            TemplateHttpRequest5("PUT","/upload/drive/v2/files/file-fixture?uploadType=media&fields=id","text/plain",after,"*"),
            TemplateHttpRequest5("PUT","/upload/drive/v2/files/folder-fixture?uploadType=media&fields=id","text/plain",after,"\"tag\""),
            TemplateHttpRequest5("POST","/drive/v3/files?fields=id","application/json","{}".toByteArray()),
        ))assertFails {http.request(request)}
        http.close();assertFails {http.request(TemplateHttpRequest5("GET",ACCOUNT_PATH))};assertEquals(0,attempts)
    }
}
