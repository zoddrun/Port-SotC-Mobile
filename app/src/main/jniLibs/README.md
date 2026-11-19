# Prebuilt native libraries

This directory is where `tools/android-integration/package-runner.sh` places the
Android build of the recompiled game runtime:

```
arm64-v8a/libps2EntryRunner.so
```

The library is a derived build artifact and is intentionally gitignored. It is
produced by `tools/capture-host/configure-android.sh` followed by
`tools/capture-host/build-android-runner.sh`, then copied here by
`package-runner.sh`, which verifies the AArch64 machine type and the
`ANativeActivity_onCreate` export, strips the copy with
`llvm-strip --strip-unneeded` (777 MB with debug info -> 107 MB) and re-runs
`verify-runner.sh` on the result. The unstripped library stays in the build
tree for symbolisation.

Without the library the launcher keeps Play disabled and reports that the
native game runtime is not installed; it never falls back to an emulator.
