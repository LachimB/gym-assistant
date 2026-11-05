package dev.gymassistant.drive

import dev.gymassistant.engine.v2.*
import dev.gymassistant.session.*
import dev.gymassistant.sync.*
import java.io.File
import kotlinx.serialization.json.*
import org.junit.Test
import kotlin.test.*

class TemplateImmutableRemote5Test {
    private val root=File(checkNotNull(System.getProperty("gym.root")))
    private fun schema(name:String)=Json.parseToJsonElement(root.resolve("schemas/$name").readText()).jsonObject
    private val contracts=ImportContracts2(mapOf("1.0.0" to schema("template.v1.schema.json"),"1.1.0" to schema("template.v1.1.schema.json")),schema("exercise.v1.schema.json"))
    private val manifest=TemplateSyncContracts5.manifest(root.resolve("examples/template-sync/synthetic-revision.json").readText())
    private val fixture=Json.parseToJsonElement(root.resolve("examples/sessions/synthetic-runtime-v2-session.json").readText()).jsonObject
    private val sources=sessionJson.decodeFromString<TemplateSources3>(fixture.getValue("sourcePlanSnapshot").jsonObject.getValue("sourceSnapshotJson").jsonPrimitive.content)
    private val payloads=manifest.files.associate {it.objectId to (if(it.role==SourceRole.TEMPLATE)sources.templateYaml else sources.exerciseYamls.single()).toByteArray()}
    private val folders=listOf(
        TemplateFolderPin5("project-test","my-drive-test","Gym Assistant","gymProjectM1","gym-project-fixture"),
        TemplateFolderPin5("dev-test","project-test","dev","gymEnvironmentM1","gym-dev-fixture"),
        TemplateFolderPin5("revision-test","dev-test","revision fixture","gymTemplateM1","gym-template-fixture"))
    private val namespace=ImmutableRevisionNamespace5("synthetic-owner","project-test","revision-test",folders)
    private fun revision(m:TemplateRevisionManifest=manifest,bytes:Map<String,ByteArray> = payloads)=ImmutableRevisionFiles5(namespace,m,"manifest-test","gym-template-fixture",bytes,contracts)
    private class Journal:ImmutableFileJournal {
        var acknowledged=0;var leased=true
        override fun requireDurable(step:ImmutableFileStep){}
        override fun requireCurrentLease(){check(leased)}
        override fun requireCreatePending(step:ImmutableFileStep){}
        override fun acknowledge(step:ImmutableFileStep,resultTag:String){acknowledged++}
    }
    private inner class Fake(val revision:ImmutableRevisionFiles5=revision()):TemplateHttp5 {
        val pins=revision.objects.associateBy {it.objectId}
        val bytes=mutableMapOf<String,ByteArray>();val tags=mutableMapOf<String,String>();val calls=mutableListOf<TemplateHttpRequest5>()
        val reads=mutableMapOf<String,Int>();var posts=0;var loseAck=false;var race409=false;var account=namespace.permissionId
        var badFolder=false;var sharedFile=false;var badOwner=false;var changeRead=false;var badMetadata:ByteArray?=null
        fun retain(pin:ImmutableFileStep){bytes[pin.objectId]=pin.bytes;tags[pin.objectId]="\"original\""}
        private fun response(d:JsonObject)=DeliveryHttpResponse(200,d.toString().toByteArray())
        private fun metadata(id:String,parent:String,name:String,mime:String,key:String,marker:String,tag:String)=buildJsonObject {
            put("id",id);put("title",name);put("mimeType",mime);put("parents",buildJsonArray {add(buildJsonObject {put("id",parent)})})
            put("etag",tag);put("ownedByMe",true);put("shared",sharedFile && id in pins);put("labels",buildJsonObject {put("trashed",false)})
            put("properties",buildJsonArray {add(buildJsonObject {put("key",key);put("value",marker);put("visibility","PRIVATE")})})
            put("permissions",buildJsonArray {add(buildJsonObject {put("id",if(badOwner)"another-test-owner" else namespace.permissionId);put("type","user");put("role","owner")})})
        }
        override fun request(request:TemplateHttpRequest5):DeliveryHttpResponse {
            calls+=request
            if(request.path==ACCOUNT_PATH)return response(buildJsonObject {put("user",buildJsonObject {put("permissionId",account)})})
            if(request.method=="POST"){
                posts++;val text=checkNotNull(request.body).toString(Charsets.UTF_8);val metadata=Json.parseToJsonElement(text.substringAfter("\r\n\r\n").substringBefore("\r\n--")).jsonObject
                val pin=checkNotNull(pins[metadata.getValue("id").jsonPrimitive.content]);assertEquals(pin.folderId,metadata.getValue("parents").jsonArray.single().jsonPrimitive.content)
                retain(pin);tags[pin.objectId]="\"created\"";if(loseAck)throw DeliveryFailure("network")
                return if(race409)DeliveryHttpResponse(409) else response(buildJsonObject {put("id",pin.objectId)})
            }
            val id=request.path.substringAfter("/drive/v2/files/").substringBefore('?')
            if(request.path.endsWith("?alt=media"))return DeliveryHttpResponse(200,checkNotNull(bytes[id]))
            folders.singleOrNull {it.id==id}?.let {p->return response(metadata(p.id,if(badFolder)p.id else p.parent,p.name,"application/vnd.google-apps.folder",p.markerKey,p.marker,"\"folder-tag\""))}
            val pin=checkNotNull(pins[id]);if(id !in bytes)return DeliveryHttpResponse(404)
            badMetadata?.let {return DeliveryHttpResponse(200,it)}
            reads[id]=(reads[id]?:0)+1;if(changeRead && reads[id]==2)tags[id]="\"external\""
            return response(metadata(id,pin.folderId,pin.name,pin.mimeType,TEMPLATE_MARKER_KEY_5,pin.marker,checkNotNull(tags[id])))
        }
    }
    @Test fun rawImmutableResponsesAreRetainedBeforeValidationAndSinkFailurePreventsCreation() {
        val r=revision();val fake=Fake(r);val captured=mutableListOf<RemoteObjectCapture5>();val pin=r.files.first()
        fake.retain(pin);fake.bytes[pin.objectId]=byteArrayOf(0xC3.toByte(),0x28)
        val remote=GoogleTemplateImmutableRemote5(fake,r){captured+=it}
        assertFails {GuardedImmutableFile(remote,Journal()).execute(pin)}
        assertTrue(captured.any {it.kind==RemoteObjectKind5.IMMUTABLE && it.contentBytes?.contentEquals(fake.bytes[pin.objectId]!!)==true})
        assertEquals(0,fake.posts)
        val missing=Fake(r);var retained=0
        val failing=GoogleTemplateImmutableRemote5(missing,r){if(it.kind==RemoteObjectKind5.IMMUTABLE){retained++;error("Synthetic retention failure")}}
        assertFails {GuardedImmutableFile(failing,Journal()).execute(pin)};assertEquals(1,retained);assertEquals(0,missing.posts)
    }
    @Test fun sourceAndJsonManifestCreateAndLostAcknowledgementsReuseExactlyTheSameReservedObjects() {
        val r=revision();val fake=Fake(r);val remote=GoogleTemplateImmutableRemote5(fake,r){}
        for(pin in listOf(r.files.first(),r.manifestFile)){
            val journal=Journal();fake.loseAck=true;assertFailsWith<DeliveryFailure> {GuardedImmutableFile(remote,journal).execute(pin)}
            assertEquals(0,journal.acknowledged);fake.loseAck=false;GuardedImmutableFile(remote,journal).execute(pin);assertEquals(1,journal.acknowledged)
        }
        assertEquals(2,fake.posts);assertTrue(fake.calls.none {it.method in setOf("PUT","PATCH","DELETE")})
    }
    @Test fun sameId409NeedsExactReadbackAndDifferentExistingBytesCannotBeReplaced() {
        val r=revision();val fake=Fake(r);fake.race409=true;val remote=GoogleTemplateImmutableRemote5(fake,r){};val journal=Journal();val pin=r.manifestFile
        GuardedImmutableFile(remote,journal).execute(pin);assertEquals(1,journal.acknowledged)
        fake.bytes[pin.objectId]=pin.bytes.copyOf().also {it[0]=32}
        assertFails {GuardedImmutableFile(remote,journal).execute(pin)};assertEquals(1,fake.posts);assertEquals(1,journal.acknowledged)
    }
    @Test fun invalidSourceHashPlanOrAliasedProviderIdsFailBeforeAnyTransportExists() {
        assertFails {revision(bytes=payloads.mapValues {it.value+byteArrayOf(32)})}
        val wrong=TemplateRevisionManifest(manifest.templateId,manifest.revisionId,null,manifest.sourceHash,"a".repeat(64),manifest.files)
        assertFails {revision(wrong)}
        assertFails {ImmutableRevisionFiles5(namespace,manifest,manifest.files.first().objectId,"gym-template-fixture",payloads,contracts)}
        assertFails {ImmutableRevisionFiles5(namespace,manifest,namespace.commonRootId,"gym-template-fixture",payloads,contracts)}
    }
    @Test fun unexpectedAccountsParentsOrSharingStopBeforeSourceAccess() {
        val r=revision()
        for(mode in 0..2){val fake=Fake(r);val pin=r.files.first();fake.retain(pin)
            when(mode){0->fake.account="different-test-owner";1->fake.badFolder=true;2->fake.sharedFile=true}
            assertFails {GuardedImmutableFile(GoogleTemplateImmutableRemote5(fake,r){},Journal()).execute(pin)}
            assertTrue(fake.calls.none {it.path.endsWith("?alt=media") || it.method=="POST"})}
    }
    @Test fun malformedMetadataChangingTagsAndOversizedContentCannotProduceReceipts() {
        val r=revision()
        for(mode in 0..3){val fake=Fake(r);val pin=r.files.first();fake.retain(pin)
            when(mode){0->fake.badMetadata=byteArrayOf(0xff.toByte());1->fake.badMetadata="{\"id\":\"first\",\"id\":\"second\"}".toByteArray();2->fake.changeRead=true;3->fake.bytes[pin.objectId]=pin.bytes+byteArrayOf(32)}
            val j=Journal();assertFails {GuardedImmutableFile(GoogleTemplateImmutableRemote5(fake,r){},j).execute(pin)};assertEquals(0,j.acknowledged);assertEquals(0,fake.posts)}
    }
    @Test fun unknownObjectsAndFailedNamespaceRecheckCannotAccessSource() {
        val r=revision();val fake=Fake(r);val remote=GoogleTemplateImmutableRemote5(fake,r){}
        assertFails {remote.read(r.files.first().objectId)};assertTrue(fake.calls.isEmpty())
        remote.verifyNamespace();fake.badFolder=true;assertFails {remote.verifyNamespace()}
        val count=fake.calls.size;assertFails {remote.read(r.files.first().objectId)};assertEquals(count,fake.calls.size)
    }
    @Test fun immutableSocketGateRejectsReplacementUnknownReadsAndChangedMultipartBeforeBudgetOrNetwork() {
        val r=revision();var attempts=0;val http=GoogleImmutableHttp5("synthetic",{attempts++;error("Synthetic socket forbidden")},r);val pin=r.files.first()
        for(request in listOf(
            TemplateHttpRequest5("PUT","/upload/drive/v2/files/${pin.objectId}?uploadType=media&fields=id","text/plain",pin.bytes,"\"tag\""),
            TemplateHttpRequest5("GET","/drive/v2/files/${namespace.commonRootId}?alt=media"),
            TemplateHttpRequest5("GET","https://outside.example.test/"),
            TemplateHttpRequest5("POST","/upload/drive/v3/files?uploadType=multipart&fields=id","multipart/related; boundary=gym-template-test","changed".toByteArray()),
        ))assertFails {http.request(request)}
        http.close();assertFails {http.request(TemplateHttpRequest5("GET",ACCOUNT_PATH))};assertEquals(0,attempts)
        val legacy=GoogleTemplateHttp5("synthetic",{attempts++;error("Synthetic socket forbidden")},setOf(pin.objectId),setOf(pin.objectId))
        assertFails {legacy.request(TemplateHttpRequest5("GET","/drive/v2/files/${pin.objectId}?fields=$IMMUTABLE_FILE_FIELDS_5"))};legacy.close();assertEquals(0,attempts)
    }
    @Test fun onlyTheExactCapturedYamlAndManifestMultipartCanReachRequestReservation() {
        val r=revision();val fake=Fake(r);val remote=GoogleTemplateImmutableRemote5(fake,r){};remote.verifyNamespace()
        var attempts=0
        val http=GoogleImmutableHttp5("synthetic",{attempts++;error("Synthetic budget stop before socket")},r)
        for(pin in listOf(r.files.first(),r.manifestFile)){
            remote.createReserved(pin);val request=fake.calls.last();val before=attempts
            assertFails {http.request(request)};assertEquals(before+1,attempts)
            val body=checkNotNull(request.body).copyOf();body[body.size-1]=32
            assertFails {http.request(request.copy(body=body))};assertEquals(before+1,attempts)
            val duplicate=checkNotNull(request.body).toString(Charsets.UTF_8).replaceFirst("{","{\"id\":\"unreviewed-test\",")
            assertFails {http.request(request.copy(body=duplicate.toByteArray()))};assertEquals(before+1,attempts)
        }
        http.close()
    }
}
