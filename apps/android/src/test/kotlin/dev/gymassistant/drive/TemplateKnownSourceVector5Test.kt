package dev.gymassistant.drive

import dev.gymassistant.session.SourceCapturePin5
import dev.gymassistant.sync.PublicationHeadSnapshot
import dev.gymassistant.sync.TemplatePublicationHead
import kotlinx.coroutines.CancellationException
import org.junit.Test
import kotlin.test.*

class TemplateKnownSourceVector5Test {
    private val pins=listOf(SourceCapturePin5("template","template.yaml","known-template","text/plain"),
        SourceCapturePin5("exercise","exercise.yaml","known-exercise","text/plain"))
    private val headValue=TemplatePublicationHead("template-test","generation-test","manifest-test","working-test","a".repeat(64),null)
    private fun head(tag:String="\"head-tag\"",content:String="synthetic retained head",objectId:String="head-test")=
        PublicationHeadSnapshot(objectId,setOf("metadata-test"),"gym-template-test","head.json","application/json",tag,false,headValue,
            "synthetic head metadata".toByteArray(),content.toByteArray())
    private inner class Reader:TemplateSourceReader5 {
        val trace=mutableListOf<String>();var verifications=0;var heads=0;val reads=mutableMapOf<String,Int>()
        var namespaceAction:(Int)->Unit={};var headAt:(Int)->PublicationHeadSnapshot?={head()}
        var sourceAt:(SourceCapturePin5,Int)->SourceReadObject5?={pin,_->source(pin)}
        fun source(pin:SourceCapturePin5,tag:String="\"file-tag\"",metadata:ByteArray="synthetic metadata".toByteArray(),bytes:ByteArray="synthetic unvalidated YAML".toByteArray())=
            SourceReadObject5(pin,tag,metadata,bytes)
        override fun verifyNamespace(){trace+="namespace";namespaceAction(++verifications)}
        override fun readHead():PublicationHeadSnapshot?{trace+="head";return headAt(++heads)}
        override fun readSource(objectId:String):SourceReadObject5? {
            trace+=objectId;val count=(reads[objectId]?:0)+1;reads[objectId]=count
            return sourceAt(pins.single {it.objectId==objectId},count)
        }
    }
    @Test fun wholeKnownVectorIsReadTwiceBetweenHeadAndNamespaceChecksWithoutAdoption() {
        val r=Reader();val vector=r.captureKnownVector(pins)
        assertEquals(listOf("namespace","head","known-template","known-exercise","head","known-template","known-exercise","head","namespace"),r.trace)
        assertEquals(headValue,vector.head!!.head);assertEquals(pins,vector.files.map {it.pin})
        assertEquals("synthetic unvalidated YAML",vector.files.first().contentBytes.toString(Charsets.UTF_8))
        vector.files.first().contentBytes.fill(0)
        assertEquals("synthetic unvalidated YAML",vector.files.first().contentBytes.toString(Charsets.UTF_8))
    }
    @Test fun emptyDuplicateOrAliasedPinsStopBeforeAnyProviderCall() {
        for(p in listOf(emptyList(),listOf(pins.first()),pins+pins.first(),listOf(pins.first(),pins.last().copy(relativeName="template.yaml")),
            listOf(pins.first(),pins.last().copy(objectId="known-template")))) {
            val r=Reader();assertFails {r.captureKnownVector(p)};assertTrue(r.trace.isEmpty())
        }
        val r=Reader();r.headAt={head(objectId="known-template")};assertFails {r.captureKnownVector(pins)};assertTrue(r.reads.isEmpty())
    }
    @Test fun changedTagBytesOrMetadataInAnyWorkingFileCannotReturnAnApparentlyStableVector() {
        for(id in pins.map {it.objectId})for(mode in 0..2) {
            val r=Reader();r.sourceAt={pin,count->if(pin.objectId==id && count==2)when(mode){
                0->r.source(pin,tag="\"changed\"");1->r.source(pin,bytes="changed source".toByteArray());else->r.source(pin,metadata="changed metadata".toByteArray())
            }else r.source(pin)}
            assertEquals("conflict",assertFailsWith<DeliveryFailure>{r.captureKnownVector(pins)}.reason)
        }
    }
    @Test fun missingHeadRemainsExplicitButHeadAppearanceDisappearanceTagOrBytesChangeStopsCapture() {
        val missing=Reader();missing.headAt={null};assertNull(missing.captureKnownVector(pins).head)
        for(at in listOf(2,3))for(mode in 0..3) {
            val r=Reader();r.headAt={n->if(n==at)when(mode){0->null;1->head(tag="\"changed\"");2->head(content="changed bytes");else->head(objectId="other-head")}else head()}
            assertEquals("conflict",assertFailsWith<DeliveryFailure>{r.captureKnownVector(pins)}.reason)
        }
        val appearance=Reader();appearance.headAt={if(it==1)null else head()};assertFails {appearance.captureKnownVector(pins)}
    }
    @Test fun missingSourceWrongPinWeakTagMalformedOrOversizePayloadStopsWithoutAResult() {
        for(mode in 0..5) {
            val r=Reader();r.sourceAt={pin,_->when(mode){
                0->null;1->r.source(pin.copy(relativeName="other.yaml"));2->r.source(pin,tag="W/\"weak\"")
                3->r.source(pin,bytes=byteArrayOf(0xC3.toByte(),0x28));4->r.source(pin,bytes=ByteArray(1048577){65});else->r.source(pin,metadata=byteArrayOf())}}
            assertFails {r.captureKnownVector(pins)}
        }
    }
    @Test fun namespaceLossDurableSinkFailureAndCancellationNeverReturnACompletedVector() {
        val moved=Reader();moved.namespaceAction={if(it==2)throw DeliveryFailure("missingRoot")}
        assertEquals("missingRoot",assertFailsWith<DeliveryFailure>{moved.captureKnownVector(pins)}.reason)
        val sink=Reader();sink.sourceAt={_,_->throw DeliveryFailure("contract")};assertFails {sink.captureKnownVector(pins)}
        val cancelled=Reader();cancelled.sourceAt={_,_->throw CancellationException("Synthetic cancellation")}
        assertFailsWith<CancellationException>{cancelled.captureKnownVector(pins)}
    }
    @Test fun aggregateSourceLimitStopsTheFirstPassAndTooManyPinsStopBeforeReads() {
        var calls=0
        val reader=object:TemplateSourceReader5 {
            override fun verifyNamespace(){}
            override fun readHead():PublicationHeadSnapshot?=null
            override fun readSource(objectId:String):SourceReadObject5 {
                calls++;val pin=SourceCapturePin5(if(objectId=="source-0")"template" else "exercise","$objectId.yaml",objectId,"text/plain")
                return SourceReadObject5(pin,"\"tag\"",byteArrayOf(65),ByteArray(1048576){65})
            }
        }
        fun pin(i:Int)=SourceCapturePin5(if(i==0)"template" else "exercise","source-$i.yaml","source-$i","text/plain")
        assertFails {reader.captureKnownVector((0..8).map(::pin))};assertEquals(9,calls)
        calls=0;assertFails {reader.captureKnownVector((0..16).map(::pin))};assertEquals(0,calls)
    }
}
