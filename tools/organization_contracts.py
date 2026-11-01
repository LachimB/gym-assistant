#!/usr/bin/env python3
"""Synthetic organization contract checks. NOT a production sync, compiler or workflow engine.

Demonstrates important invariants and expected failure paths for M1/M2 implementations.
"""
from __future__ import annotations
from copy import deepcopy
from typing import Any

class OrganizationContractError(ValueError):
    pass


def require(expr: bool, message: str) -> None:
    if not expr:
        raise OrganizationContractError(message)


def validate_records(seasons: list[dict], programs: list[dict], participations: list[dict], templates: list[dict]) -> None:
    season_by_id={s["seasonId"]:s for s in seasons}
    program_by_id={p["programId"]:p for p in programs}
    template_by_id={t["templateId"]:t for t in templates}
    require(len(season_by_id)==len(seasons),"duplicate Season ID")
    require(len(program_by_id)==len(programs),"duplicate Program ID")
    require(sum(s["status"] == "active" for s in seasons)<=1,"more than one Active Season")
    participation_ids=set()
    pairing=set()
    for p in programs:
        membership_ids=set()
        for m in p["templateMemberships"]:
            mid=m["membershipId"]
            require(mid not in membership_ids,"duplicate Program membership ID")
            membership_ids.add(mid)
            require(m["templateId"] in template_by_id,"missing Training Template")
            if m["versionPolicy"]["mode"] == "pinned":
                require(m["versionPolicy"]["revisionId"]==template_by_id[m["templateId"]]["revisionId"],
                        "pinned revision not supplied in illustrative fixture catalog")
            for override in m.get("overrides",[]):
                find_target_set(template_by_id[m["templateId"]],override)
            for usage in m.get("setUsage",[]):
                find_target_set(template_by_id[m["templateId"]],{
                    "blockId":usage["blockId"],"setId":usage["setId"],
                    "occurrenceId":next((o["occurrenceId"] for s in template_by_id[m["templateId"]]["sections"] for b in s["blocks"] if b["blockId"]==usage["blockId"] for o in b["occurrences"] if any(z["setId"]==usage["setId"] for z in o["sets"])),"unknown")})
    for part in participations:
        pid=part["participationId"]
        require(pid not in participation_ids,"duplicate participation ID")
        participation_ids.add(pid)
        require(part["seasonId"] in season_by_id,"missing Season reference")
        require(part["programId"] in program_by_id,"missing Program reference")
        key=(part["seasonId"],part["programId"])
        require(key not in pairing,"duplicate Season-Program participation pair")
        pairing.add(key)
        if part["status"] == "active":
            require(season_by_id[part["seasonId"]]["status"] == "active", "active participation outside Active Season")
            require(program_by_id[part["programId"]]["status"] == "active", "active participation under inactive Program")


def find_target_set(template:dict, target:dict)->dict:
    for section in template["sections"]:
        for block in section["blocks"]:
            if block["blockId"] != target["blockId"]:continue
            for occ in block["occurrences"]:
                if occ["occurrenceId"] != target["occurrenceId"]:continue
                for se in occ["sets"]:
                    if se["setId"]==target["setId"]:return se
    raise OrganizationContractError("override target not found by stable block/occurrence/set IDs")


def resolve_example_parameter_overrides(template:dict, membership:dict)->dict:
    """Reference demonstration only; no nontrivial setUsage/explicit-order expansion.

    Fail closed on structural adjustments; implementation must define and test a full
    compiler in M2 before accepting them as live executable plans.
    """
    require(membership["templateId"]==template["templateId"],"cross-Template membership mismatch")
    require(not membership.get("setUsage"),"setUsage needs full M2 compiler; refuse silent rewrite")
    resolved=deepcopy(template)
    for override in membership.get("overrides",[]):
        dst=find_target_set(resolved,override)
        values=override["values"]
        for key,v in values.items():
            if key=="phaseDurations":
                phase_ids={p["phaseId"]:p for p in dst["phases"]}
                for o in v:
                    require(o["phaseId"] in phase_ids,"unknown phase ID")
                    phase_ids[o["phaseId"]]["durationMs"]=o["durationMs"]
            elif key in {"repetitions","resistance"}:
                dst[key]=deepcopy(v)
            else:
                raise OrganizationContractError(f"{key}: not implemented by illustrative reference resolver")
    return resolved


def validate_start_context(context:dict | None, seasons:list[dict], programs:list[dict], participations:list[dict]):
    """Validate at workout START only; NEVER revalidate historical sessions against current states."""
    context=context or {}
    sid=context.get("season",{}).get("seasonId")
    pid=context.get("program",{}).get("programId")
    ss=next((s for s in seasons if s["seasonId"]==sid),None)
    pp=next((p for p in programs if p["programId"]==pid),None)
    if sid: require(ss is not None and ss["status"]=="active","only Active Season can receive new sessions")
    if pid: require(pp is not None and pp["status"]=="active","Program attribution requires active global Program")
    if sid and pid:
        participation_id=context["season"].get("participationId")
        require(any(x["seasonId"]==sid and x["programId"]==pid and x["status"]=="active" and x["participationId"]==participation_id for x in participations),"no active matching Season participation")
    # Program-only context is valid even with an unrelated active Season.
    return True


def clone_org_definition(source:dict,new_id:str,new_revision:str)->dict:
    """Illustrative safe clone of one Season or Program; nested clone requires a review transaction."""
    require("seasonId" in source or "programId" in source,"not a Season/Program")
    clone=deepcopy(source)
    field="seasonId" if "seasonId" in source else "programId"
    clone[field]=new_id
    clone["revisionId"]=new_revision
    clone["status"]="draft"
    clone.pop("startDate",None)
    clone.pop("endDate",None)
    clone.pop("archivedAt",None)
    clone["clonedFromId"]=source[field]
    return clone


def validate_promotion_guard(proposal:dict, current_revision_id:str)->None:
    require(proposal["status"] in ("approved","applied"),"unapproved post-session promotion")
    target=proposal["target"]
    expected=target["expectedProgramRevisionId"] if target["kind"]=="programOverride" else target["expectedTemplateRevisionId"]
    require(current_revision_id==expected,"stale target revision; re-review required")
    require(proposal.get("approvedAt") and proposal.get("approvedBy"),"missing explicit user approval audit")


def choose_prior_performance(sessions:list[dict], *, template_id:str, block_id:str,
                             occurrence_id:str, variation_id:str, set_id:str,
                             program_id:str|None=None)->dict|None:
    """Synthetic strict lookup example, not a production indexed query.

    Match all 5 structural identity dimensions. Prefer Program context across Seasons;
    if absent, transparently fall back to another execution of SAME Template.
    """
    from datetime import datetime
    candidates=[]
    for session in sessions:
        if session.get('status')!='completed' or session.get('templateId')!=template_id:
            continue
        matched=next((x for x in session.get('performanceBySet',[])
                      if all((x.get(k)==v) for k,v in [
                          ('blockId',block_id),('occurrenceId',occurrence_id),
                          ('variationId',variation_id),('setId',set_id)])),None)
        if matched is not None:
            candidates.append((session,matched))
    if not candidates:return None
    matching_context=[x for x in candidates if program_id and
                      (x[0].get('organizationContext',{}).get('program') or {}).get('programId')==program_id]
    chosen=max(matching_context or candidates,key=lambda x: datetime.fromisoformat(x[0]['finishedAt']))
    s,record=chosen
    ctx=s.get('organizationContext') or {}
    src_program=(ctx.get('program') or {}).get('programId')
    return {'sessionId':s['sessionId'],'programId':src_program,
            'seasonId':(ctx.get('season') or {}).get('seasonId'),
            'fallback': bool(program_id and src_program!=program_id),
            'performance':record}
