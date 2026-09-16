# Releasing

How to cut a release of SqlD-Lite Syntax and publish it to the JetBrains Marketplace.

## How the pipeline works

Two workflows do the work. Nothing publishes without a manual step.

1. `.github/workflows/build.yml` runs on every push to `main`. After `buildPlugin`, `check` and
   `verifyPlugin` pass, the `releaseDraft` job deletes any existing draft release and creates a new
   **draft** release, with the plugin ZIP attached. The tag is the `version` property from
   `gradle.properties`. The release notes are the `## [Unreleased]` section of `CHANGELOG.md`.
2. `.github/workflows/release.yml` runs when you **publish** that draft. It patches the changelog,
   runs `signPlugin` and `publishPlugin`, raises the patch version, and opens a pull request
   carrying both the patched `CHANGELOG.md` and the new version.

So: merge to `main`, then publish the draft. The draft is the gate.

### Release immutability

The repository has **Enable release immutability** switched on, so the assets and the tag of a
published release cannot be changed. Two consequences:

- The ZIP is attached to the **draft**, in step 1. Nothing may add an asset after publication. Do
  not move that upload into `release.yml`, which runs after the release is already published.
- A published release cannot be corrected. Check the draft's tag, notes and attached ZIP before you
  publish it. To fix a mistake, release the next patch version.

Deleting drafts is unaffected, because a draft is not a published release.

## One-time setup

Do these once. Until all of them are done, `release.yml` fails at the publish step.

### 1. Upload the first version by hand

The Marketplace will not accept an API upload for a plugin it does not know. Build the ZIP and
upload it through the web form:

```bash
./gradlew buildPlugin   # writes build/distributions/sqld-lite-syntax-<version>.zip
```

Go to <https://plugins.jetbrains.com/plugin/add>, upload the ZIP, and wait for moderation. JetBrains
reviews a new plugin by hand, which takes a few working days. Every later version publishes through
the API.

### 2. Add the screenshots

Screenshots are Marketplace metadata, not part of the plugin. Nothing in `plugin.xml`, the ZIP or
`publishPlugin` carries them, and an `<img>` tag in the plugin description is stripped. Upload them
on the plugin page, under Edit Plugin, once moderation has accepted the first version. They are
plugin-level, so every later release keeps them. The files are in `docs/images/`.

### 3. Create a Marketplace permanent token

In <https://plugins.jetbrains.com/author/me/tokens>, create a token with the **Marketplace** scope.
Save it as the repository secret `PUBLISH_TOKEN`.

### 4. Create a signing certificate

The Marketplace requires a signed plugin. Follow
<https://plugins.jetbrains.com/docs/intellij/plugin-signing.html> to generate a private key and a
self-signed certificate chain, then save three repository secrets:

| Secret | Holds |
|---|---|
| `PRIVATE_KEY` | The private key, PEM, including the `BEGIN`/`END` lines. |
| `PRIVATE_KEY_PASSWORD` | The password for that key. |
| `CERTIFICATE_CHAIN` | The certificate chain, PEM. |

### 5. Wire the secrets into the build

Done. The `signing` and `publishing` blocks in `build.gradle.kts` read all four environment
variables `release.yml` sets. Nothing to do here; the note remains so the reason is on record.

Without those blocks the build ignored the secrets entirely: `signPlugin` was `SKIPPED` and
`publishPlugin` failed with `'token' property must be specified for plugin publishing`, which is
what the 0.1.0 release run hit.

`signPlugin` is skipped whenever no certificate is configured, so a local build never signs. To
check the wiring rather than the key, run it with any non-empty values: the task then runs and fails
on the key itself instead of being skipped.

## Versioning

`version` in `gradle.properties` is always the version of the **next** release, as a plain
`MAJOR.MINOR.PATCH`. Never add a `-SNAPSHOT` or any other suffix: the draft release is tagged with
this string verbatim, and a suffix publishes to a Marketplace channel of that name instead of the
default one. Both workflows refuse a version that is not three plain numbers.

After each release the `Bump Patch Version` step raises the patch and puts it in the follow-up pull
request, so the next version is ready with no action from you.

Semantic versioning needs a person to judge a minor or a major bump, so those are manual. Before you
merge to `main`, edit `version` in `gradle.properties` yourself when the work is:

- a new feature or a new IDE build in the supported range, which is a **minor** bump;
- a breaking change, such as raising `sinceBuild` and dropping support for an older IDE, which is a
  **major** bump.

A bug fix needs no edit. The automatic patch bump already covers it.

## Each release

1. Decide the version. A bug fix needs nothing; a feature or a break needs a hand edit to
   `gradle.properties`, as above.
2. Move the finished entries in `CHANGELOG.md` from `## [Unreleased]` into shape for the release.
   The `releaseDraft` job reads only the `[Unreleased]` section, so anything left below it is not in
   the release notes.
3. Check that `sinceBuild` and `untilBuild` in `gradle/libs.versions.toml` still describe the range
   you verified. `untilBuild` caps the IDE builds the plugin claims to support. Raising `sinceBuild`
   is a breaking change and needs a major bump.
4. Run the gates locally:

   ```bash
   ./gradlew test
   ./gradlew verifyPlugin   # must report Compatible
   ```

5. Merge to `main`. Wait for the `Build` workflow to finish.
6. Open <https://github.com/sishbi/sqld-lite-syntax/releases>. Read the draft the workflow created.
   Check the tag, the notes and the attached ZIP. This is the last chance: immutability makes a
   published release permanent. Install the ZIP in a real IDE if the release is significant.
7. Publish the draft. This triggers `release.yml`.
8. Check the `Release` workflow passed, then check the version appears on the plugin's Marketplace
   page. A new version is reviewed automatically and appears within minutes, not days.
9. Merge the `Release follow-up - <version>` pull request the workflow opened. It holds the patched
   changelog and the bumped patch version, so `main` is ready for the next release.

## If the release workflow fails

The draft is already published, so the tag exists and cannot be moved. Fix the cause, then either
re-run the failed job from the Actions page, or publish the plugin by hand:

```bash
./gradlew signPlugin publishPlugin
```

Both tasks need the four secrets in the local environment. Never commit them.

Re-running is safe: no step in `release.yml` touches the release or the tag. If the fix needs a code
change, it cannot go out under this tag. Merge it and release the next patch version.
