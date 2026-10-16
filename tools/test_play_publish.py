import contextlib
import copy
import io
import unittest
from unittest.mock import MagicMock
import play_publish as play


class PlayReleaseSafetyTests(unittest.TestCase):
    def setUp(self):
        self.source = {'name': '37 (3.2.8)', 'versionCodes': ['37'], 'status': 'completed',
                       'releaseNotes': [{'language': 'en-US', 'text': 'Reviewed notes'}],
                       'inAppUpdatePriority': 2}
        self.tracks = [{'track': 'internal', 'releases': [self.source]},
                       {'track': 'production', 'releases': [{'versionCodes': ['36'], 'status': 'completed'}]}]
        self.service = MagicMock()
        self.edits = self.service.edits.return_value
        self.edits.insert.return_value.execute.return_value = {'id': 'owned-edit'}
        self.edits.tracks.return_value.list.return_value.execute.return_value = {'tracks': self.tracks}
        self.edits.bundles.return_value.list.return_value.execute.return_value = {'bundles': [{'versionCode': 37}]}
        self.edits.apks.return_value.list.return_value.execute.return_value = {'apks': []}
        self.output = io.StringIO()

    def run_mode(self, argv, identity=None):
        with contextlib.redirect_stdout(self.output):
            return play.run_edit(self.service, play.parse_args(argv), identity, MagicMock())

    def assert_no_release_writes(self):
        self.edits.bundles.return_value.upload.assert_not_called()
        self.edits.tracks.return_value.update.assert_not_called()
        self.edits.commit.assert_not_called()

    def test_promotes_existing_exact_release_without_upload(self):
        original = copy.deepcopy(self.source)
        self.run_mode(['--promote-code', '37', '--track', 'production'])
        self.edits.bundles.return_value.upload.assert_not_called()
        update = self.edits.tracks.return_value.update.call_args.kwargs
        self.assertEqual(update['body'], {'track': 'production', 'releases': [original]})
        self.assertEqual(self.source, original)
        self.edits.validate.assert_called_once()
        self.edits.commit.assert_called_once_with(packageName=play.PACKAGE_NAME, editId='owned-edit', changesNotSentForReview=False)
        self.edits.delete.assert_not_called()
        self.assertIn('public availability is NOT yet verified', self.output.getvalue())
        self.assertNotIn('Managed publishing is ON', self.output.getvalue())

    def test_status_is_read_only_and_cleans_own_edit(self):
        self.run_mode(['--status'])
        self.assert_no_release_writes()
        self.edits.delete.assert_called_once_with(packageName=play.PACKAGE_NAME, editId='owned-edit')
        self.assertIn('Not established', self.output.getvalue())

    def test_promotion_dry_run_does_not_write(self):
        self.run_mode(['--promote-code', '37', '--track', 'production', '--dry-run'])
        self.assert_no_release_writes()
        self.edits.delete.assert_called_once()

    def test_upload_dry_run_does_not_consume_version_code(self):
        self.run_mode(['--aab', 'candidate.aab', '--track', 'internal', '--dry-run'], (38, '3.2.9'))
        self.assert_no_release_writes()

    def test_already_uploaded_version_rejected_before_upload(self):
        with self.assertRaisesRegex(ValueError, 'maximum 37'):
            self.run_mode(['--aab', 'candidate.aab', '--track', 'production'], (37, '3.2.8'))
        self.assert_no_release_writes()

    def test_apk_codes_also_guard_new_upload(self):
        self.edits.apks.return_value.list.return_value.execute.return_value = {'apks': [{'versionCode': 39}]}
        with self.assertRaisesRegex(ValueError, 'maximum 39'):
            self.run_mode(['--aab', 'candidate.aab', '--track', 'production'], (38, '3.2.9'))
        self.assert_no_release_writes()

    def test_repeat_does_not_resubmit_or_reset_review(self):
        self.tracks[1]['releases'] = [copy.deepcopy(self.source)]
        self.run_mode(['--promote-code', '37', '--track', 'production'])
        self.assert_no_release_writes()
        self.assertIn('already configured', self.output.getvalue())

    def test_newer_target_fails_without_mutation(self):
        self.tracks[1]['releases'][0]['versionCodes'] = ['38']
        with self.assertRaisesRegex(ValueError, 'newer'):
            self.run_mode(['--promote-code', '37', '--track', 'production'])
        self.assert_no_release_writes()

    def test_missing_or_multi_artifact_source_cannot_silently_select_other_build(self):
        for codes in [['38'], ['36', '37']]:
            with self.subTest(codes=codes):
                self.source['versionCodes'] = codes
                with self.assertRaisesRegex(ValueError, 'exactly one'):
                    self.run_mode(['--promote-code', '37', '--track', 'production'])
        self.assert_no_release_writes()

    def test_draft_source_is_not_promoted(self):
        self.source['status'] = 'draft'
        with self.assertRaisesRegex(ValueError, 'completed'):
            self.run_mode(['--promote-code', '37', '--track', 'production'])
        self.assert_no_release_writes()

    def test_failed_validation_never_commits(self):
        self.edits.validate.return_value.execute.side_effect = ValueError('validation failed')
        with self.assertRaisesRegex(ValueError, 'validation failed'):
            self.run_mode(['--promote-code', '37', '--track', 'production'])
        self.edits.commit.assert_not_called()
        self.edits.delete.assert_called_once()

    def test_bad_options_rejected_before_network(self):
        for argv in [['--promote-code', '37'], ['--promote-code', '37', '--track', 'production', '--rollout', 'nan'],
                     ['--promote-code', '37', '--track', 'production', '--rollout', '1'],
                     ['--promote-code', '37', '--track', 'internal', '--rollout', '.1'],
                     ['--promote-code', '37', '--track', 'production', '--name', 'override'],
                     ['--status', '--track', 'production']]:
            with self.subTest(argv=argv), contextlib.redirect_stderr(io.StringIO()), self.assertRaises(SystemExit):
                play.parse_args(argv)

    def test_explicit_fraction_preserves_release_metadata(self):
        release = play.plan_promotion(self.tracks, 'internal', 'production', 37, .1)
        self.assertEqual(release['status'], 'inProgress')
        self.assertEqual(release['userFraction'], .1)
        self.assertEqual(release['releaseNotes'], self.source['releaseNotes'])


if __name__ == '__main__':
    unittest.main()
