package dev.gymassistant.drive

import dev.gymassistant.session.PublicationRawObservation5
import dev.gymassistant.session.TemplateSources3
import dev.gymassistant.session.TemplateSyncContracts5
import dev.gymassistant.sync.*

/** Offline replay of retained object facts, never a token/account-authorization receipt or a provider call. */
private class CapturedObjects5(private val raw:List<PublicationRawObservation5>,start:Int) {
    private var cursor=start
    init {require(start in 0..raw.size && raw.size<=1024)}
    private fun next(kind:String,id:String):PublicationRawObservation5 {
        val frame=checkNotNull(raw.getOrNull(cursor++))
        check(frame.kind==kind && frame.objectId==id);return frame
    }
    fun namespace(folders:List<TemplateFolderPin5>,permission:String) {
        for(pin in folders) {
            val frame=next("NAMESPACE",pin.id);check(frame.status==200 && frame.contentBytes==null)
            checkedTemplateObject5(immutableJson5(checkNotNull(frame.metadataBytes)),permission,pin.id,pin.parent,pin.name,
                "application/vnd.google-apps.folder",pin.markerKey,pin.marker)
        }
    }
    class Bytes(val tag:String,val metadata:ByteArray,val content:ByteArray)
    fun read(kind:String,id:String,parent:String,name:String,mime:String,permission:String,marker:String):Bytes? {
        val before=next(kind,id)
        if(before.status==404)return null
        check(before.contentBytes==null)
        val metadata=checkNotNull(before.metadataBytes)
        fun tag(bytes:ByteArray)=checkedTemplateObject5(immutableJson5(bytes),permission,id,parent,name,mime,TEMPLATE_MARKER_KEY_5,marker)
        val tag=tag(metadata);val body=next(kind,id)
        check(body.status==200 && checkNotNull(body.metadataBytes).contentEquals(metadata))
        val bytes=checkNotNull(body.contentBytes);val after=next(kind,id)
        check(after.status==200 && after.contentBytes==null && tag(checkNotNull(after.metadataBytes))==tag)
        return Bytes(tag,metadata,bytes)
    }
    fun finished(){check(cursor==raw.size)}
}

internal fun replayKnownVector5(scope:TemplateSourceReadScope5,start:Int):KnownSourceVector5 {
    val n=scope.capture.namespace;val replay=CapturedObjects5(scope.capture.rawObservations,start)
    val reader=object:TemplateSourceReader5 {
        override fun verifyNamespace()=replay.namespace(scope.folders.values.toList(),n.permissionId)
        override fun readHead():PublicationHeadSnapshot? {
            val read=replay.read("HEAD",n.headFileId,n.metadataFolderId,"head.json","application/json",n.permissionId,n.marker) ?: return null
            val head=TemplateSyncContracts5.head(immutableText5(read.content,65536))
            check(head.templateId==scope.capture.local.templateId && head.workingFolderId==n.workingFolderId)
            return PublicationHeadSnapshot(n.headFileId,setOf(n.metadataFolderId),n.marker,"head.json","application/json",read.tag,false,head,read.metadata,read.content)
        }
        override fun readSource(objectId:String):SourceReadObject5? {
            val pin=scope.pins.getValue(objectId)
            val read=replay.read("WORKING",objectId,n.workingFolderId,pin.relativeName,pin.mimeType,n.permissionId,n.marker) ?: return null
            return SourceReadObject5(pin,read.tag,read.metadata,read.content)
        }
    }
    val vector=reader.captureKnownVector(scope.capture.pins);replay.finished();return vector
}

internal fun replayKnownBase5(scope:KnownImmutableBaseScope5,start:Int):TemplateSources3 {
    val replay=CapturedObjects5(scope.source.capture.rawObservations,start);val n=scope.source.capture.namespace
    replay.namespace(scope.revision.namespace.folders,n.permissionId)
    val payloads=mutableMapOf<String,ByteArray>();var manifest:TemplateRevisionManifest?=null
    for(pin in scope.revision.objects) {
        val read=checkNotNull(replay.read("IMMUTABLE",pin.objectId,pin.folderId,pin.name,pin.mimeType,n.permissionId,n.marker))
        check(read.content.size==pin.byteCount && hashSourceBytes(read.content)==pin.sha256)
        if(pin.objectId==scope.revision.manifestFile.objectId)manifest=TemplateSyncContracts5.manifest(immutableText5(read.content,65536))
        else payloads[pin.objectId]=read.content
    }
    val sources=TemplateSyncContracts5.bundle(checkNotNull(manifest),payloads,scope.contracts)
    replay.namespace(scope.revision.namespace.folders,n.permissionId);replay.finished();return sources
}
