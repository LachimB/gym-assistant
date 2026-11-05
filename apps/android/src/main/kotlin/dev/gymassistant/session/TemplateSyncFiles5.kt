package dev.gymassistant.session

import dev.gymassistant.engine.v2.ImportContracts2
import dev.gymassistant.sync.*
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.charset.CodingErrorAction
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.util.UUID
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject

@Serializable private data class WorkingStepDto5(val objectId:String,val folderId:String,val marker:String,val name:String,
    val mimeType:String,val expectedTag:String?,val beforeHash:String?,val intendedHash:String)
@Serializable private data class IntentDto5(val schemaVersion:String,val jobId:String,val operationId:String,val bindingId:String,
    val expectedLocalRevisionId:String,val manifestJson:String,val workingFiles:List<WorkingStepDto5>)
data class SyncIntentFile5(val path:String,val sha256:String)
class CheckedSyncIntent5(val jobId:String,val operationId:String,val bindingId:String,val expectedLocalRevisionId:String,
    val manifest:TemplateRevisionManifest,val steps:List<WorkingFileStep>)

/** Immutable app-private source/before-images/intents. Callers commit their pointers only after this returns. */
class TemplateSyncFiles5(private val root:File,private val contracts:ImportContracts2) {
    companion object { private val lock=Any() }
    private val json=Json { encodeDefaults=true; ignoreUnknownKeys=false }
    private fun id(value:String)=require(value.matches(Regex("[A-Za-z0-9_-]{3,128}")))
    private fun hash(value:String)=require(value.matches(Regex("[a-f0-9]{64}")))
    private fun path(name:String):File {
        require(name.matches(Regex("([A-Za-z0-9_-]{3,128}\\.json|[a-f0-9]{64}\\.yaml)")))
        val file=File(root,name).canonicalFile
        check(file.parentFile==root.canonicalFile) { "Invalid private sync path" };return file
    }
    private fun syncDirectory()=FileChannel.open(root.toPath(),StandardOpenOption.READ).use {it.force(true)}
    private fun write(name:String,bytes:ByteArray)=synchronized(lock) {
        require(bytes.size in 1..1048576)
        check(root.mkdirs() || root.isDirectory)
        root.setReadable(false,false);root.setWritable(false,false);root.setExecutable(false,false)
        check(root.setReadable(true,true) && root.setWritable(true,true) && root.setExecutable(true,true))
        val target=path(name)
        if(target.exists()){check(target.isFile && target.length()==bytes.size.toLong() && target.readBytes().contentEquals(bytes)) { "Immutable sync file changed" };syncDirectory();return@synchronized}
        val temporary=File(root,".stage-${UUID.randomUUID()}")
        FileOutputStream(temporary).use {stream->
            temporary.setReadable(false,false);temporary.setWritable(false,false)
            check(temporary.setReadable(true,true) && temporary.setWritable(true,true))
            stream.write(bytes);stream.fd.sync()
        }
        // One app process serializes writers. No existing retained file is overwritten or cleaned up.
        Files.move(temporary.toPath(),target.toPath(),StandardCopyOption.ATOMIC_MOVE)
        syncDirectory()
    }
    private fun read(name:String,max:Int):ByteArray {
        val target=path(name);check(target.isFile && target.length() in 1..max.toLong()) { "Private sync file missing or outside bounds" }
        return target.readBytes().also {check(it.size in 1..max)}
    }
    private fun source(hash:String):ByteArray {
        hash(hash);return read("$hash.yaml",1048576).also {check(hashSourceBytes(it)==hash) { "Private sync source changed" }}
    }
    fun prepare(jobId:String,operationId:String,bindingId:String,expectedLocalRevisionId:String,
        manifest:TemplateRevisionManifest,payloads:Map<String,ByteArray>,steps:List<WorkingFileStep>):SyncIntentFile5 {
        listOf(jobId,operationId,bindingId,expectedLocalRevisionId).forEach(::id)
        TemplateSyncContracts5.bundle(manifest,payloads,contracts)
        check(steps.size==manifest.files.size && steps.map {it.objectId}.distinct().size==steps.size && steps.map {it.folderId}.distinct().size==1)
        check(steps.none {step->manifest.files.any {it.objectId==step.objectId}}) {"Immutable source cannot be its editable working file"}
        val descriptors=manifest.files.associateBy {it.relativeName}
        check(steps.map {it.name}.toSet()==descriptors.keys)
        for(step in steps) {
            val descriptor=descriptors.getValue(step.name)
            check(step.intendedHash==descriptor.sha256 && step.intendedBytes.size==descriptor.byteCount)
            step.beforeBytes?.let {write("${step.beforeHash}.yaml",it)}
            write("${step.intendedHash}.yaml",step.intendedBytes)
        }
        val document=IntentDto5("1.0.0",jobId,operationId,bindingId,expectedLocalRevisionId,TemplateSyncContracts5.encode(manifest),
            steps.map {WorkingStepDto5(it.objectId,it.folderId,it.marker,it.name,it.mimeType,it.expectedTag,it.beforeHash,it.intendedHash)})
        val bytes=json.encodeToString(document).toByteArray(Charsets.UTF_8)
        require(bytes.size<=65536)
        val result=SyncIntentFile5("$jobId.json",hashSourceBytes(bytes));write(result.path,bytes)
        load(result);return result
    }
    fun load(pointer:SyncIntentFile5):CheckedSyncIntent5 {
        val text=readRecord(pointer)
        val dto=json.decodeFromString<IntentDto5>(text)
        check(dto.schemaVersion=="1.0.0");listOf(dto.jobId,dto.operationId,dto.bindingId,dto.expectedLocalRevisionId).forEach(::id)
        check(pointer.path=="${dto.jobId}.json")
        val manifest=TemplateSyncContracts5.manifest(dto.manifestJson)
        check(dto.workingFiles.size==manifest.files.size && dto.workingFiles.map {it.objectId}.distinct().size==dto.workingFiles.size)
        val steps=dto.workingFiles.map {step->WorkingFileStep(step.objectId,step.folderId,step.marker,step.name,step.mimeType,step.expectedTag,step.beforeHash?.let(::source),source(step.intendedHash))}
        check(steps.none {step->manifest.files.any {it.objectId==step.objectId}}) {"Immutable source cannot be its editable working file"}
        val byName=steps.associateBy {it.name};check(byName.size==steps.size && steps.map {it.folderId}.distinct().size==1)
        val payloads=manifest.files.associate {file->
            val step=checkNotNull(byName[file.relativeName]);check(step.intendedHash==file.sha256)
            file.objectId to step.intendedBytes
        }
        TemplateSyncContracts5.bundle(manifest,payloads,contracts)
        return CheckedSyncIntent5(dto.jobId,dto.operationId,dto.bindingId,dto.expectedLocalRevisionId,manifest,steps)
    }
    /** Shared immutable-record boundary for additive progress documents; existing captured intents are unchanged. */
    internal fun readRecord(pointer:SyncIntentFile5):String {
        require(pointer.path.matches(Regex("[A-Za-z0-9_-]{3,128}\\.json")))
        hash(pointer.sha256)
        val raw=read(pointer.path,65536);check(hashSourceBytes(raw)==pointer.sha256) { "Private sync intent changed" }
        val text=Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(raw)).toString()
        Json.parseToJsonElement(text).jsonObject
        dev.gymassistant.engine.v2.readYaml2(text)
        return text
    }
    internal fun writeRecord(text:String):SyncIntentFile5 {
        val bytes=text.toByteArray(Charsets.UTF_8);require(bytes.size in 1..65536)
        Json.parseToJsonElement(text).jsonObject;dev.gymassistant.engine.v2.readYaml2(text)
        val pointer=SyncIntentFile5("${UUID.randomUUID()}.json",hashSourceBytes(bytes))
        write(pointer.path,bytes);readRecord(pointer);return pointer
    }
}
