# GTA X360 Android Tools

Two Android apps focused on GTA V Xbox 360 files:

## RPF7 Explorer 360

- Opens Xbox 360 `RPF7` archives such as `update.rpf`.
- Parses the big-endian RPF7 directory table.
- Accepts an AES-256 key supplied by the user; no game key is embedded.
- Navigates the full archive tree.
- Extracts ordinary files, including Xbox LZX/XCompress-compressed entries.
- Attempts conservative extraction of RPF resource entries using their system/graphics size flags.
- Opens nested RPF files after extraction.
- Adds/removes ordinary files and replaces ordinary files.
- Rebuilds to a **new** RPF; the selected original is never overwritten.
- Existing untouched compressed/resource payloads are copied byte-for-byte.

### Important writer limitation

Resource entries (`.xsc`/RSC and similar) have platform-specific resource metadata and compression requirements. This first writer deliberately blocks direct replacement of a resource entry rather than producing an archive that looks valid but is corrupt. Replacing the normal `script.rpf` container itself is supported after editing that nested archive.

## XSC Decompiler 360

- Opens GTA V Xbox 360 32-bit XSC resources (with or without a leading 16-byte `RSC7` header).
- Reads paged code and string blocks.
- Decodes the GTA V console opcode table.
- Reconstructs functions, native calls, local/static/global access, stack expressions, jumps, labels and switches.
- Exports a `.c` C/C-like source file.

A compiler cannot preserve original variable names, comments, typedefs, macro names, or every high-level control-flow construct. The decompiler therefore uses deterministic generated names/labels instead of fabricating missing source information.

## Android

- minSdk 24 (Android 7.0)
- target/compileSdk 35
- No storage permission: uses Android Storage Access Framework.
