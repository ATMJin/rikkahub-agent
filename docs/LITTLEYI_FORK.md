# LittleYi mobile-agent fork: first implementation slice

Historical milestone only: the full-app build below has been superseded by the
four-module phone-only build. See [MOBILE_ONLY.md](MOBILE_ONLY.md) for current commands,
capabilities, validation and limitations. `:app` now maps to `mobile-app`, not `app/`.

Upstream: https://github.com/ExTV/rikkahub-agent

Baseline commit: a88f5854b4d2e2706ea2ae8304a7d66a3f9f2bbc

Requirements: DEVELOPMENT_PLAN.md v0.2, 2026-10-24. Voice wake and speaker verification
remain required, but are intentionally deferred until the text-driven agent works.
The upstream AGPL-3.0 license is retained.

## Implemented in this slice

- Resolve the chat/agent model at the start of each turn from actual network evidence.
- Online: use only the explicitly selected enabled cloud provider. No automatic provider
  or billing change after a failure; a selected on-device provider is rejected.
- Offline: choose the enabled LiteRT provider's installed/configured
  `gemma-4-E2B-it.litertlm` model. Do not overwrite the saved cloud-model choice.
- Captive portal, network validation in progress or missing network permission: stop with
  an explicit error rather than assume offline.
- Guard text inference at ProviderManager, including auxiliary text calls and stream
  recollection/retries. Model listing/downloading remains available online.
- Require fresh confirmation for message sends, notification mutations, deletion, settings
  changes and generic screen/shell actions. Standing grants, YOLO and headless execution
  cannot auto-approve these tools, including Auto siblings resumed after another approval.
- Plain `launch_app` and read-only notification queries do not prompt unnecessarily.
- Notification-tool access requires a positively unlocked device/keyguard before lookup
  and again before returning results; an unavailable lock-state service fails closed.

## Manual setup for the first device test

1. Add/sign in to a cloud provider, enable it, and select its chat model. Existing Codex
   authentication is inherited upstream code, not a newly verified account integration.
2. Download Gemma 4 E2B in Local · LiteRT and enable that provider. Keep the cloud model
   selected as the normal chat model. No model weights are bundled or copied from PokeClaw.
3. Enable only the app-launch/settings/notification tools needed for testing. Notification
   access and Accessibility require the device owner's system authorization.
4. Online, submit a text task. In airplane mode, submit a new task and verify E2B is used.
5. Re-enable Internet; the next task must use the original cloud model. Force 401/429/5xx/
   timeout errors while online and verify no local inference starts.

## Not yet implemented or verified

- Background keyword spotting, speaker enrollment, Mandarin ASR, screen-off/Doze operation.
- Notification-content redaction and a complete lock-screen privacy boundary (including
  screen-reader, stored conversation and optional background-forwarding paths). Do not use
  this development build for unattended private-notification processing yet.
- Per-action semantic risk classification; generic UI actions currently prompt conservatively.
- A hardened confirmation UI that cannot be manipulated by Accessibility, confirmation
  expiry/parameter binding, and complete audit of nested workflows and third-party tools.
- Mid-turn network handover. Inference guards stop the next incompatible model request;
  already-started requests are not migrated or replayed. A new turn reselects the model.
- Global image/embedding routing, independent tool network access and optional TTS routing.
  The strict routing changes in this slice apply to text LLM inference.
- Device behavior, subscription entitlement and GPU health.

## Validation

Results recorded on Windows, 2026-10-24:

- `:ai:testDebugUnitTest`: passed, 319 tests in 37 suites, zero failures/errors.
  Includes 16 new routing/provider-guard tests.
- Full `:app:assembleDebug` attempt: blocked before APK creation. The Windows user
  compatibility registry sets `C:\Windows\System32\cmd.exe` to `RUNASADMIN`; CMake/Ninja
  cannot launch it from the non-elevated Gradle process (Windows error 740). No UAC or
  compatibility settings have been changed. The owner chose to retain the setting and
  handle it personally; do not alter it automatically when resuming this task.
- App JVM tests are run separately with `-x :web:buildWebUi`, since those tests do not
  exercise generated Web UI assets or native libraries. Passed: 30 tests in four suites,
  zero failures/errors; the App's Kotlin and Java sources also compiled successfully.

App test task (after setting `ANDROID_HOME` to the local SDK):

```text
:app:testDebugUnitTest --tests '*ToolApprovalDefaultsTest' --tests '*NotificationAccessPolicyTest' --tests '*GenerationLoopResumeToolSelectionTest' --tests '*GenerationHandlerTransportRetryTest' -x :web:buildWebUi
```

Source review and unit tests alone are not evidence of a working APK or a safe autonomous
phone agent. Device installation/testing remains outstanding.
