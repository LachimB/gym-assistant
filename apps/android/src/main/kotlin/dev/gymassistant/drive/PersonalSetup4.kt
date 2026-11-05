package dev.gymassistant.drive

import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable internal data class PersonalSetupIntent4(val permissionId:String,val email:String,val folderId:String,val marker:String,val state:String="reserved")
internal class PersonalSetupFiles4(private val file:File) {
    companion object {private val lock=Any()}
    private fun validate(s:PersonalSetupIntent4) {
        check(s.permissionId.isNotBlank() && s.email.isNotBlank() && s.folderId.matches(Regex("[-A-Za-z0-9_]+")) && s.folderId!=SYNTHETIC_PARENT)
        check(s.marker.matches(Regex("personal-m1-[A-Za-z0-9_-]+")) && s.state in setOf("reserved","verified"))
    }
    fun read():PersonalSetupIntent4?=synchronized(lock) {
        if(!file.exists())null else {check(file.isFile && file.length() in 1..65536);Json.decodeFromString<PersonalSetupIntent4>(file.readText()).also(::validate)}
    }
    fun save(expected:PersonalSetupIntent4?,next:PersonalSetupIntent4)=synchronized(lock) {
        validate(next);check(read()==expected)
        if(expected!=null){check(next.copy(state=expected.state)==expected);check(expected.state!="verified" || next.state=="verified")}
        check(file.parentFile!!.mkdirs() || file.parentFile!!.isDirectory)
        val stage=File(file.parentFile,".setup-${java.util.UUID.randomUUID()}")
        try {FileOutputStream(stage).use {it.write(Json.encodeToString(next).toByteArray(Charsets.UTF_8));it.fd.sync()};Files.move(stage.toPath(),file.toPath(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING)}finally {stage.delete()}
    }
}
/** Only captured existing areas may be adopted. Creation belongs to reviewed project-layout setup. */
internal suspend fun ensurePersonalRoot4(files:PersonalSetupFiles4,http:DeliveryHttp,email:String,project:ProjectDriveAccess5?=null):Pair<PersonalSetupIntent4,PersonalDeliveryRemote4> {
    var saved=files.read()
    val gate=project?.gate("personal")
    if(saved==null && gate==null)throw DeliveryFailure("missingRoot")
    val root=saved?.folderId?:checkNotNull(gate).pin.id;val marker=saved?.marker?:checkNotNull(gate).pin.marker
    val remote=PersonalDeliveryRemote4(http,root,marker,gate,{project?.identity(it)})
    val identity=remote.identity()
    if(!identity.email.equals(email,ignoreCase=true) || identity.permissionId.isBlank())throw DeliveryFailure("accountMismatch")
    // Reuse only an already inventoried exact ID. Common-root/area creation belongs to reviewed layout setup.
    if(saved!=null && (saved.permissionId!=identity.permissionId || !saved.email.equals(identity.email,ignoreCase=true)))throw DeliveryFailure("accountMismatch")
    if(remote.inspectRoot()==null)throw DeliveryFailure("missingRoot")
    if(saved==null){val proposed=PersonalSetupIntent4(identity.permissionId,identity.email,root,marker,"verified");files.save(null,proposed);saved=proposed}
    if(saved.state!="verified"){val verified=saved.copy(state="verified");files.save(saved,verified);saved=verified}
    return saved to remote
}
