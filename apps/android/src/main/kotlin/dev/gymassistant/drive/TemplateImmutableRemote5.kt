package dev.gymassistant.drive

import dev.gymassistant.engine.v2.*
import dev.gymassistant.session.TemplateSyncContracts5
import dev.gymassistant.sync.*
import java.io.Closeable
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import kotlinx.serialization.json.*

internal const val IMMUTABLE_FILE_FIELDS_5=TEMPLATE_FILE_FIELDS_5+",shared,permissions(id,type,role,deleted)"
internal fun immutableText5(bytes:ByteArray,limit:Int):String {
    require(bytes.size in 1..limit)
    return Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
}
internal fun immutableJson5(bytes:ByteArray):JsonObject {
    val text=immutableText5(bytes,65536);readYaml2(text)
    return Json.parseToJsonElement(text).jsonObject
}
internal fun JsonObject.immutableString5(key:String):String {
    val value=this[key] as? JsonPrimitive ?: error("scalar")
    require(value.isString);return value.content
}
internal fun JsonObject.immutableFlag5(key:String):Boolean {
    val value=this[key] as? JsonPrimitive ?: error("scalar")
    require(!value.isString);return checkNotNull(value.booleanOrNull)
}
internal fun immutableTag5(tag:String)=require(tag.matches(Regex("\"[\\x21\\x23-\\x7E]{1,1000}\"")))
internal inline fun <T> immutableSafe5(block:()->T):T=try{block()}catch(cancelled:kotlinx.coroutines.CancellationException){throw cancelled}
    catch(failure:DeliveryFailure){throw failure}catch(_:Exception){throw DeliveryFailure("contract")}

internal fun checkedTemplateObject5(data:JsonObject,permissionId:String,id:String,parent:String,name:String,mime:String,key:String,marker:String):String {
    val parents=data["parents"]?.jsonArray?.map {it.jsonObject.immutableString5("id")}
    val properties=data["properties"]?.jsonArray?.map {it.jsonObject}?.filter {it.immutableString5("key")==key && it.immutableString5("visibility")=="PRIVATE"}
    val permissions=data["permissions"]?.jsonArray?.map {it.jsonObject}
    if(data.immutableString5("id")!=id || data.immutableString5("title")!=name || data.immutableString5("mimeType")!=mime ||
        parents!=listOf(parent) || !data.immutableFlag5("ownedByMe") || data.immutableFlag5("shared") ||
        data["labels"]?.jsonObject?.immutableFlag5("trashed")!=false || properties?.size!=1 || properties.single().immutableString5("value")!=marker || permissions?.size!=1)throw DeliveryFailure("conflict")
    val owner=permissions.single()
    if(owner.immutableString5("id")!=permissionId || owner.immutableString5("type")!="user" || owner.immutableString5("role")!="owner" ||
        "deleted" in owner && owner.immutableFlag5("deleted"))throw DeliveryFailure("permission")
    return data.immutableString5("etag").also(::immutableTag5)
}

/** Exact reviewed chain from the common root to an immutable revision folder. No folder creation. */
internal class ImmutableRevisionNamespace5(val permissionId:String,val commonRootId:String,val revisionFolderId:String,folders:List<TemplateFolderPin5>) {
    // Reuse the existing bounded-chain validator, independently of the working-folder binding.
    val folders=TemplateNamespace5(permissionId,commonRootId,revisionFolderId,folders).folders
}

/** Full compilation precedes network access. Reserved immutable source IDs cannot overlap namespace IDs. */
internal class ImmutableRevisionFiles5(val namespace:ImmutableRevisionNamespace5,manifest:TemplateRevisionManifest,
    manifestObjectId:String,val marker:String,payloads:Map<String,ByteArray>,contracts:ImportContracts2) {
    val files:List<ImmutableFileStep>
    val manifestFile:ImmutableFileStep
    init {
        val retained=payloads.mapValues {it.value.copyOf()}
        TemplateSyncContracts5.bundle(manifest,retained,contracts)
        val allIds=manifest.files.map {it.objectId}+manifestObjectId
        require(allIds.distinct().size==allIds.size && allIds.none {id->namespace.folders.any {it.id==id}})
        files=manifest.files.map {ImmutableFileStep(it.objectId,namespace.revisionFolderId,marker,it.relativeName,"text/plain",retained.getValue(it.objectId))}
        manifestFile=ImmutableFileStep(manifestObjectId,namespace.revisionFolderId,marker,"revision.json","application/json",TemplateSyncContracts5.encode(manifest).toByteArray())
    }
    val objects:List<ImmutableFileStep> get()=files+manifestFile
}

private fun immutableCreateMetadata5(pin:ImmutableFileStep)=buildJsonObject {
    put("id",pin.objectId);put("name",pin.name);put("mimeType",pin.mimeType)
    put("parents",buildJsonArray {add(pin.folderId)});put("appProperties",buildJsonObject {put(TEMPLATE_MARKER_KEY_5,pin.marker)})
}

/** Additional immutable-only gate before the shared fixed-origin socket transport: PUT/PATCH/DELETE are absent. */
internal class GoogleImmutableHttp5(token:String,reserveRequest:()->Unit,revision:ImmutableRevisionFiles5):TemplateHttp5,Closeable {
    private val objects=revision.objects.associateBy {it.objectId}
    private val metadataIds=(objects.keys+revision.namespace.folders.map {it.id}).toSet()
    private val delegate=GoogleTemplateHttp5(token,reserveRequest,metadataIds,setOf(revision.manifestFile.objectId),IMMUTABLE_FILE_FIELDS_5)
    override fun close()=delegate.close()
    override fun request(request:TemplateHttpRequest5):DeliveryHttpResponse=immutableSafe5 {
        val own=request.copy(body=request.body?.copyOf())
        when(own.method){
            "GET" -> require(own.path==ACCOUNT_PATH || metadataIds.any {own.path=="/drive/v2/files/$it?fields=$IMMUTABLE_FILE_FIELDS_5"} ||
                objects.keys.any {own.path=="/drive/v2/files/$it?alt=media"})
            "POST" -> {
                require(own.path=="/upload/drive/v3/files?uploadType=multipart&fields=id" && own.ifMatch==null)
                val contentType=checkNotNull(own.contentType)
                require(contentType.matches(Regex("multipart/related; boundary=gym-template-[A-Za-z0-9-]{1,64}")))
                val boundary=contentType.substringAfter("boundary=")
                val prefix="--$boundary\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n"
                val body=checkNotNull(own.body);val text=immutableText5(body,1114112);require(text.startsWith(prefix))
                val end=text.indexOf("\r\n--$boundary\r\n",prefix.length);require(end>prefix.length)
                val raw=text.substring(prefix.length,end);val metadata=immutableJson5(raw.toByteArray())
                val pin=checkNotNull(objects[metadata.immutableString5("id")]);require(metadata==immutableCreateMetadata5(pin))
                val expected=(prefix+raw+"\r\n--$boundary\r\nContent-Type: ${pin.mimeType}\r\n\r\n").toByteArray()+pin.bytes+"\r\n--$boundary--\r\n".toByteArray()
                require(body.contentEquals(expected)) {"Reserved immutable body differs"}
            }
            else -> error("Immutable replacement is forbidden")
        }
        delegate.request(own)
    }
}

internal class TemplateImmutableBatch5(token:String,reserveRequest:()->Unit,revision:ImmutableRevisionFiles5):Closeable {
    private val http=GoogleImmutableHttp5(token,reserveRequest,revision)
    val remote:ImmutableFileRemote=GoogleTemplateImmutableRemote5(http,revision)
    override fun close()=http.close()
}

/** Creates only captured immutable objects. Same-ID409/lost-ack recovery belongs to GuardedImmutableFile. */
internal class GoogleTemplateImmutableRemote5(private val http:TemplateHttp5,revision:ImmutableRevisionFiles5):ImmutableFileRemote {
    private val namespace=revision.namespace
    private val pins=revision.objects.associateBy {it.objectId}
    private var verified=false
    private fun checked(request:TemplateHttpRequest5,allow:Set<Int> = setOf(200)):DeliveryHttpResponse {
        val result=http.request(request)
        if(result.status !in allow)throw DeliveryFailure(when(result.status){401->"authorization";403->"permission";404->"missingRoot";409,412->"conflict";429->"rateLimited";in 500..599->"providerUnavailable";else->"contract"},result.retryAfterMs)
        return result
    }
    private fun metadata(id:String,missing:Boolean=false):JsonObject? {
        val result=checked(TemplateHttpRequest5("GET","/drive/v2/files/$id?fields=$IMMUTABLE_FILE_FIELDS_5"),if(missing)setOf(200,404)else setOf(200))
        return if(result.status==404)null else immutableJson5(result.body)
    }
    private fun namespaceObject(data:JsonObject,id:String,parent:String,name:String,mime:String,key:String,marker:String):String {
        return checkedTemplateObject5(data,namespace.permissionId,id,parent,name,mime,key,marker)
    }
    override fun verifyNamespace()=immutableSafe5 {
        verified=false
        val user=immutableJson5(checked(TemplateHttpRequest5("GET",ACCOUNT_PATH)).body)["user"]?.jsonObject ?: error("account")
        if(user.immutableString5("permissionId")!=namespace.permissionId)throw DeliveryFailure("accountMismatch")
        for(pin in namespace.folders)namespaceObject(checkNotNull(metadata(pin.id)),pin.id,pin.parent,pin.name,"application/vnd.google-apps.folder",pin.markerKey,pin.marker)
        verified=true
    }
    private fun fileTag(pin:ImmutableFileStep,data:JsonObject)=namespaceObject(data,pin.objectId,pin.folderId,pin.name,pin.mimeType,TEMPLATE_MARKER_KEY_5,pin.marker)
    override fun read(objectId:String):ImmutableFileSnapshot?=immutableSafe5 {
        check(verified);val pin=checkNotNull(pins[objectId]);val before=metadata(objectId,true) ?: return@immutableSafe5 null
        val tag=fileTag(pin,before)
        val bytes=checked(TemplateHttpRequest5("GET","/drive/v2/files/$objectId?alt=media")).body
        require(bytes.size in 1..pin.byteCount);immutableText5(bytes,pin.byteCount)
        val after=fileTag(pin,checkNotNull(metadata(objectId)));if(tag!=after)throw DeliveryFailure("conflict")
        ImmutableFileSnapshot(objectId,setOf(pin.folderId),pin.marker,pin.name,pin.mimeType,tag,false,bytes)
    }
    override fun createReserved(step:ImmutableFileStep)=immutableSafe5 {
        check(verified);val pin=checkNotNull(pins[step.objectId])
        require(step.folderId==pin.folderId && step.marker==pin.marker && step.name==pin.name && step.mimeType==pin.mimeType && step.byteCount==pin.byteCount && step.sha256==pin.sha256)
        val boundary="gym-template-${java.util.UUID.randomUUID()}"
        val prefix="--$boundary\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n${immutableCreateMetadata5(pin)}\r\n--$boundary\r\nContent-Type: ${pin.mimeType}\r\n\r\n".toByteArray()
        val result=checked(TemplateHttpRequest5("POST","/upload/drive/v3/files?uploadType=multipart&fields=id","multipart/related; boundary=$boundary",prefix+pin.bytes+"\r\n--$boundary--\r\n".toByteArray()),setOf(200,409))
        if(result.status==200 && immutableJson5(result.body).immutableString5("id")!=pin.objectId)throw DeliveryFailure("contract")
    }
}
