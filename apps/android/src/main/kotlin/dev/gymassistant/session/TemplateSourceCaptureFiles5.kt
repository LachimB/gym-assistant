package dev.gymassistant.session

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

private fun captureId5(id:String)=require(id.matches(Regex("[A-Za-z0-9_-]{1,160}")))
@Serializable data class SourceCaptureNamespace5(val bindingHash:String,val permissionId:String,val commonRootId:String,val areaFolderId:String,
    val templateFolderId:String,val workingFolderId:String,val historyFolderId:String,val metadataFolderId:String,val headFileId:String,val marker:String) {
    init {
        require(bindingHash.matches(Regex("[a-f0-9]{64}")));captureId5(permissionId)
        val ids=listOf(commonRootId,areaFolderId,templateFolderId,workingFolderId,historyFolderId,metadataFolderId,headFileId)
        ids.forEach(::captureId5);require(ids.distinct().size==ids.size)
        require(marker.matches(Regex("gym-template-[A-Za-z0-9_-]{3,128}")))
    }
    internal val ids get()=setOf(commonRootId,areaFolderId,templateFolderId,workingFolderId,historyFolderId,metadataFolderId,headFileId)
}
@Serializable data class SourceCapturePin5(val role:String,val relativeName:String,val objectId:String,val mimeType:String) {
    init {
        require(role in setOf("template","exercise"));captureId5(objectId)
        require(relativeName.length in 1..128 && !relativeName.startsWith('.') && !relativeName.contains("..") &&
            relativeName.none {it=='/' || it=='\\' || it==':' || it.isISOControl()} && (relativeName.endsWith(".yaml") || relativeName.endsWith(".yml")))
        require(mimeType in setOf("text/plain","text/yaml","application/yaml","application/x-yaml"))
    }
}
@Serializable private data class SourceCaptureDocument5(val kind:String,val schemaVersion:String,val stage:String,val observationId:String,val originPath:String,val originHash:String,
    val namespace:SourceCaptureNamespace5,val files:List<SourceCapturePin5>,val rawAudit:PublicationAuditPointer5?=null,
    val captureOriginPath:String?=null,val captureOriginHash:String?=null)

/** Incomplete observation only. Neither an empty pin list nor missing raw response establishes remote absence. */
class CheckedSourceCapture5 internal constructor(val pointer:SyncIntentFile5,val observationId:String,val captureOrigin:SyncIntentFile5,
    val originPointer:SyncIntentFile5,val local:CheckedLocalPublication5,val namespace:SourceCaptureNamespace5,
    pins:List<SourceCapturePin5>,raw:List<PublicationRawObservation5>) {
    val pins=pins.toList();val rawObservations=raw.toList()
    fun requireJob(job:TemplateSyncJob5){local.requireJob(job);check(originPointer==SyncIntentFile5(job.intentPath,job.intentHash) && job.state=="running")}
}

/** Capture-progress records retain originals without declaring a valid candidate, a head, or adoption. */
class TemplateSourceCaptureFiles5(private val files:TemplateSyncFiles5,private val origins:TemplateSyncQueueFiles5) {
    private val json=Json {encodeDefaults=false;ignoreUnknownKeys=false};private val audit=TemplatePublicationAudit5(files)
    private fun read(pointer:SyncIntentFile5)=json.decodeFromString<SourceCaptureDocument5>(files.readRecord(pointer)).also {
        check(it.kind=="sourceCaptureProgress" && it.schemaVersion=="1.0.0" && it.stage=="capturing")
        require(it.observationId.matches(Regex("[A-Za-z0-9_-]{3,128}")))
    }
    private fun pins(namespace:SourceCaptureNamespace5,pins:List<SourceCapturePin5>) {
        require(pins.isEmpty() || pins.size in 2..16 && pins.count {it.role=="template"}==1)
        require(pins.map {it.objectId}.distinct().size==pins.size && pins.map {it.relativeName}.distinct().size==pins.size)
        require(pins.none {it.objectId in namespace.ids})
    }
    fun prepare(origin:SyncIntentFile5,namespace:SourceCaptureNamespace5,pins:List<SourceCapturePin5>,observationId:String):SyncIntentFile5 {
        val local=origins.load(origin);check(namespace.bindingHash==local.bindingHash);pins(namespace,pins)
        val d=SourceCaptureDocument5("sourceCaptureProgress","1.0.0","capturing",observationId,origin.path,origin.sha256,namespace,pins.toList())
        val next=files.writeRecord(json.encodeToString(d));load(next);return next
    }
    fun load(pointer:SyncIntentFile5):CheckedSourceCapture5 {
        val d=read(pointer);val origin=SyncIntentFile5(d.originPath,d.originHash);check(origin!=pointer)
        val local=origins.load(origin);check(d.namespace.bindingHash==local.bindingHash);pins(d.namespace,d.files)
        check((d.captureOriginPath==null)==(d.captureOriginHash==null))
        val anchor=d.captureOriginPath?.let {SyncIntentFile5(it,checkNotNull(d.captureOriginHash))} ?: pointer
        if(d.rawAudit==null)check(d.captureOriginPath==null)
        else {
            check(anchor!=pointer && d.captureOriginPath!=null)
            val initial=read(anchor)
            check(initial.rawAudit==null && initial.captureOriginPath==null && initial.captureOriginHash==null && initial.observationId==d.observationId &&
                initial.originPath==d.originPath && initial.originHash==d.originHash && initial.namespace==d.namespace && initial.files==d.files)
        }
        val raw=audit.load(d.rawAudit,anchor,d.namespace.bindingHash,d.namespace.permissionId,d.namespace.headFileId)
        return CheckedSourceCapture5(pointer,d.observationId,anchor,origin,local,d.namespace,d.files,raw)
    }
    fun retainRaw(pointer:SyncIntentFile5,observation:PublicationRawObservation5):SyncIntentFile5 {
        val checked=load(pointer);val d=read(pointer)
        val raw=audit.append(d.rawAudit,checked.captureOrigin,d.namespace.bindingHash,d.namespace.permissionId,d.namespace.headFileId,observation)
        val next=files.writeRecord(json.encodeToString(d.copy(rawAudit=raw,captureOriginPath=checked.captureOrigin.path,captureOriginHash=checked.captureOrigin.sha256)));load(next);return next
    }
}
