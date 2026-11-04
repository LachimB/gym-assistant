package dev.gymassistant.session

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.withTransaction
import dev.gymassistant.engine.EngineCommand
import dev.gymassistant.engine.initialState
import dev.gymassistant.engine.reduce
import kotlinx.serialization.encodeToString

@Entity(tableName = "session_starts")
data class StartRow(@PrimaryKey val sessionId: String, val snapshotJson: String)

@Entity(tableName = "checkpoints", foreignKeys = [ForeignKey(
    entity = StartRow::class, parentColumns = ["sessionId"], childColumns = ["sessionId"],
)])
data class CheckpointRow(@PrimaryKey val sessionId: String, val revision: Long, val checkpointJson: String)

@Entity(tableName = "actions", primaryKeys = ["sessionId", "sequence"],
    indices = [Index(value = ["sessionId", "operationId"], unique = true)], foreignKeys = [ForeignKey(
        entity = StartRow::class, parentColumns = ["sessionId"], childColumns = ["sessionId"],
    )])
data class ActionRow(
    val sessionId: String, val sequence: Long, val operationId: String,
    val expectedRevision: Long, val intentJson: String, val payloadJson: String, val checkpointJson: String,
)

@Entity(tableName = "unfinished_slot", indices = [Index(value = ["sessionId"], unique = true)],
    foreignKeys = [ForeignKey(entity = StartRow::class, parentColumns = ["sessionId"], childColumns = ["sessionId"])])
data class UnfinishedRow(@PrimaryKey val slot: Int = 1, val sessionId: String)

@Entity(tableName = "outbox", primaryKeys = ["sessionId", "revision"], foreignKeys = [ForeignKey(
    entity = StartRow::class, parentColumns = ["sessionId"], childColumns = ["sessionId"],
)])
data class OutboxRow(val sessionId: String, val revision: Long, val operationId: String, val status: String = "pending")

@Dao
interface WorkoutDao {
    @Query("SELECT * FROM session_starts WHERE sessionId = :id") suspend fun start(id: String): StartRow?
    @Query("SELECT * FROM checkpoints WHERE sessionId = :id") suspend fun checkpoint(id: String): CheckpointRow?
    @Query("SELECT * FROM unfinished_slot WHERE slot = 1") suspend fun unfinished(): UnfinishedRow?
    @Query("SELECT * FROM actions WHERE sessionId = :id AND operationId = :operation")
    suspend fun operation(id: String, operation: String): ActionRow?
    @Query("SELECT * FROM actions WHERE sessionId = :id ORDER BY sequence") suspend fun actions(id: String): List<ActionRow>
    @Query("SELECT * FROM outbox WHERE sessionId = :id ORDER BY revision") suspend fun outbox(id: String): List<OutboxRow>
    @Insert suspend fun insertStart(row: StartRow)
    @Insert suspend fun insertCheckpoint(row: CheckpointRow)
    @Insert suspend fun insertAction(row: ActionRow)
    @Insert suspend fun insertSlot(row: UnfinishedRow)
    @Insert suspend fun insertOutbox(row: OutboxRow)
    @Query("UPDATE checkpoints SET revision = :next, checkpointJson = :json WHERE sessionId = :id AND revision = :expected")
    suspend fun advanceCheckpoint(id: String, expected: Long, next: Long, json: String): Int
    @Query("DELETE FROM unfinished_slot WHERE slot = 1 AND sessionId = :id") suspend fun releaseSlot(id: String)
}

@Database(entities = [StartRow::class, CheckpointRow::class, ActionRow::class, UnfinishedRow::class, OutboxRow::class, DriveConnectionRow::class, SessionDeliveryRow::class, TemplateCandidateRow::class, TemplateRevisionRow::class, TemplateHeadRow::class, ExecutionPlanRow::class, PersonalDeliveryRow4::class],
    version = 4, exportSchema = true)
abstract class WorkoutDatabase : RoomDatabase() {
    abstract fun workouts(): WorkoutDao
    abstract fun deliveries(): DeliveryDao
    abstract fun library(): LibraryDao
    abstract fun personalDeliveries(): PersonalDeliveryDao4
}

class RoomSessionStore(
    private val database: WorkoutDatabase,
    // Deterministic transaction-failure injection for real SQLite tests. No production side effects.
    private val beforeOutbox: () -> Unit = {},
) : SessionStore {
    private val dao get() = database.workouts()

    private suspend fun read(id: String): SavedWorkout? {
        val source = dao.start(id) ?: return null
        val checkpoint = checkNotNull(dao.checkpoint(id)) { "Missing session checkpoint" }
        val start = sessionJson.decodeFromString<SessionStart>(source.snapshotJson)
        check(start.formatVersion == 1 && start.engineVersion == "kotlin-timed-1") { "Unsupported saved session" }
        check(hashPlan(start.plan) == start.planHash) { "Saved plan integrity check failed" }
        return SavedWorkout(start, checkpoint.revision, sessionJson.decodeFromString(checkpoint.checkpointJson))
    }

    override suspend fun load(sessionId: String): SavedWorkout? = database.withTransaction { read(sessionId) }
    override suspend fun unfinished(): SavedWorkout? = database.withTransaction {
        dao.unfinished()?.let { checkNotNull(read(it.sessionId)) }
    }

    // Read-only and atomic: no outbox acknowledgement, migration or sync claim.
    override suspend fun history(sessionId: String): SessionHistory? = database.withTransaction {
        read(sessionId)?.let { projectSession(it, dao.actions(sessionId)) }
    }

    override suspend fun start(snapshot: SessionStart, operationId: String): SavedWorkout {
        require(operationId.isNotBlank())
        snapshot.validate()
        val sourceJson = sessionJson.encodeToString(snapshot)
        return database.withTransaction {
            val existing = dao.start(snapshot.sessionId)
            if (existing != null) {
                val previous = dao.operation(snapshot.sessionId, operationId)
                if (existing.snapshotJson != sourceJson || previous == null || previous.intentJson != sourceJson || previous.expectedRevision != 0L) {
                    throw SessionConflict("Session ID or operation was already used")
                }
                return@withTransaction SavedWorkout(snapshot, previous.sequence,
                    sessionJson.decodeFromString(previous.checkpointJson))
            }
            if (dao.unfinished() != null) throw SessionConflict("An unfinished workout already exists")
            val state = reduce(snapshot.plan, initialState(), EngineCommand.Start)
            val checkpoint = Checkpoint.from(state)
            val encoded = sessionJson.encodeToString(checkpoint)
            dao.insertStart(StartRow(snapshot.sessionId, sourceJson))
            dao.insertCheckpoint(CheckpointRow(snapshot.sessionId, 1, encoded))
            dao.insertSlot(UnfinishedRow(sessionId = snapshot.sessionId))
            dao.insertAction(ActionRow(snapshot.sessionId, 1, operationId, 0, sourceJson,
                sessionJson.encodeToString(ActionPayload(null, state.events)), encoded))
            beforeOutbox()
            dao.insertOutbox(OutboxRow(snapshot.sessionId, 1, operationId))
            SavedWorkout(snapshot, 1, checkpoint)
        }
    }

    override suspend fun commit(sessionId: String, expectedRevision: Long, operationId: String, action: SessionAction): SavedWorkout {
        require(operationId.isNotBlank())
        val intent = sessionJson.encodeToString(action)
        return database.withTransaction {
            val current = checkNotNull(read(sessionId)) { "Unknown session" }
            val previous = dao.operation(sessionId, operationId)
            if (previous != null) {
                if (previous.expectedRevision != expectedRevision || previous.intentJson != intent) {
                    throw SessionConflict("Operation ID was already used for another action")
                }
                return@withTransaction SavedWorkout(current.start, previous.sequence,
                    sessionJson.decodeFromString(previous.checkpointJson))
            }
            if (current.revision != expectedRevision) throw SessionConflict("Checkpoint revision changed")
            val next = transition(current, action)
            val revision = Math.addExact(expectedRevision, 1)
            val encoded = sessionJson.encodeToString(next.checkpoint)
            dao.insertAction(ActionRow(sessionId, revision, operationId, expectedRevision, intent,
                sessionJson.encodeToString(next.payload), encoded))
            check(dao.advanceCheckpoint(sessionId, expectedRevision, revision, encoded) == 1)
            beforeOutbox()
            dao.insertOutbox(OutboxRow(sessionId, revision, operationId))
            if (next.checkpoint.outcome != "inProgress") dao.releaseSlot(sessionId)
            SavedWorkout(current.start, revision, next.checkpoint)
        }
    }
}

/** Single process-local Room instance shared by recording and foreground delivery. */
internal object WorkoutDatabases {
    @Volatile private var instance: WorkoutDatabase? = null
    fun open(context: android.content.Context): WorkoutDatabase = instance ?: synchronized(this) {
        instance ?: androidx.room.Room.databaseBuilder(context.applicationContext, WorkoutDatabase::class.java, "workouts-v1.db")
            .addMigrations(DELIVERY_MIGRATION_1_2, LIBRARY_MIGRATION_2_3, PERSONAL_DELIVERY_MIGRATION_3_4).build().also { instance = it }
    }
}
