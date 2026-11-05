package dev.gymassistant.drive

import dev.gymassistant.session.ProjectStorageRoot5
import java.io.File
import java.util.Base64
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.Test
import kotlin.test.*

class ProjectDriveLayout5Test {
    private val raw=File(checkNotNull(System.getProperty("gym.root")),"examples/project-layout/synthetic-layout.json").readText()
    private fun decode(text:String)=checkNotNull(projectDriveLayout5(Base64.getEncoder().encodeToString(text.toByteArray(Charsets.UTF_8))))
    private val config=decode(raw)
    private val area=config.areas.single {it.kind=="personal"}
    private fun data(id:String,name:String,parent:String,key:String,marker:String)=buildJsonObject {
        put("id",id);put("name",name);put("parents",buildJsonArray {add(parent)})
        put("mimeType","application/vnd.google-apps.folder");put("ownedByMe",true);put("trashed",false);put("shared",false)
        put("appProperties",buildJsonObject {put(key,marker)})
        put("permissions",buildJsonArray {add(buildJsonObject {put("id",config.permissionId);put("type","user");put("role","owner")})})
    }
    private fun leaf(final:Boolean)=data(area.id,if(final)area.name else area.sourceName,if(final)config.dev.id else area.sourceParentId,area.propertyName,area.marker)
    private fun folders()=listOf(config.root,config.dev,config.test,config.prod).associate {it.id to data(it.id,it.name,it.parentId,it.propertyName,it.marker)}
    private fun gate(phase:String,root:ProjectStorageRoot5?=null)=ProjectAreaGate5(config.copy(phase=phase),area,{root}).also {it.identity(config.permissionId)}
    @Test fun reservedPlanAcceptsOnlyTheCapturedSourceWithoutReadingUncreatedContainers()=runBlocking<Unit> {
        val gate=gate("reserved");var reads=0
        assertEquals(area.sourceName,gate.verify(leaf(false)){reads++;error("Uncreated root must not be read")}.name);assertEquals(0,reads)
        assertFails {gate.verify(leaf(true)){folders().getValue(it)}}
    }
    @Test fun adoptingRequiresThePinnedContainersAndAnEntireSourceOrFinalPair()=runBlocking<Unit> {
        val gate=gate("adopting");val pins=folders()
        assertEquals(area.sourceName,gate.verify(leaf(false)){pins.getValue(it)}.name)
        assertEquals(area.name,gate.verify(leaf(true)){pins.getValue(it)}.name)
        for(changed in listOf(JsonObject(leaf(true)+("name" to JsonPrimitive(area.sourceName))),
            JsonObject(leaf(false)+("name" to JsonPrimitive(area.name))),JsonObject(leaf(true)+("name" to JsonPrimitive(area.name+" extra")))))
            assertFails {gate.verify(changed){pins.getValue(it)}}
    }
    @Test fun activeNeverFallsBackToTheOldLocationOrAnotherEnvironment()=runBlocking<Unit> {
        val gate=gate("active");val pins=folders();assertEquals(area.name,gate.verify(leaf(true)){pins.getValue(it)}.name)
        for(changed in listOf(leaf(false),JsonObject(leaf(true)+("parents" to buildJsonArray {add(config.test.id)})),
            JsonObject(leaf(true)+("id" to JsonPrimitive("same-name-other-object")))))assertFails {gate.verify(changed){pins.getValue(it)}}
    }
    @Test fun movedRootAlteredMarkerOwnershipOrSharingFailsWithoutNameLookup()=runBlocking<Unit> {
        val gate=gate("active");val pins=folders()
        val invalid=listOf(JsonObject(pins.getValue(config.root.id)+("parents" to buildJsonArray {add("outside-root")})),
            JsonObject(pins.getValue(config.root.id)+("shared" to JsonPrimitive(true))),
            JsonObject(pins.getValue(config.root.id)+("ownedByMe" to JsonPrimitive(false))),
            JsonObject(pins.getValue(config.root.id)+("ownedByMe" to JsonPrimitive("true"))),
            JsonObject(pins.getValue(config.root.id)+("appProperties" to buildJsonObject {put(config.root.propertyName,"different-marker")})))
        for(root in invalid)assertFails {gate.verify(leaf(true)){if(it==config.root.id)root else pins.getValue(it)}}
        val extraOwner=buildJsonObject {put("id","another-user");put("type","user");put("role","reader")}
        val shared=JsonObject(leaf(true)+("permissions" to JsonArray(leaf(true).getValue("permissions").jsonArray+extraOwner)))
        assertFails {gate.verify(shared){pins.getValue(it)}}
    }
    @Test fun accountMustVerifyFirstAndAFailedIdentityCheckRevokesThePriorVerification()=runBlocking<Unit> {
        val gate=ProjectAreaGate5(config.copy(phase="active"),area,{null});val pins=folders()
        assertFails {gate.verify(leaf(true)){pins.getValue(it)}}
        gate.identity(config.permissionId);gate.verify(leaf(true)){pins.getValue(it)}
        assertFails {gate.identity("another-account")};assertFails {gate.verify(leaf(true)){pins.getValue(it)}}
        assertFails {gate("blocked").verify(leaf(true)){pins.getValue(it)}}
    }
    @Test fun storedRootIdentityAndPhaseMustRemainStableForTheWholeCheck()=runBlocking<Unit> {
        val stored=ProjectStorageRoot5(config.bindingId,permissionId=config.permissionId,commonRootFolderId=config.root.id,marker=config.root.marker,state="active")
        assertFails {gate("active",stored.copy(commonRootFolderId="other-project")).verify(leaf(true)){folders().getValue(it)}}
        var current=stored.copy(state="adopting");val gate=ProjectAreaGate5(config,area,{current});gate.identity(config.permissionId)
        assertFails {gate.verify(leaf(true)){id->current=current.copy(state="active");folders().getValue(id)}}
    }
    @Test fun codecRejectsUnknownVersionDuplicateKeysContainersReusedAsAreasAndProductionTargets() {
        assertNull(projectDriveLayout5(""));assertFails {projectDriveLayout5("invalid base64")}
        assertFails {decode(raw.replace("1.0.0","9.0.0"))};assertFails {decode(raw.replaceFirst("{","{\"phase\":\"active\","))}
        val json=Json.parseToJsonElement(raw).jsonObject
        assertFails {decode(JsonObject(json+("unknown" to JsonPrimitive(true))).toString())}
        assertFails {config.copy(dev=config.dev.copy(id=area.id))}
        assertFails {area.copy(environment="prod")};assertFails {config.copy(root=config.root.copy(name="Gym Assistant copy"))}
    }
    @Test fun missingConfigurationCannotDowngradeAnAccountWithADurableRoot()=runBlocking<Unit> {
        val stored=ProjectStorageRoot5(config.bindingId,permissionId=config.permissionId,commonRootFolderId=config.root.id,marker=config.root.marker,state="active")
        val identity=DeliveryIdentity(config.permissionId,"owner@example.test")
        ProjectDriveAccess5(null,{null}).identity(identity)
        assertFails {ProjectDriveAccess5(null,{stored}).identity(identity)}
        assertFails {ProjectDriveAccess5(config,{stored.copy(rootBindingId="different-binding")}).identity(identity)}
    }
    @Test fun existingPersonalSetupAndRetryKeepTheirExactIdentityAfterRelocation()=runBlocking<Unit> {
        val root=java.nio.file.Files.createTempDirectory("project-personal-layout-test").toFile()
        try {
            val files=PersonalSetupFiles4(root.resolve("setup.json"))
            val setup=PersonalSetupIntent4(config.permissionId,"owner@example.test",area.id,area.marker,"verified");files.save(null,setup)
            val reads=mutableListOf<String>();var changed=false
            val pins=folders()
            val http=DeliveryHttp {request->
                assertEquals("GET",request.method);reads+=request.path
                val body=if(request.path==ACCOUNT_PATH)buildJsonObject {put("user",buildJsonObject {put("permissionId",config.permissionId);put("emailAddress",setup.email)})}
                else {
                    val id=request.path.substringAfter("/files/").substringBefore('?')
                    if(id==area.id)if(changed)leaf(false) else leaf(true) else pins.getValue(id)
                }
                DeliveryHttpResponse(200,body.toString().toByteArray())
            }
            val access=ProjectDriveAccess5(config.copy(phase="active"),{null})
            repeat(2){assertEquals(setup,ensurePersonalRoot4(files,http,setup.email,access).first)}
            assertEquals(setup,files.read());assertEquals(2,reads.count {it==ACCOUNT_PATH})
            changed=true;assertFails {ensurePersonalRoot4(files,http,setup.email,access)}
            assertEquals(setup,files.read())
        }finally {root.deleteRecursively()}
    }
    @Test fun relocatedSyntheticProbeVerifiesContainersBeforeTheSameChildMetadata()=runBlocking<Unit> {
        val original=config.areas.single {it.kind=="synthetic"}
        val synthetic=original.copy(id=SYNTHETIC_PARENT,marker=SYNTHETIC_MARKER)
        val layout=config.copy(phase="active",areas=listOf(area,synthetic))
        val identity=buildJsonObject {put("user",buildJsonObject {put("permissionId",config.permissionId);put("emailAddress","owner@example.test")})}
        val child=buildJsonObject {put("id",SYNTHETIC_CHILD);put("name","SYNTHETIC-external-child.json");put("mimeType","application/json");put("parents",buildJsonArray {add(SYNTHETIC_PARENT)})}
        val seen=mutableListOf<String>();val ancestors=folders();var wrongParent=false
        val transport=ProbeTransport {_,path->
            seen+=path
            val value=when(path) {
                ACCOUNT_PATH->identity
                CHILD_PATH->child
                projectFolderPath5(SYNTHETIC_PARENT)->data(synthetic.id,synthetic.name,if(wrongParent)config.dev.id else config.test.id,synthetic.propertyName,synthetic.marker)
                else->ancestors.getValue(path.substringAfter("/files/").substringBefore('?'))
            }
            ProbeResponse(200,value.toString())
        }
        val access=ProjectDriveAccess5(layout,{null})
        assertTrue(runSyntheticDriveProbe("fake-token","owner@example.test",transport,access).contains("selected file accessible"))
        assertEquals(listOf(ACCOUNT_PATH,projectFolderPath5(SYNTHETIC_PARENT),projectFolderPath5(config.root.id),projectFolderPath5(config.test.id),CHILD_PATH),seen)
        wrongParent=true;seen.clear();assertFails {runSyntheticDriveProbe("fake-token","owner@example.test",transport,access)}
        assertFalse(CHILD_PATH in seen)
    }
    @Test fun reservedClientFollowsTheDurableActivePhaseWithoutBroadeningItsProbeEndpoints()=runBlocking<Unit> {
        val synthetic=config.areas.single {it.kind=="synthetic"}.copy(id=SYNTHETIC_PARENT,marker=SYNTHETIC_MARKER)
        val reserved=config.copy(areas=listOf(area,synthetic))
        assertEquals(setOf(ACCOUNT_PATH,CHILD_PATH,projectFolderPath5(synthetic.id),projectFolderPath5(config.root.id),projectFolderPath5(config.test.id)),probePaths5(reserved))
        assertFalse(projectFolderPath5(config.dev.id) in probePaths5(reserved));assertFalse(projectFolderPath5(config.prod.id) in probePaths5(reserved))
        val stored=ProjectStorageRoot5(config.bindingId,permissionId=config.permissionId,commonRootFolderId=config.root.id,marker=config.root.marker,state="active")
        val gate=ProjectDriveAccess5(reserved,{stored}).gate("personal")!!;gate.identity(config.permissionId)
        var reads=0;assertTrue(gate.verify(leaf(true)){reads++;folders().getValue(it)}.projectVerified);assertEquals(2,reads)
        assertFails {gate.verify(leaf(false)){folders().getValue(it)}}
    }
}
