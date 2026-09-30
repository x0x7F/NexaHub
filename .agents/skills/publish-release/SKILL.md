---
name: publish-release
description: Use this skill when users request to publish an release update.
---

# Publish Release

Generate a changelog from the git log, then create a new release.

## Changelog

- Review the changes from the last release tag to the current commit
- Summarize the changes into a changelog; merge bug fixes and UI tweaks where possible, and keep the total to no more than 10 entries
- Avoid technical jargon in the changelog

Use this format:

```markdown
Updates:

- xxx
- xxx
```

After generating the changelog, ask the user to confirm it looks right, and only create the release once the user confirms it can be published.

## Publishing

Use the GitHub CLI to create the release and upload the app/release/xxx.apk file.

- Only upload the arm64 build; before uploading, rename the APK to include the version number in the file name
- Use the version number as the release title and the changelog as the description
- Use the version number as the tag name, without a leading v
