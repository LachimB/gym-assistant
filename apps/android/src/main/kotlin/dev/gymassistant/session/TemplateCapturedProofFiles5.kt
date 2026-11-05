package dev.gymassistant.session

import dev.gymassistant.drive.*
import dev.gymassistant.engine.v2.ImportContracts2
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

@Serializable private data class ProofFolder5(val id:String,val parent:String,val name:String,val markerKey:String,val marker:String) {
    fun pin()=TemplateFolderPin5(id,parent,name,markerKey,marker)
}
private fun TemplateFolderPin5.proofFolder()=ProofFolder5(id,parent,name,markerKey,marker)
@Serializable private data class CapturedProofDocument5(val kind:String,val schemaVersion:String,val capturePath:String,val captureHash:String,
    val start:Int,val folders:List<ProofFolder5>,val priorPath:String?=null,val priorHash:String?=null,val revisionFolder:ProofFolder5?=null)

internal class CheckedVectorProof5(val pointer:SyncIntentFile5,val capturePointer:SyncIntentFile5,val capture:CheckedSourceCapture5,
    val scope:TemplateSourceReadScope5,val start:Int,val vector:KnownSourceVector5)
internal class CheckedBaseProof5(val pointer:SyncIntentFile5,val capturePointer:SyncIntentFile5,val capture:CheckedSourceCapture5,
    val scope:KnownImmutableBaseScope5,val start:Int,val priorPublication:SyncIntentFile5,val sources:TemplateSources3)

/** Inert immutable evidence files only. Callers still need a current leased SQL CAS and separate classification/review. */
internal class TemplateCapturedProofFiles5(private val files:TemplateSyncFiles5,private val captures:TemplateSourceCaptureFiles5,
    private val publications:TemplatePublicationFiles5,private val contracts:ImportContracts2) {
    private val json=Json {encodeDefaults=false;ignoreUnknownKeys=false}
    private fun read(pointer:SyncIntentFile5):CapturedProofDocument5=json.decodeFromString<CapturedProofDocument5>(files.readRecord(pointer)).also {
        require(it.schemaVersion=="1.0.0" && it.kind in setOf("knownVectorProof","knownBaseProof") && it.folders.size in 1..40)
        require((it.priorPath==null)==(it.priorHash==null) && (it.priorPath==null)==(it.revisionFolder==null))
        require((it.kind=="knownBaseProof")== (it.priorPath!=null))
    }
    private fun source(d:CapturedProofDocument5):Pair<CheckedSourceCapture5,TemplateSourceReadScope5> {
        val checked=captures.load(SyncIntentFile5(d.capturePath,d.captureHash))
        require(d.start in 0..checked.rawObservations.size)
        return checked to TemplateSourceReadScope5(checked,d.folders.map {it.pin()})
    }
    fun prepareVector(capture:SyncIntentFile5,folders:List<TemplateFolderPin5>,start:Int):SyncIntentFile5 {
        val d=CapturedProofDocument5("knownVectorProof","1.0.0",capture.path,capture.sha256,start,folders.map {it.proofFolder()})
        val(_,scope)=source(d);replayKnownVector5(scope,start)
        val pointer=files.writeRecord(json.encodeToString(d));loadVector(pointer);return pointer
    }
    fun loadVector(pointer:SyncIntentFile5):CheckedVectorProof5 {
        val d=read(pointer);check(d.kind=="knownVectorProof")
        val capture=SyncIntentFile5(d.capturePath,d.captureHash);check(capture!=pointer)
        val(checked,scope)=source(d);val vector=replayKnownVector5(scope,d.start)
        return CheckedVectorProof5(pointer,capture,checked,scope,d.start,vector)
    }
    fun prepareBase(capture:SyncIntentFile5,folders:List<TemplateFolderPin5>,start:Int,prior:SyncIntentFile5,revisionFolder:TemplateFolderPin5):SyncIntentFile5 {
        val d=CapturedProofDocument5("knownBaseProof","1.0.0",capture.path,capture.sha256,start,folders.map {it.proofFolder()},prior.path,prior.sha256,revisionFolder.proofFolder())
        val(_,source)=source(d);val scope=KnownImmutableBaseScope5(source,publications.load(prior),revisionFolder,contracts)
        replayKnownBase5(scope,start)
        val pointer=files.writeRecord(json.encodeToString(d));loadBase(pointer);return pointer
    }
    fun loadBase(pointer:SyncIntentFile5):CheckedBaseProof5 {
        val d=read(pointer);check(d.kind=="knownBaseProof")
        val capture=SyncIntentFile5(d.capturePath,d.captureHash);val prior=SyncIntentFile5(checkNotNull(d.priorPath),checkNotNull(d.priorHash))
        check(capture!=pointer && prior!=pointer && capture!=prior)
        val(checked,source)=source(d);val scope=KnownImmutableBaseScope5(source,publications.load(prior),checkNotNull(d.revisionFolder).pin(),contracts)
        val sources=replayKnownBase5(scope,d.start)
        return CheckedBaseProof5(pointer,capture,checked,scope,d.start,prior,sources)
    }
}
