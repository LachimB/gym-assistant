package dev.gymassistant.drive

import dev.gymassistant.engine.v2.ImportContracts2
import dev.gymassistant.session.TemplateSyncContracts5
import dev.gymassistant.session.WorkingReceipt5
import dev.gymassistant.sync.*
import java.io.Closeable
import kotlinx.serialization.json.*

internal enum class RemoteObjectKind5 { HEAD, WORKING, IMMUTABLE, NAMESPACE }
/** The sink retains original bounded bytes even when decoding or validation subsequently fails. */
internal class RemoteObjectCapture5(val kind:RemoteObjectKind5,val objectId:String,val status:Int,metadata:ByteArray?,content:ByteArray?) {
    private val originalMetadata=metadata?.also {require(it.size<=65536)}?.copyOf()
    private val originalContent=content?.also {require(it.size<=1048576)}?.copyOf()
    val metadataBytes get()=originalMetadata?.copyOf()
    val contentBytes get()=originalContent?.copyOf()
    init {require(objectId.matches(Regex("[A-Za-z0-9_-]{1,160}")) && status in setOf(200,404))
        require(if(status==404)metadata==null && content==null else metadata!=null)}
}

internal class TemplatePublicationScope5(val intent:PublicationHeadIntent,val revision:ImmutableRevisionFiles5,
    val workingNamespace:TemplateNamespace5,val metadataNamespace:ImmutableRevisionNamespace5,
    steps:List<WorkingFileStep>,receipts:List<WorkingReceipt5>,val contracts:ImportContracts2,requireCompleteReceipts:Boolean=true) {
    val workingSteps=steps.toList();val workingReceipts=receipts.associateBy {it.objectId}
    val immutableSteps=revision.objects.associateBy {it.objectId}
    val manifest=TemplateSyncContracts5.manifest(immutableText5(revision.manifestFile.bytes,65536))
    val folders:Map<String,TemplateFolderPin5>
    val readableIds:Set<String>
    val completeWorkingReceipts get()=workingReceipts.size==workingSteps.size
    init {
        val r=revision.namespace;val w=workingNamespace;val m=metadataNamespace
        require(r.permissionId==w.permissionId && r.permissionId==m.permissionId && r.commonRootId==w.commonRootId && r.commonRootId==m.commonRootId)
        require(intent.metadataFolderId==m.revisionFolderId && intent.intended.workingFolderId==w.workingFolderId &&
            intent.intended.revisionManifestId==revision.manifestFile.objectId && intent.intended.templateId==manifest.templateId &&
            intent.intended.sourceHash==manifest.sourceHash && intent.marker==revision.marker)
        val rm=r.folders.associateBy {it.id};val wm=w.folders.associateBy {it.id};val mm=m.folders.associateBy {it.id}
        val meta=mm.getValue(m.revisionFolderId);val work=wm.getValue(w.workingFolderId)
        val revisionFolder=rm.getValue(r.revisionFolderId);val history=rm.getValue(revisionFolder.parent)
        require(meta.name=="Sync metadata" && work.name=="Current working copy" && history.name=="History")
        val prefix=mm-meta.id
        require(work.parent==meta.parent && history.parent==meta.parent && wm-work.id==prefix && rm-revisionFolder.id-history.id==prefix)
        for(pin in listOf(prefix.getValue(meta.parent),work,meta,history,revisionFolder))
            require(pin.markerKey==TEMPLATE_MARKER_KEY_5 && pin.marker==intent.marker)
        folders=prefix+listOf(work,meta,history,revisionFolder).associateBy {it.id}
        require(folders.size==prefix.size+4)
        require(steps.size==manifest.files.size && steps.map {it.objectId}.distinct().size==steps.size && receipts.size<=steps.size && workingReceipts.size==receipts.size)
        require(workingReceipts.keys.all {id->steps.any {it.objectId==id}} && (!requireCompleteReceipts || completeWorkingReceipts))
        val descriptors=manifest.files.associateBy {it.relativeName}
        require(steps.map {it.name}.toSet()==descriptors.keys)
        for(step in steps){val d=descriptors.getValue(step.name);val receipt=workingReceipts[step.objectId]
            require(step.folderId==w.workingFolderId && step.marker==intent.marker && step.intendedHash==d.sha256 && step.intendedBytes.size==d.byteCount)
            receipt?.let {require(it.sha256==d.sha256);immutableTag5(it.tag)}}
        intent.expectedTag?.let(::immutableTag5)
        val ids=folders.keys+immutableSteps.keys+steps.map {it.objectId}+intent.objectId
        require(ids.size==folders.size+immutableSteps.size+steps.size+1)
        readableIds=ids.toSet();require(readableIds.size<=64)
    }
}

private fun headMetadata5(scope:TemplatePublicationScope5)=buildJsonObject {
    put("id",scope.intent.objectId);put("name","head.json");put("mimeType","application/json")
    put("parents",buildJsonArray {add(scope.intent.metadataFolderId)})
    put("appProperties",buildJsonObject {put(TEMPLATE_MARKER_KEY_5,scope.intent.marker)})
}

/** Captured head bytes are the sole mutable target. Other registered objects are read-only. */
internal class GooglePublicationHttp5(token:String,reserveRequest:()->Unit,private val scope:TemplatePublicationScope5):TemplateHttp5,Closeable {
    init {require(scope.completeWorkingReceipts)}
    private val bytes=TemplateSyncContracts5.encode(scope.intent.intended).toByteArray()
    private val delegate=GoogleTemplateHttp5(token,reserveRequest,scope.readableIds,emptySet(),IMMUTABLE_FILE_FIELDS_5,scope.intent.objectId,64)
    override fun close()=delegate.close()
    override fun request(request:TemplateHttpRequest5):DeliveryHttpResponse=immutableSafe5 {
        val own=request.copy(body=request.body?.copyOf())
        when(own.method){
            "GET" -> require(own.path==ACCOUNT_PATH || scope.readableIds.any {own.path=="/drive/v2/files/$it?fields=$IMMUTABLE_FILE_FIELDS_5"} ||
                (scope.immutableSteps.keys+scope.workingSteps.map {it.objectId}+scope.intent.objectId).any {own.path=="/drive/v2/files/$it?alt=media"})
            "PUT" -> require(scope.intent.expectedTag!=null && own.path=="/upload/drive/v2/files/${scope.intent.objectId}?uploadType=media&fields=id" &&
                own.contentType=="application/json" && own.ifMatch==scope.intent.expectedTag && own.body?.contentEquals(bytes)==true)
            "POST" -> {
                require(scope.intent.expected==null && own.path=="/upload/drive/v3/files?uploadType=multipart&fields=id" && own.ifMatch==null)
                val type=checkNotNull(own.contentType);require(type.matches(Regex("multipart/related; boundary=gym-template-[A-Za-z0-9-]{1,64}")))
                val boundary=type.substringAfter("boundary=");val prefix="--$boundary\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n"
                val body=checkNotNull(own.body);val text=immutableText5(body,131072);require(text.startsWith(prefix))
                val end=text.indexOf("\r\n--$boundary\r\n",prefix.length);require(end>prefix.length)
                val raw=text.substring(prefix.length,end);require(immutableJson5(raw.toByteArray())==headMetadata5(scope))
                val expected=(prefix+raw+"\r\n--$boundary\r\nContent-Type: application/json\r\n\r\n").toByteArray()+bytes+"\r\n--$boundary--\r\n".toByteArray()
                require(body.contentEquals(expected))
            }
            else -> error("Unsupported publication route")
        }
        delegate.request(own)
    }
}

internal class TemplatePublicationBatch5(token:String,reserveRequest:()->Unit,scope:TemplatePublicationScope5,
    retain:(RemoteObjectCapture5)->Unit,transport:TemplateHttp5?=null):Closeable {
    private val http=transport?:GooglePublicationHttp5(token,reserveRequest,scope)
    val remote:PublicationHeadRemote=GoogleTemplatePublicationRemote5(http,scope,retain)
    override fun close(){(http as? Closeable)?.close()}
}

/** Read-only head preflight before history/working mutations; incomplete receipts cannot open a writer. */
internal class TemplateHeadReadBatch5(token:String,reserveRequest:()->Unit,scope:TemplatePublicationScope5,
    retain:(RemoteObjectCapture5)->Unit,private val transport:TemplateHttp5?=null):Closeable {
    private val delegate=if(transport==null)GoogleTemplateHttp5(token,reserveRequest,scope.folders.keys+scope.intent.objectId,emptySet(),
        IMMUTABLE_FILE_FIELDS_5,scope.intent.objectId) else null
    private val http=transport?:TemplateHttp5 {request->
        require(request.method=="GET" && (request.path==ACCOUNT_PATH || (scope.folders.keys+scope.intent.objectId).any {
            request.path=="/drive/v2/files/$it?fields=$IMMUTABLE_FILE_FIELDS_5"} || request.path=="/drive/v2/files/${scope.intent.objectId}?alt=media"))
        checkNotNull(delegate).request(request)
    }
    val remote:PublicationHeadRemote=GoogleTemplatePublicationRemote5(http,scope,retain,true)
    override fun close(){try{(transport as? Closeable)?.close()}finally{delegate?.close()}}
}

internal class GoogleTemplatePublicationRemote5(private val http:TemplateHttp5,private val scope:TemplatePublicationScope5,
    private val retain:(RemoteObjectCapture5)->Unit,private val readOnly:Boolean):PublicationHeadRemote {
    constructor(http:TemplateHttp5,scope:TemplatePublicationScope5,retain:(RemoteObjectCapture5)->Unit):this(http,scope,retain,false)
    init {require(readOnly || scope.completeWorkingReceipts)}
    private var verified=false;private var immutableReady=false;private var workingReady=false
    private class ObjectBytes(val tag:String,val metadata:ByteArray,val content:ByteArray)
    private fun checked(request:TemplateHttpRequest5,allow:Set<Int> = setOf(200)):DeliveryHttpResponse {
        val r=http.request(request)
        if(r.status !in allow)throw DeliveryFailure(when(r.status){401->"authorization";403->"permission";404->"missingRoot";409,412->"conflict";429->"rateLimited";in 500..599->"providerUnavailable";else->"contract"},r.retryAfterMs)
        return r
    }
    private fun metadata(id:String,kind:RemoteObjectKind5,missing:Boolean):ByteArray? {
        val r=checked(TemplateHttpRequest5("GET","/drive/v2/files/$id?fields=$IMMUTABLE_FILE_FIELDS_5"),if(missing)setOf(200,404)else setOf(200))
        if(r.status==404){retain(RemoteObjectCapture5(kind,id,404,null,null));return null}
        retain(RemoteObjectCapture5(kind,id,200,r.body,null));return r.body
    }
    private fun tag(raw:ByteArray,id:String,parent:String,name:String,mime:String,key:String,marker:String)=
        checkedTemplateObject5(immutableJson5(raw),scope.revision.namespace.permissionId,id,parent,name,mime,key,marker)
    private fun readObject(id:String,parent:String,name:String,mime:String,kind:RemoteObjectKind5):ObjectBytes? {
        check(verified)
        val before=metadata(id,kind,true) ?: return null
        val prior=tag(before,id,parent,name,mime,TEMPLATE_MARKER_KEY_5,scope.intent.marker)
        val content=checked(TemplateHttpRequest5("GET","/drive/v2/files/$id?alt=media")).body
        retain(RemoteObjectCapture5(kind,id,200,before,content))
        val after=checkNotNull(metadata(id,kind,false));if(tag(after,id,parent,name,mime,TEMPLATE_MARKER_KEY_5,scope.intent.marker)!=prior)throw DeliveryFailure("conflict")
        return ObjectBytes(prior,before,content)
    }
    override fun verifyNamespace()=immutableSafe5 {
        verified=false
        try {
            val user=immutableJson5(checked(TemplateHttpRequest5("GET",ACCOUNT_PATH)).body)["user"]?.jsonObject ?: error("account")
            if(user.immutableString5("permissionId")!=scope.revision.namespace.permissionId)throw DeliveryFailure("accountMismatch")
            for(pin in scope.folders.values){val raw=checkNotNull(metadata(pin.id,RemoteObjectKind5.NAMESPACE,false))
                tag(raw,pin.id,pin.parent,pin.name,"application/vnd.google-apps.folder",pin.markerKey,pin.marker)}
            verified=true
        }catch(error:Exception){immutableReady=false;workingReady=false;throw error}
    }
    override fun read(objectId:String):PublicationHeadSnapshot?=immutableSafe5 {
        require(objectId==scope.intent.objectId)
        val read=readObject(objectId,scope.intent.metadataFolderId,"head.json","application/json",RemoteObjectKind5.HEAD) ?: return@immutableSafe5 null
        PublicationHeadSnapshot(objectId,setOf(scope.intent.metadataFolderId),scope.intent.marker,"head.json","application/json",read.tag,false,
            TemplateSyncContracts5.head(immutableText5(read.content,65536)),read.metadata,read.content)
    }
    override fun verifyImmutablePublication(head:TemplatePublicationHead)=immutableSafe5 {
        check(verified);require(head==scope.intent.intended);immutableReady=false
        val payloads=mutableMapOf<String,ByteArray>();var manifest:TemplateRevisionManifest?=null
        for(pin in scope.revision.objects){
            val read=checkNotNull(readObject(pin.objectId,pin.folderId,pin.name,pin.mimeType,RemoteObjectKind5.IMMUTABLE))
            require(read.content.size==pin.byteCount && hashSourceBytes(read.content)==pin.sha256)
            if(pin.objectId==head.revisionManifestId)manifest=TemplateSyncContracts5.manifest(immutableText5(read.content,65536))
            else payloads[pin.objectId]=read.content
        }
        TemplateSyncContracts5.bundle(checkNotNull(manifest),payloads,scope.contracts);immutableReady=true
    }
    override fun workingVectorMatches():Boolean=immutableSafe5 {
        check(verified);workingReady=false;var matches=true;var total=0L
        for(pin in scope.workingSteps){
            val read=readObject(pin.objectId,pin.folderId,pin.name,pin.mimeType,RemoteObjectKind5.WORKING)
            if(read==null){matches=false;continue}
            total+=read.content.size;require(read.content.size in 1..1048576 && total<=8388608)
            immutableText5(read.content,1048576)
            val receipt=scope.workingReceipts.getValue(pin.objectId)
            if(read.tag!=receipt.tag || hashSourceBytes(read.content)!=receipt.sha256)matches=false
        }
        workingReady=matches;matches
    }
    private fun mayMutate(){check(!readOnly && verified && immutableReady && workingReady)}
    override fun replace(objectId:String,expectedTag:String,head:TemplatePublicationHead)=immutableSafe5 {
        mayMutate();require(objectId==scope.intent.objectId && expectedTag==scope.intent.expectedTag && head==scope.intent.intended)
        val r=checked(TemplateHttpRequest5("PUT","/upload/drive/v2/files/$objectId?uploadType=media&fields=id","application/json",TemplateSyncContracts5.encode(head).toByteArray(),expectedTag))
        if(immutableJson5(r.body).immutableString5("id")!=objectId)throw DeliveryFailure("contract")
    }
    override fun createReserved(intent:PublicationHeadIntent)=immutableSafe5 {
        mayMutate();require(scope.intent.expected==null && intent.expected==null && intent.expectedTag==null && intent.objectId==scope.intent.objectId &&
            intent.operationId==scope.intent.operationId && intent.metadataFolderId==scope.intent.metadataFolderId && intent.marker==scope.intent.marker && intent.intended==scope.intent.intended)
        val boundary="gym-template-${java.util.UUID.randomUUID()}"
        val prefix="--$boundary\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n${headMetadata5(scope)}\r\n--$boundary\r\nContent-Type: application/json\r\n\r\n".toByteArray()
        val body=prefix+TemplateSyncContracts5.encode(intent.intended).toByteArray()+"\r\n--$boundary--\r\n".toByteArray()
        val r=checked(TemplateHttpRequest5("POST","/upload/drive/v3/files?uploadType=multipart&fields=id","multipart/related; boundary=$boundary",body),setOf(200,409))
        if(r.status==200 && immutableJson5(r.body).immutableString5("id")!=intent.objectId)throw DeliveryFailure("contract")
    }
}
