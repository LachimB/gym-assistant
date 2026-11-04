package dev.gymassistant.sync

import kotlin.test.*

class GuardedWorkingFileTest {
    private val before="name: synthetic before\n".toByteArray()
    private val intended="name: synthetic after\n".toByteArray()
    private fun step(existing:Boolean=true)=WorkingFileStep("file-test","working-test","gym-template-test","exercise.yaml","text/plain",if(existing)"tag-before" else null,if(existing)before else null,intended)
    private fun snapshot(bytes:ByteArray=before,tag:String="tag-before",parent:String="working-test")=WorkingFileSnapshot("file-test",setOf(parent),"gym-template-test","exercise.yaml","text/plain",tag,false,bytes)
    private inner class Fake(existing:Boolean=true):WorkingFileRemote,WorkingFileJournal {
        var file:WorkingFileSnapshot?=if(existing)snapshot() else null
        var writes=0;var creates=0;var acks=0;var lease=true;var durable=true;var namespace=true;var loseAck=false;var editBeforeWrite=false
        override fun verifyNamespace(){check(namespace)}
        override fun read(objectId:String)=file
        override fun replace(objectId:String,expectedTag:String,bytes:ByteArray){
            if(editBeforeWrite)file=snapshot("name: outside edit\n".toByteArray(),"tag-outside")
            check(file?.tag==expectedTag);writes++;file=snapshot(bytes,"tag-after")
            if(loseAck)error("Uncertain transport outcome")
        }
        override fun createReserved(step:WorkingFileStep){check(file==null);creates++;file=snapshot(step.intendedBytes,"tag-created");if(loseAck)error("Uncertain create outcome")}
        override fun requireDurable(step:WorkingFileStep){check(durable)}
        override fun requireCurrentLease(){check(lease)}
        override fun acknowledge(step:WorkingFileStep,resultTag:String,resultHash:String){assertEquals(step.intendedHash,resultHash);assertEquals(file?.tag,resultTag);acks++}
        fun run(step:WorkingFileStep)=GuardedWorkingFile(this,this).execute(step)
    }
    @Test fun lostWriteAcknowledgementResolvesBySameIdWithoutSecondWrite() {
        val fake=Fake();fake.loseAck=true;assertFails {fake.run(step())};assertEquals(0,fake.acks)
        fake.loseAck=false;fake.run(step());assertEquals(1,fake.writes);assertEquals(1,fake.acks)
    }
    @Test fun lostCreateAcknowledgementReusesReservedId() {
        val fake=Fake(false);fake.loseAck=true;assertFails {fake.run(step(false))}
        fake.loseAck=false;fake.run(step(false));assertEquals(1,fake.creates);assertEquals(0,fake.writes);assertEquals(1,fake.acks)
    }
    @Test fun changedRemoteBytesOrTagAndMissingExistingFileArePreserved() {
        for(file in listOf(snapshot(before,"different-tag"),snapshot("name: external\n".toByteArray()),null)) {
            val fake=Fake();fake.file=file;assertFails {fake.run(step())};assertSame(file,fake.file);assertEquals(0,fake.writes);assertEquals(0,fake.creates)
        }
    }
    @Test fun editBetweenReadAndWriteFailsProviderConditionalBoundary() {
        val fake=Fake();fake.editBeforeWrite=true;assertFails {fake.run(step())}
        assertEquals("tag-outside",fake.file?.tag);assertEquals(0,fake.writes);assertEquals(0,fake.acks)
    }
    @Test fun wrongParentEvenWithIntendedBytesDoesNotAcknowledgeOrFallback() {
        val fake=Fake();fake.file=snapshot(intended,"tag-after","outside-test");assertFails {fake.run(step())};assertEquals(0,fake.acks);assertEquals(0,fake.writes)
    }
    @Test fun missingDurableIntentLeaseOrVerifiedHierarchyPreventsMutation() {
        for(reason in 0..2){val fake=Fake();when(reason){0->fake.durable=false;1->fake.lease=false;2->fake.namespace=false};assertFails {fake.run(step())};assertEquals(0,fake.writes)}
    }
    @Test fun returnedPayloadCannotMutatePersistedIntent() {
        val captured=step();captured.intendedBytes[0]=0;captured.beforeBytes!![0]=0
        assertContentEquals(intended,captured.intendedBytes);assertContentEquals(before,captured.beforeBytes)
    }
}
