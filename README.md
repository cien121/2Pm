# Weave

A fresh-start Android proxy client — Kotlin, Jetpack Compose, single-core (Xray).

> **Design language: "Aurora Night".** No spreadsheets, no legacy lists.
> A living connection orb, floating HUD stats, and a swipeable node deck
> over a drifting aurora sky. Built to feel alive.

## Architecture: single-core (Xray)

```
core/
├── ProxyCore.kt      # engine abstraction (start/stop/latency/stats)
├── CoreType.kt       # XRAY (SING_BOX kept for DB compatibility only)
├── CoreRegistry.kt   # registers XrayCore
└── xray/XrayCore.kt  # official libv2ray.aar
```

| Core | Version | AAR source |
|------|---------|------------|
| Xray | v26.10.10 | Official: 2dust/AndroidLibXrayLite release |

> **Note:** sing-box was removed in v0.9.18. Dual Go runtimes
> (Xray's `libgojni.so` + libbox's) conflict in one process — even in a
> separate `:singbox` process, `go.Seq` hardcodes `loadLibrary("gojni")`
> and loads Xray's copy, crashing the helper process. Single-core Xray
> is the final architecture.

## Status

**v0.9.18 — single-core Xray**: the app connects for real.
- ✅ Kotlin + Compose project (minSdk 26, targetSdk 34)
- ✅ Aurora Night design system (theme, gradients, animations)
- ✅ Immersive home: connection orb + HUD + node deck
- ✅ Nodes grid, settings screens
- ✅ VpnService: TUN + foreground notification (Android 14 specialUse)
- ✅ Room database (nodes)
- ✅ CI: downloads official libv2ray.aar, builds signed debug APK
- ✅ Node add: share-link parsing (vless/vmess/trojan/ss/hysteria2/socks/http)
- ✅ Xray core start/stop: per-protocol config builder
  (vless/vmess/trojan/ss/hysteria2/socks/http/chain)
- ✅ Connection state machine (idle/connecting/connected/error)
  driving the orb
- ✅ Latency testing (real connection test), traffic stats
- ✅ IPv6 support, per-app proxy, local DNS, chain proxy
- ✅ Quick Settings tile + home screen shortcut (one-tap connect)

## Build

CI handles everything: `.github/workflows/build.yml` downloads the
official `libv2ray.aar` (v26.10.10), then builds the signed debug APK.
Grab it from Artifacts.

> Do **not** substitute a hand-built Xray AAR from an unverified
> environment — self-built cores showed ~6x latency regression vs.
> official builds (investigated 2026-10-17, root cause in build
> environment, unresolved).

## License

GPL-3.0
