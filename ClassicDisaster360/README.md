# Natural Disaster Survival — Xbox 360 native homebrew prototype

This is a native PowerPC/XEX survival-game recreation for Xbox 360 RGH/JTAG, not a port of the proprietary Roblox engine or Android APK.

## What's in this build

- A geometry-only conversion of the publicly listed classic Island template: 204 terrain parts for its grass top and layered rock underside. The model's script and unrelated dummy are not included.
- Corrected relative world scale so the island is much wider than the classic R6 avatar.
- Blocky yellow/blue/green R6-style avatar made from independently articulated parts, with walk, idle, and jump poses.
- Third-person orbit camera on the right stick, camera-relative movement on the left stick, A to play/jump, LT to run, and Back to exit.
- Natural Disaster-style rounds: tsunami, tornado, meteors, earthquake, fire, and volcanic debris; minimal UI focused on Natural Disaster Survival.
- Reuses the repository's verified XSDK GCC 15.3.0, xSDK library and shader compiler cache.

## Limitations

This is an original native homebrew game prototype, not Roblox running on Xbox 360. It does not include the Roblox Android engine, network services, scripting runtime, exact proprietary character mesh/face texture/animation assets, or multiplayer. The avatar animations are procedural approximations and still require visual testing on hardware. The terrain is a geometry conversion of the public creator-store island template; some decorative paths and props are original approximations.

## Build

GitHub Actions workflow: `.github/workflows/classic-disaster-360-cached-xsdk.yml`

The artifact `ClassicDisaster360-XEX` includes the XEX, source, island geometry table, and checksum. Physical Xbox 360 testing is still required.
