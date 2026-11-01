package dev.gymassistant.storage

data class StoredObject(
    val path: String,
    val revision: String,
    val bytes: ByteArray,
)

interface TrainingStore {
    fun list(prefix: String): List<String>
    fun read(path: String): StoredObject?
    fun write(path: String, bytes: ByteArray, expectedRevision: String?): StoredObject
}

class RevisionConflict(path: String) : IllegalStateException("revision conflict at $path")

class InMemoryTrainingStore : TrainingStore {
    private val objects = linkedMapOf<String, StoredObject>()

    override fun list(prefix: String): List<String> =
        objects.keys.filter { it.startsWith(prefix) }.sorted()

    override fun read(path: String): StoredObject? = objects[path]

    override fun write(path: String, bytes: ByteArray, expectedRevision: String?): StoredObject {
        val current = objects[path]
        if (current?.revision != expectedRevision) throw RevisionConflict(path)
        val next = StoredObject(path, revision = ((current?.revision?.toIntOrNull() ?: 0) + 1).toString(), bytes = bytes.copyOf())
        objects[path] = next
        return next
    }
}
