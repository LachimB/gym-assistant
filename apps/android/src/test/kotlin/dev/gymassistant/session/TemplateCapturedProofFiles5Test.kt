package dev.gymassistant.session

import dev.gymassistant.drive.*
import dev.gymassistant.engine.v2.ImportContracts2
import dev.gymassistant.sync.*
import java.io.File
import java.nio.file.Files
import kotlinx.serialization.json.*
import org.junit.After
import org.junit.Test
import kotlin.test.*

class TemplateCapturedProofFiles5Test {
    private val repo=File(checkNotNull(System.getProperty("gym.root")))
    private val root=Files.createTempDirectory("synthetic-capture-proofs-").toFile()
    private fun schema(name:String)=Json.parseToJsonElement(repo.resolve("schemas/$name").readText()).jsonObject
    private val contracts=ImportContracts2(mapOf("1.0.0" to schema("template.v1.schema.json"),"1.1.0" to schema("template.v1.1.schema.json")),schema("exercise.v1.schema.json"))
    private val fixture=Json.parseToJsonElement(repo.resolve("examples/sessions/synthetic-runtime-v2-session.json").readText()).jsonObject
    private val snapshot=fixture.getValue("sourcePlanSnapshot").jsonObject
    private val sources=sessionJson.decodeFromString<TemplateSources3>(snapshot.getValue("sourceSnapshotJson").jsonPrimitive.content)
    private val row=TemplateRevisionRow(fixture.getValue("templateId").jsonPrimitive.content,fixture.getValue("revisionId").jsonPrimitive.content,
        snapshot.getValue("sourceSnapshotJson").jsonPrimitive.content,snapshot.getValue("sourceHash").jsonPrimitive.content,
        snapshot.getValue("compiledPlanJson").jsonPrimitive.content,snapshot.getValue("compiledPlanHash").jsonPrimitive.content,"1.1.0",publishedAt="2026-11-05T12:00:00Z",candidateId="candidate-test")
    private val binding=TemplateSyncBinding5("binding-test","connection-test","root-binding-test",row.templateId,"template-folder","working-folder","history-folder","metadata-folder","gym-template-test","head-file",enabled=true,createdAt=row.publishedAt,updatedAt=row.publishedAt)
    private val files=TemplateSyncFiles5(root,contracts);private val queue=TemplateSyncQueueFiles5(files,contracts)
    private val captures=TemplateSourceCaptureFiles5(files,queue)
    private fun proofs()=TemplateCapturedProofFiles5(files,captures,TemplatePublicationFiles5(files),contracts)
    private val n=SourceCaptureNamespace5("f".repeat(64),"synthetic-owner","project-root","proof-area","template-folder","working-folder","history-folder","metadata-folder","head-file","gym-template-test")
    private val pins=listOf(SourceCapturePin5("template","template.yaml","working-template","text/plain"),SourceCapturePin5("exercise","exercise.yaml","working-exercise","text/plain"))
    private fun folder(id:String,parent:String,name:String,key:String=TEMPLATE_MARKER_KEY_5,marker:String=n.marker)=TemplateFolderPin5(id,parent,name,key,marker)
    private val folders=listOf(folder("project-root","my-drive-test","Gym Assistant","gymProjectM1","gym-project-test"),
        folder("test-folder","project-root","test","gymEnvironmentM1","env-test-fixture"),folder("proof-area","test-folder","synthetic proof","gymSyntheticM1","synthetic-proof-test"),
        folder("library-folder","proof-area","Templates","gymTemplateLibraryM1","library-test"),folder("template-folder","library-folder","selected fixture"),
        folder("working-folder","template-folder","Current working copy"),folder("history-folder","template-folder","History"),folder("metadata-folder","template-folder","Sync metadata"))
    private fun record(template:String=sources.templateYaml,missingHead:Boolean=false):SyncIntentFile5 {
        val origin=queue.prepare("job-test","operation-test",binding,n.bindingHash,row)
        var pointer=captures.prepare(origin,n,pins,"observation-test")
        val scope=TemplateSourceReadScope5(captures.load(pointer),folders)
        val http=TemplateHttp5 {request->
            check(request.method=="GET")
            if(request.path==ACCOUNT_PATH)DeliveryHttpResponse(200,"{\"user\":{\"permissionId\":\"synthetic-owner\"}}".toByteArray())
            else {
                val id=request.path.substringAfter("/drive/v2/files/").substringBefore('?');val folder=folders.singleOrNull {it.id==id};val pin=pins.singleOrNull {it.objectId==id}
                if(missingHead && id==n.headFileId)DeliveryHttpResponse(404)
                else if(request.path.endsWith("?alt=media")) {
                    val text=when(id){n.headFileId->TemplateSyncContracts5.encode(TemplatePublicationHead(row.templateId,"generation-test","manifest-test",n.workingFolderId,row.sourceHash,null))
                        pins.first().objectId->template;else->sources.exerciseYamls.single()}
                    DeliveryHttpResponse(200,text.toByteArray())
                }else DeliveryHttpResponse(200,buildJsonObject {
                    put("id",id);put("title",folder?.name?:pin?.relativeName?:"head.json");put("etag","\"tag-$id\"")
                    put("mimeType",if(folder!=null)"application/vnd.google-apps.folder" else pin?.mimeType?:"application/json")
                    put("ownedByMe",true);put("shared",false);put("labels",buildJsonObject {put("trashed",false)})
                    put("parents",buildJsonArray {add(buildJsonObject {put("id",folder?.parent?:if(pin!=null)n.workingFolderId else n.metadataFolderId)})})
                    put("properties",buildJsonArray {add(buildJsonObject {put("key",folder?.markerKey?:TEMPLATE_MARKER_KEY_5);put("value",folder?.marker?:n.marker);put("visibility","PRIVATE")})})
                    put("permissions",buildJsonArray {add(buildJsonObject {put("id",n.permissionId);put("type","user");put("role","owner")})})
                }.toString().toByteArray())
            }
        }
        GoogleSourceReader5(http,scope){pointer=captures.retainRaw(pointer,PublicationRawObservation5(it.kind.name,it.objectId,it.status,it.metadataBytes,it.contentBytes))}.captureKnownVector(pins)
        return pointer
    }
    @After fun cleanup(){root.deleteRecursively()}
    @Test fun completeOriginalVectorReplaysAfterReopenWithoutProviderCallsOrValidityFlag() {
        val capture=record();val pointer=proofs().prepareVector(capture,folders,0)
        val reopenedFiles=TemplateSyncFiles5(root,contracts)
        val reopened=TemplateCapturedProofFiles5(reopenedFiles,TemplateSourceCaptureFiles5(reopenedFiles,TemplateSyncQueueFiles5(reopenedFiles,contracts)),TemplatePublicationFiles5(reopenedFiles),contracts).loadVector(pointer)
        assertEquals(capture,reopened.capturePointer);assertEquals(37,reopened.capture.rawObservations.size)
        assertEquals(row.sourceHash,reopened.vector.compileWorkingSource(row.templateId,contracts).sourceHash)
        assertEquals("capturing",Json.parseToJsonElement(files.readRecord(capture)).jsonObject.getValue("stage").jsonPrimitive.content)
        assertFalse("validationCode" in Json.parseToJsonElement(files.readRecord(pointer)).jsonObject)
    }
    @Test fun malformedYamlAndAbsentHeadRemainOriginalObservationsWithoutExecutionAuthority() {
        val capture=record(template="synthetic malformed source: [",missingHead=true)
        val pointer=proofs().prepareVector(capture,folders,0);val checked=proofs().loadVector(pointer)
        assertNull(checked.vector.head);assertEquals("synthetic malformed source: [",checked.vector.files.first().contentBytes.toString(Charsets.UTF_8))
        assertFails {checked.vector.compileWorkingSource(row.templateId,contracts)}
        assertEquals(row.sourceHash,captures.load(capture).local.sourceHash)
    }
    @Test fun incompleteSkippedOrTrailingFramesCannotBePresentedAsACompleteVector() {
        val capture=record()
        for(start in listOf(-1,1,37,38))assertFails {proofs().prepareVector(capture,folders,start)}
        val extended=captures.retainRaw(capture,PublicationRawObservation5("HEAD",n.headFileId,404,null,null))
        assertFails {proofs().prepareVector(extended,folders,0)}
        assertEquals(38,captures.load(extended).rawObservations.size)
    }
    @Test fun unknownFieldsKindsVersionsAndNestedReferencesAreRejected() {
        val capture=record();val pointer=proofs().prepareVector(capture,folders,0)
        val d=Json.parseToJsonElement(files.readRecord(pointer)).jsonObject
        for(changed in listOf(d+("validationCode" to JsonPrimitive("valid")),d+("schemaVersion" to JsonPrimitive("9.0.0")),
            d+("kind" to JsonPrimitive("knownBaseProof")),d+("capturePath" to JsonPrimitive(pointer.path))+("captureHash" to JsonPrimitive(pointer.sha256))))
            assertFails {proofs().loadVector(files.writeRecord(JsonObject(changed).toString()))}
        assertFails {proofs().loadBase(pointer)}
    }
    @Test fun alteredNamespaceOrRetainedMetadataCannotPassReplay() {
        val capture=record();val pointer=proofs().prepareVector(capture,folders,0)
        assertFails {proofs().prepareVector(capture,folders.map {if(it.id==n.commonRootId)it.copy(marker="gym-project-other")else it},0)}
        val metadata=captures.load(capture).rawObservations.first().metadataBytes!!
        root.resolve(hashSourceBytes(metadata)+".yaml").writeText("synthetic changed metadata")
        assertFails {proofs().loadVector(pointer)}
    }
}
