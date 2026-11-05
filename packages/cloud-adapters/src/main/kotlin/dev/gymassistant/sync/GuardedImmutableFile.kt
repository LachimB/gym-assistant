package dev.gymassistant.sync

/** A previously reserved revision object. The exact identity and bytes must be retained before execution. */
class ImmutableFileStep(val objectId:String,val folderId:String,val marker:String,val name:String,val mimeType:String,bytes:ByteArray) {
    private val content=bytes.copyOf()
    val bytes:ByteArray get()=content.copyOf()
    val sha256=hashSourceBytes(content)
    val byteCount=content.size
    init {
        objectId(objectId);objectId(folderId);require(objectId!=folderId)
        require(marker.matches(Regex("gym-template-[A-Za-z0-9_-]{3,128}")))
        if(mimeType=="application/json")require(name=="revision.json" && byteCount in 1..65536)
        else {require(mimeType=="text/plain");RevisionFile(SourceRole.EXERCISE,name,objectId,sha256,byteCount)}
    }
}
class ImmutableFileSnapshot(val objectId:String,parents:Set<String>,val marker:String,val name:String,
    val mimeType:String,val tag:String,val trashed:Boolean,bytes:ByteArray) {
    val parents=parents.toSet()
    private val content=bytes.copyOf()
    val bytes:ByteArray get()=content.copyOf()
}
interface ImmutableFileRemote {
    fun verifyNamespace()
    fun read(objectId:String):ImmutableFileSnapshot?
    /** Create only this durable reserved identity. There is deliberately no replacement operation. */
    fun createReserved(step:ImmutableFileStep)
}
interface ImmutableFileJournal {
    fun requireDurable(step:ImmutableFileStep)
    fun requireCurrentLease()
    /** An acknowledged/published object that disappeared cannot be recreated as recovery. */
    fun requireCreatePending(step:ImmutableFileStep)
    fun acknowledge(step:ImmutableFileStep,resultTag:String)
}

/** Matching immutable bytes are reusable; any different existing object is a conflict, never a repair. */
class GuardedImmutableFile(private val remote:ImmutableFileRemote,private val journal:ImmutableFileJournal) {
    private fun checked(step:ImmutableFileStep,result:ImmutableFileSnapshot) {
        check(result.objectId==step.objectId && result.parents==setOf(step.folderId) && result.marker==step.marker &&
            result.name==step.name && result.mimeType==step.mimeType && !result.trashed &&
            result.tag.length in 1..1024 && result.tag.none(Char::isISOControl)) {"Immutable object namespace differs"}
        val bytes=result.bytes
        check(bytes.size==step.byteCount && hashSourceBytes(bytes)==step.sha256) {"Immutable object bytes differ"}
    }
    fun execute(step:ImmutableFileStep) {
        journal.requireDurable(step);journal.requireCurrentLease();remote.verifyNamespace()
        val before=remote.read(step.objectId)
        val result=if(before!=null){checked(step,before);before}else {
            journal.requireCreatePending(step);journal.requireCurrentLease();remote.createReserved(step)
            checkNotNull(remote.read(step.objectId)) {"Immutable creation acknowledgement remains unverified"}.also {checked(step,it)}
        }
        remote.verifyNamespace();journal.requireCurrentLease();journal.acknowledge(step,result.tag)
    }
}
