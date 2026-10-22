# 0012. The app updates itself from GitHub Releases

Date: 2026-09-20. Asked for by the owner before the first tablet test.

## The problem

`adb install` does not work on the tablet. Until now a new build reached it
in one of two ways: `tools/deploy.sh` with the dev machine switched on and on
the same network, or a download by hand in the tablet browser. Every fix
found in the tablet tests would have cost that trip again.

## The decision

The app looks at the GitHub Releases of its own repository, downloads the
APK that fits it, checks it, and hands it to Android to install.

Settings, About, "Updates". One press checks. One more press
downloads and installs. Android still shows its own "install this update?"
window, and the app does not try to get round that.

`tools/release.sh 0.1.1 "what changed"` makes a release from the dev machine:
it sets the version, writes the notes, commits, tags and pushes. The Release
workflow builds the files. Nobody edits a version code by hand any more.

## This changes a promise, so here is exactly how

Until this change the release build had no internet permission at all. Now
every build has `INTERNET` and `REQUEST_INSTALL_PACKAGES`. What keeps the
promise honest:

- The app goes online **only when the user presses the button**. There is no
  background check, no timer and no check at start-up.
- It talks to GitHub and to nothing else: one GET for the list of releases
  and one GET for the file. It sends no data about the user or the tablet.
- `UrlConnectionHttp` refuses any address that is not https, at every hop of
  a redirect. Plain http is allowed in debug builds only, for the LAN update.
- The EPUB reader shows book content in a web view. With the internet
  permission a book could make that web view fetch a picture from a server.
  The reader now blocks network loads in every web view it shows, so a book
  still cannot phone home. See `EpubReaderActivity`.
- The About page and the README say what the app does now, in the same words.

## The parts

| Part | Job |
| --- | --- |
| `AppVersion` | Reads `v0.1.2` and `0.1.2-viwoods`. The version code is `major * 10000 + minor * 100 + patch`, the same sum as in `app/build.gradle.kts`. |
| `ReleaseParser`, `UpdateAssets` | Read the list GitHub sends and pick the file for this build. |
| `GithubReleases` | The check and the download. No Android class, so plain tests cover it. |
| `UrlConnectionHttp` | The network, on `HttpURLConnection`. No new library. |
| `ApkCheck` | Looks at the file before Android does, to give a reason in words. |
| `ApkInstaller`, `InstallStatusReceiver` | The install, and what Android said about it. |
| `UpdateManager` | Holds the state outside any screen, so the Home key does not stop a 60 MB download. |
| `UpdateScreen` | Shows the state. Nothing else. |

## Choices inside it

**Which file a build takes.** The workflow names the files
`...-<flavour>.apk` and `...-<flavour>-debug.apk`. A debug build only takes a
debug file, and a release build only takes a release file, because the two
have different signing keys and Android refuses to put one over the other.
The tablet runs the debug build while the app is being tested: the device
test, the ink baseline and the storage measurement are debug only.

**A release without the release key.** The workflow used to stop when the key
was missing. Now it builds the debug files only. The debug key is fixed and
in the repository, so those files always install over each other. That lets
the owner test on the tablet today and make the release key later.

Such a release was marked as a pre-release at first. That lasted one release:
GitHub does not show a pre-release as "Latest", the front page of the
repository looked empty, and the owner could not find `v0.1.0`. The mark was
also not doing any work. What keeps a debug file away from a release build is
the end of the file name, and a release with no file for a build is passed
over. So every release is a normal release now. A release build still leaves
a pre-release alone, for the day the owner marks one by hand to try something.

**One version number.** `appVersionName` in `app/build.gradle.kts`. The
version code is worked out from it, and the workflow refuses a tag that does
not match it. A build from the dev machine and a build from GitHub of the
same version have the same code, so either installs over the other.

**The file is checked twice before Android sees it.** First against the
SHA-256 that GitHub lists for it, which catches a download that was cut
short. Then `ApkCheck` reads the package name, the version code and the
signing certificate. Android makes those checks too, and its word is final,
but all it shows is "App not installed". This tablet has no logcat, so the
app gives the reason itself. If Android will not name the signers, the check
steps aside and lets Android decide.

**An install session, not "open this file".** `PackageInstaller` reports the
result back in words, and that report goes to the app log. "Open this file"
reports nothing. It stays as the second route, in case this firmware refuses
to make a session.

**No silent updates.** Android 12 and later can update an app without asking,
through `UPDATE_PACKAGES_WITHOUT_USER_ACTION`. Left out on purpose: it is one
more permission in the list the user reads, and one window per update is a
fair price for knowing what the home app is doing.

**The repository stays private.** The owner decided this on 2026-09-20, after
the updater was built: the app may be sold one day, and the source must not
leak. So the token below is not the fallback, it is the way this project
works.

**The access token.** GitHub answers "not found" for a private repository. So
the update screen can take a read-only access token. It is kept in a file of
its own that both backup rule files leave out, it is never logged, and it is
dropped from a request the moment a redirect leaves GitHub, which the storage
server GitHub uses needs anyway. A public repository needs no token, and then
none of this is ever seen.

One weakness, and it is GitHub's: there is no permission for releases alone.
The files of a release come under "Contents", and so does the code, so a
token that can fetch an update can also read the source. It gets an expiry
date, it covers this one repository, and it is deleted on GitHub if the
tablet is lost. If that ever stops being good enough, the way out is a
second repository that holds nothing but the APK files, with the token
pointing at that one. Then the key on the tablet opens no source at all.

## One thing to take out again, one day

Plan section 4 keeps the Play Store open for the `generic` flavour. The Play
Store does not allow an app to update itself from anywhere else, and it
limits who may ask for `REQUEST_INSTALL_PACKAGES`. A build for the Play Store
must leave the updater and both permissions out. Nothing needs doing until
that day. It is written here so that day does not start with a rejection.

## How it was tested

- 60 unit tests. The network code runs against two small servers on the dev
  machine, one playing GitHub and one playing its storage server, and one
  test shows that the access token stops at the first.
- The real GitHub, from the emulator: a private repository answers "not
  found", and a wrong token is refused. Both give the right words on screen.
- The whole update, in the emulator on Android 13, from 0.1.0 to 0.1.1,
  against `tools/fake-github.py`: check, token, download through a redirect,
  SHA-256, the look at the real APK, the install session, "update this app?",
  the restart on the new version, the line in the log, the old file removed.
  Cancel was tried too, and the reason came back in words.
- With the app as the home app: right after the update Android showed the
  stock launcher, because for a moment this app was not there. The Home key
  brought it back, and it was still the home app. The screen says so.
- Not tested: a real GitHub Release, because the Release workflow has not
  run yet, and the ViWoods firmware. See below.

## What is not known yet

- Whether the ViWoods firmware lets an app open the "install unknown apps"
  switch. The screen has an "Allow installs" button for it. If the firmware
  hides that page, DevCheck reaches it, the same way it reaches the home app
  setting.
- Whether the firmware makes an install session. If not, the second route
  runs by itself and the log says so.

Both are on the device test list in `PROGRESS.md`.

## 2026-09-22: the repository is public

The owner made the repository public and decided the app is free and open
source. See decision 0020. What that changes here: no token is needed, the
"not found" answer from GitHub no longer means "private", and the token
button is hidden until a check fails with that answer, so a normal user
never sees it. The token code stays for a private fork, and its tests stay
with it. The paragraphs above that say "stays private" are history.
