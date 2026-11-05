package dev.gymassistant.drive

import dev.gymassistant.session.SourceCapturePin5
import dev.gymassistant.session.TemplateSources3
import dev.gymassistant.session.sessionJson
import dev.gymassistant.engine.v2.*
import dev.gymassistant.sync.PublicationHeadSnapshot
import dev.gymassistant.sync.hashSourceBytes
import kotlinx.serialization.encodeToString

/** Equal bounded observations only: no compiler validation, immutable-base proof or adoption authority. */
internal class KnownSourceVector5 internal constructor(val head:PublicationHeadSnapshot?,files:List<SourceReadObject5>) {
    val files=files.toList()
}

/** Compiled observed bytes only. A head/immutable-base comparison and explicit review are still mandatory. */
internal class CompiledKnownSources5 internal constructor(val sources:TemplateSources3,val templateId:String,val revisionId:String,
    val sourceHash:String,val compiledPlanHash:String)

internal fun KnownSourceVector5.compileWorkingSource(expectedTemplateId:String,contracts:ImportContracts2):CompiledKnownSources5=immutableSafe5 {
    require(expectedTemplateId.matches(Regex("[A-Za-z0-9_-]{3,128}")))
    require(files.size in 2..16 && files.count {it.pin.role=="template"}==1)
    require(files.map {it.pin.objectId}.distinct().size==files.size && files.map {it.pin.relativeName}.distinct().size==files.size)
    require(files.sumOf {it.contentBytes.size.toLong()}<=8388608)
    val template=immutableText5(files.single {it.pin.role=="template"}.contentBytes,1048576)
    val exercises=files.filter {it.pin.role=="exercise"}.map {immutableText5(it.contentBytes,1048576)}.sortedBy {readYaml2(it).text2("familyId")}
    val sources=TemplateSources3(template,exercises);val plan=compileTemplate2(template,exercises,contracts)
    check(plan.templateId==expectedTemplateId && (head==null || head.head.templateId==expectedTemplateId) && plan.adjustments.isEmpty())
    // An external author may retain the old revision ID. Preserve it here; proposing
    // a fresh identity belongs in the later explicit publication preview.
    CompiledKnownSources5(sources,plan.templateId,plan.revisionId,hashSourceBytes(sessionJson.encodeToString(sources).toByteArray()),
        hashSourceBytes(planJson2.encodeToString(plan).toByteArray()))
}

/**
 * Rechecks the whole explicit vector, with head brackets and namespace checks at both ends.
 * A missing head stays an explicit observation; it never establishes an empty namespace.
 * A later human edit remains possible and must be rechecked before any publication.
 * The supplied reader must retain original responses before returning them.
 */
internal fun TemplateSourceReader5.captureKnownVector(pins:List<SourceCapturePin5>):KnownSourceVector5=immutableSafe5 {
    val expected=pins.toList()
    require(expected.size in 2..16 && expected.count {it.role=="template"}==1)
    require(expected.map {it.objectId}.distinct().size==expected.size && expected.map {it.relativeName}.distinct().size==expected.size)
    fun sameHead(a:PublicationHeadSnapshot?,b:PublicationHeadSnapshot?):Boolean {
        if(a==null || b==null)return a==null && b==null
        return a.objectId==b.objectId && a.parents==b.parents && a.marker==b.marker && a.name==b.name &&
            a.mimeType==b.mimeType && a.tag==b.tag && a.trashed==b.trashed && a.head==b.head &&
            a.metadataBytes.contentEquals(b.metadataBytes) && a.contentBytes.contentEquals(b.contentBytes)
    }
    fun readVector():List<SourceReadObject5> {
        var total=0L
        return expected.map {pin->
            val observed=readSource(pin.objectId) ?: throw DeliveryFailure("conflict")
            require(observed.pin==pin);immutableTag5(observed.tag)
            require(observed.metadataBytes.size in 1..65536)
            val bytes=observed.contentBytes;immutableText5(bytes,1048576)
            total+=bytes.size;require(total<=8388608)
            SourceReadObject5(pin,observed.tag,observed.metadataBytes,bytes)
        }
    }
    verifyNamespace()
    val before=readHead()
    require(before==null || expected.none {it.objectId==before.objectId})
    val first=readVector()
    if(!sameHead(before,readHead()))throw DeliveryFailure("conflict")
    val second=readVector()
    for((a,b) in first.zip(second)) {
        if(a.pin!=b.pin || a.tag!=b.tag || !a.metadataBytes.contentEquals(b.metadataBytes) || !a.contentBytes.contentEquals(b.contentBytes))
            throw DeliveryFailure("conflict")
    }
    if(!sameHead(before,readHead()))throw DeliveryFailure("conflict")
    verifyNamespace()
    KnownSourceVector5(before,first)
}
