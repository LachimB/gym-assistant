package dev.gymassistant.session

import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Entity(tableName="template_candidates")
data class TemplateCandidateRow(
    @PrimaryKey val candidateId:String,val sourceFilesJson:String,val createdAt:String,
    val templateId:String?=null,val revisionId:String?=null,val expectedHeadRevisionId:String?=null,
    val validationCode:String,val sourceSnapshotJson:String?=null,val sourceHash:String?=null,
    val compiledPlanJson:String?=null,val compiledPlanHash:String?=null,
    val reviewStatus:String="staged",val reviewedAt:String?=null,
)
@Entity(tableName="template_revisions",primaryKeys=["templateId","revisionId"],indices=[Index(value=["candidateId"])],foreignKeys=[
    ForeignKey(entity=TemplateCandidateRow::class,parentColumns=["candidateId"],childColumns=["candidateId"]),
])
data class TemplateRevisionRow(
    val templateId:String,val revisionId:String,val sourceSnapshotJson:String,val sourceHash:String,
    val compiledPlanJson:String,val compiledPlanHash:String,val templateFormatVersion:String,
    val planFormatVersion:String="2.0.0",val engineVersion:String="kotlin-timed-2",val publishedAt:String,val candidateId:String,
)
@Entity(tableName="template_heads",indices=[Index(value=["templateId","revisionId"])],foreignKeys=[
    ForeignKey(entity=TemplateRevisionRow::class,parentColumns=["templateId","revisionId"],childColumns=["templateId","revisionId"]),
])
data class TemplateHeadRow(@PrimaryKey val templateId:String,val revisionId:String)
@Entity(tableName="execution_plan_revisions",primaryKeys=["sessionId","structuralRevision"],
    indices=[Index(value=["sessionId","adjustmentId"],unique=true)],foreignKeys=[
        ForeignKey(entity=StartRow::class,parentColumns=["sessionId"],childColumns=["sessionId"]),
    ])
data class ExecutionPlanRow(
    val sessionId:String,val structuralRevision:Long,val compiledPlanJson:String,val compiledPlanHash:String,val adjustmentId:String?=null,
)
@Dao
interface LibraryDao {
    @Insert suspend fun insertCandidate(row:TemplateCandidateRow)
    @Query("SELECT * FROM template_candidates WHERE candidateId=:id") suspend fun candidate(id:String):TemplateCandidateRow?
    @Query("UPDATE template_candidates SET reviewStatus='published', reviewedAt=:at WHERE candidateId=:id AND reviewStatus='staged' AND compiledPlanHash=:hash")
    suspend fun reviewed(id:String,hash:String,at:String):Int
    @Query("SELECT * FROM template_heads WHERE templateId=:id") suspend fun head(id:String):TemplateHeadRow?
    @Query("SELECT * FROM template_revisions WHERE templateId=:id AND revisionId=:revision") suspend fun revision(id:String,revision:String):TemplateRevisionRow?
    @Query("SELECT r.* FROM template_revisions r JOIN template_heads h ON r.templateId=h.templateId AND r.revisionId=h.revisionId ORDER BY r.templateId")
    suspend fun published():List<TemplateRevisionRow>
    @Insert suspend fun insertRevision(row:TemplateRevisionRow)
    @Insert suspend fun insertHead(row:TemplateHeadRow)
    @Query("UPDATE template_heads SET revisionId=:next WHERE templateId=:id AND revisionId=:expected")
    suspend fun advanceHead(id:String,expected:String,next:String):Int
    @Insert suspend fun insertPlan(row:ExecutionPlanRow)
    @Query("SELECT * FROM execution_plan_revisions WHERE sessionId=:session AND structuralRevision=:revision")
    suspend fun plan(session:String,revision:Long):ExecutionPlanRow?
    @Query("SELECT * FROM execution_plan_revisions WHERE sessionId=:session ORDER BY structuralRevision")
    suspend fun plans(session:String):List<ExecutionPlanRow>
}
val LIBRARY_MIGRATION_2_3:Migration=object:Migration(2,3) {
    override fun migrate(db:SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `template_candidates` (`candidateId` TEXT NOT NULL, `sourceFilesJson` TEXT NOT NULL, `createdAt` TEXT NOT NULL, `templateId` TEXT, `revisionId` TEXT, `expectedHeadRevisionId` TEXT, `validationCode` TEXT NOT NULL, `sourceSnapshotJson` TEXT, `sourceHash` TEXT, `compiledPlanJson` TEXT, `compiledPlanHash` TEXT, `reviewStatus` TEXT NOT NULL, `reviewedAt` TEXT, PRIMARY KEY(`candidateId`))")
        db.execSQL("CREATE TABLE IF NOT EXISTS `template_revisions` (`templateId` TEXT NOT NULL, `revisionId` TEXT NOT NULL, `sourceSnapshotJson` TEXT NOT NULL, `sourceHash` TEXT NOT NULL, `compiledPlanJson` TEXT NOT NULL, `compiledPlanHash` TEXT NOT NULL, `templateFormatVersion` TEXT NOT NULL, `planFormatVersion` TEXT NOT NULL, `engineVersion` TEXT NOT NULL, `publishedAt` TEXT NOT NULL, `candidateId` TEXT NOT NULL, PRIMARY KEY(`templateId`, `revisionId`), FOREIGN KEY(`candidateId`) REFERENCES `template_candidates`(`candidateId`) ON UPDATE NO ACTION ON DELETE NO ACTION)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_template_revisions_candidateId` ON `template_revisions` (`candidateId`)")
        db.execSQL("CREATE TABLE IF NOT EXISTS `template_heads` (`templateId` TEXT NOT NULL, `revisionId` TEXT NOT NULL, PRIMARY KEY(`templateId`), FOREIGN KEY(`templateId`, `revisionId`) REFERENCES `template_revisions`(`templateId`, `revisionId`) ON UPDATE NO ACTION ON DELETE NO ACTION)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_template_heads_templateId_revisionId` ON `template_heads` (`templateId`, `revisionId`)")
        db.execSQL("CREATE TABLE IF NOT EXISTS `execution_plan_revisions` (`sessionId` TEXT NOT NULL, `structuralRevision` INTEGER NOT NULL, `compiledPlanJson` TEXT NOT NULL, `compiledPlanHash` TEXT NOT NULL, `adjustmentId` TEXT, PRIMARY KEY(`sessionId`, `structuralRevision`), FOREIGN KEY(`sessionId`) REFERENCES `session_starts`(`sessionId`) ON UPDATE NO ACTION ON DELETE NO ACTION)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_execution_plan_revisions_sessionId_adjustmentId` ON `execution_plan_revisions` (`sessionId`, `adjustmentId`)")
    }
}
