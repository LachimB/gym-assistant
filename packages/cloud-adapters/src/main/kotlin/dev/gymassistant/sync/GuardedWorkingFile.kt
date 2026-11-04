package dev.gymassistant.sync

/** A captured working-file step. Persistence must retain this and both byte arrays before execution. */
class WorkingFileStep(
    val objectId:String,val folderId:String,val marker:String,val name:String,val mimeType:String,
    val expectedTag:String?,before:ByteArray?,intended:ByteArray,
) {
    private val prior=before?.copyOf()
    private val next=intended.copyOf()
    val beforeHash:String?=prior?.let(::hashSourceBytes)
    val intendedHash:String=hashSourceBytes(next)
    val beforeBytes:ByteArray? get()=prior?.copyOf()
    val intendedBytes:ByteArray get()=next.copyOf()
    init {
        objectId(objectId);objectId(folderId)
        require(objectId!=folderId && marker.matches(Regex("gym-template-[A-Za-z0-9_-]{3,128}")))
        RevisionFile(SourceRole.EXERCISE,name,objectId,intendedHash,next.size)
        require(mimeType in setOf("text/plain","text/yaml","application/yaml","application/x-yaml"))
        require((expectedTag==null)==(prior==null) && (expectedTag==null || expectedTag.length in 1..1024))
        require(prior==null || prior.size in 1..1048576)
    }
}

/** Opaque provider tag is independent of a domain revision. Never log this object or its bytes. */
class WorkingFileSnapshot(val objectId:String,parents:Set<String>,val marker:String,val name:String,
    val mimeType:String,val tag:String,val trashed:Boolean,bytes:ByteArray) {
    val parents=parents.toSet()
    private val content=bytes.copyOf()
    val bytes:ByteArray get()=content.copyOf()
}

interface WorkingFileRemote {
    /** Checks pinned account, exact root/area/Template IDs, markers and bounded ancestry. No name lookup fallback. */
    fun verifyNamespace()
    /** Missing/inaccessible existing files must never be recreated. Reads are bounded to 1 MiB. */
    fun read(objectId:String):WorkingFileSnapshot?
    /** Implementations MUST enforce If-Match. Unsupported preconditions have no unconditional fallback. */
    fun replace(objectId:String,expectedTag:String,bytes:ByteArray)
    /** ID was reserved and durably persisted before this call; conflict is resolved by same-ID readback. */
    fun createReserved(step:WorkingFileStep)
}

interface WorkingFileJournal {
    /** Must verify an immutable on-disk intent, hashes and before-image bytes, not just an in-memory flag. */
    fun requireDurable(step:WorkingFileStep)
    /** Called outside network operations, before mutations and before acknowledging results. */
    fun requireCurrentLease()
    /** Persists only a verified per-file result; this does not publish the multi-file head or adopt a Template. */
    fun acknowledge(step:WorkingFileStep,resultTag:String,resultHash:String)
}

/** One guarded step. Exceptions leave its durable intent pending; retry inspects exactly the same object ID. */
class GuardedWorkingFile(private val remote:WorkingFileRemote,private val journal:WorkingFileJournal) {
    private fun checked(step:WorkingFileStep,file:WorkingFileSnapshot):String {
        check(file.objectId==step.objectId && file.parents==setOf(step.folderId) && file.marker==step.marker &&
            file.name==step.name && file.mimeType==step.mimeType && !file.trashed && file.tag.length in 1..1024) { "Working-file namespace changed" }
        val bytes=file.bytes;check(bytes.size in 1..1048576) { "Working-file size outside bounds" }
        return hashSourceBytes(bytes)
    }
    fun execute(step:WorkingFileStep) {
        journal.requireDurable(step);journal.requireCurrentLease();remote.verifyNamespace()
        val current=remote.read(step.objectId)
        if(current!=null) {
            val hash=checked(step,current)
            if(hash==step.intendedHash) {
                remote.verifyNamespace();journal.requireCurrentLease();journal.acknowledge(step,current.tag,hash);return
            }
            check(step.expectedTag!=null && current.tag==step.expectedTag && hash==step.beforeHash) { "Working-file conflict" }
            journal.requireCurrentLease()
            remote.replace(step.objectId,step.expectedTag,step.intendedBytes)
        }else {
            check(step.expectedTag==null) { "Existing working file missing or inaccessible" }
            journal.requireCurrentLease();remote.createReserved(step)
        }
        val result=checkNotNull(remote.read(step.objectId)) { "Working-file acknowledgement remains unverified" }
        val hash=checked(step,result)
        check(hash==step.intendedHash) { "Working-file readback differs" }
        remote.verifyNamespace();journal.requireCurrentLease();journal.acknowledge(step,result.tag,hash)
    }
}
