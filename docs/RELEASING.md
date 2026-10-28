# Releasing Verbum

This guide describes how to prepare and publish a Verbum Android release. Version **0.1.4** is the first intended public release.

## Before publishing

- Confirm the Android package builds and the unit tests and lint pass:

  ```sh
  ./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
  ```

- Review `CHANGELOG.md`, `docs/releases/0.1.4.md`, and the in-app Bible catalog for accurate feature, limitation, source, and license details.
- Confirm redistribution rights and required notices for every scripture edition and bundled asset.
- Decide and add a software license for the Verbum application source. No app-code license is included yet.
- Configure a release signing key outside the repository. Never commit the keystore or its password.
- Test the signed release build on supported Android versions, including widget setup and offline reading.

## Version and tag

The app version is set in `app/build.gradle.kts`. Keep `versionName` and the release notes aligned. Increment `versionCode` for every subsequent Android release. For version 0.1.4, create the annotated tag `v0.1.4` only after release checks and signing are complete.

## Signing

The checked-in Gradle configuration does not contain a release signing key or signing credentials. Configure a protected release signing configuration in the publishing environment before generating an installable release APK or Play Store bundle. Store secrets in the CI provider's secret store or a developer-only ignored properties file. The debug APK is for development and must not be presented as the signed product release.

## GitHub release

1. Push the reviewed `v0.1.4` tag.
2. Build and verify the signed APK and/or Android App Bundle.
3. Create a GitHub release for `v0.1.4` using `docs/releases/0.1.4.md` as the notes draft.
4. Attach only the verified, signed distribution files and include checksums.
5. State supported Android versions, known limitations, and the signing status in the release description.

The repository includes CI for pull requests and pushes. CI builds and tests the debug variant; it does not sign or publish releases.
