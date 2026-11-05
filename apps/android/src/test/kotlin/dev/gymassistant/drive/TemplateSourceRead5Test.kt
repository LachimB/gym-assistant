package dev.gymassistant.drive

import dev.gymassistant.session.*
import dev.gymassistant.sync.*
import java.io.File
import kotlinx.serialization.json.*
import org.junit.Test
import kotlin.test.*

class TemplateSourceRead5Test {
    private val fixture=Json.parseToJsonElement(File(checkNotNull(System.getProperty("gym.root"))).resolve("examples/sessions/synthetic-runtime-v2-session.json").readText()).jsonObject
    private val snapshot=fixture.getValue("sourcePlanSnapshot").jsonObject
    private val sources=sessionJson.decodeFromString<TemplateSources3>(snapshot.getValue("sourceSnapshotJson").jsonPrimitive.content)
    private val local=CheckedLocalPublication5("job-test","operation-test","binding-test",fixture.getValue("templateId").jsonPrimitive.content,
        fixture.getValue("revisionId").jsonPrimitive.content,snapshot.getValue("sourceHash").jsonPrimitive.content,snapshot.getValue("compiledPlanHash").jsonPrimitive.content,
        "f".repeat(64),null,null,null,null,sources)
    private val namespace=SourceCaptureNamespace5(local.bindingHash,"synthetic-owner","project-root","proof-area","template-folder","working-folder","history-folder","metadata-folder","head-file","gym-template-test")
    private val pins=listOf(SourceCapturePin5("template","template.yaml","working-template","text/plain"),SourceCapturePin5("exercise","exercise.yaml","working-exercise","text/plain"))
    private val origin=SyncIntentFile5("local-fixture.json","a".repeat(64));private val pointer=SyncIntentFile5("capture-fixture.json","b".repeat(64))
    private fun capture(p:List<SourceCapturePin5> = pins)=CheckedSourceCapture5(pointer,"observation-test",pointer,origin,local,namespace,p,emptyList())
    private fun folder(id:String,parent:String,name:String,key:String=TEMPLATE_MARKER_KEY_5,marker:String=namespace.marker)=TemplateFolderPin5(id,parent,name,key,marker)
    private val folders=listOf(folder("project-root","my-drive-test","Gym Assistant","gymProjectM1","gym-project-test"),
        folder("test-folder","project-root","test","gymEnvironmentM1","env-test-fixture"),folder("proof-area","test-folder","synthetic proof","gymSyntheticM1","synthetic-proof-test"),
        folder("library-folder","proof-area","Templates","gymTemplateLibraryM1","library-test"),folder("template-folder","library-folder","selected fixture"),
        folder("working-folder","template-folder","Current working copy"),folder("history-folder","template-folder","History"),folder("metadata-folder","template-folder","Sync metadata"))
    private fun scope()=TemplateSourceReadScope5(capture(),folders)
    private val head=TemplatePublicationHead(local.templateId,"generation-test","manifest-test",namespace.workingFolderId,local.sourceHash,null)
    private inner class Fake:TemplateHttp5 {
        var account="synthetic-owner";var shared=false;var extraGrant=false;var missingFolder:String?=null;var movedFolder=false
        var missingHead=false;var headValue=head;var badHead:ByteArray?=null;var badMetadata:ByteArray?=null;var changingId:String?=null
        val counts=mutableMapOf<String,Int>();val calls=mutableListOf<TemplateHttpRequest5>();val captured=mutableListOf<RemoteObjectCapture5>()
        val bytes=mutableMapOf("working-template" to sources.templateYaml.toByteArray(),"working-exercise" to sources.exerciseYamls.single().toByteArray())
        val reader=GoogleSourceReader5(this,scope()){captured+=it}
        private fun metadata(id:String,parent:String,name:String,mime:String,key:String,marker:String)=buildJsonObject {
            put("id",id);put("title",name);put("mimeType",mime);put("etag",if(changingId==id && counts[id]==2)"\"changed-tag\"" else "\"tag-$id\"")
            put("ownedByMe",true);put("shared",shared);put("labels",buildJsonObject {put("trashed",false)})
            put("parents",buildJsonArray {add(buildJsonObject {put("id",parent)})})
            put("properties",buildJsonArray {add(buildJsonObject {put("key",key);put("value",marker);put("visibility","PRIVATE")})})
            put("permissions",buildJsonArray {add(buildJsonObject {put("id","synthetic-owner");put("type","user");put("role","owner")})
                if(extraGrant)add(buildJsonObject {put("id","another-user");put("type","user");put("role","reader")})})
        }.toString().toByteArray()
        override fun request(request:TemplateHttpRequest5):DeliveryHttpResponse {
            calls+=request;check(request.method=="GET")
            if(request.path==ACCOUNT_PATH)return DeliveryHttpResponse(200,"{\"user\":{\"permissionId\":\"$account\"}}".toByteArray())
            val id=request.path.substringAfter("/drive/v2/files/").substringBefore('?')
            if(request.path.endsWith("?alt=media"))return DeliveryHttpResponse(200,if(id==namespace.headFileId)badHead?: ("\n"+TemplateSyncContracts5.encode(headValue)+"\n").toByteArray() else checkNotNull(bytes[id]))
            if(id==missingFolder)return DeliveryHttpResponse(404)
            folders.singleOrNull {it.id==id}?.let {p->return DeliveryHttpResponse(200,metadata(id,if(movedFolder && id==namespace.templateFolderId)"outside-folder" else p.parent,p.name,"application/vnd.google-apps.folder",p.markerKey,p.marker))}
            if(id==namespace.headFileId && missingHead || id!=namespace.headFileId && id !in bytes)return DeliveryHttpResponse(404)
            badMetadata?.let {return DeliveryHttpResponse(200,it)}
            counts[id]=(counts[id]?:0)+1
            return DeliveryHttpResponse(200,if(id==namespace.headFileId)metadata(id,namespace.metadataFolderId,"head.json","application/json",TEMPLATE_MARKER_KEY_5,namespace.marker)
                else pins.single {it.objectId==id}.let {metadata(id,namespace.workingFolderId,it.relativeName,it.mimeType,TEMPLATE_MARKER_KEY_5,namespace.marker)})
        }
    }
    @Test fun exactKnownSourcesAndHeadReturnCopiedOriginalBytesWithoutAnyWrite() {
        val f=Fake();f.reader.verifyNamespace();val observed=f.reader.readHead()!!;assertEquals(head,observed.head)
        assertTrue(observed.contentBytes.toString(Charsets.UTF_8).startsWith("\n"))
        val source=f.reader.readSource(pins.first().objectId)!!;assertContentEquals(sources.templateYaml.toByteArray(),source.contentBytes)
        source.contentBytes.fill(32);assertContentEquals(sources.templateYaml.toByteArray(),f.reader.readSource(pins.first().objectId)!!.contentBytes)
        assertTrue(f.captured.any {it.kind==RemoteObjectKind5.WORKING && it.contentBytes!=null});assertTrue(f.calls.all {it.method=="GET"})
    }
    @Test fun wrongAccountSharingExtraPermissionsAndMovedNamespaceStopBeforeSourceReads() {
        for(mode in 0..3){val f=Fake();when(mode){0->f.account="another-owner";1->f.shared=true;2->f.extraGrant=true;3->f.movedFolder=true}
            assertFails {f.reader.verifyNamespace()};assertTrue(f.calls.none {it.path.endsWith("?alt=media")})}
        val f=Fake();f.reader.verifyNamespace();f.extraGrant=true;assertFails {f.reader.verifyNamespace()};val count=f.calls.size
        assertFails {f.reader.readHead()};assertFails {f.reader.readSource(pins.first().objectId)};assertEquals(count,f.calls.size)
    }
    @Test fun malformedContentMetadataChangingTagsAndWrongHeadIdentityRetainRawBeforeFailure() {
        for(mode in 0..4){val f=Fake();f.reader.verifyNamespace()
            when(mode){0->f.badHead="{bad-json".toByteArray();1->f.badMetadata="{\"id\":\"first\",\"id\":\"duplicate\"}".toByteArray();2->f.changingId=namespace.headFileId;3->f.headValue=head.copy(templateId="another-template");4->f.headValue=head.copy(workingFolderId="another-working")}
            assertFails {f.reader.readHead()};assertTrue(f.captured.any {it.kind==RemoteObjectKind5.HEAD})}
        val f=Fake();f.reader.verifyNamespace();f.bytes[pins.first().objectId]=byteArrayOf(0xC3.toByte(),0x28)
        assertFails {f.reader.readSource(pins.first().objectId)}
        assertTrue(f.captured.any {it.contentBytes?.contentEquals(f.bytes[pins.first().objectId]!!)==true})
    }
    @Test fun missingObjectsRemainExplicitAndDurableSinkFailureStopsFurtherReads() {
        val f=Fake();f.missingHead=true;f.bytes.remove(pins.first().objectId);f.reader.verifyNamespace()
        assertNull(f.reader.readHead());assertNull(f.reader.readSource(pins.first().objectId));assertEquals(2,f.captured.count {it.status==404})
        val g=Fake();g.missingFolder=namespace.historyFolderId;assertEquals("missingRoot",assertFailsWith<DeliveryFailure> {g.reader.verifyNamespace()}.reason)
        assertTrue(g.captured.any {it.kind==RemoteObjectKind5.NAMESPACE && it.status==404})
        val h=Fake();val reader=GoogleSourceReader5(h,scope()){if(it.kind==RemoteObjectKind5.HEAD)error("Synthetic durable sink failure")}
        reader.verifyNamespace();assertFails {reader.readHead()};assertTrue(h.calls.none {it.path.endsWith("?alt=media")})
        val count=h.calls.size;assertFails {reader.readSource("unknown-source")};assertEquals(count,h.calls.size)
    }
    @Test fun socketProfileRejectsAllMutationsListingUnknownIdsAndFolderMediaBeforeBudget() {
        var attempts=0;val http=GoogleSourceReadHttp5("synthetic",{attempts++;error("Synthetic socket forbidden")},scope())
        assertFails {http.request(TemplateHttpRequest5("GET","/drive/v2/files/${namespace.headFileId}?alt=media"))};assertEquals(1,attempts)
        for(r in listOf(TemplateHttpRequest5("PUT","/upload/drive/v2/files/${namespace.headFileId}?uploadType=media&fields=id","application/json","{}".toByteArray(),"\"tag\""),
            TemplateHttpRequest5("POST","/upload/drive/v3/files?uploadType=multipart&fields=id"),TemplateHttpRequest5("DELETE","/drive/v3/files/${pins.first().objectId}"),
            TemplateHttpRequest5("GET","/drive/v2/files/${namespace.workingFolderId}?alt=media"),TemplateHttpRequest5("GET","/drive/v3/files?q=all"),
            TemplateHttpRequest5("GET","/drive/v2/files/unknown-source?alt=media"),TemplateHttpRequest5("GET",ACCOUNT_PATH,body=byteArrayOf(1))))assertFails {http.request(r)}
        assertEquals(1,attempts);http.close();assertFails {http.request(TemplateHttpRequest5("GET",ACCOUNT_PATH))};assertEquals(1,attempts)
    }
    @Test fun namespaceAliasesDisconnectedRolesAndDuplicatePinsCannotConstructScope() {
        for(changed in listOf(folders.map {if(it.id==namespace.workingFolderId)it.copy(parent="outside-folder")else it},
            folders.map {if(it.id==namespace.commonRootId)it.copy(name="another root")else it},folders+folders.first()))
            assertFails {TemplateSourceReadScope5(capture(),changed)}
        assertFails {TemplateSourceReadScope5(capture(pins+pins.first()),folders)}
        assertFails {TemplateSourceReadScope5(capture(pins.map {it.copy(objectId=namespace.headFileId)}),folders)}
    }
}
