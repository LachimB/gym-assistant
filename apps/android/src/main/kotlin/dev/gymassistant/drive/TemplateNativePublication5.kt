package dev.gymassistant.drive

import dev.gymassistant.engine.v2.ImportContracts2
import dev.gymassistant.session.*

/** Explicit caller-owned batch only; no worker, OAuth launch, allowance reset, or namespace creation. */
internal class TemplateNativePublication5(private val store:TemplatePublicationStore5,private val contracts:ImportContracts2,
    private val working:TemplateNamespace5,private val metadata:ImmutableRevisionNamespace5,private val history:ImmutableRevisionNamespace5) {
    suspend fun execute(lease:SyncLease5,token:String,reserveRequest:()->Unit):VerifiedPublication5=
        TemplatePublicationExecutor5(store).execute(lease){captured,retain->
            val c=captured.working.captured;val byName=c.steps.associateBy {it.name}
            val payloads=c.manifest.files.associate {it.objectId to byName.getValue(it.relativeName).intendedBytes}
            val revision=ImmutableRevisionFiles5(history,c.manifest,captured.intent.intended.revisionManifestId,captured.intent.marker,payloads,contracts)
            val scope=TemplatePublicationScope5(captured.intent,revision,working,metadata,c.steps,captured.working.receipts.values.toList(),contracts)
            val selected=scope.folders.getValue(working.workingFolderId).parent
            val historyId=scope.folders.getValue(history.revisionFolderId).parent
            store.requireNativeScope(lease,history.permissionId,history.commonRootId,scope.folders.keys,selected,working.workingFolderId,
                historyId,metadata.revisionFolderId,history.revisionFolderId)
            val sink:(RemoteObjectCapture5)->Unit={raw->retain(PublicationRawObservation5(raw.kind.name,raw.objectId,raw.status,raw.metadataBytes,raw.contentBytes))}
            val immutable=TemplateImmutableBatch5(token,reserveRequest,revision,sink)
            try {
                val head=TemplatePublicationBatch5(token,reserveRequest,scope,sink)
                PublicationRemoteSet5(immutable.remote,head.remote){try{head.close()}finally{immutable.close()}}
            }catch(error:Throwable){immutable.close();throw error}
        }
}
