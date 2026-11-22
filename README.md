# EasyNotes92 for macOS 🍎

> The **macOS version** of [EasyNotes92](https://github.com/MohamedG92/EasyNotes92).
> Same app, same notes, built for Mac: **Cmd** shortcuts, window buttons on the left,
> settings in `~/Library/Application Support`, Finder integration, and a `.dmg` built automatically by GitHub Actions.
> The same code still runs on Windows.

## Download (macOS)

Get the `.dmg` for your Mac from the [**Releases**](../../releases) page:
- **Apple Silicon** (M1, M2, M3, M4): `EasyNotes92-…-macos-arm64.dmg`
- **Intel Macs**: `EasyNotes92-…-macos-x64.dmg`

Open the `.dmg` and drag **EasyNotes92** into **Applications**.

**First launch:** the app isn't signed by Apple yet (that needs a paid Apple Developer account), so macOS
says it "cannot verify the developer". Open it once like this:
1. Double-click EasyNotes92, then click **Done** (or **Cancel**) on the warning.
2. Open **System Settings → Privacy & Security**, scroll down, and click **Open Anyway** next to EasyNotes92.
3. Confirm. From then on it opens normally.

## Mac shortcuts

| Windows | macOS | Does |
|---|---|---|
| Ctrl + key | **⌘ + key** | every menu shortcut (⌘S save, ⌘N new tab, ⌘F search, ⌘B bold…) |
| Ctrl+Space | **⌃Space** or **⌥Space** | suggestions / `new title` → chapter (⌥Space if ⌃Space switches your keyboard language) |
| Ctrl+Alt+↑/↓ | **⌘⌥↑/↓** | duplicate line |
| Alt+↑/↓ | **⌥↑/↓** | move line |
| Ctrl+D | **⌘D** | delete line |
| Alt+A | **⌘L** | select line |
| Ctrl+H | **⌘⌥F** | replace |
| F5 | **⌘⇧D** | insert time and date |
| Ctrl+Tab | **⌃Tab** | next tab |
| Ctrl+wheel | **⌘+wheel** | zoom |

Help → *Raccourcis clavier* shows the Mac keys automatically.

---

A dark, tabbed notepad for **taking course notes** ("prise de note") while you code.
It looks and behaves like the Windows 11 Notepad, plus shortcuts made for notes:
separator lines, numbered chapters, auto-numbered lists, code blocks with syntax colours,
and autocomplete from the notes you already wrote.

**Created by Mohamed Iraqi Houssaini.** Free, and the source code is public.

Written in **Java 25 / Swing** with **[FlatLaf](https://www.formdev.com/flatlaf/)** for the look and themes.

![Choose format page](docs/screenshots/choose-format.png)

| Code pasted from an IDE | Colours in ordinary notes | Search with ↑ / ↓ |
|---|---|---|
| ![Code block](docs/screenshots/code-block.png) | ![Note colours](docs/screenshots/note-colors.png) | ![Search](docs/screenshots/search.png) |

---

## Features

### Like Windows Notepad
- Tabs (in the title bar, like Windows 11 Notepad), **drag a tab to reorder it**
- **Reopens everything you had open**, even unsaved notes, in the same order, same tab, same cursor
- Open / Save / Save As / Save All / Print, recent files (◁ button), drag & drop files **or a whole folder**
- Undo / Redo, Find / Replace, Go To line, Time/Date (F5), Word Wrap, zoom (Ctrl+wheel), status bar
- UTF-8 / UTF-8 BOM / ANSI and CRLF / LF / CR are detected and kept when saving
- Works with Windows Snap (Win+←/→, drag to an edge, snap layouts): the layout adapts down to a quarter of the screen
- Only one window: starting the app again brings the open window to the front

### For taking notes
| Type | You get |
|---|---|
| `==+` | a line of 57 `=` |
| `=+` | a line of 41 `=` |
| `*+` | a line of 24 `*` |
| `**+` | a line of 57 `*` |
| `new heritage` then **Ctrl+Space**, **Enter** | a numbered chapter in this layout:<br>`================…`<br>`			Chapitre 5 : Heritage`<br>`================…` |
| `a)` / `I)` / `1)` then **Enter** | `b)` / `II)` / `2)` on the next line (Enter on an empty item ends the list) |
| a few letters | grey suggestion from your past notes, **Tab** to accept, keep typing to ignore |
| **Ctrl+Alt+↑ / ↓** | duplicate the line(s) |
| **Ctrl+D** | delete the line(s) |
| **Alt+A** | select the whole line(s) |
| **Alt+↑ / ↓** | move the line(s) up / down |
| click under the text | there are always 20 free lines: the click puts the cursor right there |

- **New tab → "Choisir le format"**: a normal `.txt`, or a *prise de note* for Python, Java, C#, C++, C,
  JavaScript, React, HTML/CSS, SQL or Linux. The language shows as a logo on the tab and adds its keywords
  to autocomplete.
- The language is guessed from the file name (`python.txt`, `java_first.txt`, `csharp.txt`…), or from the
  content when the name says nothing (`npm`, `require(`, `const` → JavaScript), and remembered per file.
- **Code blocks**: paste code from an IDE and it gets its own card with syntax colours (keywords, strings,
  numbers, comments, operators, `( )`, `[ ]`, `{ }`). **Ctrl+Shift+K** turns lines into a code block or back.
- **Colours in normal note lines** (prise de note mode): `//` comments green, every `*` like the `*****` lines,
  operators and brackets coloured. Colours are only drawn on screen, files stay plain text.
- Bold / italic / underline / text colour (saved as `.rtf`, a plain `.txt` can't hold them).
- **Format → Note Colors…** to change all 12 note colours, **View → Theme** for 26 themes (default *Night Blue*).

---

## Build and run

Requirements: **JDK 25 or newer** (`javac`, `jar`, `jpackage` on the PATH).

```powershell
# macOS: builds build/EasyNotes92-1.0.0.dmg (needs JDK 25+)
./build-mac.sh

# Windows: builds EasyNotes92.exe (with its own Java inside) + a Desktop shortcut
powershell -ExecutionPolicy Bypass -File build.ps1
```
The app goes to `%LOCALAPPDATA%\Programs\EasyNotes92\EasyNotes92.exe` (about 120 MB, no Java needed on the PC).

From an IDE: import the folder as a Java project (Eclipse: *File → Import → Existing Projects into Workspace*),
the two FlatLaf jars in `lib/` are already on the build path, then run `com.esisa.notepad.Main`.

Settings, recent files and the open tabs are stored in `%APPDATA%\EasyNotes92\`
(start Java with `-Deasynotes.dir=<folder>` to use another folder, e.g. for tests).

---

## Project structure

```
src/com/esisa/notepad/
  Main.java              start, theme, single instance
  AppFrame.java          the window: tabs, path + search bars, menus, status bar, open/save, session restore
  NoteEditor.java        one document: text, files, undo, bold/colours, code blocks, separator colours, free space
  NoteAssist.java        ==+ / =+ / *+ / **+, chapters (Ctrl+Space), numbered lists, Tab autocomplete
  CodeColors.java        syntax colours (code blocks + note lines), drawn by a custom text view
  Vocabulary.java        words from past notes, for autocomplete
  Language.java          languages, keywords, detection from file name / content
  TabStrip.java          the tabs (logos, unsaved dot, close, drag to reorder)
  WelcomePanel.java      the "Choisir le format" page
  Theme.java             the 26 themes (Night Blue + FlatLaf theme pack)
  Settings.java          everything remembered between runs
  SingleInstance.java    hands a second launch over to the open window (localhost + token)
  Platform.java          everything that differs between Windows and macOS (keys, folders, font, Finder)
  MacIntegration.java    macOS app menu (Quit, About), Finder "open with", Dock icon
  LangIcon.java Glyph.java AppIcon.java   logos, small icons and the app icon, drawn in code
  FindReplaceDialog.java FontDialog.java Ui.java
lib/        flatlaf-3.7.1.jar, flatlaf-intellij-themes-3.7.1.jar
build.ps1   Windows build (jpackage)
docs/       screenshots
```

---

## Roadmap

### 1. Open source on GitHub
- [x] License: PolyForm Noncommercial 1.0.0 (`LICENSE.md`) + `NOTICE.md`
- [x] FlatLaf is **Apache-2.0**: its license text ships with the jars in `lib/LICENSE-FlatLaf.txt`
- [x] `.gitignore` (`build/`, `bin/`, `*.class`)
- [ ] Move the automated tests into the repo (`test/`): they currently live outside the project.
      They cover the shortcuts, chapters, lists, autocomplete, code blocks, colours, search, folder drop,
      session restore and single instance, and they run off-screen
- [ ] Maybe rename the package from `com.esisa.notepad` to something like `io.github.<user>.easynotes`
- [ ] Translate menus or pick one language (today: English menus, French note texts)

### 2. macOS version (this repo)
Everything that differs between Windows and macOS is in `Platform.java` and `MacIntegration.java`.
- [x] **Shortcuts**: Cmd instead of Ctrl (`Platform.key`); ⌥Space as an extra key for suggestions
- [x] **Title bar**: tabs in the title bar, red/yellow/green buttons on the left
- [x] **Settings folder**: `~/Library/Application Support/EasyNotes92`
- [x] **Font**: SF Mono / Menlo when Cascadia Code / Consolas are missing
- [x] **"Open folder"**: `open -R` shows the file in Finder
- [x] **App menu**: ⌘Q saves the session like closing the window, About opens the app's About, Dock icon
- [x] **Finder**: files opened with EasyNotes92 from Finder open as tabs
- [x] **Icon**: `.icns` generated from the same drawing (`AppIcon --iconset` + `iconutil`)
- [x] **Build**: `build-mac.sh` (`jpackage --type dmg`), run by GitHub Actions on Apple Silicon and Intel
- [ ] **Tested by a person on a real Mac** (the CI only starts it and takes a screenshot)
- [ ] Signing + notarization (Apple Developer account, $99/year) to remove the first-launch warning
- [ ] Mac App Store

### 3. Downloadable versions
- [ ] Windows installer: `jpackage --type msi` (needs the WiX Toolset) or a zipped app folder (no install)
- [ ] GitHub Actions: build on `windows-latest` and `macos-latest` on every tag, attach the `.msi` / `.zip` / `.dmg`
      to a **GitHub Release**
- [ ] Optional: Linux `.deb` with `jpackage --type deb`
- [ ] Optional: code signing for Windows (avoids the SmartScreen warning)
- [ ] A small "Download" section in this README pointing to the latest release

---

## Copyright and license

**Copyright © 2026 Mohamed Iraqi Houssaini**, the creator and author of EasyNotes92.

EasyNotes92 is **free**. Its source code is published under the
**[PolyForm Noncommercial License 1.0.0](LICENSE.md)**. See [`NOTICE.md`](NOTICE.md) for the full terms.

- ✅ You may use it, share it, modify it and add features, free of charge.
- ❌ You may **not sell it**, or sell any modified version, or use it for any commercial purpose.
- 📌 Any copy or modified version you share must stay free, keep `LICENSE.md` and `NOTICE.md`,
  and credit Mohamed Iraqi Houssaini as the original creator.

Anyone who breaks these terms loses the right to use the software. The author may take legal action against them.

**FR —** EasyNotes92 est créé par **Mohamed Iraqi Houssaini**. Il est gratuit et son code est public.
Vous pouvez le modifier et le partager gratuitement, mais **il est interdit de le vendre**,
ou de vendre une version modifiée. Toute copie doit rester gratuite et citer l'auteur original.

FlatLaf © FormDev Software GmbH is used under the Apache License 2.0 ([`lib/LICENSE-FlatLaf.txt`](lib/LICENSE-FlatLaf.txt)).

## Privacy

EasyNotes92 collects **no data**: no account, no ads, no analytics, no internet connection.
Notes and settings stay on your PC. Full policy: [`PRIVACY.md`](PRIVACY.md).

