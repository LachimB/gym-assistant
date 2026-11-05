package dev.gymassistant.drive

import dev.gymassistant.sync.*
import java.io.Closeable
import kotlinx.serialization.json.*

private fun workingMetadata5(step:WorkingFileStep)=buildJsonObject {
    put("id",step.objectId);put("name",step.name);put("mimeType",step.mimeType)
    put("parents",buildJsonArray {add(step.folderId)});put("appProperties",buildJsonObject {put(TEMPLATE_MARKER_KEY_5,step.marker)})
}

/** Exact working source intent at the socket boundary; all folders and other objects are read-only. */
internal class GoogleStrictWorkingHttp5(token:String,reserveRequest:()->Unit,namespace:TemplateNamespace5,steps:List<WorkingFileStep>):TemplateHttp5,Closeable {
    private val pins=steps.associateBy {it.objectId};private val folders=namespace.folders.associateBy {it.id}
    private val http:GoogleTemplateHttp5
    init {
        require(steps.size in 1..16 && pins.size==steps.size && pins.keys.none {it in folders})
        require(steps.all {it.folderId==namespace.workingFolderId});steps.mapNotNull {it.expectedTag}.forEach(::immutableTag5)
        http=GoogleTemplateHttp5(token,reserveRequest,folders.keys+pins.keys,pins.keys,IMMUTABLE_FILE_FIELDS_5)
    }
    override fun close()=http.close()
    override fun request(request:TemplateHttpRequest5):DeliveryHttpResponse=immutableSafe5 {
        val own=request.copy(body=request.body?.copyOf())
        when(own.method) {
            "GET" -> require(own.path==ACCOUNT_PATH || (folders.keys+pins.keys).any {own.path=="/drive/v2/files/$it?fields=$IMMUTABLE_FILE_FIELDS_5"} ||
                pins.keys.any {own.path=="/drive/v2/files/$it?alt=media"})
            "PUT" -> {
                val pin=pins.values.single {own.path=="/upload/drive/v2/files/${it.objectId}?uploadType=media&fields=id"}
                require(pin.expectedTag!=null && own.ifMatch==pin.expectedTag && own.contentType==pin.mimeType && own.body?.contentEquals(pin.intendedBytes)==true)
            }
            "POST" -> {
                require(own.path=="/upload/drive/v3/files?uploadType=multipart&fields=id" && own.ifMatch==null)
                val type=checkNotNull(own.contentType);require(type.matches(Regex("multipart/related; boundary=gym-template-[A-Za-z0-9-]{1,64}")))
                val boundary=type.substringAfter("boundary=");val prefix="--$boundary\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n"
                val body=checkNotNull(own.body);val text=immutableText5(body,1114112);require(text.startsWith(prefix))
                val end=text.indexOf("\r\n--$boundary\r\n",prefix.length);require(end>prefix.length)
                val raw=text.substring(prefix.length,end);val metadata=immutableJson5(raw.toByteArray());val pin=checkNotNull(pins[metadata.immutableString5("id")])
                require(pin.expectedTag==null && metadata==workingMetadata5(pin))
                val expected=(prefix+raw+"\r\n--$boundary\r\nContent-Type: ${pin.mimeType}\r\n\r\n").toByteArray()+pin.intendedBytes+"\r\n--$boundary--\r\n".toByteArray()
                require(body.contentEquals(expected))
            }
            else -> error("Unsupported working source route")
        }
        http.request(own)
    }
}

internal class TemplateStrictWorkingBatch5(token:String,reserveRequest:()->Unit,namespace:TemplateNamespace5,steps:List<WorkingFileStep>,
    retain:(RemoteObjectCapture5)->Unit,transport:TemplateHttp5?=null):Closeable {
    private val http=transport?:GoogleStrictWorkingHttp5(token,reserveRequest,namespace,steps)
    val remote:WorkingFileRemote=GoogleStrictWorkingRemote5(http,namespace,steps,retain)
    override fun close(){(http as? Closeable)?.close()}
}

/** Owner-only bounded reads retain original metadata/content before any decoding or interpretation. */
internal class GoogleStrictWorkingRemote5(private val http:TemplateHttp5,private val namespace:TemplateNamespace5,steps:List<WorkingFileStep>,
    private val retain:(RemoteObjectCapture5)->Unit):WorkingFileRemote {
    private val pins=steps.associateBy {it.objectId};private var verified=false
    init {require(steps.size in 1..16 && pins.size==steps.size && steps.all {it.folderId==namespace.workingFolderId})
        require(pins.keys.none {id->namespace.folders.any {it.id==id}});steps.mapNotNull {it.expectedTag}.forEach(::immutableTag5)}
    private fun checked(request:TemplateHttpRequest5,allow:Set<Int> = setOf(200)):DeliveryHttpResponse {
        val r=http.request(request)
        if(r.status !in allow)throw DeliveryFailure(when(r.status){401->"authorization";403->"permission";404->"missingRoot";409,412->"conflict";429->"rateLimited";in 500..599->"providerUnavailable";else->"contract"},r.retryAfterMs)
        return r
    }
    private fun metadata(id:String,kind:RemoteObjectKind5,missing:Boolean=false):ByteArray? {
        val r=checked(TemplateHttpRequest5("GET","/drive/v2/files/$id?fields=$IMMUTABLE_FILE_FIELDS_5"),if(missing)setOf(200,404)else setOf(200))
        retain(RemoteObjectCapture5(kind,id,r.status,if(r.status==404)null else r.body,null));return if(r.status==404)null else r.body
    }
    override fun verifyNamespace()=immutableSafe5 {
        verified=false
        val user=immutableJson5(checked(TemplateHttpRequest5("GET",ACCOUNT_PATH)).body)["user"]?.jsonObject ?: error("account")
        if(user.immutableString5("permissionId")!=namespace.permissionId)throw DeliveryFailure("accountMismatch")
        for(pin in namespace.folders)checkedTemplateObject5(immutableJson5(checkNotNull(metadata(pin.id,RemoteObjectKind5.NAMESPACE))),namespace.permissionId,
            pin.id,pin.parent,pin.name,"application/vnd.google-apps.folder",pin.markerKey,pin.marker)
        verified=true
    }
    private fun tag(pin:WorkingFileStep,raw:ByteArray)=checkedTemplateObject5(immutableJson5(raw),namespace.permissionId,
        pin.objectId,pin.folderId,pin.name,pin.mimeType,TEMPLATE_MARKER_KEY_5,pin.marker)
    override fun read(objectId:String):WorkingFileSnapshot?=immutableSafe5 {
        check(verified);val pin=checkNotNull(pins[objectId]);val before=metadata(objectId,RemoteObjectKind5.WORKING,true) ?: return@immutableSafe5 null
        val tag=tag(pin,before);val content=checked(TemplateHttpRequest5("GET","/drive/v2/files/$objectId?alt=media")).body
        retain(RemoteObjectCapture5(RemoteObjectKind5.WORKING,objectId,200,before,content));immutableText5(content,1048576)
        val after=checkNotNull(metadata(objectId,RemoteObjectKind5.WORKING));if(tag(pin,after)!=tag)throw DeliveryFailure("conflict")
        WorkingFileSnapshot(objectId,setOf(pin.folderId),pin.marker,pin.name,pin.mimeType,tag,false,content)
    }
    override fun replace(objectId:String,expectedTag:String,bytes:ByteArray)=immutableSafe5 {
        check(verified);val pin=checkNotNull(pins[objectId]);require(pin.expectedTag!=null && expectedTag==pin.expectedTag && bytes.contentEquals(pin.intendedBytes))
        val r=checked(TemplateHttpRequest5("PUT","/upload/drive/v2/files/$objectId?uploadType=media&fields=id",pin.mimeType,pin.intendedBytes,expectedTag))
        if(immutableJson5(r.body).immutableString5("id")!=objectId)throw DeliveryFailure("contract")
    }
    override fun createReserved(step:WorkingFileStep)=immutableSafe5 {
        check(verified);val pin=checkNotNull(pins[step.objectId]);require(pin.expectedTag==null && step.expectedTag==null && pin.folderId==step.folderId &&
            pin.marker==step.marker && pin.name==step.name && pin.mimeType==step.mimeType && pin.intendedHash==step.intendedHash)
        val boundary="gym-template-${java.util.UUID.randomUUID()}"
        val prefix="--$boundary\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n${workingMetadata5(pin)}\r\n--$boundary\r\nContent-Type: ${pin.mimeType}\r\n\r\n".toByteArray()
        val r=checked(TemplateHttpRequest5("POST","/upload/drive/v3/files?uploadType=multipart&fields=id","multipart/related; boundary=$boundary",prefix+pin.intendedBytes+"\r\n--$boundary--\r\n".toByteArray()),setOf(200,409))
        if(r.status==200 && immutableJson5(r.body).immutableString5("id")!=pin.objectId)throw DeliveryFailure("contract")
    }
}
