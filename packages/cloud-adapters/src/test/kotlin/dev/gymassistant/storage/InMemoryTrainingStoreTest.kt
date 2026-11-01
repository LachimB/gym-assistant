package dev.gymassistant.storage

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class InMemoryTrainingStoreTest {
    @Test
    fun writeRequiresTheExpectedRevision() {
        val store = InMemoryTrainingStore()
        val created = store.write("templates/demo.yaml", "a".encodeToByteArray(), expectedRevision = null)
        assertEquals("1", created.revision)
        assertEquals(listOf("templates/demo.yaml"), store.list("templates/"))
        assertFailsWith<RevisionConflict> {
            store.write("templates/demo.yaml", "b".encodeToByteArray(), expectedRevision = null)
        }
        val updated = store.write("templates/demo.yaml", "b".encodeToByteArray(), expectedRevision = "1")
        assertEquals("2", updated.revision)
        assertEquals("b", store.read("templates/demo.yaml")?.bytes?.decodeToString())
        assertNull(store.read("missing.yaml"))
    }
}
