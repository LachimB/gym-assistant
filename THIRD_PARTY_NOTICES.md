# Third-party licenses and release obligations

The root `LICENSE` reserves rights only in original project material. It does not
relicense dependencies, the Gradle wrapper or upstream license/notice texts.
MIT remains a possible future decision, not the current project license.

## Review performed for this remediation

- Both npm lockfiles declare a license for every dependency entry. The combined
  inventory has183 distinct package/version/license entries, including tooling,
  optional platform packages and transitive packages. Declared licenses include
  MIT, ISC, BSD-3-Clause, Apache-2.0 and CC-BY-4.0. See the
  [versioned inventory](docs/operations/third-party-inventory.md).
- Cached direct JVM/Android POMs identify Apache licensing for Kotlin-related,
  YAML and AndroidX components inspected. Google Play Services Auth declares the
  Android Software Development Kit License; do not describe it as MIT or assume
  every Android dependency is open source.
- The tracked Gradle wrapper JAR contains its upstream Apache license; preserve
  that embedded text. Android packaging now merges upstream license/notice
  resources instead of explicitly excluding AL2.0/LGPL2.1 resources.
- This is a source/metadata review. It is not clearance of all transitive Android
  components, every upstream NOTICE or an eventual assembled APK/web distribution.
  No general-use binary release is approved by this document.

## Required before any general-use or binary release

1. Resolve and record the exact shipped dependency graph for Android and web;
   separate runtime/bundled code from development-only tools and platform variants.
2. Collect the original license/copyright texts and NOTICE files for each shipped
   component. Preserve upstream attribution verbatim; review CC-BY attribution
   requirements for any included data and SDK-specific redistribution terms.
3. Verify the actual release artifact contains or accompanies all required texts,
   including after minification, bundling, resource merging and packaging. Provide
   an accessible third-party notices document with the release.
4. Stop release on missing/unknown terms or an unmet notice requirement. Do not
   replace upstream licensing with the project license or silently remove notices.
5. Obtain the owner's explicit license decision for original code before applying
   MIT or another reuse license. Review new/changed dependencies on every PR.

Authoritative references:
[MIT license](https://opensource.org/license/mit),
[Apache2.0 redistribution requirements](https://www.apache.org/licenses/LICENSE-2.0.html),
[CC-BY4.0 terms](https://creativecommons.org/licenses/by/4.0/),
[Android SDK terms](https://developer.android.com/studio/terms.html).

`python3 tools/check_license_inventory.py` and privacy CI reject dependency-input
or license-inventory drift until an explicit review record is updated. Updating
that record does not itself clear binary redistribution; required notices and
SDK terms still need the release-specific review above.
