# Contributing

Thanks for helping improve Verbum. Focused bug fixes, documentation improvements, accessibility fixes, and well-scoped feature changes are welcome.

## Before opening a pull request

1. Check existing issues and pull requests for related work.
2. Keep changes focused and explain the user problem they solve.
3. Run the relevant checks locally:

   ```sh
   ./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
   ```

4. For database, catalog, or scripture changes, document the origin, license, attribution, language, and complete book count. Do not describe a 66-book source as a complete Catholic edition.
5. Never commit signing keys, credentials, personal data, or files covered by a license that does not permit the intended use.

## Reporting a bug

Include steps to reproduce, expected and actual behavior, Android version, device or emulator, and relevant logs or screenshots. Remove personal information from attachments.

## Pull request expectations

- Describe the change and why it is needed.
- Note how it was verified and list any known limitations.
- Include screenshots for visible UI changes when practical.
- Keep generated build output and local machine configuration out of the change.

## Scripture and liturgical data

Text and data rights are separate from application code. Before proposing a dataset, provide the exact upstream source and version, license or written permission, required attribution, format, language, book coverage, and any approval authority being claimed. Do not infer Catholic approval solely from a 73-book count.
