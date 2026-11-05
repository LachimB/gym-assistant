package dev.gymassistant.session

import dev.gymassistant.engine.v2.readYaml2
import dev.gymassistant.sync.*
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.*

@Serializable data class ImmutableReceipt5(val objectId:String,val tag:String,val sha256:String) {
    init {WorkingReceipt5(objectId,tag,sha256);publicationTag5(tag)}
}
@Serializable private data class HeadObservation5(val tag:String?,val metadataHash:String?,val contentHash:String?)
@Serializable private data class HeadReceipt5(val tag:String,val observationIndex:Int)
@Serializable private data class PublicationDocument5(val kind:String,val schemaVersion:String,val jobId:String,val operationId:String,val bindingId:String,
    val workingPath:String,val workingHash:String,val bindingHash:String,val permissionId:String,val revisionFolderId:String,
    val headObjectId:String,val metadataFolderId:String,val marker:String,val expectedHead:String?,val expectedTag:String?,val intendedHead:String,
    val immutableReceipts:List<ImmutableReceipt5>,val observations:List<HeadObservation5>,val publication:HeadReceipt5?,
    val rawAudit:PublicationAuditPointer5?=null,val originPath:String?=null,val originHash:String?=null)

private fun publicationId5(id:String)=require(id.matches(Regex("[A-Za-z0-9_-]{1,160}")))
private fun publicationHash5(hash:String)=require(hash.matches(Regex("[a-f0-9]{64}")))
private fun publicationTag5(tag:String)=require(tag.matches(Regex("\"[\\x21\\x23-\\x7E]{1,1000}\"")))
private fun publicationText5(bytes:ByteArray):String {
    require(bytes.size in 1..65536)
    return Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
}
private fun JsonObject.publicationString5(key:String):String {
    val value=this[key] as? JsonPrimitive ?: error("scalar");require(value.isString);return value.content
}
private fun JsonObject.publicationFlag5(key:String):Boolean {
    val value=this[key] as? JsonPrimitive ?: error("scalar");require(!value.isString);return checkNotNull(value.booleanOrNull)
}

class CheckedPublication5 internal constructor(val workingPointer:SyncIntentFile5,val working:CheckedSyncProgress5,val bindingHash:String,
    val permissionId:String,val revisionFolderId:String,val intent:PublicationHeadIntent,steps:List<ImmutableFileStep>,receipts:List<ImmutableReceipt5>,
    observations:List<PublicationHeadSnapshot?>,val publishedTag:String?,rawObservations:List<PublicationRawObservation5>,
    val historyFirst:Boolean,val originPointer:SyncIntentFile5) {
    val immutableSteps=steps.toList();val receipts=receipts.associateBy {it.objectId};val observations=observations.toList()
    val rawObservations=rawObservations.toList()
    val allImmutableAcknowledged get()=this.receipts.size==immutableSteps.size
    fun requireJob(job:TemplateSyncJob5) {
        working.requireJob(job)
        check(job.direction=="push" && job.expectedRemoteGeneration==intent.expected?.generationId && job.expectedRemoteHeadTag==intent.expectedTag)
    }
}

/** Additive immutable records linked to completed working progress; no local adoption or whole-job completion. */
class TemplatePublicationFiles5(private val files:TemplateSyncFiles5) {
    private val json=Json {encodeDefaults=false;ignoreUnknownKeys=false}
    private val workingFiles=TemplateSyncProgressFiles5(files)
    private val audit=TemplatePublicationAudit5(files)
    internal fun isPublication(pointer:SyncIntentFile5):Boolean=
        Json.parseToJsonElement(files.readRecord(pointer)).jsonObject["kind"]?.jsonPrimitive?.content in setOf("publicationProgress","transferProgress")
    private fun read(pointer:SyncIntentFile5):PublicationDocument5=json.decodeFromString<PublicationDocument5>(files.readRecord(pointer)).also {
        check(it.kind in setOf("publicationProgress","transferProgress") && it.schemaVersion in setOf("1.0.0","1.1.0","1.2.0"))
        check((it.kind=="transferProgress")== (it.schemaVersion=="1.2.0"))
        if(it.schemaVersion=="1.0.0")check(it.rawAudit==null)
        if(it.schemaVersion!="1.2.0")check(it.originPath==null && it.originHash==null && it.kind=="publicationProgress")
        if(it.kind=="transferProgress")check(it.originPath!=null && it.originHash!=null)
    }
    private fun head(d:PublicationDocument5)=PublicationHeadIntent(d.operationId,d.headObjectId,d.metadataFolderId,d.marker,
        d.expectedHead?.let(TemplateSyncContracts5::head),d.expectedTag,TemplateSyncContracts5.head(d.intendedHead))
    private fun checkObservation(intent:PublicationHeadIntent,permissionId:String,s:PublicationHeadSnapshot) {
        check(s.objectId==intent.objectId && s.parents==setOf(intent.metadataFolderId) && s.marker==intent.marker &&
            s.name=="head.json" && s.mimeType=="application/json" && !s.trashed)
        publicationTag5(s.tag)
        check(TemplateSyncContracts5.head(publicationText5(s.contentBytes))==s.head)
        val raw=publicationText5(s.metadataBytes);readYaml2(raw);val m=Json.parseToJsonElement(raw).jsonObject
        check(m.publicationString5("id")==s.objectId && m.publicationString5("title")==s.name && m.publicationString5("mimeType")==s.mimeType &&
            m.publicationString5("etag")==s.tag && m.publicationFlag5("ownedByMe") && !m.publicationFlag5("shared") &&
            !m.getValue("labels").jsonObject.publicationFlag5("trashed"))
        check(m.getValue("parents").jsonArray.map {it.jsonObject.publicationString5("id")}==listOf(intent.metadataFolderId))
        val props=m.getValue("properties").jsonArray.map {it.jsonObject}.filter {it.publicationString5("key")=="gymTemplateM1" && it.publicationString5("visibility")=="PRIVATE"}
        check(props.size==1 && props.single().publicationString5("value")==intent.marker)
        val owner=m.getValue("permissions").jsonArray.single().jsonObject
        check(owner.publicationString5("id")==permissionId && owner.publicationString5("type")=="user" && owner.publicationString5("role")=="owner" &&
            ("deleted" !in owner || !owner.publicationFlag5("deleted")))
    }
    private fun retainObservation(intent:PublicationHeadIntent,permission:String,s:PublicationHeadSnapshot?):HeadObservation5 {
        if(s==null)return HeadObservation5(null,null,null)
        checkObservation(intent,permission,s)
        return HeadObservation5(s.tag,files.retainSource(s.metadataBytes),files.retainSource(s.contentBytes))
    }
    private fun restoreObservation(intent:PublicationHeadIntent,permission:String,d:HeadObservation5):PublicationHeadSnapshot? {
        if(d.tag==null){check(d.metadataHash==null && d.contentHash==null);return null}
        publicationTag5(d.tag);val metadata=files.source(checkNotNull(d.metadataHash));val content=files.source(checkNotNull(d.contentHash))
        val s=PublicationHeadSnapshot(intent.objectId,setOf(intent.metadataFolderId),intent.marker,"head.json","application/json",d.tag,false,
            TemplateSyncContracts5.head(publicationText5(content)),metadata,content)
        checkObservation(intent,permission,s);return s
    }
    fun prepare(workingPointer:SyncIntentFile5,bindingHash:String,permissionId:String,revisionFolderId:String,
        intent:PublicationHeadIntent,initialObservation:PublicationHeadSnapshot?,headAbsenceVerified:Boolean,historyFirst:Boolean=false):SyncIntentFile5 {
        val w=workingFiles.load(workingPointer);check(w.captured.expectedLocalRevisionId==w.captured.manifest.revisionId)
        if(historyFirst)check(w.receipts.isEmpty()) else check(w.allFilesAcknowledged)
        publicationHash5(bindingHash);publicationId5(permissionId);publicationId5(revisionFolderId)
        check(intent.operationId==w.captured.operationId && intent.intended.templateId==w.captured.manifest.templateId &&
            intent.intended.sourceHash==w.captured.manifest.sourceHash && intent.intended.workingFolderId==w.captured.steps.first().folderId)
        check(w.captured.steps.all {it.marker==intent.marker})
        check(headAbsenceVerified==(intent.expected==null) && (initialObservation==null)==headAbsenceVerified)
        if(initialObservation!=null)check(initialObservation.head==intent.expected && initialObservation.tag==intent.expectedTag)
        intent.expectedTag?.let(::publicationTag5)
        val d=PublicationDocument5(if(historyFirst)"transferProgress" else "publicationProgress",if(historyFirst)"1.2.0" else "1.1.0",w.captured.jobId,w.captured.operationId,w.captured.bindingId,
            workingPointer.path,workingPointer.sha256,bindingHash,permissionId,revisionFolderId,intent.objectId,intent.metadataFolderId,intent.marker,
            intent.expected?.let(TemplateSyncContracts5::encode),intent.expectedTag,TemplateSyncContracts5.encode(intent.intended),emptyList(),
            listOf(retainObservation(intent,permissionId,initialObservation)),null,
            originPath=if(historyFirst)workingPointer.path else null,originHash=if(historyFirst)workingPointer.sha256 else null)
        val next=files.writeRecord(json.encodeToString(d));load(next);return next
    }
    fun load(pointer:SyncIntentFile5):CheckedPublication5 {
        val d=read(pointer);publicationHash5(d.bindingHash);publicationId5(d.permissionId);publicationId5(d.revisionFolderId)
        val wp=SyncIntentFile5(d.workingPath,d.workingHash);check(wp!=pointer)
        val w=workingFiles.load(wp);check(w.captured.expectedLocalRevisionId==w.captured.manifest.revisionId)
        val historyFirst=d.kind=="transferProgress"
        if(!historyFirst)check(w.allFilesAcknowledged)
        check((d.originPath==null)==(d.originHash==null))
        val origin=d.originPath?.let {SyncIntentFile5(it,checkNotNull(d.originHash))} ?: wp
        val original=workingFiles.load(origin);check(original.capturedPointer==w.capturedPointer)
        if(historyFirst)check(original.receipts.isEmpty())
        check(d.jobId==w.captured.jobId && d.operationId==w.captured.operationId && d.bindingId==w.captured.bindingId)
        val intent=head(d);val manifest=w.captured.manifest
        check(intent.intended.templateId==manifest.templateId && intent.intended.sourceHash==manifest.sourceHash &&
            intent.intended.workingFolderId==w.captured.steps.first().folderId)
        check(w.captured.steps.all {it.marker==intent.marker})
        intent.expectedTag?.let(::publicationTag5)
        val ids=manifest.files.map {it.objectId}+listOf(intent.intended.revisionManifestId,intent.objectId,intent.metadataFolderId,d.revisionFolderId,intent.intended.workingFolderId)
        check(ids.distinct().size==ids.size && w.captured.steps.none {it.objectId in ids})
        val sourceByName=w.captured.steps.associateBy {it.name}
        val steps=manifest.files.map {ImmutableFileStep(it.objectId,d.revisionFolderId,intent.marker,it.relativeName,"text/plain",sourceByName.getValue(it.relativeName).intendedBytes)}+
            ImmutableFileStep(intent.intended.revisionManifestId,d.revisionFolderId,intent.marker,"revision.json","application/json",TemplateSyncContracts5.encode(manifest).toByteArray())
        val byId=steps.associateBy {it.objectId};check(d.immutableReceipts.size<=steps.size && d.immutableReceipts.map {it.objectId}.distinct().size==d.immutableReceipts.size)
        for(r in d.immutableReceipts)check(byId[r.objectId]?.sha256==r.sha256)
        check(d.observations.size in 1..64)
        val observations=d.observations.map {restoreObservation(intent,d.permissionId,it)}
        check(observations.first()?.head==intent.expected && observations.first()?.tag==intent.expectedTag)
        d.publication?.let {r->publicationTag5(r.tag);check(w.allFilesAcknowledged && d.immutableReceipts.size==steps.size)
            val observation=checkNotNull(observations.getOrNull(r.observationIndex));check(observation.head==intent.intended && observation.tag==r.tag)}
        val raw=audit.load(d.rawAudit,origin,d.bindingHash,d.permissionId,intent.objectId)
        return CheckedPublication5(wp,w,d.bindingHash,d.permissionId,d.revisionFolderId,intent,steps,d.immutableReceipts,observations,d.publication?.tag,raw,historyFirst,origin)
    }
    fun acknowledgeImmutable(pointer:SyncIntentFile5,receipt:ImmutableReceipt5):SyncIntentFile5 {
        val checked=load(pointer);check(checked.immutableSteps.singleOrNull {it.objectId==receipt.objectId}?.sha256==receipt.sha256)
        checked.receipts[receipt.objectId]?.let {check(it==receipt);return pointer}
        check(checked.publishedTag==null)
        val d=read(pointer);val next=files.writeRecord(json.encodeToString(d.copy(immutableReceipts=d.immutableReceipts+receipt)));load(next);return next
    }
    fun observe(pointer:SyncIntentFile5,snapshot:PublicationHeadSnapshot?):SyncIntentFile5 {
        val checked=load(pointer);val d=read(pointer);val observation=retainObservation(checked.intent,checked.permissionId,snapshot)
        if(d.observations.last()==observation)return pointer
        check(d.observations.size<64)
        val next=files.writeRecord(json.encodeToString(d.copy(observations=d.observations+observation)));load(next);return next
    }
    fun acknowledgePublication(pointer:SyncIntentFile5,tag:String):SyncIntentFile5 {
        val checked=load(pointer);publicationTag5(tag)
        checked.publishedTag?.let {check(it==tag);return pointer}
        check(checked.allImmutableAcknowledged && checked.working.allFilesAcknowledged)
        val observation=checkNotNull(checked.observations.last());check(observation.head==checked.intent.intended && observation.tag==tag)
        val d=read(pointer);val next=files.writeRecord(json.encodeToString(d.copy(publication=HeadReceipt5(tag,d.observations.lastIndex))));load(next);return next
    }
    fun retainRaw(pointer:SyncIntentFile5,observation:PublicationRawObservation5):SyncIntentFile5 {
        val checked=load(pointer);val d=read(pointer)
        val raw=audit.append(d.rawAudit,checked.originPointer,d.bindingHash,d.permissionId,checked.intent.objectId,observation)
        val next=files.writeRecord(json.encodeToString(d.copy(schemaVersion=if(checked.historyFirst)"1.2.0" else "1.1.0",rawAudit=raw)));load(next);return next
    }
    fun acknowledgeWorking(pointer:SyncIntentFile5,receipt:WorkingReceipt5):SyncIntentFile5 {
        val checked=load(pointer);check(checked.historyFirst && checked.allImmutableAcknowledged)
        val nextWorking=workingFiles.acknowledge(checked.workingPointer,receipt)
        if(nextWorking==checked.workingPointer)return pointer
        val d=read(pointer);val next=files.writeRecord(json.encodeToString(d.copy(workingPath=nextWorking.path,workingHash=nextWorking.sha256)))
        load(next);return next
    }
}
