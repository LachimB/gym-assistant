package dev.gymassistant.drive

import dev.gymassistant.engine.v2.readYaml2
import dev.gymassistant.sync.*
import java.io.Closeable
import java.io.IOException
import java.net.URL
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import javax.net.ssl.HttpsURLConnection
import kotlinx.serialization.json.*

internal const val TEMPLATE_MARKER_KEY_5="gymTemplateM1"
internal const val TEMPLATE_FOLDER_FIELDS_5="id,name,mimeType,parents,appProperties,trashed,ownedByMe"
internal const val TEMPLATE_FILE_FIELDS_5="id,title,mimeType,parents(id),properties(key,value,visibility),labels(trashed),ownedByMe,etag"
private fun handle5(id:String)=require(id.matches(Regex("[A-Za-z0-9_-]{1,160}")))
private fun etag5(tag:String)=require(tag.matches(Regex("\"[\\x21\\x23-\\x7E]{1,1000}\"")))
private val yamlMimes5=setOf("text/plain","text/yaml","application/yaml","application/x-yaml")

internal data class TemplateHttpRequest5(val method:String,val path:String,val contentType:String?=null,val body:ByteArray?=null,val ifMatch:String?=null)
internal fun interface TemplateHttp5 {fun request(request:TemplateHttpRequest5):DeliveryHttpResponse}

/** Dedicated narrow transport; legacy delivery endpoints/permissions are not broadened. */
internal class GoogleTemplateHttp5(@Volatile private var token:String,private val reserveRequest:()->Unit,
    readableIds:Set<String>,writableIds:Set<String>,private val fileFields:String=TEMPLATE_FILE_FIELDS_5,
    private val jsonWritableId:String?=null,private val maxReadableIds:Int=40):TemplateHttp5,Closeable {
    private val reads=readableIds.toSet();private val writes=writableIds.toSet()
    @Volatile private var active:HttpsURLConnection?=null
    init {require(maxReadableIds in 1..64 && reads.size in 1..maxReadableIds && writes.size in 0..16 && reads.containsAll(writes))
        require(writes.isNotEmpty() || jsonWritableId!=null);reads.forEach(::handle5)
        jsonWritableId?.let {handle5(it);require(it in reads && it !in writes)}
        require(fileFields in setOf(TEMPLATE_FILE_FIELDS_5,IMMUTABLE_FILE_FIELDS_5))}
    override fun close(){token="";active?.disconnect()}
    override fun request(request:TemplateHttpRequest5):DeliveryHttpResponse {
        check(token.isNotBlank() && token.none {it.isISOControl()})
        val allowed=when(request.method){
            "GET" -> request.body==null && request.ifMatch==null && request.contentType==null && (request.path==ACCOUNT_PATH || reads.any {id->
                request.path=="/drive/v3/files/$id?fields=$TEMPLATE_FOLDER_FIELDS_5" ||
                request.path=="/drive/v2/files/$id?fields=$fileFields" || request.path=="/drive/v2/files/$id?alt=media"})
            "PUT" -> request.ifMatch!=null && (
                writes.any {request.path=="/upload/drive/v2/files/$it?uploadType=media&fields=id"} && request.contentType in yamlMimes5 && request.body?.size in 1..1048576 ||
                jsonWritableId!=null && request.path=="/upload/drive/v2/files/$jsonWritableId?uploadType=media&fields=id" && request.contentType=="application/json" && request.body?.size in 1..65536)
            "POST" -> request.path=="/upload/drive/v3/files?uploadType=multipart&fields=id" && request.ifMatch==null &&
                request.body?.size in 1..1114112 && request.contentType?.matches(Regex("multipart/related; boundary=gym-template-[A-Za-z0-9-]{1,64}"))==true
            else -> false
        }
        require(allowed) {"Unsupported Template sync endpoint"};request.ifMatch?.let(::etag5)
        reserveRequest() // Counts the attempt before opening a socket, including failure or an uncertain acknowledgement.
        val connection=URL("https://www.googleapis.com${request.path}").openConnection() as HttpsURLConnection
        active=connection
        connection.instanceFollowRedirects=false;connection.useCaches=false
        connection.connectTimeout=10000;connection.readTimeout=10000;connection.requestMethod=request.method
        connection.setRequestProperty("Authorization","Bearer $token")
        request.ifMatch?.let {connection.setRequestProperty("If-Match",it)}
        try {
            request.body?.let {body->connection.doOutput=true;connection.setRequestProperty("Content-Type",checkNotNull(request.contentType))
                connection.setFixedLengthStreamingMode(body.size);connection.outputStream.use {it.write(body)}}
            val code=connection.responseCode
            val retry=retryAfterDelayMs(connection.getHeaderField("Retry-After"),java.time.Instant.now())
            if(code!=200)return DeliveryHttpResponse(code,retryAfterMs=retry)
            val limit=if(request.path.endsWith("?alt=media"))1048576 else 65536
            val bytes=connection.inputStream.use {input->
                val output=java.io.ByteArrayOutputStream();val buffer=ByteArray(8192)
                while(output.size()<=limit){val count=input.read(buffer,0,minOf(buffer.size,limit+1-output.size()));if(count<0)break;output.write(buffer,0,count)}
                output.toByteArray()
            }
            if(bytes.size>limit)throw DeliveryFailure("contract")
            return DeliveryHttpResponse(code,bytes,retry)
        }catch(_:IOException){throw DeliveryFailure("network")}finally{connection.disconnect();active=null}
    }
}

internal data class TemplateFolderPin5(val id:String,val parent:String,val name:String,val markerKey:String,val marker:String) {
    init {handle5(id);handle5(parent);require(id!=parent && name.isNotBlank() && name.toByteArray().size<=4096)
        require(markerKey.matches(Regex("gym[A-Za-z0-9]{1,64}")) && marker.matches(Regex("[A-Za-z0-9_-]{3,160}")))}
}
internal class TemplateNamespace5(val permissionId:String,val commonRootId:String,val workingFolderId:String,folders:List<TemplateFolderPin5>) {
    val folders=folders.toList()
    init {
        handle5(permissionId);handle5(commonRootId);handle5(workingFolderId)
        require(this.folders.size in 3..8 && this.folders.map {it.id}.distinct().size==this.folders.size)
        val byId=this.folders.associateBy {it.id};val root=checkNotNull(byId[commonRootId]);require(root.name=="Gym Assistant" && root.parent !in byId)
        val visited=mutableSetOf<String>();var cursor=workingFolderId
        while(cursor!=commonRootId){require(visited.add(cursor));cursor=checkNotNull(byId[cursor]).parent}
        visited+=commonRootId;require(visited==byId.keys) {"Namespace must be one pinned chain to the common root"}
    }
}

/** A new, closeable transport per serialized batch; only captured folders/file IDs can reach the network. */
internal class TemplateWorkingBatch5(token:String,reserveRequest:()->Unit,namespace:TemplateNamespace5,steps:List<WorkingFileStep>):Closeable {
    private val http=GoogleTemplateHttp5(token,reserveRequest,(namespace.folders.map {it.id}+steps.map {it.objectId}).toSet(),steps.map {it.objectId}.toSet())
    val remote:WorkingFileRemote=GoogleTemplateWorkingRemote5(http,namespace,steps)
    override fun close()=http.close()
}

/** Exact-ID working-file operations only. Folder creation/moves and publication heads remain separate gates. */
internal class GoogleTemplateWorkingRemote5(private val http:TemplateHttp5,private val namespace:TemplateNamespace5,steps:List<WorkingFileStep>):WorkingFileRemote {
    private val pins=steps.associateBy {it.objectId}
    private var namespaceVerified=false
    init {require(steps.size in 1..16 && pins.size==steps.size && steps.all {it.folderId==namespace.workingFolderId})
        require(pins.keys.none {id->namespace.folders.any {it.id==id}})}
    private fun checked(request:TemplateHttpRequest5,allow:Set<Int> = setOf(200)):DeliveryHttpResponse {
        val result=http.request(request)
        if(result.status !in allow)throw DeliveryFailure(when(result.status){401->"authorization";403->"permission";404->"missingRoot";409,412->"conflict";429->"rateLimited";in 500..599->"providerUnavailable";else->"contract"},result.retryAfterMs)
        return result
    }
    private fun json(response:DeliveryHttpResponse):JsonObject=try {
        require(response.body.size in 1..65536)
        val text=Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(response.body)).toString()
        val value=Json.parseToJsonElement(text).jsonObject;readYaml2(text);value
    }catch(_:Exception){throw DeliveryFailure("contract")}
    private fun JsonObject.text(key:String):String {
        val value=this[key] as? JsonPrimitive ?: throw DeliveryFailure("contract")
        if(!value.isString)throw DeliveryFailure("contract");return value.content
    }
    private fun string(value:JsonElement):String {
        val primitive=value as? JsonPrimitive ?: throw DeliveryFailure("contract")
        if(!primitive.isString)throw DeliveryFailure("contract");return primitive.content
    }
    private fun JsonObject.flag(key:String):Boolean {
        val primitive=this[key] as? JsonPrimitive ?: throw DeliveryFailure("contract")
        if(primitive.isString)throw DeliveryFailure("contract")
        return primitive.booleanOrNull ?: throw DeliveryFailure("contract")
    }
    private inline fun <T> safely(block:()->T):T=try{block()}catch(cancelled:kotlinx.coroutines.CancellationException){throw cancelled}
        catch(failure:DeliveryFailure){throw failure}catch(_:Exception){throw DeliveryFailure("contract")}
    override fun verifyNamespace()=safely {
        namespaceVerified=false
        val user=json(checked(TemplateHttpRequest5("GET",ACCOUNT_PATH)))["user"]?.jsonObject ?: throw DeliveryFailure("contract")
        if(user.text("permissionId")!=namespace.permissionId)throw DeliveryFailure("accountMismatch")
        for(pin in namespace.folders){
            val data=json(checked(TemplateHttpRequest5("GET","/drive/v3/files/${pin.id}?fields=$TEMPLATE_FOLDER_FIELDS_5")))
            val parents=data["parents"]?.jsonArray?.map(::string)
            if(data.text("id")!=pin.id || data.text("name")!=pin.name || data.text("mimeType")!="application/vnd.google-apps.folder" ||
                parents!=listOf(pin.parent) || !data.flag("ownedByMe") || data.flag("trashed") ||
                data["appProperties"]?.jsonObject?.text(pin.markerKey)!=pin.marker)throw DeliveryFailure("missingRoot")
        }
        namespaceVerified=true
    }
    private fun metadata(id:String,allowMissing:Boolean):JsonObject? {
        checkNotNull(pins[id]) {"File is outside the immutable working-file intent"}
        val response=checked(TemplateHttpRequest5("GET","/drive/v2/files/$id?fields=$TEMPLATE_FILE_FIELDS_5"),if(allowMissing)setOf(200,404)else setOf(200))
        return if(response.status==404)null else json(response)
    }
    private fun snapshot(id:String,data:JsonObject,bytes:ByteArray):WorkingFileSnapshot {
        val pin=checkNotNull(pins[id]);val parents=data["parents"]?.jsonArray?.map {it.jsonObject.text("id")}.orEmpty()
        val props=data["properties"]?.jsonArray?.map {it.jsonObject}.orEmpty()
        val markers=props.filter {it.text("key")==TEMPLATE_MARKER_KEY_5 && it.text("visibility")=="PRIVATE"}
        val tag=data.text("etag");try{etag5(tag)}catch(_:Exception){throw DeliveryFailure("contract")}
        if(data.text("id")!=id || data.text("title")!=pin.name || data.text("mimeType")!=pin.mimeType || parents!=listOf(pin.folderId) ||
            !data.flag("ownedByMe") || data["labels"]?.jsonObject?.flag("trashed")!=false ||
            markers.size!=1 || markers.single().text("value")!=pin.marker)throw DeliveryFailure("conflict")
        return WorkingFileSnapshot(id,parents.toSet(),pin.marker,pin.name,pin.mimeType,tag,false,bytes)
    }
    override fun read(objectId:String):WorkingFileSnapshot?=safely {
        check(namespaceVerified) {"Verify the pinned account and namespace before reading source"}
        val first=metadata(objectId,true) ?: return@safely null
        val before=snapshot(objectId,first,byteArrayOf())
        val bytes=checked(TemplateHttpRequest5("GET","/drive/v2/files/$objectId?alt=media")).body
        if(bytes.size !in 1..1048576)throw DeliveryFailure("contract")
        val last=snapshot(objectId,checkNotNull(metadata(objectId,false)),bytes)
        if(before.tag!=last.tag)throw DeliveryFailure("conflict")
        last
    }
    override fun replace(objectId:String,expectedTag:String,bytes:ByteArray)=safely {
        check(namespaceVerified) {"Verify the pinned account and namespace before writing source"}
        val pin=checkNotNull(pins[objectId]);etag5(expectedTag)
        require(expectedTag==pin.expectedTag && bytes.size in 1..1048576 && hashSourceBytes(bytes)==pin.intendedHash)
        val result=checked(TemplateHttpRequest5("PUT","/upload/drive/v2/files/$objectId?uploadType=media&fields=id",pin.mimeType,bytes,expectedTag))
        if(json(result).text("id")!=objectId)throw DeliveryFailure("contract")
    }
    override fun createReserved(step:WorkingFileStep)=safely {
        check(namespaceVerified) {"Verify the pinned account and namespace before creating source"}
        val pin=checkNotNull(pins[step.objectId]);require(pin.expectedTag==null && pin.beforeHash==null)
        require(step.folderId==pin.folderId && step.name==pin.name && step.marker==pin.marker && step.mimeType==pin.mimeType && step.intendedHash==pin.intendedHash)
        val boundary="gym-template-${java.util.UUID.randomUUID()}"
        val metadata=buildJsonObject {put("id",pin.objectId);put("name",pin.name);put("mimeType",pin.mimeType)
            put("parents",buildJsonArray {add(pin.folderId)});put("appProperties",buildJsonObject {put(TEMPLATE_MARKER_KEY_5,pin.marker)})}
        val prefix="--$boundary\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n$metadata\r\n--$boundary\r\nContent-Type: ${pin.mimeType}\r\n\r\n".toByteArray(Charsets.UTF_8)
        val response=checked(TemplateHttpRequest5("POST","/upload/drive/v3/files?uploadType=multipart&fields=id","multipart/related; boundary=$boundary",prefix+pin.intendedBytes+"\r\n--$boundary--\r\n".toByteArray()),setOf(200,409))
        if(response.status==200 && json(response).text("id")!=pin.objectId)throw DeliveryFailure("contract")
        // GuardedWorkingFile must verify the same reserved ID even after409 or an uncertain acknowledgement.
    }
}
