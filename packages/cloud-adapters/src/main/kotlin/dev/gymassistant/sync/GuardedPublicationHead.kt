package dev.gymassistant.sync

/** Persist this exact lineage/tag and reserved head identity before a provider mutation. */
class PublicationHeadIntent(val operationId:String,val objectId:String,val metadataFolderId:String,val marker:String,
    val expected:TemplatePublicationHead?,val expectedTag:String?,val intended:TemplatePublicationHead) {
    init {
        domainId(operationId);objectId(objectId);objectId(metadataFolderId)
        require(objectId!=metadataFolderId && objectId!=intended.revisionManifestId && metadataFolderId!=intended.workingFolderId)
        require(marker.matches(Regex("gym-template-[A-Za-z0-9_-]{3,128}")))
        require((expected==null)==(expectedTag==null))
        expectedTag?.let {require(it.length in 1..1024 && it.none(Char::isISOControl))}
        if(expected==null)require(intended.previousGenerationId==null)
        else expected.advance(expected,intended)
    }
}
class PublicationHeadSnapshot(val objectId:String,parents:Set<String>,val marker:String,val name:String,val mimeType:String,
    val tag:String,val trashed:Boolean,val head:TemplatePublicationHead) {val parents=parents.toSet()}

interface PublicationHeadRemote {
    /** Same pinned account/common root/area/Template and exact metadata folder; no root replacement. */
    fun verifyNamespace()
    /** Strict bounded head decoding; absence never permits recreation of an expected existing head. */
    fun read(objectId:String):PublicationHeadSnapshot?
    fun replace(objectId:String,expectedTag:String,head:TemplatePublicationHead)
    /** Previously reserved and durably retained ID; conflicts are resolved only by that same ID. */
    fun createReserved(intent:PublicationHeadIntent)
    /** Verify every immutable file/manifest by identity, namespace, exact hash and complete source compilation. */
    fun verifyImmutablePublication(head:TemplatePublicationHead)
    /** Re-read the captured working-file tag/hash vector; implementations retain divergent observations. */
    fun workingVectorMatches():Boolean
}
interface PublicationHeadJournal {
    fun requireDurable(intent:PublicationHeadIntent)
    fun requireCurrentLease()
    /** Retain bounded original head metadata/bytes, including a competing/missing head, without adopting it. */
    fun observe(intent:PublicationHeadIntent,snapshot:PublicationHeadSnapshot?)
    /** Only verified head publication; this cannot acknowledge a local library adoption or a whole job. */
    fun acknowledgePublication(intent:PublicationHeadIntent,resultTag:String)
}
enum class HeadPublicationResult { PUBLISHED_WORKING_COPY_VERIFIED, PUBLISHED_WITH_PENDING_WORKING_CHANGE, PUBLISHED_WITH_COMPETING_HEAD }

/** A committed immutable publication and a subsequently edited working copy are distinct facts. */
class GuardedPublicationHead(private val remote:PublicationHeadRemote,private val journal:PublicationHeadJournal) {
    private fun checked(intent:PublicationHeadIntent,snapshot:PublicationHeadSnapshot):PublicationHeadSnapshot {
        check(snapshot.objectId==intent.objectId && snapshot.parents==setOf(intent.metadataFolderId) && snapshot.marker==intent.marker &&
            snapshot.name=="head.json" && snapshot.mimeType=="application/json" && !snapshot.trashed &&
            snapshot.tag.length in 1..1024 && snapshot.tag.none(Char::isISOControl)) {"Publication namespace changed"}
        return snapshot
    }
    private fun observe(intent:PublicationHeadIntent):PublicationHeadSnapshot? {
        val snapshot=remote.read(intent.objectId)?.let {checked(intent,it)}
        journal.requireCurrentLease();journal.observe(intent,snapshot);return snapshot
    }
    fun execute(intent:PublicationHeadIntent):HeadPublicationResult {
        journal.requireDurable(intent);journal.requireCurrentLease();remote.verifyNamespace()
        var current=observe(intent)
        if(current?.head!=intent.intended) {
            if(intent.expected==null)check(current==null) {"Reserved publication head already exists"}
            else check(current!=null && current.tag==intent.expectedTag && current.head==intent.expected) {"Publication head conflict"}
            remote.verifyImmutablePublication(intent.intended)
            check(remote.workingVectorMatches()) {"Working copy changed before head publication"}
            remote.verifyNamespace();journal.requireCurrentLease()
            if(intent.expectedTag==null)remote.createReserved(intent) else remote.replace(intent.objectId,intent.expectedTag,intent.intended)
            current=checkNotNull(observe(intent)) {"Publication acknowledgement remains unverified"}
            check(current.head==intent.intended) {"Publication readback differs"}
        }
        remote.verifyImmutablePublication(intent.intended)
        remote.verifyNamespace();journal.requireCurrentLease()
        journal.acknowledgePublication(intent,checkNotNull(current).tag)
        // A later edit is a pending candidate, not a reason to erase a verified committed publication.
        val matches=remote.workingVectorMatches()
        remote.verifyNamespace();journal.requireCurrentLease()
        val latest=observe(intent)
        remote.verifyNamespace();journal.requireCurrentLease()
        if(latest?.head!=intent.intended || latest.tag!=current.tag)return HeadPublicationResult.PUBLISHED_WITH_COMPETING_HEAD
        return if(matches)HeadPublicationResult.PUBLISHED_WORKING_COPY_VERIFIED else HeadPublicationResult.PUBLISHED_WITH_PENDING_WORKING_CHANGE
    }
}
