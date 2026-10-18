# CianCore

A fresh-start Android proxy client — Kotlin, Jetpack Compose, dual-core.

> **Design language: "Aurora Night".** No spreadsheets, no legacy lists.
> A living connection orb, floating HUD stats, and a swipeable node deck
> over a drifting aurora sky. Built to feel alive.

## Architecture: pluggable dual-core

```
core/
├── ProxyCore.kt      # engine abstraction (start/stop/latency/stats)
├── CoreType.kt       # XRAY | SING_BOX (add one line for a third core)
├── CoreRegistry.kt   # the ONLY place that knows which cores exist
├── xray/XrayCore.kt      # official libv2ray.aar
└── singbox/SingBoxCore.kt # official-source libbox.aar
```

To add a third core: add a `CoreType` entry, implement `ProxyCore`,
register it in `CoreRegistry.registerBuiltins()`. Nothing else changes.
Each node stores its own `coreType`; the VPN service routes to the
right engine per connection.

| Core | Version | AAR source |
|------|---------|------------|
| Xray | v26.10.10 | Official: 2dust/AndroidLibXrayLite release |
| sing-box | v1.14.3 | Built from SagerNet/sing-box `experimental/libbox` via gomobile |

## Status

**v0.2 — first working build**: the app connects for real.
- ✅ Kotlin + Compose project (minSdk 26, targetSdk 34)
- ✅ Aurora Night design system (theme, gradients, animations)
- ✅ Immersive home: connection orb + HUD + node deck
- ✅ Nodes grid, settings screens, core selector
- ✅ VpnService: TUN + foreground notification (Android 14 specialUse)
- ✅ Room database (nodes, with per-node core selection)
- ✅ CI: downloads/builds both AARs, builds debug APK
- ✅ Node add: share-link parsing (vless/vmess/trojan/ss/hysteria2/tuic)
- ✅ Xray core start/stop: per-protocol config builder
  (vless/vmess/trojan/ss/hysteria2; tuic needs sing-box)
- ✅ Connection state machine (idle/connecting/connected/error)
  driving the orb
- ⬜ Latency testing, traffic stats (HUD shows 0 for now)
- ⬜ sing-box core start/stop, routing settings

## Build

CI handles everything: `.github/workflows/build.yml` downloads the
official `libv2ray.aar` (v26.10.10), builds `libbox.aar` from official
sing-box source, then builds the debug APK. Grab it from Artifacts.

> Do **not** substitute a hand-built Xray AAR from an unverified
> environment — self-built cores showed ~6x latency regression vs.
> official builds (investigated 2026-10-17, root cause in build
> environment, unresolved).

## License

GPL-3.0
