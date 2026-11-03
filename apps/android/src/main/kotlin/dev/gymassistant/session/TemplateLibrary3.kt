package dev.gymassistant.session

import androidx.room.withTransaction
import dev.gymassistant.engine.v2.*
import java.time.Instant
import java.util.UUID
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString

/** Staging and explicit publication only; never edits an existing revision or running session. */
class TemplateLibrary3(
    private val database:WorkoutDatabase,private val files:LibraryFiles3,private val contracts:ImportContracts2,
    private val beforeHead:()->Unit={},
) {
    private val dao get()=database.library()
    suspend fun stage(inputs:List<ImportFile3>,at:String=Instant.now().toString()):TemplateCandidateRow {
        val id=UUID.randomUUID().toString();val staged=files.stage(id,inputs)
        val fileJson=sessionJson.encodeToString(staged)
        val checked=try {
            val source=files.read(staged);val plan=compileTemplate2(source.templateYaml,source.exerciseYamls,contracts)
            val sourceJson=sessionJson.encodeToString(source);val planJson=planJson2.encodeToString(plan)
            TemplateCandidateRow(id,fileJson,at,plan.templateId,plan.revisionId,null,"valid",
                sourceJson,hashUtf8_2(sourceJson),planJson,hashUtf8_2(planJson))
        }catch(cancelled:kotlinx.coroutines.CancellationException){throw cancelled}
        catch(_:Exception){TemplateCandidateRow(id,fileJson,at,validationCode="invalidOrUnsupported")}
        val row=if(checked.validationCode=="valid")checked.copy(expectedHeadRevisionId=dao.head(checkNotNull(checked.templateId))?.revisionId)else checked
        dao.insertCandidate(row)
        return row
    }
    suspend fun candidate(id:String)=dao.candidate(id)
    suspend fun published():List<TemplateRevisionRow> = dao.published().onEach { verify(it) }
    fun verify(row:TemplateRevisionRow):Plan2 {
        check(row.engineVersion=="kotlin-timed-2" && row.planFormatVersion=="2.0.0")
        check(hashUtf8_2(row.sourceSnapshotJson)==row.sourceHash && hashUtf8_2(row.compiledPlanJson)==row.compiledPlanHash)
        val plan=planJson2.decodeFromString<Plan2>(row.compiledPlanJson)
        check(plan.templateId==row.templateId && plan.revisionId==row.revisionId && plan.adjustments.isEmpty())
        requirePlanBudget2(plan)
        return plan
    }
    suspend fun publish(id:String,reviewedSourceHash:String,reviewedPlanHash:String,at:String=Instant.now().toString()):TemplateRevisionRow {
        val candidate=checkNotNull(dao.candidate(id));check(candidate.validationCode=="valid") { "Source is quarantined" }
        val descriptors=sessionJson.decodeFromString<List<LibraryFile3>>(candidate.sourceFilesJson)
        val source=files.read(descriptors);val sourceJson=sessionJson.encodeToString(source)
        val plan=compileTemplate2(source.templateYaml,source.exerciseYamls,contracts);val encoded=planJson2.encodeToString(plan)
        check(hashUtf8_2(sourceJson)==reviewedSourceHash && reviewedSourceHash==candidate.sourceHash && sourceJson==candidate.sourceSnapshotJson) { "Reviewed source changed" }
        check(hashUtf8_2(encoded)==reviewedPlanHash && reviewedPlanHash==candidate.compiledPlanHash && encoded==candidate.compiledPlanJson) { "Reviewed plan changed" }
        // These immutable files can be orphaned by an interrupted/stale transaction, never made executable.
        files.exportPublished(plan.templateId,plan.revisionId,source)
        return database.withTransaction {
            check(dao.candidate(id)==candidate) { "Candidate review changed" }
            val head=dao.head(plan.templateId)
            val existing=dao.revision(plan.templateId,plan.revisionId)
            if(existing!=null) {
                check(existing.sourceHash==reviewedSourceHash && existing.compiledPlanHash==reviewedPlanHash) { "Revision identity already used" }
                check(head?.revisionId==existing.revisionId) { "Restoring an older revision requires a new revision identity" }
                if(candidate.reviewStatus=="staged")check(dao.reviewed(id,reviewedPlanHash,at)==1)
                return@withTransaction existing
            }
            check(candidate.reviewStatus=="staged" && head?.revisionId==candidate.expectedHeadRevisionId) { "Published head changed; review a fresh candidate" }
            val sourceVersion=readYaml2(source.templateYaml).text2("schemaVersion")
            val row=TemplateRevisionRow(plan.templateId,plan.revisionId,sourceJson,reviewedSourceHash,encoded,reviewedPlanHash,sourceVersion,
                publishedAt=at,candidateId=id)
            dao.insertRevision(row)
            beforeHead()
            if(head==null)dao.insertHead(TemplateHeadRow(plan.templateId,plan.revisionId))
            else check(dao.advanceHead(plan.templateId,head.revisionId,plan.revisionId)==1)
            check(dao.reviewed(id,reviewedPlanHash,at)==1)
            row
        }
    }
}
