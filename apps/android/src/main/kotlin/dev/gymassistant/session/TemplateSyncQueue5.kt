package dev.gymassistant.session

import androidx.room.withTransaction
import dev.gymassistant.engine.v2.*
import dev.gymassistant.sync.hashSourceBytes
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.*

@Serializable private data class LocalSourceFile5(val name:String,val sha256:String,val byteCount:Int)
@Serializable private data class LocalPublication5(val kind:String,val schemaVersion:String,
    val jobId:String,val operationId:String,val bindingId:String,val templateId:String,val revisionId:String,
    val sourceHash:String,val compiledPlanHash:String,val bindingHash:String,val baseRevisionId:String?,val baseSourceHash:String?,
    val expectedRemoteGeneration:String?,val expectedRemoteHeadTag:String?,val sources:List<LocalSourceFile5>)

/** No remote object IDs, before-images or inferred absent head: reconnection still requires a verified remote capture. */
class CheckedLocalPublication5(val jobId:String,val operationId:String,val bindingId:String,val templateId:String,val revisionId:String,
    val sourceHash:String,val compiledPlanHash:String,val bindingHash:String,val baseRevisionId:String?,val baseSourceHash:String?,
    val expectedRemoteGeneration:String?,val expectedRemoteHeadTag:String?,val sources:TemplateSources3) {
    fun requireJob(job:TemplateSyncJob5) {
        check(job.jobId==jobId && job.operationId==operationId && job.bindingId==bindingId && job.direction=="push" &&
            job.expectedLocalRevisionId==revisionId && job.sourceHash==sourceHash && job.compiledPlanHash==compiledPlanHash &&
            job.expectedRemoteGeneration==expectedRemoteGeneration && job.expectedRemoteHeadTag==expectedRemoteHeadTag)
    }
}

class TemplateSyncQueueFiles5(private val files:TemplateSyncFiles5,private val contracts:ImportContracts2) {
    private val json=Json {encodeDefaults=true;ignoreUnknownKeys=false}
    private fun verify(row:TemplateRevisionRow,sources:TemplateSources3) {
        check(row.engineVersion=="kotlin-timed-2" && row.planFormatVersion=="2.0.0")
        check(sources.exerciseYamls.size in 1..15)
        val encoded=sessionJson.encodeToString(sources)
        check(encoded==row.sourceSnapshotJson && hashSourceBytes(encoded.toByteArray())==row.sourceHash)
        val plan=compileTemplate2(sources.templateYaml,sources.exerciseYamls,contracts)
        val compiled=planJson2.encodeToString(plan)
        check(plan.templateId==row.templateId && plan.revisionId==row.revisionId && plan.adjustments.isEmpty())
        check(compiled==row.compiledPlanJson && hashSourceBytes(compiled.toByteArray())==row.compiledPlanHash)
        check(readYaml2(sources.templateYaml).text2("schemaVersion")==row.templateFormatVersion)
    }
    fun prepare(jobId:String,operationId:String,binding:TemplateSyncBinding5,bindingHash:String,row:TemplateRevisionRow):SyncIntentFile5 {
        listOf(jobId,operationId,binding.bindingId,row.templateId,row.revisionId).forEach {require(it.matches(Regex("[A-Za-z0-9_-]{3,128}")))}
        require(bindingHash.matches(Regex("[a-f0-9]{64}")))
        check(binding.templateId==row.templateId)
        val sources=sessionJson.decodeFromString<TemplateSources3>(row.sourceSnapshotJson);verify(row,sources)
        val payloads=listOf(sources.templateYaml)+sources.exerciseYamls
        require(payloads.sumOf {it.toByteArray(Charsets.UTF_8).size.toLong()}<=8388608)
        val descriptors=payloads.mapIndexed {i,text->val raw=text.toByteArray(Charsets.UTF_8)
            LocalSourceFile5(if(i==0)"template.yaml" else "exercise-${i-1}.yaml",files.retainSource(raw),raw.size)}
        val dto=LocalPublication5("localPublication","1.0.0",jobId=jobId,operationId=operationId,bindingId=binding.bindingId,templateId=row.templateId,
            revisionId=row.revisionId,sourceHash=row.sourceHash,compiledPlanHash=row.compiledPlanHash,bindingHash=bindingHash,
            baseRevisionId=binding.baseRevisionId,baseSourceHash=binding.baseSourceHash,expectedRemoteGeneration=binding.lastRemoteGeneration,
            expectedRemoteHeadTag=binding.lastRemoteHeadTag,sources=descriptors)
        val pointer=files.writeRecord(json.encodeToString(dto));load(pointer);return pointer
    }
    fun load(pointer:SyncIntentFile5):CheckedLocalPublication5 {
        val dto=json.decodeFromString<LocalPublication5>(files.readRecord(pointer))
        check(dto.kind=="localPublication" && dto.schemaVersion=="1.0.0")
        listOf(dto.jobId,dto.operationId,dto.bindingId,dto.templateId,dto.revisionId).forEach {require(it.matches(Regex("[A-Za-z0-9_-]{3,128}")))}
        listOf(dto.sourceHash,dto.compiledPlanHash,dto.bindingHash).forEach {require(it.matches(Regex("[a-f0-9]{64}")))}
        require((dto.baseRevisionId==null)==(dto.baseSourceHash==null))
        dto.baseRevisionId?.let {require(it.matches(Regex("[A-Za-z0-9_-]{3,160}")))}
        dto.baseSourceHash?.let {require(it.matches(Regex("[a-f0-9]{64}")))}
        dto.expectedRemoteGeneration?.let {require(it.matches(Regex("[A-Za-z0-9_-]{3,160}")))}
        dto.expectedRemoteHeadTag?.let {require(it.length in 1..1024 && it.none(Char::isISOControl))}
        require(dto.sources.size in 2..16 && dto.sources.map {it.name}.distinct().size==dto.sources.size)
        val payloads=dto.sources.mapIndexed {i,file->
            require(file.name==if(i==0)"template.yaml" else "exercise-${i-1}.yaml")
            val raw=files.source(file.sha256);require(file.byteCount==raw.size)
            Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(raw)).toString()}
        require(dto.sources.sumOf {it.byteCount.toLong()}<=8388608)
        val sources=TemplateSources3(payloads[0],payloads.drop(1));val source=sessionJson.encodeToString(sources)
        val plan=compileTemplate2(sources.templateYaml,sources.exerciseYamls,contracts);val compiled=planJson2.encodeToString(plan)
        check(plan.templateId==dto.templateId && plan.revisionId==dto.revisionId && plan.adjustments.isEmpty())
        check(hashSourceBytes(source.toByteArray())==dto.sourceHash && hashSourceBytes(compiled.toByteArray())==dto.compiledPlanHash)
        return CheckedLocalPublication5(dto.jobId,dto.operationId,dto.bindingId,dto.templateId,dto.revisionId,dto.sourceHash,dto.compiledPlanHash,
            dto.bindingHash,dto.baseRevisionId,dto.baseSourceHash,dto.expectedRemoteGeneration,dto.expectedRemoteHeadTag,sources)
    }
}

/** Explicit per-Template opt-in and expected local head; no implicit library-wide enqueue or network access. */
class TemplateSyncQueue5(private val db:WorkoutDatabase,private val files:TemplateSyncQueueFiles5,private val beforeCommit:()->Unit={}) {
    private suspend fun scope(id:String):Pair<TemplateSyncBinding5,String> {
        val binding=checkNotNull(db.templateSync().binding(id));val root=checkNotNull(db.templateSync().root(binding.rootBindingId))
        val connection=checkNotNull(db.deliveries().connection(binding.connectionId))
        check(binding.enabled && root.state=="active" && root.provider==connection.provider && root.permissionId==connection.permissionId)
        val identity=listOf(binding.bindingId,binding.connectionId,binding.rootBindingId,binding.templateId,binding.templateFolderId,
            binding.workingFolderId,binding.historyFolderId,binding.metadataFolderId,binding.headFileId,binding.marker,
            root.provider,root.permissionId,root.commonRootFolderId,root.marker,connection.rootFolderId,connection.namespaceMarker)
        return binding to hashSourceBytes(JsonArray(identity.map(::JsonPrimitive)).toString().toByteArray(Charsets.UTF_8))
    }
    suspend fun enqueue(bindingId:String,expectedRevisionId:String,reviewedSourceHash:String,reviewedPlanHash:String,
        operationId:String,jobId:String=UUID.randomUUID().toString()):TemplateSyncJob5=withContext(Dispatchers.IO) {
        val snapshot=scope(bindingId);val binding=snapshot.first
        val revision=checkNotNull(db.library().revision(binding.templateId,expectedRevisionId))
        check(revision.sourceHash==reviewedSourceHash && revision.compiledPlanHash==reviewedPlanHash)
        // Files/compilation precede SQL. A rollback may retain inert evidence; it cannot enqueue a partial intent.
        val pointer=files.prepare(jobId,operationId,binding,snapshot.second,revision)
        val proposed=TemplateSyncJob5(jobId,bindingId,operationId,"push",expectedRevisionId,binding.lastRemoteGeneration,binding.lastRemoteHeadTag,
            reviewedSourceHash,reviewedPlanHash,pointer.path,pointer.sha256)
        db.withTransaction {
            check(scope(bindingId)==snapshot) {"Template sync binding changed"}
            check(db.library().head(binding.templateId)?.revisionId==expectedRevisionId && db.library().revision(binding.templateId,expectedRevisionId)==revision) {"Published source changed"}
            val existing=db.templateSync().operation(operationId)
            if(existing!=null) {
                check(existing.bindingId==bindingId && existing.direction=="push" && existing.expectedLocalRevisionId==expectedRevisionId &&
                    existing.sourceHash==reviewedSourceHash && existing.compiledPlanHash==reviewedPlanHash) {"Operation identity already used"}
                return@withTransaction existing
            }
            db.templateSync().insertJob(proposed);beforeCommit();proposed
        }
    }
    suspend fun load(lease:SyncLease5,now:()->Long):CheckedLocalPublication5=withContext(Dispatchers.IO) {
        val leases=TemplateSyncLeases5(db);val job=leases.owned(lease,now())
        val checked=files.load(SyncIntentFile5(job.intentPath,job.intentHash));checked.requireJob(job)
        db.withTransaction {
            check(leases.owned(lease,now())==job)
            val(binding,hash)=scope(job.bindingId);check(hash==checked.bindingHash && binding.templateId==checked.templateId)
            val revision=checkNotNull(db.library().revision(checked.templateId,checked.revisionId))
            check(revision.sourceHash==checked.sourceHash && revision.compiledPlanHash==checked.compiledPlanHash)
        }
        checked
    }
}
