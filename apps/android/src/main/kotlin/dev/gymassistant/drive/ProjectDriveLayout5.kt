package dev.gymassistant.drive

import dev.gymassistant.engine.v2.readYaml2
import dev.gymassistant.session.ProjectStorageRoot5
import dev.gymassistant.session.WorkoutDatabase
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.*

private fun layoutId5(id:String)=require(id.matches(Regex("[A-Za-z0-9_-]{1,128}")))
private fun layoutName5(name:String)=require(name.isNotBlank() && name.toByteArray(Charsets.UTF_8).size<=4096 && name.none(Char::isISOControl))
@Serializable internal data class ProjectFolderPin5(val id:String,val parentId:String,val name:String,val propertyName:String,val marker:String) {
    init {layoutId5(id);layoutId5(parentId);require(id!=parentId);layoutName5(name)
        require(propertyName.matches(Regex("gym[A-Za-z0-9]{1,64}")) && marker.matches(Regex("[A-Za-z0-9_-]{3,160}")))}
}
@Serializable internal data class ProjectAreaPin5(val kind:String,val id:String,val sourceName:String,val sourceParentId:String,
    val name:String,val environment:String,val marker:String) {
    val propertyName get()=if(kind=="personal")"gymPersonalM1" else "gymSyntheticM1"
    init {layoutId5(id);layoutId5(sourceParentId);require(id!=sourceParentId);layoutName5(sourceName)
        require(kind in setOf("personal","synthetic") && environment==if(kind=="personal")"dev" else "test")
        require(name==if(kind=="personal")"PERSONAL M1" else "SYNTHETIC M1")
        require(marker.matches(if(kind=="personal")Regex("personal-m1-[A-Za-z0-9_-]{1,128}") else Regex("[A-Za-z0-9_-]{3,160}")))}
}
@Serializable internal data class ProjectDriveLayout5(val schemaVersion:String,val bindingId:String,val permissionId:String,val phase:String,
    val root:ProjectFolderPin5,val dev:ProjectFolderPin5,val test:ProjectFolderPin5,val prod:ProjectFolderPin5,val areas:List<ProjectAreaPin5>) {
    init {
        require(schemaVersion=="1.0.0" && bindingId.matches(Regex("[A-Za-z0-9_-]{3,128}")));layoutId5(permissionId)
        require(phase in setOf("reserved","verified","adopting","active","blocked"))
        require(root.name=="Gym Assistant" && root.propertyName=="gymProjectM1" && root.marker.matches(Regex("gym-project-[A-Za-z0-9_-]{3,128}")))
        for((pin,name) in listOf(dev to "dev",test to "test",prod to "prod"))
            require(pin.name==name && pin.parentId==root.id && pin.propertyName=="gymEnvironmentM1")
        require(areas.size==2 && areas.map {it.kind}.toSet()==setOf("personal","synthetic"))
        val ids=listOf(root.id,dev.id,test.id,prod.id)+areas.map {it.id}
        require(ids.distinct().size==ids.size && root.parentId !in ids && areas.none {it.sourceParentId in ids})
    }
    fun area(id:String)=areas.singleOrNull {it.id==id}?:throw DeliveryFailure("contract")
    fun environment(area:ProjectAreaPin5)=if(area.environment=="dev")dev else test
}
internal fun projectDriveLayout5(encoded:String):ProjectDriveLayout5? {
    if(encoded.isEmpty())return null
    return try {
        require(encoded.length<=50000 && encoded.matches(Regex("[A-Za-z0-9+/]+={0,2}")))
        val bytes=java.util.Base64.getDecoder().decode(encoded);require(bytes.size in 1..32768)
        val raw=Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
        readYaml2(raw);Json {ignoreUnknownKeys=false}.decodeFromString<ProjectDriveLayout5>(raw)
    }catch(_:Exception){throw DeliveryFailure("contract")}
}

internal const val PROJECT_FOLDER_FIELDS_5="id,name,mimeType,parents,appProperties,trashed,ownedByMe,shared,permissions(id,type,role,deleted)"
private fun JsonObject.layoutText5(key:String):String {
    val value=this[key] as? JsonPrimitive?:throw DeliveryFailure("missingRoot")
    if(!value.isString)throw DeliveryFailure("missingRoot");return value.content
}
private fun JsonObject.layoutFlag5(key:String):Boolean {
    val value=this[key] as? JsonPrimitive?:throw DeliveryFailure("missingRoot")
    if(value.isString)throw DeliveryFailure("missingRoot")
    return value.booleanOrNull?:throw DeliveryFailure("missingRoot")
}
private fun JsonObject.layoutParents5():List<String> {
    val values=this["parents"] as? JsonArray?:throw DeliveryFailure("missingRoot")
    return values.map {val value=it as? JsonPrimitive?:throw DeliveryFailure("missingRoot");if(!value.isString)throw DeliveryFailure("missingRoot");value.content}
}
private fun verifiedFolder5(data:JsonObject,id:String,key:String,marker:String,permission:String) {
    val properties=data["appProperties"] as? JsonObject?:throw DeliveryFailure("missingRoot")
    if(data.layoutText5("id")!=id || data.layoutText5("mimeType")!="application/vnd.google-apps.folder" ||
        !data.layoutFlag5("ownedByMe") || data.layoutFlag5("trashed") || data.layoutFlag5("shared") || properties.layoutText5(key)!=marker)throw DeliveryFailure("missingRoot")
    val permissions=data["permissions"] as? JsonArray?:throw DeliveryFailure("missingRoot")
    if(permissions.size!=1)throw DeliveryFailure("permission")
    val owner=permissions.single() as? JsonObject?:throw DeliveryFailure("permission")
    if(owner.layoutText5("id")!=permission || owner.layoutText5("type")!="user" || owner.layoutText5("role")!="owner" ||
        "deleted" in owner && owner.layoutFlag5("deleted"))throw DeliveryFailure("permission")
}

/** Exact source/final pairs only. An active binding has no legacy-parent or name-prefix fallback. */
internal class ProjectAreaGate5(val layout:ProjectDriveLayout5,val pin:ProjectAreaPin5,
    private val rootState:suspend ()->ProjectStorageRoot5?) {
    private var identityVerified=false
    init {require(layout.areas.contains(pin))}
    fun identity(permissionId:String) {identityVerified=false;if(permissionId!=layout.permissionId)throw DeliveryFailure("accountMismatch");identityVerified=true}
    private suspend fun state():Pair<ProjectStorageRoot5?,String> {
        if(layout.phase=="blocked")throw DeliveryFailure("missingRoot")
        val row=rootState()
        if(row!=null && (row.rootBindingId!=layout.bindingId || row.provider!="googleDrive" || row.permissionId!=layout.permissionId ||
            row.commonRootFolderId!=layout.root.id || row.marker!=layout.root.marker))throw DeliveryFailure("contract")
        val phase=row?.state?:layout.phase
        if(phase=="blocked")throw DeliveryFailure("missingRoot")
        val order=listOf("reserved","verified","adopting","active")
        if(order.indexOf(phase)<order.indexOf(layout.phase))throw DeliveryFailure("contract")
        return row to phase
    }
    suspend fun verify(data:JsonObject,read:suspend (String)->JsonObject):DeliveryFolder {
        if(!identityVerified)throw DeliveryFailure("accountMismatch")
        val before=state();val phase=before.second
        verifiedFolder5(data,pin.id,pin.propertyName,pin.marker,layout.permissionId)
        val name=data.layoutText5("name");val parents=data.layoutParents5();val environment=layout.environment(pin)
        val source=name==pin.sourceName && parents==listOf(pin.sourceParentId)
        val target=name==pin.name && parents==listOf(environment.id)
        if(when(phase){"reserved","verified"->!source;"adopting"->!source && !target;"active"->!target;else->true})throw DeliveryFailure("missingRoot")
        if(phase in setOf("verified","adopting","active")) {
            for(folder in listOf(layout.root,environment)) {
                val actual=read(folder.id)
                verifiedFolder5(actual,folder.id,folder.propertyName,folder.marker,layout.permissionId)
                if(actual.layoutText5("name")!=folder.name || actual.layoutParents5()!=listOf(folder.parentId))throw DeliveryFailure("missingRoot")
            }
        }
        if(state()!=before)throw DeliveryFailure("contract")
        return DeliveryFolder(name,pin.marker,"application/vnd.google-apps.folder",projectVerified=true)
    }
}

/** A missing layout cannot silently downgrade an account that already has a durable project-root binding. */
internal class ProjectDriveAccess5(val layout:ProjectDriveLayout5?,private val root:suspend (String)->ProjectStorageRoot5?) {
    constructor(database:WorkoutDatabase,layout:ProjectDriveLayout5?):this(layout,{database.templateSync().rootForAccount("googleDrive",it)})
    suspend fun identity(identity:DeliveryIdentity) {
        if(layout==null) {if(root(identity.permissionId)!=null)throw DeliveryFailure("contract")}
        else {
            if(identity.permissionId!=layout.permissionId)throw DeliveryFailure("accountMismatch")
            val row=root(identity.permissionId)
            if(row!=null && (row.rootBindingId!=layout.bindingId || row.provider!="googleDrive" || row.permissionId!=layout.permissionId ||
                row.commonRootFolderId!=layout.root.id || row.marker!=layout.root.marker))throw DeliveryFailure("contract")
            if(row?.state=="blocked" || layout.phase=="blocked")throw DeliveryFailure("missingRoot")
        }
    }
    fun gate(kind:String):ProjectAreaGate5?=layout?.let {config->
        val pin=config.areas.singleOrNull {it.kind==kind}?:throw DeliveryFailure("contract")
        ProjectAreaGate5(config,pin,{root(config.permissionId)})
    }
}
