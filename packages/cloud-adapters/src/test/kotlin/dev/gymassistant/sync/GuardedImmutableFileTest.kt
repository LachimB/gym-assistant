package dev.gymassistant.sync

import kotlin.test.*

class GuardedImmutableFileTest {
    private fun step()=ImmutableFileStep("reserved-fixture","revision-folder","gym-template-fixture","revision.json","application/json","{}".toByteArray())
    private fun snapshot(s:ImmutableFileStep,bytes:ByteArray=s.bytes)=ImmutableFileSnapshot(s.objectId,setOf(s.folderId),s.marker,s.name,s.mimeType,"tag",false,bytes)
    private class Journal:ImmutableFileJournal {
        var durable=true;var leased=true;var acknowledged=0;var createPending=true
        override fun requireDurable(step:ImmutableFileStep){check(durable)}
        override fun requireCurrentLease(){check(leased)}
        override fun requireCreatePending(step:ImmutableFileStep){check(createPending)}
        override fun acknowledge(step:ImmutableFileStep,resultTag:String){acknowledged++;createPending=false}
    }
    private class Remote:ImmutableFileRemote {
        var value:ImmutableFileSnapshot?=null;var creates=0;var lostAck=false;var namespace=true;var afterRead:()->Unit={}
        override fun verifyNamespace(){check(namespace)}
        override fun read(objectId:String)=value.also {afterRead()}
        override fun createReserved(step:ImmutableFileStep){creates++;value=ImmutableFileSnapshot(step.objectId,setOf(step.folderId),step.marker,step.name,step.mimeType,"created",false,step.bytes);if(lostAck)error("Synthetic lost acknowledgement")}
    }
    @Test fun lostCreationAcknowledgementReusesTheSameObjectWithoutAnotherCreate() {
        val r=Remote();val j=Journal();val s=step();r.lostAck=true
        assertFails {GuardedImmutableFile(r,j).execute(s)};assertEquals(0,j.acknowledged)
        r.lostAck=false;GuardedImmutableFile(r,j).execute(s)
        assertEquals(1,r.creates);assertEquals(1,j.acknowledged)
    }
    @Test fun differentExistingBytesOrNamespaceNeverCauseReplacementOrAcknowledgement() {
        val s=step()
        for(value in listOf(snapshot(s,"different".toByteArray()),ImmutableFileSnapshot(s.objectId,setOf("outside-folder"),s.marker,s.name,s.mimeType,"tag",false,s.bytes))) {
            val r=Remote();r.value=value;val j=Journal();assertFails {GuardedImmutableFile(r,j).execute(s)}
            assertEquals(0,r.creates);assertEquals(0,j.acknowledged)
        }
    }
    @Test fun durabilityOwnershipAndNamespaceAreRequiredBeforeCreationAndAcknowledgement() {
        for(mode in 0..3){val r=Remote();val j=Journal();when(mode){0->j.durable=false;1->j.leased=false;2->r.namespace=false;3->{r.value=snapshot(step());r.afterRead={j.leased=false}}}
            assertFails {GuardedImmutableFile(r,j).execute(step())};assertEquals(0,r.creates);assertEquals(0,j.acknowledged)}
    }
    @Test fun fileInputsAndReturnedBytesCannotRetargetAnImmutableStep() {
        val bytes="{}".toByteArray();val s=ImmutableFileStep("reserved-fixture","revision-folder","gym-template-fixture","revision.json","application/json",bytes)
        bytes[0]=32;s.bytes[0]=32;assertContentEquals("{}".toByteArray(),s.bytes)
        val snap=snapshot(s);snap.bytes[0]=32;assertContentEquals(s.bytes,snap.bytes)
    }
    @Test fun anAcknowledgedObjectThatDisappearsCannotBeRecreated() {
        val r=Remote();val j=Journal();val s=step();GuardedImmutableFile(r,j).execute(s)
        r.value=null;assertFails {GuardedImmutableFile(r,j).execute(s)}
        assertEquals(1,r.creates);assertEquals(1,j.acknowledged)
    }
}
