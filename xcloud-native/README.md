# Diniz XCloud TV — Native Edition

This build uses the open-source XStreaming Android client as its native xCloud streaming engine.

## Build target

- Android TV / Android TV boxes
- 32-bit ARM package: `armeabi-v7a`
- Default video request: 1280×720
- Default renderer: Android native renderer
- Default language: Portuguese
- Microsoft device-code sign-in enabled
- Free-to-play cloud offering supported by the upstream client

The Xbox service, account entitlement, game availability, queue and stream quality remain authoritative. The client requests 720p; actual frame rate, resolution, queue time and playback smoothness depend on the Xbox session, network and device decoder. It does not manufacture queue estimates or add games the account cannot play.

## Source and license

Upstream project: https://github.com/Geocld/XStreaming

Pinned upstream commit: `06ef1c7ee01d09cf9bb2a97fc4895da85e8488a8`

XStreaming is licensed under GNU AGPL-3.0. The upstream license is included in the APK at `assets/AGPL-3.0.txt`. The Diniz changes are kept in `diniz.patch`; the workflow checks out the pinned upstream source, applies this patch, and builds the APK.

Changes made by Diniz:
- Android package ID changed to `com.diniz.xcloudtv`.
- Launcher label and icon changed to Diniz XCloud.
- Portuguese language and Brazilian game language selected by default.
- Microsoft device-code authentication enabled by default.
- Native video renderer and 720p defaults retained from upstream.
- Release variant is signed with the Android debug key for personal sideloading.

This is an independent community build and is not affiliated with Microsoft or Xbox.
