package dev.gymassistant.session

import dev.gymassistant.engine.v2.*
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID
import kotlinx.serialization.Serializable

@Serializable data class LibraryFile3(val path:String,val sha256:String,val originalName:String)
@Serializable data class TemplateSources3(val templateYaml:String,val exerciseYamls:List<String>)
data class ImportFile3(val name:String,val bytes:ByteArray)
/** App-private staged copies. No source URI permissions retained; no file is truncated/overwritten. */
class LibraryFiles3(private val root:File) {
    companion object { private val lock=Any() }
    private fun path(relative:String):File {
        val file=File(root,relative).canonicalFile
        check(file.path.startsWith(root.canonicalPath+File.separator)) { "Invalid private source path" }
        return file
    }
    private fun rawHash(bytes:ByteArray)=java.security.MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    private fun write(relative:String,bytes:ByteArray)=synchronized(lock) {
        val target=path(relative);check(target.parentFile!!.mkdirs() || target.parentFile!!.isDirectory)
        if(target.exists()) {check(target.isFile && target.readBytes().contentEquals(bytes)) { "Immutable source changed" };return@synchronized}
        val temporary=File(target.parentFile,".stage-${UUID.randomUUID()}")
        FileOutputStream(temporary).use { it.write(bytes);it.fd.sync() }
        Files.move(temporary.toPath(),target.toPath(),StandardCopyOption.ATOMIC_MOVE)
    }
    fun stage(candidate:String,inputs:List<ImportFile3>):List<LibraryFile3> {
        check(candidate.matches(Regex("[A-Za-z0-9_-]{3,128}")))
        check(inputs.size in 2..33 && inputs.sumOf { it.bytes.size.toLong() }<=8388608 && inputs.all { it.bytes.size<=1048576 && it.name.toByteArray(Charsets.UTF_8).size<=4096 })
        return inputs.mapIndexed { index,input ->
            val relative="imported/$candidate/source-$index.yaml";write(relative,input.bytes)
            LibraryFile3(relative,rawHash(input.bytes),input.name)
        }
    }
    fun read(files:List<LibraryFile3>):TemplateSources3 {
        check(files.size in 2..33)
        val parsed=files.map { descriptor ->
            val file=path(descriptor.path);check(file.isFile && file.length()<=1048576)
            val raw=file.readBytes();check(rawHash(raw)==descriptor.sha256) { "Staged source changed" }
            val text=Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(raw)).toString()
            text to readYaml2(text)
        }
        val templates=parsed.filter { "templateId" in it.second };check(templates.size==1)
        val exercises=parsed.filter { "familyId" in it.second };check(exercises.size==parsed.size-1)
        return TemplateSources3(templates.single().first,exercises.sortedBy { it.second.text2("familyId") }.map { it.first })
    }
    fun exportPublished(templateId:String,revisionId:String,sources:TemplateSources3) {
        check(listOf(templateId,revisionId).all { it.matches(Regex("[A-Za-z0-9][A-Za-z0-9_-]{2,127}")) })
        val prefix="personal/templates/$templateId/$revisionId"
        write("$prefix/template.yaml",sources.templateYaml.toByteArray(Charsets.UTF_8))
        sources.exerciseYamls.forEachIndexed { index,yaml->write("$prefix/exercise-$index.yaml",yaml.toByteArray(Charsets.UTF_8)) }
    }
}
