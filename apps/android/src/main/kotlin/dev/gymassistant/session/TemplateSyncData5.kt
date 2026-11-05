package dev.gymassistant.session

import androidx.room.*
import dev.gymassistant.engine.v2.readYaml2
import kotlinx.serialization.json.*

private val syncId5 = Regex("[A-Za-z0-9_-]{3,160}")
private fun checkHash5(value:String?) { require(value==null || value.matches(Regex("[a-f0-9]{64}"))) }
private fun checkId5(value:String?) { require(value==null || syncId5.matches(value)) }
private fun checkTag5(value:String?) { require(value==null || value.length in 1..1024) }
private fun checkReserved5(raw:String) {
    require(raw.toByteArray(Charsets.UTF_8).size<=32768)
    val values=Json.parseToJsonElement(raw).jsonObject
    readYaml2(raw)
    require(values.size<=64)
    for((key,value) in values) {
        require(key.matches(Regex("[A-Za-z0-9_-]{1,128}")))
        require(value is JsonPrimitive && value.isString && value.content.matches(Regex("[A-Za-z0-9_-]{1,160}")))
    }
    require(values.values.distinct().size==values.size)
}
private fun checkPath5(value:String) { require(value.matches(Regex("[A-Za-z0-9_-]{3,128}\\.json"))) }

@Entity(tableName="project_storage_roots",indices=[Index(value=["provider","permissionId"],unique=true)])
data class ProjectStorageRoot5(@PrimaryKey val rootBindingId:String,val provider:String="googleDrive",val permissionId:String,
    val commonRootFolderId:String,val marker:String,val state:String="reserved",val verifiedAt:String?=null) {
    init { checkId5(rootBindingId);checkId5(commonRootFolderId);require(provider=="googleDrive" && permissionId.isNotBlank() && permissionId.length<=160)
        require(marker.matches(Regex("gym-project-[A-Za-z0-9_-]{3,128}")) && state in setOf("reserved","verified","adopting","active","blocked")) }
}

@Entity(tableName="template_sync_bindings",indices=[Index(value=["connectionId","templateId"],unique=true),Index(value=["rootBindingId"]),Index(value=["templateId"])],foreignKeys=[
    ForeignKey(entity=DriveConnectionRow::class,parentColumns=["connectionId"],childColumns=["connectionId"]),
    ForeignKey(entity=ProjectStorageRoot5::class,parentColumns=["rootBindingId"],childColumns=["rootBindingId"]),
    ForeignKey(entity=TemplateHeadRow::class,parentColumns=["templateId"],childColumns=["templateId"]),
])
data class TemplateSyncBinding5(@PrimaryKey val bindingId:String,val connectionId:String,val rootBindingId:String,val templateId:String,
    val templateFolderId:String,val workingFolderId:String,val historyFolderId:String,val metadataFolderId:String,val marker:String,
    val headFileId:String,val lastRemoteHeadTag:String?=null,val lastRemoteGeneration:String?=null,val baseRevisionId:String?=null,
    val baseSourceHash:String?=null,val enabled:Boolean=false,val createdAt:String,val updatedAt:String) {
    init { listOf(bindingId,connectionId,rootBindingId,templateId,templateFolderId,workingFolderId,historyFolderId,metadataFolderId,headFileId).forEach(::checkId5)
        require(listOf(templateFolderId,workingFolderId,historyFolderId,metadataFolderId,headFileId).distinct().size==5)
        require(marker.matches(Regex("gym-template-[A-Za-z0-9_-]{3,128}")))
        checkId5(lastRemoteGeneration);checkId5(baseRevisionId);checkHash5(baseSourceHash)
        require((baseRevisionId==null)==(baseSourceHash==null));checkTag5(lastRemoteHeadTag) }
}

@Entity(tableName="template_sync_jobs",indices=[Index(value=["operationId"],unique=true),Index(value=["bindingId"])],foreignKeys=[
    ForeignKey(entity=TemplateSyncBinding5::class,parentColumns=["bindingId"],childColumns=["bindingId"]),
])
data class TemplateSyncJob5(@PrimaryKey val jobId:String,val bindingId:String,val operationId:String,val direction:String,
    val expectedLocalRevisionId:String,val expectedRemoteGeneration:String?=null,val expectedRemoteHeadTag:String?=null,
    val sourceHash:String,val compiledPlanHash:String,val intentPath:String,val intentHash:String,val reservedObjectIdsJson:String="{}",
    val state:String="queued",val errorCode:String?=null,val attemptCount:Int=0,val nextAttemptAt:Long?=null,
    val leaseOwner:String?=null,val leaseGeneration:Long=0,val leaseUntil:Long?=null,val leaseBootId:String?=null) {
    init { listOf(jobId,bindingId,operationId,expectedLocalRevisionId).forEach(::checkId5);checkId5(expectedRemoteGeneration)
        require(direction in setOf("push","pull","consolidate"));listOf(sourceHash,compiledPlanHash,intentHash).forEach(::checkHash5);checkPath5(intentPath)
        require(state in setOf("queued","running","retry","waitingAuthorization","verifying","publishing","awaitingReview","conflict","complete","blocked"))
        require(errorCode==null || errorCode in setOf("network","budget","authorization","accountMismatch","missingRoot","contract","conflict","permission"))
        checkTag5(expectedRemoteHeadTag)
        require(attemptCount in 0..1000 && leaseGeneration in 0..9007199254740991L && (nextAttemptAt==null || nextAttemptAt>=0))
        require((leaseOwner==null)==(leaseUntil==null) && (leaseOwner==null)==(leaseBootId==null))
        require(leaseOwner==null || leaseOwner.matches(syncId5));require(leaseUntil==null || leaseUntil>=0)
        require(leaseBootId==null || leaseBootId.matches(Regex("[A-Za-z0-9_-]{1,128}")))
        checkReserved5(reservedObjectIdsJson) }
}

@Entity(tableName="template_sync_observations",indices=[Index(value=["bindingId"]),Index(value=["candidateId"])],foreignKeys=[
    ForeignKey(entity=TemplateSyncBinding5::class,parentColumns=["bindingId"],childColumns=["bindingId"]),
    ForeignKey(entity=TemplateCandidateRow::class,parentColumns=["candidateId"],childColumns=["candidateId"]),
])
data class TemplateSyncObservation5(@PrimaryKey val observationId:String,val bindingId:String,val candidateId:String?=null,
    val observationPath:String,val observationHash:String,val expectedLocalRevisionId:String,val remoteGeneration:String?=null,
    val remoteHeadTag:String?=null,val validationCode:String,val reviewState:String="pending",val capturedAt:String) {
    init { listOf(observationId,bindingId,expectedLocalRevisionId).forEach(::checkId5);checkId5(candidateId);checkId5(remoteGeneration)
        checkPath5(observationPath);checkHash5(observationHash);checkTag5(remoteHeadTag)
        require(validationCode in setOf("valid","invalid","unsupported","changing","conflict"));require(reviewState in setOf("pending","published","rejected")) }
}

@Dao interface TemplateSyncDao5 {
    @Insert suspend fun insertRoot(row:ProjectStorageRoot5)
    @Insert suspend fun insertBinding(row:TemplateSyncBinding5)
    @Insert suspend fun insertJob(row:TemplateSyncJob5)
    @Insert suspend fun insertObservation(row:TemplateSyncObservation5)
    @Query("SELECT * FROM project_storage_roots WHERE rootBindingId=:id") suspend fun root(id:String):ProjectStorageRoot5?
    @Query("SELECT * FROM project_storage_roots WHERE provider=:provider AND permissionId=:permission")
    suspend fun rootForAccount(provider:String,permission:String):ProjectStorageRoot5?
    @Query("SELECT * FROM template_sync_bindings WHERE bindingId=:id") suspend fun binding(id:String):TemplateSyncBinding5?
    @Query("SELECT * FROM template_sync_bindings WHERE enabled=1 ORDER BY bindingId") suspend fun enabledBindings():List<TemplateSyncBinding5>
    @Query("SELECT b.* FROM template_sync_bindings b JOIN project_storage_roots r ON r.rootBindingId=b.rootBindingId JOIN drive_connections c ON c.connectionId=b.connectionId WHERE b.enabled=1 AND r.state='active' AND r.provider=c.provider AND r.permissionId=c.permissionId ORDER BY b.bindingId")
    suspend fun schedulableBindings():List<TemplateSyncBinding5>
    @Query("SELECT * FROM template_sync_jobs WHERE jobId=:id") suspend fun job(id:String):TemplateSyncJob5?
    @Query("SELECT * FROM template_sync_jobs WHERE operationId=:operation") suspend fun operation(operation:String):TemplateSyncJob5?
    @Query("SELECT * FROM template_sync_observations WHERE bindingId=:binding ORDER BY capturedAt, observationId") suspend fun observations(binding:String):List<TemplateSyncObservation5>
    @Query("UPDATE template_sync_jobs SET leaseOwner=:owner,leaseGeneration=leaseGeneration+1,leaseUntil=:until,leaseBootId=:boot,state='running',attemptCount=attemptCount+1 WHERE jobId=:id AND attemptCount<1000 AND leaseGeneration<9007199254740991 AND state IN ('queued','retry','running','verifying','publishing') AND (nextAttemptAt IS NULL OR nextAttemptAt<=:now) AND (leaseOwner IS NULL OR leaseUntil<=:now OR leaseBootId!=:boot) AND EXISTS(SELECT 1 FROM template_sync_bindings b JOIN project_storage_roots r ON r.rootBindingId=b.rootBindingId JOIN drive_connections c ON c.connectionId=b.connectionId WHERE b.bindingId=template_sync_jobs.bindingId AND b.enabled=1 AND r.state='active' AND r.provider=c.provider AND r.permissionId=c.permissionId) AND NOT EXISTS(SELECT 1 FROM template_sync_jobs other WHERE other.bindingId=template_sync_jobs.bindingId AND other.jobId!=:id AND other.leaseOwner IS NOT NULL AND other.leaseUntil>:now AND other.leaseBootId=:boot)")
    suspend fun claim(id:String,owner:String,now:Long,until:Long,boot:String):Int
    @Query("UPDATE template_sync_jobs SET state=:state,errorCode=:error,nextAttemptAt=:next,leaseOwner=NULL,leaseUntil=NULL,leaseBootId=NULL WHERE jobId=:id AND leaseOwner=:owner AND leaseGeneration=:generation")
    suspend fun release(id:String,owner:String,generation:Long,state:String,error:String?,next:Long?):Int
    @Query("UPDATE template_sync_jobs SET intentPath=:nextPath,intentHash=:nextHash WHERE jobId=:id AND leaseOwner=:owner AND leaseGeneration=:generation AND intentPath=:priorPath AND intentHash=:priorHash AND state='running'")
    suspend fun advanceIntent(id:String,owner:String,generation:Long,priorPath:String,priorHash:String,nextPath:String,nextHash:String):Int
}
