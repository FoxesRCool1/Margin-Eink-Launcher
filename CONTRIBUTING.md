# Contributing

Thank you for looking. This is a small project by one person, made for one
tablet, and shared in case it helps someone else. Changes are welcome. Here is
how to make one that lands.

## Before you write code

- Read `CLAUDE.md`. It holds the rules of the code, and most of them come
  from e-ink: no animations, pages instead of scrolling, pure black on pure
  white, and a few more. A change that breaks one of them will not land,
  however good it is.
- Read `docs/HANDOFF.md`. It says what is finished, what is not, and which
  traps have already cost a day.
- For anything larger than a fix, open an issue first and say what you have
  in mind. That saves both of us a rewrite.

## Build

You need JDK 17 or newer and the Android SDK with platform 37. Android
Studio is not needed.

```
./gradlew assembleViwoodsDebug          # for a ViWoods tablet
./gradlew assembleGenericDebug          # for any other Android device
./gradlew testViwoodsDebugUnitTest      # unit tests, and the screenshots
./gradlew lintViwoodsDebug
```

The screenshots land in `app/build/outputs/roborazzi/`. They are the only
eyes there are, because `adb` does not work on the tablet this was made for.
Look at them.

There are two flavours:

- `viwoods` reaches the hidden ViWoods display and pen calls by reflection.
  Every call is guarded, and none can crash the app. It targets SDK 30,
  because the fast pen is said to need that.
- `generic` has no vendor code and targets the newest SDK.

## Try it

In an Android emulator the size of the panel:

```
tools/emulator.sh
```

It needs the `emulator` and `system-images;android-33;google_apis;x86_64`
packages of the Android SDK, and KVM. The mouse draws on handwriting pages.
It is not e-ink, so it shows layout and bugs, not ghosting.

On a tablet on your local network:

```
tools/deploy.sh viwoods 8000
```

It builds the APK, serves it on the local network and prints the address.
Open that address in the browser of the tablet.

## Where things are

- `CLAUDE.md`: the rules of the code.
- `docs/HANDOFF.md`: the state of the work.
- `docs/decisions/`: one short file per decision.
- `PROGRESS.md`: what works, and what still needs a test on the tablet.
- `docs/RELEASING.md`: how a release is made. Only the owner can publish one.

## Send a change

1. Fork the repository and make your change on a branch of the fork.
2. Run the four commands above. All green, and look at the pictures.
3. A new screen goes into `ScreensScreenshotTest`, upright, on its side, and
   in half of a split screen.
4. A new dependency needs a licence check first: no GPL, no AGPL. Add a line
   to `licenses/DEPENDENCIES.md`. A new font or drawing gets a line in
   `ASSETS.md`.
5. Write plain English in the app and in the docs. No em dashes.
6. Open a pull request against `Dev`. Say what changed and why, in a few
   lines. A picture from the screenshot tests helps.

## Report a problem

Open an issue. Attach the log: Settings, About, Log, then the download icon.
The files go to the `Download/Margin` folder. Say which
tablet, which version (Settings shows it at the top), and what you did just
before. The log holds the vendor calls that worked and did not, which is the
one thing a report from a tablet with no `adb` can give.

## Licence

By sending a change you agree that it is under the Apache License 2.0, the
same as the rest of the project.
