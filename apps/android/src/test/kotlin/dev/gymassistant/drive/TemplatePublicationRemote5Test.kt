package dev.gymassistant.drive

import dev.gymassistant.engine.v2.*
import dev.gymassistant.session.*
import dev.gymassistant.sync.*
import java.io.File
import kotlinx.serialization.json.*
import org.junit.Test
import kotlin.test.*

class TemplatePublicationRemote5Test {
    private val root=File(checkNotNull(System.getProperty("gym.root")))
    private fun schema(n:String)=Json.parseToJsonElement(root.resolve("schemas/$n").readText()).jsonObject
    private val contracts=ImportContracts2(mapOf("1.0.0" to schema("template.v1.schema.json"),"1.1.0" to schema("template.v1.1.schema.json")),schema("exercise.v1.schema.json"))
    private val manifest=TemplateSyncContracts5.manifest(root.resolve("examples/template-sync/synthetic-revision.json").readText())
    private val fixture=Json.parseToJsonElement(root.resolve("examples/sessions/synthetic-runtime-v2-session.json").readText()).jsonObject
    private val sources=sessionJson.decodeFromString<TemplateSources3>(fixture.getValue("sourcePlanSnapshot").jsonObject.getValue("sourceSnapshotJson").jsonPrimitive.content)
    private val payloads=manifest.files.associate {it.objectId to (if(it.role==SourceRole.TEMPLATE)sources.templateYaml else sources.exerciseYamls.single()).toByteArray()}
    private fun folder(id:String,parent:String,name:String,key:String=TEMPLATE_MARKER_KEY_5,marker:String="gym-template-fixture")=TemplateFolderPin5(id,parent,name,key,marker)
    private val prefix=listOf(folder("project-test","my-drive-test","Gym Assistant","gymProjectM1","gym-project-fixture"),
        folder("dev-test","project-test","dev","gymEnvironmentM1","env-dev-fixture"),
        folder("personal-test","dev-test","PERSONAL M1","gymPersonalM1","personal-m1-fixture"),
        folder("templates-test","personal-test","Templates","gymTemplateLibraryM1","library-fixture"),folder("template-test","templates-test","selected fixture"))
    private val work=folder("working-test","template-test","Current working copy")
    private val meta=folder("metadata-test","template-test","Sync metadata")
    private val history=folder("history-test","template-test","History")
    private val revisionFolder=folder("revision-test","history-test","revision fixture")
    private val revision=ImmutableRevisionFiles5(ImmutableRevisionNamespace5("synthetic-owner","project-test",revisionFolder.id,prefix+history+revisionFolder),manifest,"manifest-test","gym-template-fixture",payloads,contracts)
    private val working=manifest.files.mapIndexed {i,f->WorkingFileStep("working-file-$i",work.id,"gym-template-fixture",f.relativeName,"text/plain",null,null,payloads.getValue(f.objectId))}
    private val receipts=working.mapIndexed {i,s->WorkingReceipt5(s.objectId,"\"working-$i\"",s.intendedHash)}
    private val prior=TemplatePublicationHead(manifest.templateId,"generation-prior","prior-manifest",work.id,"a".repeat(64),null)
    private fun scope(existing:Boolean=true):TemplatePublicationScope5 {
        val intended=TemplatePublicationHead(manifest.templateId,"generation-next",revision.manifestFile.objectId,work.id,manifest.sourceHash,if(existing)prior.generationId else null)
        val intent=PublicationHeadIntent("operation-test","head-object",meta.id,"gym-template-fixture",if(existing)prior else null,if(existing)"\"head-before\"" else null,intended)
        return TemplatePublicationScope5(intent,revision,TemplateNamespace5("synthetic-owner","project-test",work.id,prefix+work),
            ImmutableRevisionNamespace5("synthetic-owner","project-test",meta.id,prefix+meta),working,receipts,contracts)
    }
    private class Journal:PublicationHeadJournal {
        var acknowledged=0;var lease=true;var onAcknowledge:()->Unit={};val observed=mutableListOf<PublicationHeadSnapshot?>()
        override fun requireDurable(intent:PublicationHeadIntent){}
        override fun requireCurrentLease(){check(lease)}
        override fun observe(intent:PublicationHeadIntent,snapshot:PublicationHeadSnapshot?){observed+=snapshot}
        override fun acknowledgePublication(intent:PublicationHeadIntent,resultTag:String){acknowledged++;onAcknowledge()}
    }
    private inner class Fake(val s:TemplatePublicationScope5=scope()):TemplateHttp5 {
        var head=s.intent.expected;var headTag="\"head-before\"";var writes=0;var creates=0;var loseAck=false;var create409=false
        var account="synthetic-owner";var wrongFolder=false;var beforePutConflict=false;var afterWriteWorkingEdit=false
        var changingHead=false;var headReads=0;var badHeadMetadata:ByteArray?=null;var badHeadContent:ByteArray?=null
        val calls=mutableListOf<TemplateHttpRequest5>();val captured=mutableListOf<RemoteObjectCapture5>()
        val bytes=(revision.objects.associate {it.objectId to it.bytes}+working.associate {it.objectId to it.intendedBytes}).toMutableMap()
        val tags=(revision.objects.associate {it.objectId to "\"immutable-tag\""}+receipts.associate {it.objectId to it.tag}).toMutableMap()
        val remote=GoogleTemplatePublicationRemote5(this,s){captured+=it}
        private fun response(d:JsonObject)=DeliveryHttpResponse(200,d.toString().toByteArray())
        private fun metadata(id:String,parent:String,name:String,mime:String,key:String,marker:String,tag:String)=buildJsonObject {
            put("id",id);put("title",name);put("mimeType",mime);put("etag",tag);put("ownedByMe",true);put("shared",false)
            put("parents",buildJsonArray {add(buildJsonObject {put("id",parent)})});put("labels",buildJsonObject {put("trashed",false)})
            put("properties",buildJsonArray {add(buildJsonObject {put("key",key);put("value",marker);put("visibility","PRIVATE")})})
            put("permissions",buildJsonArray {add(buildJsonObject {put("id","synthetic-owner");put("type","user");put("role","owner")})})
        }
        override fun request(r:TemplateHttpRequest5):DeliveryHttpResponse {
            calls+=r
            if(r.path==ACCOUNT_PATH)return response(buildJsonObject {put("user",buildJsonObject {put("permissionId",account)})})
            if(r.method in setOf("PUT","POST")){
                if(r.method=="PUT"){
                    if(beforePutConflict){head=prior.copy(generationId="competing-generation");headTag="\"competing-tag\""}
                    if(r.ifMatch!=headTag)return DeliveryHttpResponse(412)
                    writes++
                }else creates++
                head=s.intent.intended;headTag="\"head-after\""
                if(afterWriteWorkingEdit){bytes[working.first().objectId]=working.first().intendedBytes+byteArrayOf(32);tags[working.first().objectId]="\"external-working-tag\""}
                if(loseAck)throw DeliveryFailure("network")
                return if(r.method=="POST" && create409)DeliveryHttpResponse(409) else response(buildJsonObject {put("id",s.intent.objectId)})
            }
            val id=r.path.substringAfter("/drive/v2/files/").substringBefore('?')
            if(r.path.endsWith("?alt=media"))return DeliveryHttpResponse(200,if(id==s.intent.objectId)badHeadContent?: ("\n"+TemplateSyncContracts5.encode(checkNotNull(head))+"\n").toByteArray() else checkNotNull(bytes[id]))
            s.folders[id]?.let {p->return response(metadata(id,if(wrongFolder && id=="template-test")"outside-test" else p.parent,p.name,"application/vnd.google-apps.folder",p.markerKey,p.marker,"\"folder-tag\""))}
            if(id==s.intent.objectId){
                if(head==null)return DeliveryHttpResponse(404)
                badHeadMetadata?.let {return DeliveryHttpResponse(200,it)}
                headReads++;if(changingHead && headReads==2)headTag="\"head-changed-during-read\""
                return response(metadata(id,meta.id,"head.json","application/json",TEMPLATE_MARKER_KEY_5,s.intent.marker,headTag))
            }
            revision.objects.singleOrNull {it.objectId==id}?.let {p->return response(metadata(id,p.folderId,p.name,p.mimeType,TEMPLATE_MARKER_KEY_5,p.marker,checkNotNull(tags[id])))}
            val p=working.single {it.objectId==id};return response(metadata(id,p.folderId,p.name,p.mimeType,TEMPLATE_MARKER_KEY_5,p.marker,checkNotNull(tags[id])))
        }
        fun run(j:Journal)=GuardedPublicationHead(remote,j).execute(s.intent)
    }
    @Test fun exactHeadWriteAndLostAcknowledgementReuseTheSameHeadAndRetainRawBytes() {
        val f=Fake();val j=Journal();f.loseAck=true;assertFailsWith<DeliveryFailure> {f.run(j)};assertEquals(0,j.acknowledged)
        f.loseAck=false;assertEquals(HeadPublicationResult.PUBLISHED_WORKING_COPY_VERIFIED,f.run(j));assertEquals(1,f.writes);assertEquals(1,j.acknowledged)
        assertTrue(j.observed.first()!!.contentBytes.toString(Charsets.UTF_8).startsWith("\n"))
        assertTrue(f.captured.any {it.kind==RemoteObjectKind5.HEAD && it.contentBytes!=null})
    }
    @Test fun initialHead409RequiresSameIdentityReadbackAndNeverUsesUpdate() {
        val f=Fake(scope(false));f.create409=true;val j=Journal()
        assertEquals(HeadPublicationResult.PUBLISHED_WORKING_COPY_VERIFIED,f.run(j));assertEquals(1,f.creates);assertEquals(0,f.writes);assertEquals(1,j.acknowledged)
        assertTrue(f.captured.any {it.kind==RemoteObjectKind5.HEAD && it.status==404})
    }
    @Test fun staleWriteAndMissingExistingHeadCannotFallBackToCreation() {
        val f=Fake();f.beforePutConflict=true;val j=Journal();assertEquals("conflict",assertFailsWith<DeliveryFailure> {f.run(j)}.reason)
        assertEquals(0,f.writes);assertEquals(0,f.creates);assertEquals(0,j.acknowledged)
        val g=Fake();g.head=null;assertFails {g.run(Journal())};assertEquals(0,g.creates)
    }
    @Test fun laterWorkingEditAndCompetingHeadKeepTheVerifiedPublicationWithoutRepair() {
        val f=Fake();f.afterWriteWorkingEdit=true;val j=Journal();assertEquals(HeadPublicationResult.PUBLISHED_WITH_PENDING_WORKING_CHANGE,f.run(j));assertEquals(1,j.acknowledged);assertEquals(1,f.writes)
        val g=Fake();val k=Journal();k.onAcknowledge={g.head=g.s.intent.intended.copy(generationId="later-generation");g.headTag="\"later-tag\""}
        assertEquals(HeadPublicationResult.PUBLISHED_WITH_COMPETING_HEAD,g.run(k));assertEquals(1,k.acknowledged);assertEquals(1,g.writes)
    }
    @Test fun badImmutableSourceOrChangedWorkingVectorCannotPublish() {
        val f=Fake();f.bytes[revision.files.first().objectId]=byteArrayOf(32);assertFails {f.run(Journal())};assertEquals(0,f.writes)
        val g=Fake();g.tags[working.first().objectId]="\"externally-changed\"";assertFails {g.run(Journal())};assertEquals(0,g.writes)
    }
    @Test fun malformedAndChangingHeadReadsRetainOriginalObservationsBeforeFailure() {
        for(mode in 0..2){val f=Fake();when(mode){0->f.badHeadMetadata="{\"id\":\"first\",\"id\":\"second\"}".toByteArray();1->f.badHeadContent="{bad-json".toByteArray();2->f.changingHead=true}
            assertFails {f.run(Journal())};assertEquals(0,f.writes);assertTrue(f.captured.any {it.kind==RemoteObjectKind5.HEAD})
            if(mode==1)assertTrue(f.captured.any {it.contentBytes?.contentEquals(f.badHeadContent!!)==true})}
    }
    @Test fun wrongAccountOrNamespaceStopsSourceAndFailedRecheckRevokesAccess() {
        for(mode in 0..1){val f=Fake();if(mode==0)f.account="another-test-owner" else f.wrongFolder=true;assertFails {f.run(Journal())};assertTrue(f.calls.none {it.path.endsWith("?alt=media")})}
        val f=Fake();f.remote.verifyNamespace();f.wrongFolder=true;assertFails {f.remote.verifyNamespace()};val count=f.calls.size
        assertFails {f.remote.read(f.s.intent.objectId)};assertEquals(count,f.calls.size)
    }
    @Test fun jsonHeadGateIsExactAndLegacyTransportDefaultsRemainClosed() {
        val s=scope();var attempts=0;val http=GooglePublicationHttp5("synthetic",{attempts++;error("Synthetic budget stop before socket")},s)
        val correct=TemplateHttpRequest5("PUT","/upload/drive/v2/files/${s.intent.objectId}?uploadType=media&fields=id","application/json",TemplateSyncContracts5.encode(s.intent.intended).toByteArray(),s.intent.expectedTag)
        assertFails {http.request(correct)};assertEquals(1,attempts)
        for(r in listOf(correct.copy(ifMatch="*"),correct.copy(body="{}".toByteArray()),correct.copy(contentType="text/plain"),
            correct.copy(path="/upload/drive/v2/files/${working.first().objectId}?uploadType=media&fields=id"),TemplateHttpRequest5("GET","/drive/v2/files/${meta.id}?alt=media")))assertFails {http.request(r)}
        assertEquals(1,attempts);http.close()
        val legacy=GoogleTemplateHttp5("synthetic",{attempts++;error("Synthetic socket forbidden")},setOf(s.intent.objectId),setOf(s.intent.objectId));assertFails {legacy.request(correct)};legacy.close();assertEquals(1,attempts)
        val ids=(0..42).map {"read-fixture-$it"}.toSet();assertFails {GoogleTemplateHttp5("synthetic",{},ids,setOf(ids.first()))}
        GoogleTemplateHttp5("synthetic",{},ids,emptySet(),IMMUTABLE_FILE_FIELDS_5,ids.first(),64).close()
    }
    @Test fun immutableAndWorkingNamespaceBranchesCannotRetargetTheSelectedTemplate() {
        val s=scope();val changed=(prefix+meta).map {if(it.id=="template-test")it.copy(marker="gym-template-another")else it}
        assertFails {TemplatePublicationScope5(s.intent,revision,s.workingNamespace,ImmutableRevisionNamespace5("synthetic-owner","project-test",meta.id,changed),working,receipts,contracts)}
        val badReceipts=receipts.map {it.copy(sha256="c".repeat(64))}
        assertFails {TemplatePublicationScope5(s.intent,revision,s.workingNamespace,s.metadataNamespace,working,badReceipts,contracts)}
    }
    @Test fun initialHeadSocketGateAcceptsOnlyTheCapturedMultipartIdentityAndBytes() {
        val s=scope(false);val f=Fake(s);f.run(Journal())
        val request=f.calls.single {it.method=="POST"};var attempts=0
        val http=GooglePublicationHttp5("synthetic",{attempts++;error("Synthetic socket forbidden")},s)
        assertFails {http.request(request)};assertEquals(1,attempts)
        val original=checkNotNull(request.body).toString(Charsets.UTF_8)
        for(body in listOf(original.replace("head-object","another-object"),original.replace("application/json","text/plain"),
            original.replace("generation-next","generation-other")))assertFails {http.request(request.copy(body=body.toByteArray()))}
        assertFails {http.request(request.copy(ifMatch="*"))};assertEquals(1,attempts);http.close()
        val existing=GooglePublicationHttp5("synthetic",{attempts++;error("Synthetic socket forbidden")},scope())
        assertFails {existing.request(request)};existing.close();assertEquals(1,attempts)
    }
    @Test fun failedOriginalObservationRetentionStopsBeforeAnyHeadMutation() {
        val f=Fake();var retained=0
        val remote=GoogleTemplatePublicationRemote5(f,f.s){capture->
            if(capture.kind==RemoteObjectKind5.HEAD){retained++;error("Synthetic durable sink failure")}}
        assertFails {GuardedPublicationHead(remote,Journal()).execute(f.s.intent)}
        assertEquals(1,retained);assertEquals(0,f.writes);assertEquals(0,f.creates)
    }
    @Test fun leaseExpiryDuringWorkingVerificationPreventsHeadMutation() {
        val f=Fake();val j=Journal()
        val remote=object:PublicationHeadRemote by f.remote {
            override fun workingVectorMatches():Boolean=f.remote.workingVectorMatches().also {j.lease=false}
        }
        assertFails {GuardedPublicationHead(remote,j).execute(f.s.intent)}
        assertEquals(0,f.writes);assertEquals(0,f.creates);assertEquals(0,j.acknowledged)
    }
    @Test fun incompleteWorkingScopeCanCheckRolesButCannotOpenAnyHeadTransport() {
        val s=scope();val incomplete=TemplatePublicationScope5(s.intent,revision,s.workingNamespace,s.metadataNamespace,working,emptyList(),contracts,false)
        assertFalse(incomplete.completeWorkingReceipts);var attempts=0
        assertFails {GooglePublicationHttp5("synthetic",{attempts++},incomplete)}
        assertFails {GoogleTemplatePublicationRemote5(TemplateHttp5 {attempts++;error("Synthetic socket forbidden")},incomplete){}}
        assertEquals(0,attempts)
    }
    @Test fun readOnlyPreflightCannotMutateEvenWithCompleteImmutableAndWorkingVectors() {
        val f=Fake();val remote=GoogleTemplatePublicationRemote5(f,f.s,{f.captured+=it},true)
        remote.verifyNamespace();assertEquals(prior,remote.read(f.s.intent.objectId)!!.head)
        remote.verifyImmutablePublication(f.s.intent.intended);assertTrue(remote.workingVectorMatches())
        assertFails {remote.replace(f.s.intent.objectId,f.s.intent.expectedTag!!,f.s.intent.intended)}
        assertFails {remote.createReserved(f.s.intent)};assertEquals(0,f.writes);assertEquals(0,f.creates)
    }
}
