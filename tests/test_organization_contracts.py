import sys
import unittest
from copy import deepcopy
from pathlib import Path
ROOT=Path(__file__).resolve().parent.parent
sys.path.insert(0,str(ROOT/'tools'))
from validate_starter import read_yaml,read_json,load_fixture,check_schema,compile_synthetic_timeline
from organization_contracts import (OrganizationContractError,validate_records,find_target_set,
    resolve_example_parameter_overrides,validate_start_context,clone_org_definition,validate_promotion_guard,choose_prior_performance)


class OrganizationContracts(unittest.TestCase):
    def setUp(self):
        self.seasons=[read_yaml('examples/seasons/season-2026-2027.yaml'),read_yaml('examples/seasons/season-2027-2028.yaml')]
        self.programs=[read_yaml('examples/programs/corrective-training.yaml'),read_yaml('examples/programs/standalone-mobility.yaml')]
        self.parts=[read_yaml('examples/participations/corrective-2026.yaml'),read_yaml('examples/participations/corrective-2027.yaml')]
        self.template=load_fixture()
        self.session=read_json('examples/sessions/synthetic-completed-session.json')

    def test_valid_multi_season_program_and_standalone(self):
        validate_records(self.seasons,self.programs,self.parts,[self.template])
        self.assertEqual(len({p['programId'] for p in self.parts}),1)
        self.assertEqual(self.programs[1]['templateMemberships'],[])

    def test_reject_two_active_seasons(self):
        self.seasons[1]['status']='active'
        with self.assertRaisesRegex(OrganizationContractError,'more than one Active Season'):
            validate_records(self.seasons,self.programs,self.parts,[self.template])

    def test_reject_active_participation_outside_active_season(self):
        self.parts[1]['status']='active'  # future Season is only planned
        with self.assertRaisesRegex(OrganizationContractError,'outside Active Season'):
            validate_records(self.seasons,self.programs,self.parts,[self.template])

    def test_reject_unknown_program_template_membership(self):
        self.programs[0]['templateMemberships'][0]['templateId']='unknown_template'
        with self.assertRaisesRegex(OrganizationContractError,'missing Training Template'):
            validate_records(self.seasons,self.programs,self.parts,[self.template])

    def test_override_with_stable_target_and_immutable_base(self):
        before=deepcopy(self.template)
        membership=self.programs[0]['templateMemberships'][0]
        resolved=resolve_example_parameter_overrides(self.template,membership)
        self.assertEqual(self.template,before)
        selection={'blockId':'block_alternating','occurrenceId':'occurrence_hip_lift','setId':'set_hip_red'}
        self.assertEqual(find_target_set(resolved,selection)['resistance']['bands'][0]['equipmentId'],'band_black')
        self.assertEqual(find_target_set(self.template,selection)['resistance']['bands'][0]['equipmentId'],'band_red')
        self.assertEqual(compile_synthetic_timeline(resolved)['expectedDurationMs'],50000)

    def test_invalid_override_does_not_mutate_original(self):
        membership=deepcopy(self.programs[0]['templateMemberships'][0])
        membership['overrides'][0]['setId']='set_not_there'
        before=deepcopy(self.template)
        with self.assertRaisesRegex(OrganizationContractError,'stable block/occurrence/set'):
            resolve_example_parameter_overrides(self.template,membership)
        self.assertEqual(self.template,before)
        self.assertEqual(self.programs[0]['templateMemberships'][0]['lastValidated']['templateRevisionId'],'revision_demo_v1')

    def test_unsupported_set_pattern_changes_fail_closed(self):
        membership=deepcopy(self.programs[0]['templateMemberships'][0])
        membership['setUsage']=[{'blockId':'block_alternating','setId':'set_hip_red','copies':2}]
        check_schema('training-program',{**self.programs[0], 'templateMemberships':[membership]})
        with self.assertRaisesRegex(OrganizationContractError,'needs full M2 compiler'):
            resolve_example_parameter_overrides(self.template,membership)

    def test_start_context_allows_both_program_only_or_neither(self):
        self.assertTrue(validate_start_context(self.session['organizationContext'],self.seasons,self.programs,self.parts))
        self.assertTrue(validate_start_context({'program':{'programId':'program_standalone_mobility'}},self.seasons,self.programs,self.parts))
        self.assertTrue(validate_start_context(None,self.seasons,self.programs,self.parts))

    def test_start_context_rejects_nonactive_season_or_participation(self):
        ctx=deepcopy(self.session['organizationContext']);ctx['season']['seasonId']='season_2027_2028'
        with self.assertRaisesRegex(OrganizationContractError,'only Active Season'):
            validate_start_context(ctx,self.seasons,self.programs,self.parts)
        ctx=deepcopy(self.session['organizationContext']);ctx['season']['participationId']='other'
        with self.assertRaisesRegex(OrganizationContractError,'no active matching'):
            validate_start_context(ctx,self.seasons,self.programs,self.parts)

    def test_historical_session_can_remain_unchanged_after_season_transition(self):
        historical=deepcopy(self.session)
        self.seasons[0]['status']='completed'
        self.seasons[1]['status']='active'
        self.parts[0]['status']='ended'
        self.parts[1]['status']='active'
        validate_records(self.seasons,self.programs,self.parts,[self.template])
        check_schema('session',historical)
        self.assertEqual(historical['organizationContext']['season']['seasonId'],'season_2026_2027')
        self.assertEqual(historical['revisionId'], 'revision_demo_v1')

    def test_clone_draft_clears_dates_preserves_memberships_no_sessions(self):
        s=clone_org_definition(self.seasons[0],'season_new_clone','season_clone_rev1')
        self.assertEqual(s['status'],'draft')
        self.assertNotIn('startDate',s)
        self.assertNotIn('endDate',s)
        self.assertEqual(s['clonedFromId'],'season_2026_2027')
        p=clone_org_definition(self.programs[0],'program_new_clone','program_clone_rev1')
        self.assertEqual(p['templateMemberships'],self.programs[0]['templateMemberships'])
        self.assertNotIn('sessions',p)
        check_schema('training-season',s);check_schema('training-program',p)

    def test_promotion_must_be_approved_and_revision_current(self):
        proposal=read_json('examples/promotions/selective-promotion.json')
        with self.assertRaisesRegex(OrganizationContractError,'unapproved'):
            validate_promotion_guard(proposal,'program_corrective_rev1')
        proposal['status']='approved'
        proposal['approvedAt']='2026-10-03T10:00:00+02:00'
        proposal['approvedBy']='user_personal_test'
        with self.assertRaisesRegex(OrganizationContractError,'stale target revision'):
            validate_promotion_guard(proposal,'program_new_rev2')
        self.assertIsNone(validate_promotion_guard(proposal,'program_corrective_rev1'))
        check_schema('promotion-proposal',proposal)

    def test_unknown_parameter_override_rejected_by_schema(self):
        p=deepcopy(self.programs[0]);p['templateMemberships'][0]['overrides'][0]['values']['insertNewBlock']='bad'
        with self.assertRaises(AssertionError):check_schema('training-program',p)

    def test_latest_and_pinned_policy_schema(self):
        p=deepcopy(self.programs[0]);ref=p['templateMemberships'][0]['versionPolicy']
        ref['mode']='pinned';ref['revisionId']='revision_demo_v1'
        check_schema('training-program',p)
        ref.pop('revisionId')
        with self.assertRaises(AssertionError):check_schema('training-program',p)

    def test_prior_performance_prefers_program_then_falls_back_with_provenance(self):
        same_prog=deepcopy(self.session)
        same_prog['sessionId']='session_same_program_older'
        same_prog['finishedAt']='2026-10-02T09:00:50+02:00'
        another=read_json('examples/sessions/synthetic-standalone-session.json')
        another['finishedAt']='2026-10-04T09:00:50+02:00'
        latest=choose_prior_performance([same_prog,another],template_id=self.template['templateId'],
            block_id='block_alternating',occurrence_id='occurrence_hip_lift',
            variation_id='hip_lift_level_2',set_id='set_hip_red',program_id='program_corrective')
        self.assertEqual(latest['sessionId'],'session_same_program_older')
        self.assertEqual(latest['seasonId'],'season_2026_2027')
        self.assertFalse(latest['fallback'])
        fallback=choose_prior_performance([another],template_id=self.template['templateId'],
            block_id='block_alternating',occurrence_id='occurrence_hip_lift',
            variation_id='hip_lift_level_2',set_id='set_hip_red',program_id='program_corrective')
        self.assertTrue(fallback['fallback'])
        self.assertEqual(fallback['programId'],'program_standalone_mobility')
        no_cross_template=choose_prior_performance([another],template_id='template_other',
            block_id='block_alternating',occurrence_id='occurrence_hip_lift',
            variation_id='hip_lift_level_2',set_id='set_hip_red',program_id='program_corrective')
        self.assertIsNone(no_cross_template)

    def test_reject_similar_set_from_different_occurrence(self):
        another=read_json('examples/sessions/synthetic-standalone-session.json')
        result=choose_prior_performance([another],template_id=self.template['templateId'],
            block_id='block_alternating',occurrence_id='occurrence_fake',
            variation_id='hip_lift_level_2',set_id='set_hip_red')
        self.assertIsNone(result)

if __name__=='__main__':unittest.main()
