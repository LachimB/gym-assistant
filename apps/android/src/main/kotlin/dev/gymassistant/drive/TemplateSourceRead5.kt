package dev.gymassistant.drive

import dev.gymassistant.session.CheckedSourceCapture5
import dev.gymassistant.session.SourceCapturePin5
import dev.gymassistant.session.TemplateSyncContracts5
import dev.gymassistant.sync.PublicationHeadSnapshot
import java.io.Closeable
import kotlinx.serialization.json.*

/** Known IDs only. No listing, discovery, revision-folder creation or name-based resolution. */
internal class TemplateSourceReadScope5(val capture:CheckedSourceCapture5,folders:List<TemplateFolderPin5>) {
    val folders=folders.associateBy {it.id};val pins=capture.pins.associateBy {it.objectId}
    val readableIds:Set<String>
    init {
        val n=capture.namespace;require(this.folders.size==folders.size)
        require(pins.size==capture.pins.size && (pins.isEmpty() || pins.size in 2..16 && pins.values.count {it.role=="template"}==1))
        require(pins.values.map {it.relativeName}.distinct().size==pins.size)
        val leaves=setOf(n.workingFolderId,n.historyFolderId,n.metadataFolderId);val prefix=this.folders-leaves
        TemplateNamespace5(n.permissionId,n.commonRootId,n.templateFolderId,prefix.values.toList())
        require(n.areaFolderId in prefix && prefix.getValue(n.commonRootId).name=="Gym Assistant" && prefix.getValue(n.commonRootId).markerKey=="gymProjectM1")
        for((id,name) in listOf(n.workingFolderId to "Current working copy",n.historyFolderId to "History",n.metadataFolderId to "Sync metadata")) {
            val pin=this.folders.getValue(id);require(pin.parent==n.templateFolderId && pin.name==name && pin.markerKey==TEMPLATE_MARKER_KEY_5 && pin.marker==n.marker)
        }
        val selected=prefix.getValue(n.templateFolderId);require(selected.markerKey==TEMPLATE_MARKER_KEY_5 && selected.marker==n.marker)
        val ids=this.folders.keys+pins.keys+n.headFileId;require(ids.size==folders.size+pins.size+1 && ids.size<=40)
        readableIds=ids
    }
}
internal class SourceReadObject5(val pin:SourceCapturePin5,val tag:String,metadata:ByteArray,content:ByteArray) {
    private val metadata=metadata.copyOf();private val content=content.copyOf()
    val metadataBytes get()=metadata.copyOf()
    val contentBytes get()=content.copyOf()
}
internal interface TemplateSourceReader5 {
    fun verifyNamespace()
    fun readHead():PublicationHeadSnapshot?
    fun readSource(objectId:String):SourceReadObject5?
}
/** The inner generic transport is never exposed; this profile permits only captured GETs. */
internal class GoogleSourceReadHttp5(token:String,reserveRequest:()->Unit,private val scope:TemplateSourceReadScope5):TemplateHttp5,Closeable {
    private val n=scope.capture.namespace
    private val http=GoogleTemplateHttp5(token,reserveRequest,scope.readableIds,emptySet(),IMMUTABLE_FILE_FIELDS_5,n.headFileId)
    override fun close()=http.close()
    override fun request(request:TemplateHttpRequest5):DeliveryHttpResponse=immutableSafe5 {
        require(request.method=="GET" && (request.path==ACCOUNT_PATH || scope.readableIds.any {request.path=="/drive/v2/files/$it?fields=$IMMUTABLE_FILE_FIELDS_5"} ||
            (scope.pins.keys+n.headFileId).any {request.path=="/drive/v2/files/$it?alt=media"}))
        http.request(request)
    }
}
internal class TemplateSourceReadBatch5(token:String,reserveRequest:()->Unit,scope:TemplateSourceReadScope5,retain:(RemoteObjectCapture5)->Unit,
    private val testTransport:TemplateHttp5?=null):Closeable {
    private val production=if(testTransport==null)GoogleSourceReadHttp5(token,reserveRequest,scope) else null
    private val http=production?:TemplateHttp5 {request->reserveRequest();checkNotNull(testTransport).request(request)}
    val reader:TemplateSourceReader5=GoogleSourceReader5(http,scope,retain)
    override fun close(){try{(testTransport as? Closeable)?.close()}finally{production?.close()}}
}
internal class GoogleSourceReader5(private val http:TemplateHttp5,private val scope:TemplateSourceReadScope5,
    private val retain:(RemoteObjectCapture5)->Unit):TemplateSourceReader5 {
    private val n=scope.capture.namespace;private var verified=false
    private class ObjectRead(val tag:String,val metadata:ByteArray,val content:ByteArray)
    private fun checked(request:TemplateHttpRequest5,allow:Set<Int> = setOf(200)):DeliveryHttpResponse {
        val r=http.request(request)
        if(r.status !in allow)throw DeliveryFailure(when(r.status){401->"authorization";403->"permission";404->"missingRoot";409,412->"conflict";429->"rateLimited";in 500..599->"providerUnavailable";else->"contract"},r.retryAfterMs)
        return r
    }
    private fun metadata(id:String,kind:RemoteObjectKind5):ByteArray? {
        val r=checked(TemplateHttpRequest5("GET","/drive/v2/files/$id?fields=$IMMUTABLE_FILE_FIELDS_5"),setOf(200,404))
        val bytes=if(r.status==404)null else r.body.copyOf()
        retain(RemoteObjectCapture5(kind,id,r.status,bytes,null));return bytes
    }
    override fun verifyNamespace()=immutableSafe5 {
        verified=false
        val user=immutableJson5(checked(TemplateHttpRequest5("GET",ACCOUNT_PATH)).body)["user"]?.jsonObject ?: error("account")
        if(user.immutableString5("permissionId")!=n.permissionId)throw DeliveryFailure("accountMismatch")
        for(pin in scope.folders.values) {
            val raw=metadata(pin.id,RemoteObjectKind5.NAMESPACE) ?: throw DeliveryFailure("missingRoot")
            checkedTemplateObject5(immutableJson5(raw),n.permissionId,pin.id,pin.parent,pin.name,"application/vnd.google-apps.folder",pin.markerKey,pin.marker)
        }
        verified=true
    }
    private fun readObject(id:String,parent:String,name:String,mime:String,kind:RemoteObjectKind5,limit:Int):ObjectRead? {
        check(verified)
        val before=metadata(id,kind) ?: return null
        fun tag(raw:ByteArray)=checkedTemplateObject5(immutableJson5(raw),n.permissionId,id,parent,name,mime,TEMPLATE_MARKER_KEY_5,n.marker)
        val tag=tag(before);val content=checked(TemplateHttpRequest5("GET","/drive/v2/files/$id?alt=media")).body.copyOf()
        retain(RemoteObjectCapture5(kind,id,200,before,content));immutableText5(content,limit)
        val after=metadata(id,kind) ?: throw DeliveryFailure("conflict");if(tag(after)!=tag)throw DeliveryFailure("conflict")
        return ObjectRead(tag,before,content)
    }
    override fun readHead():PublicationHeadSnapshot?=immutableSafe5 {
        val r=readObject(n.headFileId,n.metadataFolderId,"head.json","application/json",RemoteObjectKind5.HEAD,65536) ?: return@immutableSafe5 null
        val head=TemplateSyncContracts5.head(immutableText5(r.content,65536))
        if(head.templateId!=scope.capture.local.templateId || head.workingFolderId!=n.workingFolderId)throw DeliveryFailure("conflict")
        PublicationHeadSnapshot(n.headFileId,setOf(n.metadataFolderId),n.marker,"head.json","application/json",r.tag,false,head,r.metadata,r.content)
    }
    override fun readSource(objectId:String):SourceReadObject5?=immutableSafe5 {
        val pin=checkNotNull(scope.pins[objectId]);val r=readObject(objectId,n.workingFolderId,pin.relativeName,pin.mimeType,RemoteObjectKind5.WORKING,1048576) ?: return@immutableSafe5 null
        SourceReadObject5(pin,r.tag,r.metadata,r.content)
    }
}
