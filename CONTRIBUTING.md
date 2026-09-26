# Contributing to JKCEMU Multilingual

## Building

You need a JDK (17 or newer) and [Apache Ant](https://ant.apache.org/).

```sh
ant jar                     # builds jkcemu.jar
java -jar jkcemu.jar        # runs it
java -jar jkcemu.jar --lang=de
ant jkcemu                  # compile and run from build/ without a jar
ant clean                   # removes build/ and jkcemu.jar
```

Use Ant. The scripts in `cmd/` come from the original JKCEMU and list
the files to package by hand, so they miss newer resources such as
`src/lang/` and translated help pages.

## Project layout

| Path | Contents |
| --- | --- |
| `src/jkcemu/`, `src/z80emu/` | Java sources |
| `src/lang/` | UI texts, one `.properties` file per language |
| `src/help/` | Help pages in German (the original language) |
| `src/help/<code>/` | Translated help pages, e.g. `src/help/en/` |
| `src/images/`, `src/rom/`, `src/disks/` | Resources bundled into the jar |
| `packaging/` | Icons and scripts for the native packages |

## Adding a new language

Full details, including the formatting rules you have to follow, are in
[`src/lang/TRANSLATING.txt`](src/lang/TRANSLATING.txt). Read
[`src/lang/GLOSSARY.txt`](src/lang/GLOSSARY.txt) for the retro-computing
terms that are easy to get wrong. In short:

1. **UI texts.** Copy `src/lang/jkcemu.properties` (English) to
   `src/lang/jkcemu_<code>.properties`, where `<code>` is the ISO 639-1
   code of your language (`fr`, `pl`, `cs`, ...), and translate the text
   to the right of each `=`. Missing keys fall back to English, so a
   partial translation works.
2. **Language menu.** Add a line `<code>=<language name>` to
   `src/lang/languages.txt`, e.g. `fr=Français`.
3. **Help pages (optional).** Copy pages from `src/help/en/` (or the
   German originals in `src/help/`) into `src/help/<code>/`, keeping the
   same file names and subdirectories, and translate them. Pages that
   have not been translated are shown in German.
4. **Check and test.**

   ```sh
   ant lang-check              # reports missing or extra keys
   ant jar && java -jar jkcemu.jar --lang=<code>
   ```

   Also look through the menus and dialogs you translated. Long texts
   can break layouts.

Properties files are read as ISO-8859-1, so write non-ASCII characters
as `\uXXXX` escapes. See TRANSLATING.txt, section 5.6.

## Adding or changing UI texts in the code

Don't hard-code user-visible strings. Look them up by key:

```java
LangUtil.getText( "disk.action.unpack" )
```

Add every new key to both `jkcemu.properties` (English) and
`jkcemu_de.properties` (German), then run `ant lang-check`, which fails
on keys that are missing or unused.

## Updating the version

The version lives in `src/jkcemu/Main.java` (`VERSION`). The first three
parts follow upstream JKCEMU, and the fourth counts releases of this
fork. The packaging scripts read it from there.

## Packaging and releases

Native packages bundle a Java runtime, so users don't need Java
installed.

```sh
ant jar
packaging/package.sh        # output goes to dist/
```

`jpackage` only builds for the OS it runs on. The script makes a `.dmg`
on macOS, a portable `.zip` on Windows, and a `.tar.gz` plus a `.deb`
on Linux.

GitHub Actions (`.github/workflows/release.yml`) builds every platform
when a push touches `packaging/` or the workflow. To publish a release:

```sh
git tag v0.9.9.2
git push origin v0.9.9.2
```

This creates a **draft** release with all packages attached. Review it
on GitHub, then publish it.

### Updating the icon

Replace the 1024×1024 master and regenerate every size (needs macOS and
Python with Pillow):

```sh
packaging/make-icons.sh path/to/new-icon.png
```

This updates `packaging/icons/` and the window icons in
`src/images/icon/`.
