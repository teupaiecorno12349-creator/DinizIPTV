# Sonic X360 Port Lab — REV00

This is the first native Xbox 360 XEX milestone. It boots on RGH/JTAG homebrew dashboards, initializes the Xenos GPU/display, reads the Xbox 360 controller, and checks the original Sonic 1 Mega Drive REV00 ROM beside the XEX.

**It is not yet the playable Sonic game.** It does not emulate the Mega Drive CPU and does not yet contain the ported game logic, graphics, sound, levels, collisions, or enemies.

## GitHub Actions build

The workflow in `.github/workflows/sonic-x360.yml` builds the xsdk PowerPC toolchain on an Ubuntu runner and produces `sonic_x360.xex` as a downloadable Actions artifact.

No ROM is committed or uploaded by the workflow. Keep your own legally obtained ROM as `sonic1.bin` beside the XEX on the console.

## Local build

See the xsdk getting-started guide: https://github.com/xenosworks/xsdk/blob/main/docs/GETTING_STARTED.md

## ROM target

- Size: 524288 bytes
- Serial: `GM 00001009-00`
- Header checksum: `0x264A`
- SHA-256: `46160baa06362c711c9f1a5017cb7371026444936c8af5e93a78996cf32ff2a6`
- CRC32: `F9394E97`

## Console controls

- A: toggle technical details
- Back: exit to Aurora/FSD

## Next port milestones

1. Port the original game logic to native PowerPC C, without executing 68000 instructions.
2. Replace Mega Drive VDP calls with an Xenos 2D tile/sprite renderer.
3. Convert the original level/object data into native runtime assets.
4. Port FM/PSG audio to Xbox 360 audio output.
5. Validate player physics, collision, scrolling, enemies, bosses, and all zones.
