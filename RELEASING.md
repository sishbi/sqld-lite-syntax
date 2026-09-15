# Releasing

How to cut a release of SqlD-Lite Syntax and publish it to the JetBrains Marketplace.

## How the pipeline works

Two workflows do the work. Nothing publishes without a manual step.

1. `.github/workflows/build.yml` runs on every push to `main`. After `buildPlugin`, `check` and
   `verifyPlugin` pass, the `releaseDraft` job deletes any existing draft release and creates a new
   **draft** release. The tag is the `version` property from `gradle.properties`. The release notes
   are the `## [Unreleased]` section of `CHANGELOG.md`.
2. `.github/workflows/release.yml` runs when you **publish** that draft. It patches the changelog,
   runs `signPlugin` and `publishPlugin`, uploads the ZIP as a release asset, raises the patch
   version, and opens a pull request carrying both the patched `CHANGELOG.md` and the new version.

So: merge to `main`, then publish the draft. The draft is the gate.

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

### 2. Create a Marketplace permanent token

In <https://plugins.jetbrains.com/author/me/tokens>, create a token with the **Marketplace** scope.
Save it as the repository secret `PUBLISH_TOKEN`.

### 3. Create a signing certificate

The Marketplace requires a signed plugin. Follow
<https://plugins.jetbrains.com/docs/intellij/plugin-signing.html> to generate a private key and a
self-signed certificate chain, then save three repository secrets:

| Secret | Holds |
|---|---|
| `PRIVATE_KEY` | The private key, PEM, including the `BEGIN`/`END` lines. |
| `PRIVATE_KEY_PASSWORD` | The password for that key. |
| `CERTIFICATE_CHAIN` | The certificate chain, PEM. |

### 4. Wire the secrets into the build

`build.gradle.kts` does not yet read them. `release.yml` puts all four in the environment, but
without this block `publishPlugin` fails with no token specified. Add to the `intellijPlatform`
block:

```kotlin
signing {
    certificateChain = providers.environmentVariable("CERTIFICATE_CHAIN")
    privateKey = providers.environmentVariable("PRIVATE_KEY")
    password = providers.environmentVariable("PRIVATE_KEY_PASSWORD")
}

publishing {
    token = providers.environmentVariable("PUBLISH_TOKEN")

    // A version with a dash publishes to a channel of that name, so 1.0.0-beta.1 goes to "beta".
    // A stable version publishes to the default channel.
    channels = listOf(providers.gradleProperty("version").map {
        it.substringAfter('-', "").substringBefore('.').ifEmpty { "default" }
    }.get())
}
```

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
   Check the tag and the notes.
7. Publish the draft. This triggers `release.yml`.
8. Check the `Release` workflow passed, then check the version appears on the plugin's Marketplace
   page. A new version is reviewed automatically and appears within minutes, not days.
9. Merge the `Release follow-up - <version>` pull request the workflow opened. It holds the patched
   changelog and the bumped patch version, so `main` is ready for the next release.

## If the release workflow fails

The draft is already published, so the tag exists. Fix the cause, then either re-run the failed job
from the Actions page, or publish the plugin by hand:

```bash
./gradlew signPlugin publishPlugin
```

Both tasks need the four secrets in the local environment. Never commit them.
