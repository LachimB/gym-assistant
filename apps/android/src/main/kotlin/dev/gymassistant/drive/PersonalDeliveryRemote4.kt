package dev.gymassistant.drive

import kotlinx.serialization.json.*
internal const val PERSONAL_FOLDER_NAME_4="Gym Assistant — PERSONAL M1 DEV"
internal const val PERSONAL_MARKER_KEY_4="gymPersonalM1"
/** Separate personal namespace. Synthetic transport gates are not broadened. */
internal class PersonalDeliveryRemote4(private val http:DeliveryHttp,private val rootId:String,private val marker:String):DeliveryRemote {
    init {require(rootId.matches(Regex("[-A-Za-z0-9_]+")) && rootId!=SYNTHETIC_PARENT && marker.matches(Regex("personal-m1-[A-Za-z0-9_-]+")))}
    private fun id(value:String):String {if(!value.matches(Regex("[-A-Za-z0-9_]+")))throw DeliveryFailure("contract");return value}
    private fun request(method:String,path:String,body:ByteArray?=null,type:String?=null,allow:Set<Int> = setOf(200)):DeliveryHttpResponse {
        val result=http.request(DeliveryHttpRequest(method,path,type,body))
        if(result.status !in allow)throw DeliveryFailure(when(result.status){401->"authorization";403->"permission";404->"missingRoot";429->"rateLimited";in 500..599->"providerUnavailable";else->"contract"},result.retryAfterMs)
        return result
    }
    private fun json(r:DeliveryHttpResponse)=try {Json.parseToJsonElement(r.body.toString(Charsets.UTF_8)).jsonObject}catch(_:Exception){throw DeliveryFailure("contract")}
    private fun JsonObject.text(k:String)=this[k]?.jsonPrimitive?.content?:throw DeliveryFailure("contract")
    override suspend fun identity():DeliveryIdentity {
        val u=json(request("GET",ACCOUNT_PATH))["user"]?.jsonObject?:throw DeliveryFailure("contract")
        return DeliveryIdentity(u.text("permissionId"),u.text("emailAddress"))
    }
    suspend fun inspectRoot():DeliveryFolder? {
        check(rootId!=SYNTHETIC_PARENT && marker.matches(Regex("personal-m1-[A-Za-z0-9_-]+")))
        val r=request("GET","/drive/v3/files/${id(rootId)}?fields=id,name,mimeType,appProperties,trashed",allow=setOf(200,404))
        if(r.status==404)return null
        val data=json(r)
        if(data.text("id")!=rootId || data["trashed"]?.jsonPrimitive?.booleanOrNull!=false)throw DeliveryFailure("missingRoot")
        val folder=DeliveryFolder(data.text("name"),data["appProperties"]?.jsonObject?.text(PERSONAL_MARKER_KEY_4)?:throw DeliveryFailure("missingRoot"),data.text("mimeType"))
        if(folder.name!=PERSONAL_FOLDER_NAME_4 || folder.marker!=marker || folder.mimeType!="application/vnd.google-apps.folder")throw DeliveryFailure("missingRoot")
        return folder
    }
    override suspend fun folder(id:String):DeliveryFolder {if(id!=rootId)throw DeliveryFailure("contract");return inspectRoot()?:throw DeliveryFailure("missingRoot")}
    suspend fun createRoot() {
        val metadata=buildJsonObject {put("id",id(rootId));put("name",PERSONAL_FOLDER_NAME_4);put("mimeType","application/vnd.google-apps.folder");put("appProperties",buildJsonObject {put(PERSONAL_MARKER_KEY_4,marker)})}
        request("POST","/drive/v3/files?fields=id",metadata.toString().toByteArray(Charsets.UTF_8),"application/json; charset=UTF-8",setOf(200,409))
    }
    override suspend fun reserveFileId():String {
        val ids=json(request("GET","/drive/v3/files/generateIds?count=1&space=drive"))["ids"]?.jsonArray?:throw DeliveryFailure("contract")
        if(ids.size!=1)throw DeliveryFailure("contract");return id(ids.single().jsonPrimitive.content)
    }
    override suspend fun inspect(id:String,expectedParent:String):RemoteDelivery? {
        if(expectedParent!=rootId)throw DeliveryFailure("contract")
        val path="/drive/v3/files/${id(id)}"
        val r=request("GET",path+"?fields=id,name,mimeType,parents,appProperties,trashed",allow=setOf(200,404));if(r.status==404)return null
        val data=json(r);val parents=data["parents"]?.jsonArray?.map {it.jsonPrimitive.content}.orEmpty()
        if(data.text("id")!=id || data.text("mimeType")!="application/json" || parents!=listOf(rootId) || data["trashed"]?.jsonPrimitive?.booleanOrNull!=false ||
            data["appProperties"]?.jsonObject?.get(PERSONAL_MARKER_KEY_4)?.jsonPrimitive?.content!=marker || !data.text("name").matches(Regex("PERSONAL-session-[-A-Za-z0-9_]+\\.json")))throw DeliveryFailure("conflict")
        return RemoteDelivery(id,"application/json",parents,request("GET",path+"?alt=media").body)
    }
    override suspend fun create(id:String,parent:String,name:String,canonical:ByteArray) {
        id(id);if(parent!=rootId || !name.matches(Regex("PERSONAL-session-[-A-Za-z0-9_]+\\.json")) || canonical.size !in 1..8388608)throw DeliveryFailure("contract")
        val boundary="gym-personal-${java.util.UUID.randomUUID()}"
        val metadata=buildJsonObject {put("id",id);put("name",name);put("mimeType","application/json");put("parents",buildJsonArray {add(parent)});put("appProperties",buildJsonObject {put(PERSONAL_MARKER_KEY_4,marker)})}
        val prefix="--$boundary\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n$metadata\r\n--$boundary\r\nContent-Type: application/json\r\n\r\n".toByteArray(Charsets.UTF_8)
        request("POST","/upload/drive/v3/files?uploadType=multipart&fields=id",prefix+canonical+"\r\n--$boundary--\r\n".toByteArray(),"multipart/related; boundary=$boundary",setOf(200,409))
    }
}
