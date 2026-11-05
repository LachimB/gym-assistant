package dev.gymassistant.drive

import dev.gymassistant.engine.v2.*
import dev.gymassistant.session.*
import dev.gymassistant.sync.*
import java.io.Closeable
import java.io.File
import java.nio.file.Files
import kotlinx.serialization.json.*
import org.junit.After
import org.junit.Test
import kotlin.test.*

class TemplateKnownImmutableBase5Test {
    private val repo=File(checkNotNull(System.getProperty("gym.root")))
    private val root=Files.createTempDirectory("synthetic-base-reader-").toFile()
    private fun schema(name:String)=Json.parseToJsonElement(repo.resolve("schemas/$name").readText()).jsonObject
    private val contracts=ImportContracts2(mapOf("1.0.0" to schema("template.v1.schema.json"),"1.1.0" to schema("template.v1.1.schema.json")),schema("exercise.v1.schema.json"))
    private val manifest=TemplateSyncContracts5.manifest(repo.resolve("examples/template-sync/synthetic-revision.json").readText())
    private val fixture=Json.parseToJsonElement(repo.resolve("examples/sessions/synthetic-runtime-v2-session.json").readText()).jsonObject
    private val sources=sessionJson.decodeFromString<TemplateSources3>(fixture.getValue("sourcePlanSnapshot").jsonObject.getValue("sourceSnapshotJson").jsonPrimitive.content)
    private val files=TemplateSyncFiles5(root,contracts);private val publications=TemplatePublicationFiles5(files)
    private val hash="f".repeat(64);private val marker="gym-template-test"
    private val n=SourceCaptureNamespace5(hash,"synthetic-owner","project-root","proof-area","template-folder","working-folder","history-folder","metadata-folder","head-file",marker)
    private val pins=manifest.files.mapIndexed {i,f->SourceCapturePin5(if(f.role==SourceRole.TEMPLATE)"template" else "exercise",f.relativeName,"working-$i","text/plain")}
    private val payloads=manifest.files.associate {it.objectId to (if(it.role==SourceRole.TEMPLATE)sources.templateYaml else sources.exerciseYamls.single()).toByteArray()}
    private val steps=manifest.files.mapIndexed {i,f->WorkingFileStep(pins[i].objectId,n.workingFolderId,marker,f.relativeName,"text/plain",null,null,payloads.getValue(f.objectId))}
    private fun folder(id:String,parent:String,name:String,key:String=TEMPLATE_MARKER_KEY_5,value:String=marker)=TemplateFolderPin5(id,parent,name,key,value)
    private val folders=listOf(folder("project-root","my-drive-test","Gym Assistant","gymProjectM1","gym-project-test"),
        folder("test-area","project-root","test","gymEnvironmentM1","env-test-fixture"),folder("proof-area","test-area","synthetic proof","gymSyntheticM1","synthetic-proof-test"),
        folder("library-folder","proof-area","Templates","gymTemplateLibraryM1","library-test"),folder("template-folder","library-folder","selected fixture"),
        folder("working-folder","template-folder","Current working copy"),folder("history-folder","template-folder","History"),folder("metadata-folder","template-folder","Sync metadata"))
    private val revisionFolder=folder("base-revision-folder","history-folder","retained revision")
    private val head=TemplatePublicationHead(manifest.templateId,"base-generation","base-manifest",n.workingFolderId,manifest.sourceHash,null)
    private val intent=PublicationHeadIntent("prior-operation",n.headFileId,n.metadataFolderId,marker,null,null,head)
    private fun metadata(id:String,parent:String,name:String,mime:String,key:String,value:String,tag:String="\"tag\"",owner:String=n.permissionId)=buildJsonObject {
        put("id",id);put("title",name);put("mimeType",mime);put("etag",tag);put("ownedByMe",true);put("shared",false)
        put("labels",buildJsonObject {put("trashed",false)});put("parents",buildJsonArray {add(buildJsonObject {put("id",parent)})})
        put("properties",buildJsonArray {add(buildJsonObject {put("key",key);put("value",value);put("visibility","PRIVATE")})})
        put("permissions",buildJsonArray {add(buildJsonObject {put("id",owner);put("type","user");put("role","owner")})})
    }.toString().toByteArray()
    private fun prior(published:Boolean=true):CheckedPublication5 {
        var pointer=files.prepare("prior-job","prior-operation","binding-test",manifest.revisionId,manifest,payloads,steps)
        val working=TemplateSyncProgressFiles5(files)
        for(step in steps)pointer=working.acknowledge(pointer,WorkingReceipt5(step.objectId,"\"working-tag\"",step.intendedHash))
        pointer=publications.prepare(pointer,hash,n.permissionId,revisionFolder.id,intent,null,true)
        for(pin in publications.load(pointer).immutableSteps)pointer=publications.acknowledgeImmutable(pointer,ImmutableReceipt5(pin.objectId,"\"immutable-tag\"",pin.sha256))
        if(published) {
            val bytes=TemplateSyncContracts5.encode(head).toByteArray()
            pointer=publications.observe(pointer,PublicationHeadSnapshot(n.headFileId,setOf(n.metadataFolderId),marker,"head.json","application/json","\"base-tag\"",false,head,
                metadata(n.headFileId,n.metadataFolderId,"head.json","application/json",TEMPLATE_MARKER_KEY_5,marker,"\"base-tag\""),bytes))
            pointer=publications.acknowledgePublication(pointer,"\"base-tag\"")
        }
        return publications.load(pointer)
    }
    private fun source(base:String?=manifest.revisionId,sourceHash:String?=manifest.sourceHash,generation:String?=head.generationId,tag:String?="\"base-tag\""):TemplateSourceReadScope5 {
        val local=CheckedLocalPublication5("current-job","current-operation","binding-test",manifest.templateId,manifest.revisionId,
            manifest.sourceHash,manifest.compiledPlanHash,hash,base,sourceHash,generation,tag,sources)
        val pointer=SyncIntentFile5("capture-test.json","a".repeat(64));val origin=SyncIntentFile5("local-test.json","b".repeat(64))
        return TemplateSourceReadScope5(CheckedSourceCapture5(pointer,"observation-test",pointer,origin,local,n,pins,emptyList()),folders)
    }
    private fun scope()=KnownImmutableBaseScope5(source(),prior(),revisionFolder,contracts)
    private inner class Fake(val scope:KnownImmutableBaseScope5):TemplateHttp5,Closeable {
        var closed=false;var account=n.permissionId;var missing:String?=null;var missingMedia:String?=null;var changed:String?=null;var moved=false;var badOwner=false;var changeTag=false
        val calls=mutableListOf<TemplateHttpRequest5>();val reads=mutableMapOf<String,Int>();val retained=mutableListOf<RemoteObjectCapture5>()
        override fun close(){closed=true}
        override fun request(request:TemplateHttpRequest5):DeliveryHttpResponse {
            calls+=request;check(request.method=="GET")
            if(request.path==ACCOUNT_PATH)return DeliveryHttpResponse(200,"{\"user\":{\"permissionId\":\"$account\"}}".toByteArray())
            val id=request.path.substringAfter("/drive/v2/files/").substringBefore('?')
            if(id==missing || request.path.endsWith("?alt=media") && id==missingMedia)return DeliveryHttpResponse(404)
            val folder=scope.revision.namespace.folders.singleOrNull {it.id==id};val pin=scope.revision.objects.singleOrNull {it.objectId==id}
            if(request.path.endsWith("?alt=media"))return DeliveryHttpResponse(200,checkNotNull(pin).bytes.let {if(id==changed)it.copyOf().also {b->b[0]=32}else it})
            reads[id]=(reads[id]?:0)+1
            val tag=if(changeTag && reads[id]==2)"\"changed\"" else "\"tag\""
            val owner=if(badOwner)"another-owner" else n.permissionId
            return DeliveryHttpResponse(200,if(folder!=null)metadata(id,if(moved)"outside-folder" else folder.parent,folder.name,"application/vnd.google-apps.folder",folder.markerKey,folder.marker,tag,owner)
                else checkNotNull(pin).let {metadata(id,it.folderId,it.name,it.mimeType,TEMPLATE_MARKER_KEY_5,it.marker,tag,owner)})
        }
    }
    @After fun cleanup(){root.deleteRecursively()}
    @Test fun retainedAcknowledgedBaseIsReadBackAndRecompiledUsingGetOnlyWithExactCharges() {
        val scope=scope();val f=Fake(scope);var charged=0
        val result=KnownImmutableBaseBatch5("synthetic",{charged++},scope,{f.retained+=it},f).use {it.verify()}
        assertEquals(sources,result);assertEquals(25,charged);assertEquals(charged,f.calls.size);assertTrue(f.closed)
        assertEquals(23,f.retained.size);assertTrue(f.calls.all {it.method=="GET"})
    }
    @Test fun missingReceiptOrChangedCommonBaseTagGenerationHashAndFolderFailBeforeTransport() {
        assertFails {KnownImmutableBaseScope5(source(),prior(false),revisionFolder,contracts)}
        for(s in listOf(source(base=null,sourceHash=null),source(base="another-revision"),source(sourceHash="a".repeat(64)),source(generation="another-generation"),source(tag="\"other-tag\"")))
            assertFails {KnownImmutableBaseScope5(s,prior(),revisionFolder,contracts)}
        assertFails {KnownImmutableBaseScope5(source(),prior(),revisionFolder.copy(parent="outside-folder"),contracts)}
        assertFails {KnownImmutableBaseScope5(source(),prior(),revisionFolder.copy(marker="gym-template-other"),contracts)}
    }
    @Test fun missingFilesFoldersAndMediaAreRetainedOnceAndNeverRecreated() {
        val scope=scope()
        for(mode in 0..2) {
            val f=Fake(scope);when(mode){0->f.missing=scope.revision.files.first().objectId;1->f.missing=revisionFolder.id;2->f.missingMedia=scope.revision.manifestFile.objectId}
            KnownImmutableBaseBatch5("synthetic",{},scope,{f.retained+=it},f).use {batch->assertFails {batch.verify()}}
            assertTrue(f.closed);assertEquals(1,f.retained.count {it.status==404});assertTrue(f.calls.all {it.method=="GET"})
        }
    }
    @Test fun immutableTamperingAccountOwnershipMovedFolderAndChangingTagsBlockReadback() {
        val scope=scope()
        for(mode in 0..5) {
            val f=Fake(scope);when(mode){0->f.changed=scope.revision.files.first().objectId;1->f.changed=scope.revision.manifestFile.objectId;2->f.account="another-owner";3->f.badOwner=true;4->f.moved=true;5->f.changeTag=true}
            KnownImmutableBaseBatch5("synthetic",{},scope,{f.retained+=it},f).use {batch->assertFails {batch.verify()}}
            if(mode<2)assertTrue(f.retained.any {it.kind==RemoteObjectKind5.IMMUTABLE && it.contentBytes!=null})
            assertTrue(f.closed)
        }
    }
    @Test fun sinkFailureStopsBeforeFurtherReadsAndClosesTheBatch() {
        val scope=scope();val f=Fake(scope)
        KnownImmutableBaseBatch5("synthetic",{},scope,{if(it.kind==RemoteObjectKind5.IMMUTABLE)error("Synthetic sink failure")},f).use {batch->assertFails {batch.verify()}}
        assertTrue(f.closed);assertTrue(f.calls.none {it.path.endsWith("?alt=media")})
    }
    @Test fun productionProfileRejectsMutationListingUnknownObjectsAndFolderMediaBeforeCharge() {
        val scope=scope();var charged=0;val http=GoogleKnownBaseHttp5("synthetic",{charged++;error("Synthetic socket forbidden")},scope)
        assertFails {http.request(TemplateHttpRequest5("GET","/drive/v2/files/${scope.revision.manifestFile.objectId}?alt=media"))};assertEquals(1,charged)
        for(request in listOf(TemplateHttpRequest5("POST","/upload/drive/v3/files?uploadType=multipart&fields=id"),
            TemplateHttpRequest5("PUT","/upload/drive/v2/files/${scope.revision.manifestFile.objectId}?uploadType=media&fields=id","application/json",byteArrayOf(65),"\"tag\""),
            TemplateHttpRequest5("DELETE","/drive/v3/files/${scope.revision.files.first().objectId}"),TemplateHttpRequest5("GET","/drive/v3/files?q=all"),
            TemplateHttpRequest5("GET","/drive/v2/files/unknown-object?alt=media"),TemplateHttpRequest5("GET","/drive/v2/files/${revisionFolder.id}?alt=media")))
            assertFails {http.request(request)}
        assertEquals(1,charged);http.close();assertFails {http.request(TemplateHttpRequest5("GET",ACCOUNT_PATH))};assertEquals(1,charged)
    }
}
