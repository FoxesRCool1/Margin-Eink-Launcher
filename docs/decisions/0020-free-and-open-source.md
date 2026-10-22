# 0020. Free, open source, and a Ko-fi link

Date: 2026-09-22. Decided by the owner.

## The decision

The app is free. There is no paid version and no plan for one. The source is
public on GitHub under Apache-2.0, as `LICENSE` has said since step 1. People
who want to help pay for the work can, at https://ko-fi.com/foxesrcool.

This replaces the decision of 2026-09-20 to keep the repository private in
case the app was sold one day. That decision is in 0012 and in the older
parts of `PROGRESS.md`. It is history now.

## What follows from it

- The Ko-fi link is in three places and nowhere else: the README, the
  GitHub "Sponsor" button through `.github/FUNDING.yml`, and Settings, About,
  "Support This App". The row opens the page in the browser of the tablet.
  The app itself still goes online in one place only, `core/update/`: handing
  an address to the browser is not the app going online.
- No asking. The app never shows a "please pay" message, never counts
  launches, and never reminds. One row in Help that the user goes to on
  purpose is the whole of it.
- The updater needs no token. GitHub shows the releases of a public
  repository to everyone. The token screen stays for a private fork, and
  shows only after a check fails with "not found".
- The repository must look after itself in public: no secrets, no personal
  paths, no email address. `CLAUDE.md`, "The source is public", has the
  rules. `CONTRIBUTING.md` says how to build and how to send a change.
- F-Droid is open again. It needs a public repository and a build that F-Droid
  can repeat. Not done yet. See `docs/HANDOFF.md`, next steps.

## What was checked before the repository went public

The tree and the whole git history were searched for tokens, keys and
passwords. None were found. The only personal detail in the repository is
the owner's first name in `plan.md` and at the end of `docs/viwoods-request.md`,
which the owner signs. The debug keystore is checked in on purpose and is not
a secret: it signs test builds only, so a test build from any machine installs
over one from another.
