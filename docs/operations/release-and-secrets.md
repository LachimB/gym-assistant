# Release operations and credential handling

- M0 only local or explicitly approved preview deployments, no covert hosting. Later GitHub Actions builds source and tests, creates signed APK (signing secret stored in appropriate authorized CI secret vault) and updates web staging. Google Play testing track only when publisher account and costs explicitly approved.
- Keep separate application IDs and OAuth credentials for staging and prod. Signing-key recovery, secret rotation, emergency rollback and data migration rollback must be documented and tested before production.
- Never include keys in APK/web bundle beyond public OAuth client IDs that are *designed* to be non-secret; never embed AI provider keys. API key safety is not achieved by hiding the JavaScript UI.
- All external services and any billable resource require appropriate separate user authorization. Avoid actual cost projection guesses: review current pricing/limits during provider evaluation.
- Manual push and deployment need `tools/preflight_personal_github.sh` to pass using an explicit personal Git author email and repository owner. Do not use a company's global Git config accidentally.
- CI must not access private training materials; synthetic fixture only. Observability logs redact tokens and personal notes.
