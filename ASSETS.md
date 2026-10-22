# Assets

Every font, drawing and sample file in this repository is listed here with its
source and its licence. The project will be published, so nothing enters the
repository without a line in this file.

Rule: no GPL, no AGPL, no CC BY-NC, no "free for personal use". See `plan.md`
sections 3.5 and 4.

## Fonts

All three are variable fonts under the SIL Open Font Licence 1.1. The full
licence text of each one is in `licenses/fonts/`.

| File | Family | Source | Licence | Checked |
| --- | --- | --- | --- | --- |
| `app/src/main/res/font/bodoni_moda.ttf` | Bodoni Moda | google/fonts, `ofl/bodonimoda` | SIL OFL 1.1 | 2026-09-19 |
| `app/src/main/res/font/jost.ttf` | Jost | google/fonts, `ofl/jost` | SIL OFL 1.1 | 2026-09-19 |
| `app/src/main/res/font/literata.ttf` | Literata | google/fonts, `ofl/literata` | SIL OFL 1.1 | 2026-09-19 |

The OFL allows use, change and redistribution, including inside an app. The
font files are not renamed and the licence text travels with them.

## Icons and drawings

Every icon, every plant drawing and the app icon come from one source:
[Lucide](https://lucide.dev), release 1.47.0, checked on 2026-09-20. The owner
asked for that on 2026-09-20, see `docs/decisions/0013-icons-and-round-shapes.md`.

| File | What it is | Source | Licence |
| --- | --- | --- | --- |
| `design/icons/LucideIcons.kt` | The path data of the icons the app uses, about 90 of them | `lucide-static` 1.47.0 from npm, written out by `tools/lucide.py` | ISC. Icons that come from Feather: MIT |
| `app/src/main/res/drawable/ic_launcher_foreground.xml` | The app icon: the Lucide icon "sprout" | The same, written out by `tools/lucide.py` | ISC |
| `design/components/PlantArt.kt` | The plant at the top of a screen. It holds no drawing: it draws a Lucide plant icon larger than a control, with Lucide's light 1.5 unit line | The same | ISC |
| `tools/lucide-icons.txt` | The names of the icons in use | This project | Same as the app |

The full licence text, both parts, is in `licenses/lucide/LICENSE`. The ISC
and the MIT licence both allow use in a closed and in a sold app. Both ask
for the copyright notice to travel with the work: it is in that file, and in
Settings, Help, Credits.

`tools/lucide.py` pins the release and its SHA-256, so the file can be made
again, byte for byte. To add an icon, add its name to
`tools/lucide-icons.txt` and run the script.

The two drawings the project had before, a stem with six leaves as the app
icon and a corner sprig drawn in code, were original work. They are gone.

Nothing here is traced from, copied from or derived from the article that
inspired the project. That article is under CC BY-SA 4.0, and its images are
not used, not traced and not reproduced.

## Sample content

| Where | What it is | Source | Licence |
| --- | --- | --- | --- |
| `ReaderChromeScreenshotTest.kt`, and so `docs/screenshots/reader_menu.png` and `reader_notes.png` | The first sentences of *Walden*, Henry David Thoreau, 1854 | Project Gutenberg, ebook 205 | Public domain |
| `EpubReaderSmokeTest.kt` | A two chapter EPUB made by the test itself | Written for this project | Apache-2.0, same as the app |
| `docs/screenshots/*.png` | Pictures of this app, made by the screenshot tests | This project | Apache-2.0, same as the app |

No book file is in the repository. When a screenshot needs more text, use
only public domain text from Standard Ebooks or Project Gutenberg, and add a
line here.

## Libraries

Fonts, icons and drawings are on this page. Libraries are in
`licenses/DEPENDENCIES.md`.
