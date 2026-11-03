package dev.gymassistant.session

import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.test.*

class SyntheticDeliverySampleTest {
    private val root=File(checkNotNull(System.getProperty("gym.root")))
    private val template=File(root,"examples/templates/composite-pyramid.yaml").readText()
    private val exercises=listOf("bridge-band-family.yaml","plank-family.yaml").map { File(root,"examples/exercises/$it").readText() }
    @Test fun onlyTheExactlyGeneratedSampleCanBeSent() {
        val sample=SyntheticDeliverySample.create(template,exercises)
        val second=SyntheticDeliverySample.create(template,exercises)
        assertEquals(sample.canonicalJson,second.canonicalJson)
        assertEquals("completed",sample.saved.checkpoint.outcome)
        assertEquals(50000,sample.saved.state.observedRunMs)
        val job=SessionDeliveryRow("job","binding",sample.saved.start.sessionId,sample.saved.revision,sample.canonicalJson,sha256Utf8(sample.canonicalJson))
        assertTrue(sample.permits(job))
        assertFalse(sample.permits(job.copy(sessionId="another")))
        assertFalse(sample.permits(job.copy(canonicalJson=sample.canonicalJson+" ")))
        assertFalse(sample.permits(job.copy(contentSha256="wrong")))
        val json=Json.parseToJsonElement(sample.canonicalJson).jsonObject
        assertFalse("actions" in json); assertFalse("checkpoint" in json)
    }
    @Test fun changedSourceCannotMasqueradeAsTheApprovedSyntheticSample() {
        assertFails { SyntheticDeliverySample.create(template+"\n",exercises) }
        assertFails { SyntheticDeliverySample.create(template,exercises.reversed()) }
    }
}
