package dev.gymassistant.drive

import dev.gymassistant.engine.v2.ImportContracts2
import dev.gymassistant.session.*
import dev.gymassistant.sync.*
import java.io.Closeable

/** Only an acknowledged retained publication can identify this client's known common base. */
internal class KnownImmutableBaseScope5(val source:TemplateSourceReadScope5,prior:CheckedPublication5,
    revisionFolder:TemplateFolderPin5,val contracts:ImportContracts2) {
    val manifest=prior.working.captured.manifest
    val revision:ImmutableRevisionFiles5
    init {
        val capture=source.capture;val local=capture.local;val n=capture.namespace;val head=prior.intent.intended
        require(prior.allImmutableAcknowledged && prior.working.allFilesAcknowledged && prior.publishedTag!=null)
        immutableTag5(prior.publishedTag)
        require(local.baseRevisionId==manifest.revisionId && local.baseSourceHash==manifest.sourceHash &&
            local.expectedRemoteGeneration==head.generationId && local.expectedRemoteHeadTag==prior.publishedTag)
        require(prior.bindingHash==n.bindingHash && prior.permissionId==n.permissionId && prior.working.captured.bindingId==local.bindingId &&
            manifest.templateId==local.templateId && head.templateId==local.templateId && head.sourceHash==manifest.sourceHash &&
            head.workingFolderId==n.workingFolderId && prior.intent.objectId==n.headFileId && prior.intent.metadataFolderId==n.metadataFolderId && prior.intent.marker==n.marker)
        require(revisionFolder.id==prior.revisionFolderId && revisionFolder.id !in source.readableIds &&
            revisionFolder.parent==n.historyFolderId && revisionFolder.markerKey==TEMPLATE_MARKER_KEY_5 && revisionFolder.marker==n.marker)
        val folders=source.folders.values.filterNot {it.id in setOf(n.workingFolderId,n.metadataFolderId)}+revisionFolder
        val namespace=ImmutableRevisionNamespace5(n.permissionId,n.commonRootId,revisionFolder.id,folders)
        val byName=prior.working.captured.steps.associateBy {it.name}
        val payloads=manifest.files.associate {it.objectId to byName.getValue(it.relativeName).intendedBytes}
        revision=ImmutableRevisionFiles5(namespace,manifest,head.revisionManifestId,n.marker,payloads,contracts)
        require(revision.objects.none {it.objectId in source.readableIds})
        require(prior.immutableSteps.map {it.objectId}.toSet()==revision.objects.map {it.objectId}.toSet())
        for(pin in revision.objects) {
            val retained=prior.immutableSteps.single {it.objectId==pin.objectId}
            require(retained.folderId==pin.folderId && retained.name==pin.name && retained.mimeType==pin.mimeType && retained.marker==pin.marker && retained.bytes.contentEquals(pin.bytes))
            require(prior.receipts[pin.objectId]?.sha256==pin.sha256)
        }
    }
}

/** The production transport cannot create, replace, list or read unknown objects. */
internal class GoogleKnownBaseHttp5(token:String,reserveRequest:()->Unit,scope:KnownImmutableBaseScope5):TemplateHttp5,Closeable {
    private val objects=scope.revision.objects.map {it.objectId}.toSet()
    private val readable=objects+scope.revision.namespace.folders.map {it.id}
    private val http=GoogleTemplateHttp5(token,reserveRequest,readable,emptySet(),IMMUTABLE_FILE_FIELDS_5,scope.revision.manifestFile.objectId)
    override fun close()=http.close()
    override fun request(request:TemplateHttpRequest5):DeliveryHttpResponse=immutableSafe5 {
        require(request.method=="GET" && (request.path==ACCOUNT_PATH || readable.any {request.path=="/drive/v2/files/$it?fields=$IMMUTABLE_FILE_FIELDS_5"} ||
            objects.any {request.path=="/drive/v2/files/$it?alt=media"}))
        http.request(request)
    }
}

/** Readback verification only. Original responses must be retained before interpretation; no acknowledgement/adoption callback. */
internal class KnownImmutableBaseBatch5(token:String,reserveRequest:()->Unit,private val scope:KnownImmutableBaseScope5,
    retain:(RemoteObjectCapture5)->Unit,private val testTransport:TemplateHttp5?=null):Closeable {
    private val production=if(testTransport==null)GoogleKnownBaseHttp5(token,reserveRequest,scope)else null
    private val transport=production?:TemplateHttp5 {request->reserveRequest();checkNotNull(testTransport).request(request)}
    private val folderIds=scope.revision.namespace.folders.map {it.id}.toSet()
    private val objectIds=scope.revision.objects.map {it.objectId}.toSet()
    private val http=TemplateHttp5 {request->
        val response=transport.request(request)
        if(response.status==404 && request.path.startsWith("/drive/v2/files/")) {
            val id=request.path.substringAfter("/drive/v2/files/").substringBefore('?')
            require(id in folderIds || id in objectIds)
            retain(RemoteObjectCapture5(if(id in folderIds)RemoteObjectKind5.NAMESPACE else RemoteObjectKind5.IMMUTABLE,id,404,null,null))
        }
        response
    }
    // The wrapper captures each404 before interpretation, including folder/media
    // failures. Suppress only this delegate's duplicate receipt of that same404.
    private val remote=GoogleTemplateImmutableRemote5(http,scope.revision){if(it.status!=404)retain(it)}
    override fun close(){try{(testTransport as? Closeable)?.close()}finally{production?.close()}}
    fun verify():TemplateSources3=immutableSafe5 {
        remote.verifyNamespace()
        val payloads=mutableMapOf<String,ByteArray>();var observedManifest:TemplateRevisionManifest?=null
        for(pin in scope.revision.objects) {
            val observed=remote.read(pin.objectId) ?: throw DeliveryFailure("conflict")
            require(observed.objectId==pin.objectId && observed.parents==setOf(pin.folderId) && observed.marker==pin.marker &&
                observed.name==pin.name && observed.mimeType==pin.mimeType && !observed.trashed)
            if(observed.bytes.size!=pin.byteCount || hashSourceBytes(observed.bytes)!=pin.sha256)throw DeliveryFailure("conflict")
            if(pin.objectId==scope.revision.manifestFile.objectId)observedManifest=TemplateSyncContracts5.manifest(immutableText5(observed.bytes,65536))
            else payloads[pin.objectId]=observed.bytes
        }
        val sources=TemplateSyncContracts5.bundle(checkNotNull(observedManifest),payloads,scope.contracts)
        remote.verifyNamespace()
        sources
    }
}
