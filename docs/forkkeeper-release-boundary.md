# ForkKeeper debug builds and production boundary

Prepared for parent review against ForkKeeper source
`b9c380bbcdc2dba7816966fd944ca27941c25635` (debug implementation `1a7cc21`).
No commit, push, workflow run, key provisioning, activation or device operation
was performed by this migration. The production `.github/workflows/release.yml`
remains byte-identical, SHA-256
`6d234c3c92750383cb34fed1c3134c1ddd74c7aed5b5364852024955bfcb6b77`.
App source/customizations and Gradle files match accepted main `f28672a9`.
The earlier uncommitted unsigned-staging flag, helper and production template
were removed. Existing tracked staging configuration is historical app code;
ForkKeeper validation exclusively builds debug.

## Build contract

`.github/workflows/forkkeeper-test-build.yml` runs on pushes to
`forkkeeper/setup/**` and `forkkeeper/candidates/**`, only in
`muntedcrocodile/Transcribro`. It has contents:read, no main/PR/manual/release
trigger, no publishing step, and no production, admission, controller or receipt
key input. Only the three explicitly named debug secrets below enter the build
step. The environment must contain only those secrets. Candidate code intentionally
has access to this debug identity during its build; it must never share custody
with production or trusted-service keys.

The control checkout is exactly `github.sha`. Candidate source is that checkout.
Baseline source is a separate immutable checkout pinned in
`forkkeeper/test-baseline.json`:

- SHA `f28672a9d635393e271ba91991272fc1348be2df`;
- tree `5c57b049ba52358eea06f1d517b798db3e411779`;
- baseline version **100003**, candidate **100004** currently reserved;
- stable package `dev.soupslurpr.transcribro.debug`;
- certificate SHA-256
  `54a0974a5fa86212c67416b844943c7184d143df0527f98c72190138b770fcb9`.

Reserve a greater debug version for every later installed candidate, including
versions installed during failed validation. Do not reuse these allocations once
a different APK at that version is installed. Production has its own version lane.

`scripts/forkkeeper-debug-build SOURCE_DIRECTORY VERSION EXACT_SHA` requires a
clean exact source checkout, initialized pinned submodules, no stale debug APK,
a dedicated absolute nonsymlink PKCS12 path and passwords in environment variables.
It runs only:

```bash
./gradlew --no-daemon --no-configuration-cache --no-build-cache \
  --init-script CONTROL_CHECKOUT/forkkeeper/debug-signing.init.gradle \
  :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

The same reviewed init script supplies the persistent debug signing configuration
and allocated version before Android DSL finalization for both sources. It forces
the `.debug` suffix, debuggable=true, no R8/resource shrinking, and CMake3.22.1.
It leaves each source tree unchanged and does not pass the production
forkReleaseVersionCode property. The historical baseline is a **new build with an
explicit build-time override**, never a historical APK or automatic acceptance.
The actual `lib/build.gradle` sets a CMake path without a version; the shared
override uses AGP8.12's [Cmake.version DSL](https://developer.android.com/reference/tools/gradle-api/8.12/com/android/build/api/dsl/Cmake#version)
through `finalizeDsl` so the installed3.22.1 is selected without source edits.
The helper checks the APK's exact package/version, debuggable flag, one signer and
expected certificate with aapt2/apksigner. Those tools inspect only; no postbuild
APK signing, zipalign or rewriting occurs. CI copies and cmp-checks identical bytes.

Each artifact ZIP contains **only `app-debug.apk`**:

- `candidate-debug-CANDIDATE_SHA-RUN-ATTEMPT`;
- `baseline-debug-BASELINE_SHA-RUN-ATTEMPT`.

Separate `debug-provenance-ROLE-SOURCE_SHA-RUN-ATTEMPT` artifacts contain `build.json`.
They record source SHA/tree, control SHA/tree, exact workflow/override/helper and
wrapper hashes, version, package/certificate/debuggable flag, run/attempt/event,
APK digest, native submodule pins, requested toolchain pins, actual java/Gradle/
CMake version output, installed SDK package properties and runner versions. The two
source roles are never interchangeable. `artifact_sha256 == unsigned_sha256` describes
the already signed CI bytes. `accepted_baseline=false` and `historical_apk=false`
are explicit. This descriptive JSON is untrusted build output, not an Ed25519
receipt. The current observer independently verifies the candidate API/ZIP/APK;
it does not automatically authenticate this separate baseline/provenance JSON.
Parent must independently check that JSON against Git/CI facts and artifact bytes.

Required job checks are `debug-baseline` and `debug-candidate`; parent must observe
the actual check names, run URL and CI App ID before pinning policies. All actions
use full commit SHAs. Toolchain pins are Temurin21.0.13+9 x64, Android CLI12266719,
SDK36, build-tools36.0.0, NDK27.2.12479018, CMake3.22.1, Gradle8.14.3 (wrapper ZIP
SHA-256 pinned), AGP8.12.0 and Kotlin2.2.0. The JDK patch pin was independently
checked against [Temurin jdk-21.0.13+9](https://github.com/adoptium/temurin21-binaries/releases/tag/jdk-21.0.13%2B9)
(published 2026-10-22). The hosted ubuntu24.04 image and Android
repository metadata remain externally maintained; this is not a hermetic build
image claim. Independently approve the runner and transitive dependency policy
before admission, and re-review workflow/helper/override changes. Action input
schemas were checked against the pinned [setup-java action](https://github.com/actions/setup-java/blob/b6effb05e454b25005698d916606bdc6ffcbf961/action.yml)
and [setup-android action](https://github.com/android-actions/setup-android/blob/9fc6c4e9069bf8d3d10b2204b1fb8f6ef7065407/action.yml).

## Debug key custody and private provisioning schema

Preserve root-only `/var/lib/forkkeeper-test-signing/test-signing.p12` and
`store-password`, alias `forkkeeper-test`, plus the existing local recovery copy.
The migration does not open or mutate them. Do not generate a replacement or use
Android's ephemeral default debug key. The private debug environment is
`forkkeeper-debug-build`, with exactly these GitHub environment secrets:

- `FORKKEEPER_DEBUG_KEYSTORE_BASE64`: base64 of the existing PKCS12;
- `FORKKEEPER_DEBUG_STORE_PASSWORD`: existing store password;
- `FORKKEEPER_DEBUG_KEY_PASSWORD`: existing key password (same password for this PKCS12).

After parent review, parent creates/reviews the environment, limits its deployment
branches to setup/candidate patterns and decides independent environment approval.
These are command **schemas for the parent's private session**, not executed here:

```bash
# Existing authorized gh transport; never print values or use shell tracing.
set +x
sudo base64 -w 0 /var/lib/forkkeeper-test-signing/test-signing.p12 |
  gh secret set FORKKEEPER_DEBUG_KEYSTORE_BASE64 --env forkkeeper-debug-build --repo muntedcrocodile/Transcribro
sudo cat /var/lib/forkkeeper-test-signing/store-password |
  gh secret set FORKKEEPER_DEBUG_STORE_PASSWORD --env forkkeeper-debug-build --repo muntedcrocodile/Transcribro
sudo cat /var/lib/forkkeeper-test-signing/store-password |
  gh secret set FORKKEEPER_DEBUG_KEY_PASSWORD --env forkkeeper-debug-build --repo muntedcrocodile/Transcribro
```

The workflow decodes the key only into a private runner-temp directory; EXIT and
always-run cleanup remove that directory. It never uploads keys, build reports,
Gradle caches or whole output directories. Hosted runners are disposable. No
production/admission/receipt key may be provisioned to this environment or supplied
to this debug build. Preserve CI production secrets unchanged.

## Parent review, push and artifact inspection

1. Review the full dirty checkout against the existing private snapshots. Verify
   release.yml's hash above and that tracked Gradle/customization files have no
   diff. Review the workflow, helper, override and allocation files together.
2. Provision only the debug environment/secrets through private streams after
   review. Do not dispatch the legacy production workflow.
3. Create an unmerged `forkkeeper/setup/ci-debug-contract` branch from current main,
   selectively stage the seven preparation files listed below, commit and push
   that branch; open a draft PR if desired. A push starts the debug workflow;
   PR builds are deliberately absent. Never push preparation directly to main.
4. Inspect the successful **push** run's exact SHA, workflow ID/content hash, both
   job/check names and App, run ID/attempt, four artifact IDs and expiration. A
   newer failed/pending run invalidates an older successful candidate run.
5. Download each exact artifact separately outside Git using
   `gh run download RUN -R muntedcrocodile/Transcribro -n EXACT_NAME -D PRIVATE_DIR`.
   Inspect the archive member list and artifact digest, verify package/version,
   debuggable flag and certificate independently. Match source/tree and separate
   workflow/helper/override hashes to Git; compare provenance digest to APK bytes.
6. Parent independently accepts/provisions the new baseline, seeds representative
   settings/data and records installed package/certificate/version/APK/provenance.
   A fresh baseline install establishes no upgrade claim. Do not send the baseline
   through candidate admission with invented installed-baseline data.
7. For a managed candidate, use its exact push SHA/CI bytes and reviewed source
   graph with the new standalone debug observer/controller policies. The observer
   signs **only an Ed25519 provenance receipt**, copies APK bytes unchanged, and
   requires the real installed baseline version below the candidate version.
   Device authority performs the authenticated same-identity upgrade/plan/cleanup.
   Source/run/baseline/plan changes invalidate old evidence. Activation belongs to
   the parent and remains blocked until all gates hold.

Preparation files to stage: `.gitignore`,
`.github/workflows/forkkeeper-test-build.yml`,
`docs/forkkeeper-release-boundary.md`, `forkkeeper/test-baseline.json`,
`forkkeeper/test-version-code.txt`, `forkkeeper/debug-signing.init.gradle`,
`scripts/forkkeeper-debug-build` (seven files; no Gradle or production workflow diff).

Concrete parent-only branch/CI commands, **after review and private provisioning**:

```bash
cd /home/user/Documents/transcribro
git switch -c forkkeeper/setup/ci-debug-contract
git add -- .gitignore .github/workflows/forkkeeper-test-build.yml \
  docs/forkkeeper-release-boundary.md forkkeeper/test-baseline.json \
  forkkeeper/test-version-code.txt forkkeeper/debug-signing.init.gradle \
  scripts/forkkeeper-debug-build
git diff --cached --check
git diff --cached --stat
# Review staged content, then:
git commit -m 'Prepare exact CI debug artifacts for ForkKeeper'
git push -u origin forkkeeper/setup/ci-debug-contract
gh run list -R muntedcrocodile/Transcribro --branch forkkeeper/setup/ci-debug-contract --event push
gh run view RUN -R muntedcrocodile/Transcribro
# For each exact APK/provenance artifact, in distinct private directories:
gh run download RUN -R muntedcrocodile/Transcribro -n EXACT_NAME -D PRIVATE_DIR
```

Controller contract: workflow path `.github/workflows/forkkeeper-test-build.yml`,
actual numeric workflow ID and SHA-256 observed after push, build_events `["push"]`,
required checks above, actual CI App ID, artifact pattern
`candidate-debug-{candidate}-{build_run_id}-{build_run_attempt}`,
apk_filename `app-debug.apk`, separate stable debug package/certificate and the
observed installed baseline. Receipt purpose `internal-debug-artifact`; fetched
`unsigned_sha256` equals final `artifact_sha256`. `signed_receipt_sha256` hashes
the service-signed envelope. Observer/controller private keys stay outside builds.

## Production and physical acceptance

The production release workflow remains unchanged: it builds/signs/publishes in
one job, uses mutable action tags and clobbers same-version assets on reruns.
Debug preparation does not repair that boundary. Parent must independently review
production isolation/protection before automatic rollout. No production key
recovery is needed for debug validation. Obtainium continues to track stable
`Transcribro-fork-N.apk` releases of `dev.soupslurpr.transcribro.fork`; debug
artifacts are never published, including as prereleases. A debug upgrade proves
only debug-to-debug behavior, never production certificate compatibility.

The full keep-screen-awake physical gate remains blocked on reviewed broker
primitives and plan/cleanup evidence described in ForkKeeper's
`docs/TRANSCRIBRO-ENROLLMENT.md`. Static checks and successful CI alone cannot
substitute for physical evidence. Local validation results are recorded there.

## Initial environment provisioning record

The parent independently reviewed this preparation, fixed the helper package check
to compare identity literally, and verified the reported wildcard reproduction plus
five additional identity cases. The initial `forkkeeper-debug-build` environment
is restricted to the exact branch `forkkeeper/setup/ci-debug-contract`. Broader
candidate branch admission requires a separate reviewed policy update. Only the
three dedicated debug secrets above were provisioned through private streams;
production secrets were preserved. CI artifacts and physical acceptance remain
unverified until the corresponding runs/tests complete.
