package dev.gymassistant.session

import dev.gymassistant.engine.v2.*
import dev.gymassistant.sync.*
import java.io.File
import java.nio.file.Files
import kotlinx.serialization.json.*
import org.junit.After
import org.junit.Test
import kotlin.test.*

class TemplatePublicationFiles5Test {
    private val repo=File(checkNotNull(System.getProperty("gym.root")))
    private val root=Files.createTempDirectory("synthetic-publication-test-").toFile()
    private fun schema(n:String)=Json.parseToJsonElement(repo.resolve("schemas/$n").readText()).jsonObject
    private val contracts=ImportContracts2(mapOf("1.0.0" to schema("template.v1.schema.json"),"1.1.0" to schema("template.v1.1.schema.json")),schema("exercise.v1.schema.json"))
    private val files=TemplateSyncFiles5(root,contracts)
    private val publication=TemplatePublicationFiles5(files)
    private val manifest=TemplateSyncContracts5.manifest(repo.resolve("examples/template-sync/synthetic-revision.json").readText())
    private val fixture=Json.parseToJsonElement(repo.resolve("examples/sessions/synthetic-runtime-v2-session.json").readText()).jsonObject
    private val sources=sessionJson.decodeFromString<TemplateSources3>(fixture.getValue("sourcePlanSnapshot").jsonObject.getValue("sourceSnapshotJson").jsonPrimitive.content)
    private val payloads=manifest.files.associate {it.objectId to (if(it.role==SourceRole.TEMPLATE)sources.templateYaml else sources.exerciseYamls.single()).toByteArray()}
    private val steps=manifest.files.mapIndexed {i,f->WorkingFileStep("working-file-$i","working-folder","gym-template-test",f.relativeName,"text/plain",null,null,payloads.getValue(f.objectId))}
    private val intended=TemplatePublicationHead(manifest.templateId,"generation-test","manifest-object","working-folder",manifest.sourceHash,null)
    private fun intent(expected:TemplatePublicationHead?=null)=PublicationHeadIntent("operation-test","head-object","metadata-folder","gym-template-test",expected,
        if(expected==null)null else "\"prior-tag\"",intended.copy(previousGenerationId=expected?.generationId))
    private fun working(complete:Boolean=true):SyncIntentFile5 {
        var pointer=files.prepare("job-test","operation-test","binding-test",manifest.revisionId,manifest,payloads,steps)
        if(complete)for(step in steps)pointer=TemplateSyncProgressFiles5(files).acknowledge(pointer,WorkingReceipt5(step.objectId,"working-tag",step.intendedHash))
        return pointer
    }
    private fun prepare(w:SyncIntentFile5=working(),i:PublicationHeadIntent=intent(),observed:PublicationHeadSnapshot?=null,absent:Boolean=true)=
        publication.prepare(w,"a".repeat(64),"synthetic-owner","revision-folder",i,observed,absent)
    private fun snapshot(head:TemplatePublicationHead=intended,tag:String="\"published-tag\""):PublicationHeadSnapshot {
        val metadata=buildJsonObject {
            put("id","head-object");put("title","head.json");put("mimeType","application/json");put("etag",tag)
            put("ownedByMe",true);put("shared",false);put("labels",buildJsonObject {put("trashed",false)})
            put("parents",buildJsonArray {add(buildJsonObject {put("id","metadata-folder")})})
            put("properties",buildJsonArray {add(buildJsonObject {put("key","gymTemplateM1");put("value","gym-template-test");put("visibility","PRIVATE")})})
            put("permissions",buildJsonArray {add(buildJsonObject {put("id","synthetic-owner");put("type","user");put("role","owner")})})
        }.toString()+"\n"
        return PublicationHeadSnapshot("head-object",setOf("metadata-folder"),"gym-template-test","head.json","application/json",tag,false,head,
            metadata.toByteArray(),("\n"+TemplateSyncContracts5.encode(head)+"\n").toByteArray())
    }
    private fun acknowledgeAll(pointer:SyncIntentFile5):SyncIntentFile5 {
        var next=pointer
        for((i,step) in publication.load(pointer).immutableSteps.withIndex())next=publication.acknowledgeImmutable(next,ImmutableReceipt5(step.objectId,"\"immutable-$i\"",step.sha256))
        return next
    }
    @After fun cleanup(){root.deleteRecursively()}
    @Test fun onlyCompletedWorkingProgressAndExplicitObservedHeadStateCanPreparePublication() {
        val incomplete=working(false);assertFails {prepare(incomplete)}
        val full=working();assertFails {prepare(full,absent=false)}
        val pointer=prepare(full);val loaded=publication.load(pointer)
        assertEquals(full,loaded.workingPointer);assertEquals(manifest.sourceHash,loaded.working.captured.manifest.sourceHash)
        assertEquals(3,loaded.immutableSteps.size);assertTrue(loaded.receipts.isEmpty());assertNull(loaded.publishedTag)
        val prior=intended.copy(generationId="generation-prior",sourceHash="b".repeat(64))
        assertFails {prepare(full,intent(prior),null,false)}
        val expected=prepare(full,intent(prior),snapshot(prior,"\"prior-tag\""),false)
        assertEquals(prior,publication.load(expected).intent.expected)
    }
    @Test fun reopenedReceiptsAndRawOriginalBytesRemainExactAfterLaterCompetingAndMissingHeads() {
        var pointer=acknowledgeAll(prepare());val original=snapshot();pointer=publication.observe(pointer,original)
        pointer=publication.acknowledgePublication(pointer,original.tag)
        val reopened=TemplatePublicationFiles5(TemplateSyncFiles5(root,contracts));val before=reopened.load(pointer)
        assertContentEquals(original.metadataBytes,before.observations.last()!!.metadataBytes);assertContentEquals(original.contentBytes,before.observations.last()!!.contentBytes)
        val competing=intended.copy(generationId="generation-later",previousGenerationId=intended.generationId,revisionManifestId="other-manifest")
        pointer=reopened.observe(pointer,snapshot(competing,"\"later-tag\""));pointer=reopened.observe(pointer,null)
        val after=reopened.load(pointer);assertEquals(original.tag,after.publishedTag);assertNull(after.observations.last())
        assertTrue(after.observations.any {it?.head==competing});assertEquals(pointer,reopened.acknowledgePublication(pointer,original.tag))
    }
    @Test fun duplicateReceiptAndObservationReusePointerButChangedFactsAreRejected() {
        val original=prepare();val step=publication.load(original).immutableSteps.first();val receipt=ImmutableReceipt5(step.objectId,"\"immutable-tag\"",step.sha256)
        val next=publication.acknowledgeImmutable(original,receipt);val count=root.listFiles()!!.size
        assertEquals(next,publication.acknowledgeImmutable(next,receipt));assertEquals(count,root.listFiles()!!.size)
        for(invalid in listOf(receipt.copy(objectId="unexpected-object"),receipt.copy(sha256="b".repeat(64)),receipt.copy(tag="\"different-tag\"")))assertFails {publication.acknowledgeImmutable(next,invalid)}
        assertEquals(next,publication.observe(next,null));assertNull(publication.load(next).publishedTag)
    }
    @Test fun fileReceiptsAloneMissingOrCompetingHeadsCannotAcknowledgePublication() {
        val original=prepare();assertFails {publication.acknowledgePublication(original,"\"published-tag\"")}
        val all=acknowledgeAll(original);assertFails {publication.acknowledgePublication(all,"\"published-tag\"")}
        val other=publication.observe(all,snapshot(intended.copy(generationId="competing-generation")))
        assertFails {publication.acknowledgePublication(other,"\"published-tag\"")}
        val observed=publication.observe(all,snapshot());assertFails {publication.acknowledgePublication(observed,"\"wrong-tag\"")}
        assertEquals("\"published-tag\"",publication.load(publication.acknowledgePublication(observed,"\"published-tag\"")).publishedTag)
    }
    @Test fun rawHeadMetadataAndDecodedContentMustAgreeBeforeObservationIsRetained() {
        val pointer=prepare();val s=snapshot()
        val wrong=PublicationHeadSnapshot(s.objectId,s.parents,s.marker,s.name,s.mimeType,s.tag,false,s.head,s.metadataBytes,TemplateSyncContracts5.encode(s.head.copy(generationId="another-generation")).toByteArray())
        assertFails {publication.observe(pointer,wrong)}
        val duplicate=s.metadataBytes.toString(Charsets.UTF_8).replaceFirst("{","{\"id\":\"duplicate-object\",")
        val bad=PublicationHeadSnapshot(s.objectId,s.parents,s.marker,s.name,s.mimeType,s.tag,false,s.head,duplicate.toByteArray(),s.contentBytes)
        assertFails {publication.observe(pointer,bad)};assertEquals(1,publication.load(pointer).observations.size)
    }
    @Test fun tamperedRetainedMetadataFailsWithoutReplacingOriginalWorkingSource() {
        val original=working();val s=snapshot();val pointer=publication.observe(prepare(original),s)
        root.resolve(hashSourceBytes(s.metadataBytes)+".yaml").writeText("{}")
        assertFails {publication.load(pointer)};assertEquals(manifest.sourceHash,TemplateSyncProgressFiles5(files).load(original).captured.manifest.sourceHash)
    }
    @Test fun duplicateReceiptsRetargetedJobsAndUnboundedObservationsFailOnReopen() {
        val pointer=acknowledgeAll(prepare());val d=Json.parseToJsonElement(files.readRecord(pointer)).jsonObject
        val receipts=d.getValue("immutableReceipts").jsonArray;val observations=d.getValue("observations").jsonArray
        for(changed in listOf(
            d+("immutableReceipts" to JsonArray(receipts+receipts.first())),
            d+("jobId" to JsonPrimitive("different-job")),
            d+("revisionFolderId" to JsonPrimitive(steps.first().objectId)),
            d+("observations" to JsonArray(List(65){observations.first()})),
            d+("schemaVersion" to JsonPrimitive("9.0.0")),
        ))assertFails {publication.load(files.writeRecord(JsonObject(changed).toString()))}
        assertTrue(publication.load(pointer).allImmutableAcknowledged);assertNull(publication.load(pointer).publishedTag)
    }
    @Test fun malformedEmptyAndMissingRawObservationsReopenWithoutBecomingSemanticHeads() {
        val original=prepare();val metadata="{\"id\":\"first\",\"id\":\"duplicate\"}".toByteArray();val content=byteArrayOf(0xC3.toByte(),0x28)
        val observation=PublicationRawObservation5("HEAD","head-object",200,metadata,content)
        metadata.fill(32);content.fill(32)
        var pointer=publication.retainRaw(original,observation)
        pointer=publication.retainRaw(pointer,PublicationRawObservation5("HEAD","head-object",200,byteArrayOf(),byteArrayOf()))
        pointer=publication.retainRaw(pointer,PublicationRawObservation5("WORKING","working-file-0",404,null,null))
        val reopened=TemplatePublicationFiles5(TemplateSyncFiles5(root,contracts)).load(pointer)
        assertEquals(3,reopened.rawObservations.size);assertContentEquals(byteArrayOf(0xC3.toByte(),0x28),reopened.rawObservations.first().contentBytes)
        assertTrue(reopened.rawObservations.first().metadataBytes!!.toString(Charsets.UTF_8).contains("duplicate"))
        assertContentEquals(byteArrayOf(),reopened.rawObservations[1].metadataBytes);assertNull(reopened.rawObservations.last().metadataBytes)
        val copy=reopened.rawObservations.first().contentBytes!!;copy.fill(32)
        assertContentEquals(byteArrayOf(0xC3.toByte(),0x28),publication.load(pointer).rawObservations.first().contentBytes)
        assertEquals(1,reopened.observations.size);assertNull(reopened.publishedTag);assertTrue(publication.load(original).rawObservations.isEmpty())
    }
    @Test fun rawAuditHashLinksAndOriginCannotBeRetargetedOrTampered() {
        val original=prepare();val pointer=publication.retainRaw(original,PublicationRawObservation5("HEAD","head-object",200,"{bad".toByteArray(),null))
        val d=Json.parseToJsonElement(files.readRecord(pointer)).jsonObject
        val audit=d.getValue("rawAudit").jsonObject;val auditPointer=SyncIntentFile5(audit.getValue("path").jsonPrimitive.content,audit.getValue("sha256").jsonPrimitive.content)
        val record=Json.parseToJsonElement(files.readRecord(auditPointer)).jsonObject
        for(changed in listOf(record+("permissionId" to JsonPrimitive("another-owner")),record+("sequence" to JsonPrimitive(2)),
            record+("byteCount" to JsonPrimitive(999)),record+("workingHash" to JsonPrimitive("f".repeat(64))))) {
            val candidate=files.writeRecord(JsonObject(changed).toString())
            val link=JsonObject(audit+mapOf("path" to JsonPrimitive(candidate.path),"sha256" to JsonPrimitive(candidate.sha256)))
            assertFails {publication.load(files.writeRecord(JsonObject(d+("rawAudit" to link)).toString()))}
        }
        root.resolve(hashSourceBytes("{bad".toByteArray())+".yaml").writeText("edited")
        assertFails {publication.load(pointer)};assertNull(publication.load(original).publishedTag)
    }
    @Test fun rawObservationLimitsRejectInvalidShapesAndNeverTrimOriginalFacts() {
        assertFails {PublicationRawObservation5("unknown","head-object",200,byteArrayOf(),null)}
        assertFails {PublicationRawObservation5("HEAD","head-object",404,byteArrayOf(),null)}
        assertFails {PublicationRawObservation5("HEAD","head-object",200,null,byteArrayOf())}
        assertFails {PublicationRawObservation5("HEAD","head-object",200,ByteArray(65537),null)}
        assertFails {PublicationRawObservation5("HEAD","head-object",200,byteArrayOf(),ByteArray(1048577))}
        val audit=TemplatePublicationAudit5(files);val working=working();val raw=PublicationRawObservation5("HEAD","head-object",200,byteArrayOf(),null)
        assertFails {audit.append(PublicationAuditPointer5("unused.json","a".repeat(64),1024,0),working,"a".repeat(64),"synthetic-owner","head-object",raw)}
        assertFails {audit.append(PublicationAuditPointer5("unused.json","a".repeat(64),1,16777216),working,"a".repeat(64),"synthetic-owner","head-object",PublicationRawObservation5("HEAD","head-object",200,byteArrayOf(1),null))}
    }
    @Test fun originalVersionReopensAndOnlyAnExplicitNewRecordAddsTheRawAudit() {
        val original=prepare();val d=Json.parseToJsonElement(files.readRecord(original)).jsonObject
        val old=files.writeRecord(JsonObject(d-"rawAudit"+("schemaVersion" to JsonPrimitive("1.0.0"))).toString())
        assertTrue(publication.load(old).rawObservations.isEmpty())
        val next=publication.retainRaw(old,PublicationRawObservation5("HEAD","head-object",404,null,null))
        assertEquals("1.1.0",Json.parseToJsonElement(files.readRecord(next)).jsonObject.getValue("schemaVersion").jsonPrimitive.content)
        assertTrue(publication.load(old).rawObservations.isEmpty());assertEquals(1,publication.load(next).rawObservations.size)
    }
}
