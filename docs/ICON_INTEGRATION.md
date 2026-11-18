# Icon Integration

> Status: **Integrated and verified (Session 5, 2026-11-11). Re-verified during recovery audit (2026-11-18).**

This document records how the developer-supplied application icon in `icon/` is integrated into the Android app. Detailed asset geometry, generation, and verification notes live in [`docs/ICON_ASSET.md`](ICON_ASSET.md).

## Source asset (developer-provided)

- **Path:** `icon/icon.png`
- **Repository status:** tracked (intended project asset; **not** gitignored). Original file preserved unmodified.
- **Inspected properties:** 1254 × 1254 PNG, RGB (opaque/no alpha), centered subject on pure black background (see `docs/ICON_ASSET.md` and `docs/agent/FILE_AUDIT.md`).
- **Policy:** no generated placeholder or substitute artwork; no distortion or important-element cropping.

## Generated Android launcher resources

Generated deterministically by `tools/generate_launcher_icons.py` (re-runnable; analysis helper: `tools/analyze_icon.py`).

| Resource | Purpose |
|---|---|
| `app/src/main/res/drawable/ic_launcher_background.xml` | Solid black adaptive-icon background layer |
| `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml` | Adaptive launcher icon (background + foreground + monochrome) |
| `app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml` | Round adaptive launcher icon alias |
| `app/src/main/res/mipmap-{mdpi,hdpi,xhdpi,xxhdpi,xxxhdpi}/ic_launcher_foreground.png` | Foreground artwork on 108dp adaptive canvas per density |
| `app/src/main/res/mipmap-{mdpi,hdpi,xhdpi,xxhdpi,xxxhdpi}/ic_launcher_monochrome.png` | Themed/monochrome layer (Android 13+ themed icons) |

- **Adaptive icon strategy:** artwork scaled/centered to fit the 66dp adaptive safe zone (content within safe area across mask shapes); black foreground/background avoids visible seams. `minSdk 28` means adaptive resources apply on all supported devices.
- **Removed placeholder:** prior `res/drawable/ic_launcher.xml` placeholder vector was deleted when the manifest switched to mipmap adaptive icons.

## Manifest wiring

`app/src/main/AndroidManifest.xml` (application):

```xml
android:icon="@mipmap/ic_launcher"
android:roundIcon="@mipmap/ic_launcher_round"
```

## Verification evidence

- **Programmatic/resource checks (Session 5):** foreground corner transparency and safe-zone/content sizing validated; monochrome alpha/content validated; adaptive XML and density resources present.
- **Build/package checks (Session 5):** `assembleDebug` succeeded; `aapt2 dump badging` reported launcher icons resolving to `@mipmap/ic_launcher` resources; APK contents included adaptive XMLs and foreground/monochrome assets for all target densities.
- **Unit tests (Session 5):** `testDebugUnitTest` reported 49/49 passing.
- **Historical record:** commit `643e6bb` (`app(icon): integrate developer artwork as adaptive launcher icon`) and `docs/agent/SESSION_LOG.md` (Session 5).

## Notes

- `icon/` is intentionally **not** listed in `.gitignore` (corrected during 2026-11-18 recovery audit so the ignore rules match project policy and tracked state).
- Re-run `tools/generate_launcher_icons.py` if the source artwork is intentionally updated by the developer, then re-verify with `aapt2 dump badging` and a device/emulator launcher check.
- For release builds, confirm the installed APK displays the supplied artwork on-device (physical-device launcher verification remains pending until hardware testing; see blockers).
