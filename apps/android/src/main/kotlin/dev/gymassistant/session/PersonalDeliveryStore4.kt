package dev.gymassistant.session

import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import dev.gymassistant.engine.v2.ImportContracts2
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID

@Entity(tableName="personal_session_deliveries", indices=[Index(value=["connectionId","sessionId"],unique=true),Index(value=["sessionId"])],foreignKeys=[
    ForeignKey(entity=DriveConnectionRow::class,parentColumns=["connectionId"],childColumns=["connectionId"]),
    ForeignKey(entity=StartRow::class,parentColumns=["sessionId"],childColumns=["sessionId"])])
data class PersonalDeliveryRow4(@PrimaryKey val deliveryId:String,val connectionId:String,val sessionId:String,
    val terminalRevision:Long,val canonicalSchemaVersion:String="1.2.0",val projectionVersion:String="kotlin-human-1.2",
    val payloadRelativePath:String,val payloadByteCount:Int,val contentSha256:String,val remoteFileId:String?=null,
    val state:String="queued",val attemptCount:Int=0,val nextAttemptAt:String?=null,val errorCode:String?=null)

@Dao interface PersonalDeliveryDao4 {
    @Query("SELECT * FROM personal_session_deliveries WHERE deliveryId=:id") suspend fun get(id:String):PersonalDeliveryRow4?
    @Query("SELECT * FROM personal_session_deliveries WHERE connectionId=:connection AND sessionId=:session") suspend fun forSession(connection:String,session:String):PersonalDeliveryRow4?
    @Query("SELECT * FROM personal_session_deliveries WHERE sessionId=:session ORDER BY rowid DESC LIMIT 1") suspend fun latest(session:String):PersonalDeliveryRow4?
    @Insert suspend fun insert(row:PersonalDeliveryRow4)
    @Update suspend fun update(row:PersonalDeliveryRow4):Int
    @Query("SELECT sessionId FROM session_starts ORDER BY rowid DESC") suspend fun sessionIds():List<String>
}
val PERSONAL_DELIVERY_MIGRATION_3_4:Migration=object:Migration(3,4) {
    override fun migrate(db:SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `personal_session_deliveries` (`deliveryId` TEXT NOT NULL, `connectionId` TEXT NOT NULL, `sessionId` TEXT NOT NULL, `terminalRevision` INTEGER NOT NULL, `canonicalSchemaVersion` TEXT NOT NULL, `projectionVersion` TEXT NOT NULL, `payloadRelativePath` TEXT NOT NULL, `payloadByteCount` INTEGER NOT NULL, `contentSha256` TEXT NOT NULL, `remoteFileId` TEXT, `state` TEXT NOT NULL, `attemptCount` INTEGER NOT NULL, `nextAttemptAt` TEXT, `errorCode` TEXT, PRIMARY KEY(`deliveryId`), FOREIGN KEY(`connectionId`) REFERENCES `drive_connections`(`connectionId`) ON UPDATE NO ACTION ON DELETE NO ACTION, FOREIGN KEY(`sessionId`) REFERENCES `session_starts`(`sessionId`) ON UPDATE NO ACTION ON DELETE NO ACTION)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_personal_session_deliveries_connectionId_sessionId` ON `personal_session_deliveries` (`connectionId`, `sessionId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_personal_session_deliveries_sessionId` ON `personal_session_deliveries` (`sessionId`)")
    }
}
/** Immutable bounded files; no provider-controlled paths, replacement or automatic cleanup. */
internal class DeliveryPayloadFiles4(private val root:File) {
    companion object {private val lock=Any();const val LIMIT=8*1024*1024}
    fun relative(id:String):String {require(id.matches(Regex("[A-Za-z0-9_-]{3,128}")));return "$id.json"}
    private fun file(path:String):File {
        require(path.matches(Regex("[A-Za-z0-9_-]{3,128}\\.json")))
        val f=File(root,path).canonicalFile;check(f.parentFile==root.canonicalFile);return f
    }
    fun write(path:String,canonical:String)=synchronized(lock) {
        val bytes=canonical.toByteArray(Charsets.UTF_8);require(bytes.size in 1..LIMIT)
        val target=file(path);check(root.mkdirs() || root.isDirectory)
        if(target.exists()){check(read(path,bytes.size,sha256Utf8(canonical))==canonical);return@synchronized}
        val stage=File(root,".stage-${UUID.randomUUID()}")
        try {FileOutputStream(stage).use {it.write(bytes);it.fd.sync()};Files.move(stage.toPath(),target.toPath(),StandardCopyOption.ATOMIC_MOVE)}
        finally {stage.delete()}
    }
    fun exists(path:String)=file(path).exists()
    fun read(path:String,count:Int,hash:String):String {
        require(count in 1..LIMIT && hash.matches(Regex("[a-f0-9]{64}")))
        val f=file(path);check(f.isFile && f.length()==count.toLong());val bytes=f.readBytes()
        val text=bytes.toString(Charsets.UTF_8);check(text.toByteArray(Charsets.UTF_8).contentEquals(bytes) && sha256Utf8(text)==hash);return text
    }
}

/** Personal adapter only: old inline synthetic rows are never read or rewritten here. */
internal class PersonalDeliveryStore4(private val db:WorkoutDatabase,private val files:DeliveryPayloadFiles4,private val contracts:ImportContracts2,
    private val beforeInsert:()->Unit={}) : dev.gymassistant.drive.DeliveryPersistence {
    private val dao get()=db.personalDeliveries()
    private val sessions get()=RoomSessionStore2(db,contracts)
    suspend fun preview(id:String):Pair<Long,SessionHistory2> = db.withTransaction {
        val saved=checkNotNull(sessions.load(id));check(saved.checkpoint.outcome=="completed") {"Select a finalized completed or partial session"}
        saved.revision to checkNotNull(sessions.history(id))
    }
    suspend fun eligible():List<SessionHistory2> = db.withTransaction {
        dao.sessionIds().mapNotNull {id->try {preview(id).second}catch(cancelled:kotlinx.coroutines.CancellationException){throw cancelled}catch(_:Exception){null}}
    }
    suspend fun enqueue(connection:String,session:String,id:String,reviewedRevision:Long,reviewedHash:String):PersonalDeliveryRow4 = db.withTransaction {
        checkNotNull(db.deliveries().connection(connection));val (revision,history)=preview(session)
        val canonical=history.toCanonicalJson();val hash=sha256Utf8(canonical)
        check(revision==reviewedRevision && hash==reviewedHash) {"Session changed since review"}
        val prior=dao.forSession(connection,session)
        if(prior!=null){check(prior.terminalRevision==revision && prior.contentSha256==hash);load(prior.deliveryId);return@withTransaction prior}
        check(dao.get(id)==null);val path=files.relative(id);files.write(path,canonical)
        val row=PersonalDeliveryRow4(id,connection,session,revision,payloadRelativePath=path,payloadByteCount=canonical.toByteArray(Charsets.UTF_8).size,contentSha256=hash)
        beforeInsert();dao.insert(row);row
    }
    private fun validate(row:PersonalDeliveryRow4) {
        check(row.canonicalSchemaVersion=="1.2.0" && row.projectionVersion=="kotlin-human-1.2" && row.payloadRelativePath==files.relative(row.deliveryId))
        check(row.terminalRevision>0 && row.payloadByteCount in 1..DeliveryPayloadFiles4.LIMIT && row.contentSha256.matches(Regex("[a-f0-9]{64}")) && row.attemptCount>=0)
        check(row.state in setOf("queued","awaitingAuthorization","ready","verifying","retry","conflict","delivered"))
        check(row.errorCode==null || row.errorCode in setOf("authorization","network","rateLimited","providerUnavailable","permission","missingRoot","accountMismatch","contract","conflict","budget"))
        check(row.remoteFileId==null || row.remoteFileId.matches(Regex("[-A-Za-z0-9_]+")))
        row.nextAttemptAt?.let {java.time.Instant.parse(it)}
    }
    override suspend fun load(id:String):Pair<DriveConnectionRow,SessionDeliveryRow> = db.withTransaction {
        val row=checkNotNull(dao.get(id));validate(row)
        val (revision,history)=preview(row.sessionId);val canonical=history.toCanonicalJson()
        check(revision==row.terminalRevision && sha256Utf8(canonical)==row.contentSha256 && canonical.toByteArray(Charsets.UTF_8).size==row.payloadByteCount)
        if(!files.exists(row.payloadRelativePath))files.write(row.payloadRelativePath,canonical)
        val bytes=files.read(row.payloadRelativePath,row.payloadByteCount,row.contentSha256);check(bytes==canonical)
        // The existing foreground sender receives bytes in memory, never an empty placeholder in SQL.
        checkNotNull(db.deliveries().connection(row.connectionId)) to SessionDeliveryRow(row.deliveryId,row.connectionId,row.sessionId,row.terminalRevision,bytes,row.contentSha256,row.remoteFileId,row.state,row.attemptCount,row.nextAttemptAt,row.errorCode)
    }
    override suspend fun save(expected:SessionDeliveryRow,next:SessionDeliveryRow)=db.withTransaction {
        check(load(expected.deliveryId).second==expected)
        check(expected.state !in setOf("delivered","conflict") || next==expected)
        check(next.copy(remoteFileId=expected.remoteFileId,state=expected.state,attemptCount=expected.attemptCount,nextAttemptAt=expected.nextAttemptAt,errorCode=expected.errorCode)==expected)
        check(expected.remoteFileId==null || expected.remoteFileId==next.remoteFileId);check(next.attemptCount>=expected.attemptCount)
        val row=checkNotNull(dao.get(expected.deliveryId)).copy(remoteFileId=next.remoteFileId,state=next.state,attemptCount=next.attemptCount,nextAttemptAt=next.nextAttemptAt,errorCode=next.errorCode)
        validate(row);check(dao.update(row)==1)
        if(row.state=="delivered"){check(row.remoteFileId!=null);db.deliveries().acknowledgeThrough(row.sessionId,row.terminalRevision)}
        Unit
    }
}
