package dev.gymassistant.sync

import kotlin.test.*

class GuardedPublicationHeadTest {
    private val prior=TemplatePublicationHead("template-test","generation-before","manifest-before","working-folder","a".repeat(64),null)
    private val next=TemplatePublicationHead("template-test","generation-next","manifest-next","working-folder","b".repeat(64),prior.generationId)
    private fun intent(existing:Boolean=true)=PublicationHeadIntent("operation-test","head-object","metadata-folder","gym-template-test",
        if(existing)prior else null,if(existing)"tag-before" else null,if(existing)next else next.copy(previousGenerationId=null))
    private fun snapshot(head:TemplatePublicationHead=prior,tag:String="tag-before",parent:String="metadata-folder")=
        PublicationHeadSnapshot("head-object",setOf(parent),"gym-template-test","head.json","application/json",tag,false,head)
    private inner class Fake(existing:Boolean=true):PublicationHeadRemote,PublicationHeadJournal {
        var head:PublicationHeadSnapshot?=if(existing)snapshot() else null
        var writes=0;var creates=0;var acknowledged=0;var observed=mutableListOf<PublicationHeadSnapshot?>()
        var durable=true;var lease=true;var namespace=true;var immutable=true;var working=true;var loseAck=false
        var competeBeforeWrite=false;var editWorkingAfterWrite=false;var expireAfterWrite=false;var badReadback=false;var competeAfterPublication=false
        override fun verifyNamespace(){check(namespace)}
        override fun read(objectId:String)=head
        override fun replace(objectId:String,expectedTag:String,head:TemplatePublicationHead) {
            if(competeBeforeWrite)this.head=snapshot(prior.copy(generationId="competing-generation"),"competing-tag")
            check(this.head?.tag==expectedTag);writes++;this.head=snapshot(if(badReadback)prior else head,"tag-next")
            if(editWorkingAfterWrite)working=false;if(expireAfterWrite)lease=false;if(loseAck)error("Synthetic uncertain acknowledgement")
        }
        override fun createReserved(intent:PublicationHeadIntent) {
            check(head==null);creates++;head=snapshot(intent.intended,"tag-created");if(loseAck)error("Synthetic uncertain create")
        }
        override fun verifyImmutablePublication(head:TemplatePublicationHead){check(immutable)}
        override fun workingVectorMatches():Boolean {
            if(competeAfterPublication && acknowledged>0)head=snapshot(prior.copy(generationId="later-generation"),"later-tag")
            return working
        }
        override fun requireDurable(intent:PublicationHeadIntent){check(durable)}
        override fun requireCurrentLease(){check(lease)}
        override fun observe(intent:PublicationHeadIntent,snapshot:PublicationHeadSnapshot?){observed+=snapshot}
        override fun acknowledgePublication(intent:PublicationHeadIntent,resultTag:String) {
            assertEquals(intent.intended,head?.head);assertEquals(head?.tag,resultTag);acknowledged++
        }
        fun run(intent:PublicationHeadIntent)=GuardedPublicationHead(this,this).execute(intent)
    }
    @Test fun uncertainConditionalWriteRecoversSameHeadWithoutWritingAgain() {
        val fake=Fake();fake.loseAck=true;assertFails {fake.run(intent())};assertEquals(0,fake.acknowledged)
        fake.loseAck=false;assertEquals(HeadPublicationResult.PUBLISHED_WORKING_COPY_VERIFIED,fake.run(intent()))
        assertEquals(1,fake.writes);assertEquals(1,fake.acknowledged);assertEquals(next,fake.observed.last()?.head)
    }
    @Test fun uncertainCreateUsesOnlyThePreviouslyReservedIdentity() {
        val fake=Fake(false);val captured=intent(false);fake.loseAck=true;assertFails {fake.run(captured)}
        fake.loseAck=false;fake.run(captured);assertEquals(1,fake.creates);assertEquals(0,fake.writes);assertEquals(1,fake.acknowledged)
    }
    @Test fun alreadyCommittedHeadWithLaterWorkingEditKeepsPublicationAndReportsPendingCandidate() {
        val fake=Fake();fake.editWorkingAfterWrite=true
        assertEquals(HeadPublicationResult.PUBLISHED_WITH_PENDING_WORKING_CHANGE,fake.run(intent()));assertEquals(1,fake.acknowledged)
        assertEquals(next,fake.head?.head);assertEquals(1,fake.writes)
        assertEquals(HeadPublicationResult.PUBLISHED_WITH_PENDING_WORKING_CHANGE,fake.run(intent()));assertEquals(1,fake.writes)
    }
    @Test fun changedWorkingCopyOrInvalidImmutableSourceStopsBeforeHeadMutation() {
        for(reason in 0..1){val fake=Fake();if(reason==0)fake.working=false else fake.immutable=false
            assertFails {fake.run(intent())};assertEquals(0,fake.writes);assertEquals(prior,fake.head?.head);assertEquals(0,fake.acknowledged)}
    }
    @Test fun competingOrMissingExistingHeadIsObservedAndPreservedWithoutCreateFallback() {
        for(changed in listOf(null,snapshot(prior,"another-tag"),snapshot(prior.copy(generationId="competing-generation")))) {
            val fake=Fake();fake.head=changed;assertFails {fake.run(intent())};assertSame(changed,fake.head)
            assertEquals(listOf(changed),fake.observed);assertEquals(0,fake.writes);assertEquals(0,fake.creates)
        }
        val fake=Fake();fake.competeBeforeWrite=true;assertFails {fake.run(intent())};assertEquals("competing-tag",fake.head?.tag);assertEquals(0,fake.writes)
    }
    @Test fun badReadbackOrExpiredOwnerCannotAcknowledgeEvenAfterProviderWrite() {
        for(reason in 0..1){val fake=Fake();if(reason==0)fake.badReadback=true else fake.expireAfterWrite=true
            assertFails {fake.run(intent())};assertEquals(1,fake.writes);assertEquals(0,fake.acknowledged)}
    }
    @Test fun invalidNamespaceOrMissingDurabilityCannotMutateOrAcceptAnAlreadyIntendedHead() {
        for(reason in 0..3){val fake=Fake();when(reason){0->fake.durable=false;1->fake.lease=false;2->fake.namespace=false;3->fake.head=snapshot(next,"tag-next","outside-folder")}
            assertFails {fake.run(intent())};assertEquals(0,fake.writes);assertEquals(0,fake.acknowledged)}
    }
    @Test fun capturedLineageCannotRetargetTheTemplateFolderOrPreviousGeneration() {
        for(changed in listOf(next.copy(workingFolderId="another-folder"),next.copy(templateId="another-template"),next.copy(previousGenerationId="wrong-generation")))
            assertFails {PublicationHeadIntent("operation-test","head-object","metadata-folder","gym-template-test",prior,"tag-before",changed)}
        assertFails {PublicationHeadIntent("operation-test","head-object","metadata-folder","gym-template-test",null,null,next)}
        assertFails {PublicationHeadIntent("operation-test","head-object","metadata-folder","gym-template-test",prior,"tag\r\ninvalid",next)}
    }
    @Test fun laterCompetingHeadRetainsTheVerifiedPublicationButCannotReportCurrentHeadSuccess() {
        val fake=Fake();fake.competeAfterPublication=true
        assertEquals(HeadPublicationResult.PUBLISHED_WITH_COMPETING_HEAD,fake.run(intent()))
        assertEquals(1,fake.writes);assertEquals(1,fake.acknowledged)
        assertEquals("later-tag",fake.head?.tag);assertEquals("later-generation",fake.observed.last()?.head?.generationId)
        assertTrue(fake.observed.any {it?.head==next})
    }
}
