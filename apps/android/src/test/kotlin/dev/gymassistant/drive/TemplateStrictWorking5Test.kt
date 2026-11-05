package dev.gymassistant.drive

import dev.gymassistant.sync.*
import kotlinx.serialization.json.*
import org.junit.Test
import kotlin.test.*

class TemplateStrictWorking5Test {
    private val folders=listOf(TemplateFolderPin5("project-test","my-drive-test","Gym Assistant","gymProjectM1","gym-project-fixture"),
        TemplateFolderPin5("test-area","project-test","test","gymEnvironmentM1","env-test-fixture"),
        TemplateFolderPin5("working-folder","test-area","Current working copy",TEMPLATE_MARKER_KEY_5,"gym-template-fixture"))
    private val namespace=TemplateNamespace5("synthetic-owner","project-test","working-folder",folders)
    private fun step(existing:Boolean=true)=WorkingFileStep("working-file","working-folder","gym-template-fixture","template.yaml","text/plain",
        if(existing)"\"before-tag\"" else null,if(existing)"synthetic-before\n".toByteArray() else null,"synthetic-intended\n".toByteArray())
    private class Journal:WorkingFileJournal {
        var receipts=0
        override fun requireDurable(step:WorkingFileStep){}
        override fun requireCurrentLease(){}
        override fun acknowledge(step:WorkingFileStep,resultTag:String,resultHash:String){assertEquals(step.intendedHash,resultHash);receipts++}
    }
    private inner class Fake(val pin:WorkingFileStep=step()):TemplateHttp5 {
        var bytes=pin.beforeBytes;var tag="\"before-tag\"";var writes=0;var creates=0;var loseAck=false;var create409=false;var stale=false
        var shared=false;var extraGrant=false;var account="synthetic-owner";var changing=false;var reads=0;var invalidMetadata:ByteArray?=null
        val calls=mutableListOf<TemplateHttpRequest5>();val captures=mutableListOf<RemoteObjectCapture5>()
        val remote=GoogleStrictWorkingRemote5(this,namespace,listOf(pin)){captures+=it}
        private fun response(value:JsonObject)=DeliveryHttpResponse(200,value.toString().toByteArray())
        private fun metadata(id:String,parent:String,name:String,mime:String,key:String,marker:String)=buildJsonObject {
            put("id",id);put("title",name);put("mimeType",mime);put("etag",tag);put("ownedByMe",true);put("shared",shared)
            put("labels",buildJsonObject {put("trashed",false)});put("parents",buildJsonArray {add(buildJsonObject {put("id",parent)})})
            put("properties",buildJsonArray {add(buildJsonObject {put("key",key);put("value",marker);put("visibility","PRIVATE")})})
            put("permissions",buildJsonArray {add(buildJsonObject {put("id","synthetic-owner");put("type","user");put("role","owner")})
                if(extraGrant)add(buildJsonObject {put("id","another-test-user");put("type","user");put("role","reader")})})
        }
        override fun request(request:TemplateHttpRequest5):DeliveryHttpResponse {
            calls+=request
            if(request.path==ACCOUNT_PATH)return response(buildJsonObject {put("user",buildJsonObject {put("permissionId",account)})})
            if(request.method in setOf("POST","PUT")) {
                if(request.method=="PUT") {if(stale){tag="\"external-tag\"";return DeliveryHttpResponse(412)};assertEquals(pin.expectedTag,request.ifMatch);writes++}else creates++
                bytes=pin.intendedBytes;tag="\"written-tag\"";if(loseAck)throw DeliveryFailure("network")
                return if(request.method=="POST" && create409)DeliveryHttpResponse(409) else response(buildJsonObject {put("id",pin.objectId)})
            }
            val id=request.path.substringAfter("/drive/v2/files/").substringBefore('?')
            if(request.path.endsWith("?alt=media"))return DeliveryHttpResponse(200,checkNotNull(bytes))
            folders.singleOrNull {it.id==id}?.let {p->return response(metadata(id,p.parent,p.name,"application/vnd.google-apps.folder",p.markerKey,p.marker))}
            assertEquals(pin.objectId,id);if(bytes==null)return DeliveryHttpResponse(404)
            invalidMetadata?.let {return DeliveryHttpResponse(200,it)}
            reads++;if(changing && reads==2)tag="\"changed-during-read\""
            return response(metadata(id,pin.folderId,pin.name,pin.mimeType,TEMPLATE_MARKER_KEY_5,pin.marker))
        }
        fun run(j:Journal=Journal())=GuardedWorkingFile(remote,j).execute(pin)
    }
    @Test fun conditionalWriteAndLostAcknowledgementReuseSameFileAndRetainOriginalBytes() {
        val f=Fake();val j=Journal();f.loseAck=true;assertFails {f.run(j)};assertEquals(0,j.receipts)
        f.loseAck=false;f.run(j);assertEquals(1,f.writes);assertEquals(0,f.creates);assertEquals(1,j.receipts)
        assertTrue(f.captures.any {it.contentBytes?.contentEquals(f.pin.beforeBytes!!)==true})
    }
    @Test fun initial409UsesSameIdReadbackButStaleOrMissingExistingFileCannotCreate() {
        val f=Fake(step(false));f.create409=true;f.run();assertEquals(1,f.creates);assertEquals(0,f.writes)
        val g=Fake();g.stale=true;assertEquals("conflict",assertFailsWith<DeliveryFailure> {g.run()}.reason);assertEquals(0,g.creates)
        val missing=Fake();missing.bytes=null;assertFails {missing.run()};assertEquals(0,missing.creates)
    }
    @Test fun wrongAccountOrEffectiveAccessStopsBeforeContentAndFailedRecheckRevokesReads() {
        for(mode in 0..2){val f=Fake();when(mode){0->f.account="another-test-owner";1->f.shared=true;2->f.extraGrant=true}
            assertFails {f.run()};assertTrue(f.calls.none {it.path.endsWith("?alt=media")})}
        val f=Fake();f.remote.verifyNamespace();f.extraGrant=true;assertFails {f.remote.verifyNamespace()};val calls=f.calls.size
        assertFails {f.remote.read(f.pin.objectId)};assertEquals(calls,f.calls.size)
    }
    @Test fun malformedAndChangingRawResponsesAreCapturedBeforeValidationFailure() {
        for(mode in 0..2){val f=Fake();when(mode){0->f.invalidMetadata="{bad-json".toByteArray();1->f.bytes=byteArrayOf(0xC3.toByte(),0x28);2->f.changing=true}
            assertFails {f.run()};assertEquals(0,f.writes);assertEquals(0,f.creates)
            assertTrue(f.captures.any {it.kind==RemoteObjectKind5.WORKING})
            if(mode==1)assertTrue(f.captures.any {it.contentBytes?.contentEquals(f.bytes!!)==true})}
    }
    @Test fun failedDurableSinkPreventsAnyWorkingMutation() {
        val f=Fake(step(false));var retained=0
        val remote=GoogleStrictWorkingRemote5(f,namespace,listOf(f.pin)){if(it.kind==RemoteObjectKind5.WORKING){retained++;error("Synthetic retention failure")}}
        assertFails {GuardedWorkingFile(remote,Journal()).execute(f.pin)};assertEquals(1,retained);assertEquals(0,f.creates)
    }
    @Test fun socketGateAllowsOnlyCapturedStrongTagBodyAndReservedMultipartIdentity() {
        val pin=step();var attempts=0;val http=GoogleStrictWorkingHttp5("synthetic",{attempts++;error("Synthetic socket forbidden")},namespace,listOf(pin))
        val valid=TemplateHttpRequest5("PUT","/upload/drive/v2/files/${pin.objectId}?uploadType=media&fields=id",pin.mimeType,pin.intendedBytes,pin.expectedTag)
        assertFails {http.request(valid)};assertEquals(1,attempts)
        for(r in listOf(valid.copy(ifMatch="*"),valid.copy(body="another".toByteArray()),valid.copy(contentType="application/json"),
            valid.copy(path="/upload/drive/v2/files/project-test?uploadType=media&fields=id"),TemplateHttpRequest5("GET","/drive/v2/files/project-test?alt=media")))assertFails {http.request(r)}
        assertEquals(1,attempts);http.close()
        val f=Fake(step(false));f.run();val request=f.calls.single {it.method=="POST"}
        val create=GoogleStrictWorkingHttp5("synthetic",{attempts++;error("Synthetic socket forbidden")},namespace,listOf(f.pin))
        assertFails {create.request(request)};assertEquals(2,attempts)
        val body=request.body!!.toString(Charsets.UTF_8)
        for(changed in listOf(body.replace("working-file","another-file"),body.replace("synthetic-intended","unreviewed-content")))assertFails {create.request(request.copy(body=changed.toByteArray()))}
        assertFails {create.request(valid.copy(ifMatch="\"before-tag\""))};assertEquals(2,attempts);create.close()
        assertFails {GoogleStrictWorkingHttp5("synthetic",{},namespace,listOf(WorkingFileStep("working-file","working-folder","gym-template-fixture","template.yaml","text/plain","*","before".toByteArray(),"next".toByteArray())))}
    }
}
