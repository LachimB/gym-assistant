package dev.gymassistant.session

import dev.gymassistant.sync.hashSourceBytes
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

/** Untrusted original responses. Retention confers no validation, publication or adoption authority. */
class PublicationRawObservation5(val kind:String,val objectId:String,val status:Int,metadata:ByteArray?,content:ByteArray?) {
    private val metadata=metadata?.also {require(it.size<=65536)}?.copyOf()
    private val content=content?.also {require(it.size<=1048576)}?.copyOf()
    val metadataBytes get()=metadata?.copyOf()
    val contentBytes get()=content?.copyOf()
    init {
        require(kind in setOf("HEAD","WORKING","IMMUTABLE","NAMESPACE"))
        require(objectId.matches(Regex("[A-Za-z0-9_-]{1,160}")) && status in setOf(200,404))
        require(if(status==404)metadata==null && content==null else metadata!=null)
    }
    internal val byteCount get()=(metadata?.size?:0)+(content?.size?:0)
}

@Serializable internal data class PublicationAuditPointer5(val path:String,val sha256:String,val count:Int,val byteCount:Long)
@Serializable private data class RawBytes5(val sha256:String,val size:Int)
@Serializable private data class AuditRecord5(val kind:String,val schemaVersion:String,val workingPath:String,val workingHash:String,
    val bindingHash:String,val permissionId:String,val headObjectId:String,val previous:PublicationAuditPointer5?,
    val sequence:Int,val byteCount:Long,val objectKind:String,val objectId:String,val status:Int,val metadata:RawBytes5?,val content:RawBytes5?)

/** Append-only hash-linked private observations, independent of semantic head receipts. */
internal class TemplatePublicationAudit5(private val files:TemplateSyncFiles5) {
    private val json=Json {encodeDefaults=true;ignoreUnknownKeys=false}
    private val emptyHash=hashSourceBytes(byteArrayOf())
    private fun retain(bytes:ByteArray?):RawBytes5?=bytes?.let {
        RawBytes5(if(it.isEmpty())emptyHash else files.retainSource(it),it.size)
    }
    private fun restore(d:RawBytes5?):ByteArray?=d?.let {
        require(it.size in 0..1048576)
        if(it.size==0){check(it.sha256==emptyHash);byteArrayOf()}
        else files.source(it.sha256).also {bytes->check(bytes.size==it.size)}
    }
    fun load(pointer:PublicationAuditPointer5?,working:SyncIntentFile5,bindingHash:String,permissionId:String,headId:String):List<PublicationRawObservation5> {
        if(pointer==null)return emptyList()
        check(pointer.count in 1..1024 && pointer.byteCount in 0L..16777216L)
        val result=mutableListOf<PublicationRawObservation5>();val visited=mutableSetOf<String>();var current:PublicationAuditPointer5?=pointer
        while(current!=null) {
            val p=current;check(visited.add(p.path))
            val d=json.decodeFromString<AuditRecord5>(files.readRecord(SyncIntentFile5(p.path,p.sha256)))
            check(d.kind=="publicationRawObservation" && d.schemaVersion=="1.0.0" && d.workingPath==working.path && d.workingHash==working.sha256 &&
                d.bindingHash==bindingHash && d.permissionId==permissionId && d.headObjectId==headId && d.sequence==p.count && d.byteCount==p.byteCount)
            val observation=PublicationRawObservation5(d.objectKind,d.objectId,d.status,restore(d.metadata),restore(d.content))
            check(p.count==(d.previous?.count?:0)+1 && p.byteCount==(d.previous?.byteCount?:0)+observation.byteCount)
            check((p.count==1)==(d.previous==null));result+=observation;current=d.previous
        }
        check(result.size==pointer.count);return result.asReversed()
    }
    fun append(previous:PublicationAuditPointer5?,working:SyncIntentFile5,bindingHash:String,permissionId:String,headId:String,
        observation:PublicationRawObservation5):PublicationAuditPointer5 {
        val count=(previous?.count?:0)+1;val byteCount=(previous?.byteCount?:0)+observation.byteCount
        check(count<=1024 && byteCount<=16777216) {"Retained observation capacity requires review"}
        val d=AuditRecord5("publicationRawObservation","1.0.0",working.path,working.sha256,bindingHash,permissionId,headId,previous,count,byteCount,
            observation.kind,observation.objectId,observation.status,retain(observation.metadataBytes),retain(observation.contentBytes))
        val record=files.writeRecord(json.encodeToString(d));return PublicationAuditPointer5(record.path,record.sha256,count,byteCount)
    }
}
