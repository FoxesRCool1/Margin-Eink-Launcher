# Making a release

A release is a set of APK files on the GitHub Releases page. A GitHub Actions
workflow makes them when a tag is pushed. The app on the tablet finds the
release by itself: Settings, About, Updates.

## Each release: one command

```
tools/release.sh 0.1.1 "What changed, in one line."
```

It does five things:

1. Sets `appVersionName` in `app/build.gradle.kts`. The version code is worked
   out from it, so there is no second number to raise.
2. Adds a `## 0.1.1` part to the top of `docs/RELEASE_NOTES.md`. For more than
   one line, write that part yourself first and leave the second argument out.
3. Commits both files.
4. Makes the tag `v0.1.1`.
5. Pushes the branch and the tag.

Then watch the "Release" workflow. It takes about 15 minutes. When it is
done, press "Check for updates" on the tablet.

The script refuses to run with changes that are not committed, with a version
lower than the current one, and with a tag that exists already. The workflow
refuses a tag that does not match `appVersionName`.

## What a release holds

| Release key in the GitHub secrets | Files |
| --- | --- |
| Yes (this repository, since 1.0.0) | `margin-v1.0.0-viwoods.apk`, `margin-v1.0.0-generic.apk` |
| No (a fork) | `margin-v1.0.0-viwoods-debug.apk`, `...-generic-debug.apk` |

Plus `SHA256SUMS.txt` every time. Every release is a normal release, so the
newest one shows as "Latest" on the front page of the repository.

A debug file is signed with the fixed key in `keystore/debug.keystore`. That
key is public, so anyone can sign a file with it. A release file is signed
with the release key, which only the owner has. **One does not install over
the other.** That is why a release of this repository holds no debug file
since 1.0.0: nobody but the owner can make an update for what people install.
Decision 0022.

The updater knows the two apart by the end of the file name: a debug build
only ever takes a debug file, and a release build only ever takes a release
file. A release build also leaves alone any release that you mark as
"Pre-release" by hand on GitHub.

The workflow checks every release file before it publishes it. It must be
signed, and with the release key of this project. Its SHA-256 fingerprint:

```
CF:1A:38:65:62:EE:A9:22:FE:7E:43:18:74:B0:15:96:1F:DF:CE:61:8B:38:2E:EC:02:16:DD:87:EE:0E:FD:B6
```

A fork with its own key changes `RELEASE_CERT_SHA256` in
`.github/workflows/release.yml`.

A debug build from the dev machine still works for development:
`tools/deploy.sh`, and the Dev panel. It does not find updates on GitHub any
more, because the releases hold no debug file.

Do not change the file names without changing `UpdateAssets` in
`core/update/ReleaseInfo.kt`. The app picks its file by the end of the name.

## A token is only for a private fork

The repository is public since 2026-09-22, so the app on the tablet needs no
token: GitHub shows the releases to everyone. The token screen is still in
the app, for anyone who runs the updater against a private fork. It shows
only after a check fails with "not found".

If you need it: on GitHub make a fine-grained personal access token for that
one repository with "Contents" set to "Read-only", then on the tablet,
Settings, About, Updates, "Enter access token". Such a token can
read the source of that repository, so give it an expiry date. It is kept in
the private storage of the app, outside every backup, it is never written to
the log, and it is only ever sent to `api.github.com`.

## The release key

Made on 2026-09-22, for 1.0.0. The key is not in this repository and must
never be. Whoever holds it can publish an update that installs over the app.
If it is lost, no update can reach anyone: every user has to uninstall and
install again. **Only the owner has it.** It lives in two files on the dev
machine, outside the repository:

```
~/margin-release/margin-release.keystore   the key
~/margin-release/password.txt              its password
```

Keep a copy of both somewhere safe that is not this computer, such as a
password manager. The same password opens the keystore and the key.

If it ever has to be made again (a fork, say):

```
mkdir -p ~/margin-release && cd ~/margin-release
python3 -c 'import secrets; print(secrets.token_urlsafe(32))' > password.txt
keytool -genkeypair -noprompt \
  -keystore margin-release.keystore -storetype PKCS12 \
  -alias margin -keyalg RSA -keysize 4096 -validity 10000 \
  -dname "CN=<your GitHub name>, O=Margin" \
  -storepass:file password.txt -keypass:file password.txt
```

In the repository on GitHub: Settings, then "Secrets and variables", then
Actions, then "New repository secret". Make these four.

| Secret | Value |
| --- | --- |
| `EINK_RELEASE_KEYSTORE_BASE64` | The output of `base64 -w0 ~/margin-release/margin-release.keystore` |
| `EINK_RELEASE_STORE_PASSWORD` | The password |
| `EINK_RELEASE_KEY_ALIAS` | `margin` |
| `EINK_RELEASE_KEY_PASSWORD` | The same password |

Or from the dev machine, with the password never shown:

```
R=FoxesRCool1/Margin-Eink-Launcher
base64 -w0 ~/margin-release/margin-release.keystore | gh secret set EINK_RELEASE_KEYSTORE_BASE64 -R $R
tr -d '\n' < ~/margin-release/password.txt | gh secret set EINK_RELEASE_STORE_PASSWORD -R $R
tr -d '\n' < ~/margin-release/password.txt | gh secret set EINK_RELEASE_KEY_PASSWORD -R $R
printf 'margin' | gh secret set EINK_RELEASE_KEY_ALIAS -R $R
```

## A signed build on the dev machine

```
export EINK_RELEASE_STORE_FILE=~/margin-release/margin-release.keystore
export EINK_RELEASE_STORE_PASSWORD="$(cat ~/margin-release/password.txt)"
export EINK_RELEASE_KEY_PASSWORD="$EINK_RELEASE_STORE_PASSWORD"
export EINK_RELEASE_KEY_ALIAS=margin
./gradlew assembleViwoodsRelease
```

Without those four values the same command makes an unsigned APK, which
Android will not install.

## Things to know

- Moving a tablet from a debug build to the release build means an
  uninstall, and an uninstall deletes the data folder. Back up first:
  Settings, Files, Back Up. Restore after the new install.
- Right after an update Android may show the stock launcher, because for a
  moment this app was not there to be the home screen. Press the Home key.
- A build from the dev machine and a build from GitHub of the same version
  have the same version code, so either installs over the other. A lower
  version never installs over a higher one.
- The app is not on the Play Store and there is no plan for it. Decision
  0022.
- Code shrinking (R8) is off. See the comment in `app/build.gradle.kts`.
