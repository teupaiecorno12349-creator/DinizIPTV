# Classic Disaster Survival — Xbox 360 build branch

Original, procedural low-poly survival prototype. It is inspired by classic block-world disaster games but contains no Roblox client code or extracted proprietary assets.

## Build
The GitHub Actions workflow builds the XSDK PowerPC toolchain, HLSL shader compiler, XSDK libraries, then compiles this CMake title to \`classic_disaster.xex\`. Its artifact is named \`ClassicDisaster360-XEX\`.

This is an early playable-systems prototype, not a full Roblox runtime or the original Natural Disaster Survival game. A real Xbox 360 with RGH/JTAG is required to launch unsigned homebrew XEX files. It still needs testing on physical hardware.
