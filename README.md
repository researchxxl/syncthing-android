# A syncthing wrapper for old Android ARMv7 devices without NEON

Aimed for old devices such as Samsung Galaxy Tab 8.9 GT-P7300 from 2011 which based on NVidia Tegra 2 (ARMv7 without NEON instruction set):

- it uses older NDK r23 to support non-NEON builds
- it adds `-mfpu=vfpv3-d16` to the armv7 compiler flags to match Tegra 2 capabilities

## Build and install

Clone the repo with submodules:

```sh
git clone https://github.com/skatsubo/syncthing-android-no-neon --recursive
cd syncthing-android-no-neon
```

Build with docker; run from the repo root:

```sh
# build the builder docker image
img=syncthing-android-builder:zulu
( cd docker && docker build --platform linux/amd64 -t "$img" . )

# run the builder
docker run --platform linux/amd64 --name builder --rm -it \
  -v $(pwd):/build/app -v syncthing-android-prereq:/build/syncthing-android-prereq \
  -v syncthing-android-gradle:/root/.gradle -v syncthing-android-android:/root/.android \
  -e ANDROID_HOME=/build/syncthing-android-prereq -w /build/app "$img" /build/build.sh
```

Install with ADB:

```sh
adb -d install -r app/build/outputs/apk/debug/syncthingfork_debug_armeabi-v7a.apk
```

<details><summary>For reference: objdump and readelf of libsyncthingnative.so</summary>

Inside the build container:

```sh
file=app/src/main/jniLibs/armeabi-v7a/libsyncthingnative.so
readelf -A "$file"
$ANDROID_HOME/ndk/*/toolchains/llvm/prebuilt/*/bin/llvm-objdump -d "$file" | grep -A15 x_cgo_thread_start
$ANDROID_HOME/ndk/*/toolchains/llvm/prebuilt/*/bin/llvm-objdump -d "$file" | grep -E "vld|vst"
```

readelf

```sh
Attribute Section: aeabi
File Attributes
  Tag_conformance: "2.09"
  Tag_CPU_arch: v7
  Tag_CPU_arch_profile: Application
  Tag_ARM_ISA_use: Yes
  Tag_THUMB_ISA_use: Thumb-2
  Tag_FP_arch: VFPv3-D16
  Tag_ABI_PCS_R9_use: V6
  Tag_ABI_PCS_RW_data: PC-relative
  Tag_ABI_PCS_RO_data: PC-relative
  Tag_ABI_PCS_GOT_use: GOT-indirect
  Tag_ABI_PCS_wchar_t: 4
  Tag_ABI_FP_denormal: Needed
  Tag_ABI_FP_exceptions: Unused
  Tag_ABI_FP_number_model: IEEE 754
  Tag_ABI_align_needed: 8-byte
  Tag_ABI_align_preserved: 8-byte, except leaf SP
  Tag_ABI_enum_size: int
  Tag_CPU_unaligned_access: v6
  Tag_ABI_FP_16bit_format: IEEE 754
```

objdump of `x_cgo_thread_start`

```sh
010f4ca4 <x_cgo_thread_start>:
 10f4ca4: 10 4c 2d e9  	push	{r4, r10, r11, lr}
 10f4ca8: 08 b0 8d e2  	add	r11, sp, #8
 10f4cac: 00 40 a0 e1  	mov	r4, r0
 10f4cb0: 0c 00 a0 e3  	mov	r0, #12
 10f4cb4: 1d 03 00 eb  	bl	#3188 <$a>
 10f4cb8: 00 00 50 e3  	cmp	r0, #0
 10f4cbc: 03 00 00 0a  	beq	#12 <x_cgo_thread_start+0x2c>
 10f4cc0: 0e 00 94 e8  	ldm	r4, {r1, r2, r3}
 10f4cc4: 0e 00 80 e8  	stm	r0, {r1, r2, r3}
 10f4cc8: 10 4c bd e8  	pop	{r4, r10, r11, lr}
 10f4ccc: 86 ff ff ea  	b	#-488 <_cgo_sys_thread_start>
 10f4cd0: 1c 00 9f e5  	ldr	r0, [pc, #28]
 10f4cd4: 2b 10 a0 e3  	mov	r1, #43
 10f4cd8: 01 20 a0 e3  	mov	r2, #1
 10f4cdc: 00 00 9f e7  	ldr	r0, [pc, r0]
 10f4ce0: 00 30 90 e5  	ldr	r3, [r0]
 10f4ce4: 0c 00 9f e5  	ldr	r0, [pc, #12]
 10f4ce8: 00 00 8f e0  	add	r0, pc, r0
 10f4cec: bb 03 00 eb  	bl	#3820 <$a>
 10f4cf0: ca 03 00 eb  	bl	#3880 <$a>
```

objdump grepped for "vld|vst": it is rather long, but there are only operations on d0..d15 registers, hence matching `vfpv3-d16` (without this flag it would produce operations on registers d16..d31 too, causing crash on Tegra 2)

```sh
  f93d68: 00 8b 8d ed  	vstr	d8, [sp]
  f94408: 00 8b 8d ed  	vstr	d8, [sp]
  f97d54: 1b 4b 9f ed  	vldr	d4, [pc, #108]
  f97d84: 11 ab 9f ed  	vldr	d10, [pc, #68]
  f97d94: 0f 9b 9f ed  	vldr	d9, [pc, #60]
  f97d98: 10 db 9f ed  	vldr	d13, [pc, #64]
  f97d9c: 11 fb 9f ed  	vldr	d15, [pc, #68]
  ...
```

</details>

---

This project is forked from https://github.com/researchxxl/syncthing-android ❤️

Original readme below:

# Syncthing-Fork - A Syncthing Wrapper for Android

[![License: MPLv2](https://img.shields.io/badge/License-MPLv2-blue.svg)](https://opensource.org/licenses/MPL-2.0)
<a href="https://github.com/researchxxl/syncthing-android/releases/latest" alt="GitHub release"><img src="https://img.shields.io/github/v/release/researchxxl/syncthing-android" /></a>
<a href="https://tooomm.github.io/github-release-stats/?username=researchxxl&repository=syncthing-android" alt="GitHub Stats"><img src="https://img.shields.io/github/downloads/researchxxl/syncthing-android/total.svg" /></a>
<a href="https://f-droid.org/packages/com.github.catfriend1.syncthingfork" alt="F-Droid release"><img src="https://img.shields.io/f-droid/v/com.github.catfriend1.syncthingfork.svg" /></a>
<a href="https://fdroid-metrics.streamlit.app/package_details?package=com.github.catfriend1.syncthingfork"><img src="https://img.shields.io/badge/dynamic/json?url=https%3A%2F%2Fgithub.com%2Fkitswas%2Ffdroid-metrics-dashboard%2Fraw%2Frefs%2Fheads%2Fmain%2Fprocessed%2Fmonthly%2Fcom.github.catfriend1.syncthingfork.json&query=%24.total_downloads&style=for-the-badge&logo=fdroid&label=F-Droid%20%F0%9F%93%A5%20last%20month" height="22" /></a>
<a href="https://apps.obtainium.imranr.dev/redirect?r=obtainium://app/%7B%22id%22%3A%22com.github.catfriend1.syncthingfork%22%2C%22url%22%3A%22https%3A%2F%2Fgithub.com%2Fresearchxxl%2Fsyncthing-android%22%2C%22author%22%3A%22researchxxl%22%2C%22name%22%3A%22Syncthing-Fork%22%2C%22preferredApkIndex%22%3A0%2C%22additionalSettings%22%3A%22%7B%5C%22verifyLatestTag%5C%22%3Atrue%2C%5C%22apkFilterRegEx%5C%22%3A%5C%22com.github.catfriend1.syncthingfork%5C%22%7D%22%2C%22overrideSource%22%3Anull%7D"><img src="https://raw.githubusercontent.com/ImranR98/Obtainium/main/assets/graphics/badge_obtainium.png" alt="Get it on Obtainium" height="22"></a>
<a href="https://hosted.weblate.org/projects/syncthing-fork/app/"><img src="https://hosted.weblate.org/widget/syncthing-fork/app/svg-badge.svg" alt="Translation status" /></a>
[![Build App](https://github.com/researchxxl/syncthing-android/actions/workflows/build-app.yaml/badge.svg)](https://github.com/researchxxl/syncthing-android/actions/workflows/build-app.yaml)

A wrapper of [Syncthing](https://github.com/syncthing/syncthing) for Android. Head to the "releases" section or F-Droid for builds. Please seek help on the forum and/or social media apps first before creating issues on the tracker.

<img src="app/src/main/play/listings/en-US/graphics/phone-screenshots/1.png" alt="screenshot 1" width="200" /> · <img src="app/src/main/play/listings/en-US/graphics/phone-screenshots/2.png" alt="screenshot 2" width="200" /> · <img src="app/src/main/play/listings/en-US/graphics/phone-screenshots/4.png" alt="screenshot 3" width="200" />

## Switching from the deprecated official version

Switching is easier than you may think! See our [wiki article](wiki/migration/Switching-from-the-deprecated-official-version.md) for detailed instructions.

## Wiki and Useful Articles

Our knowledge base is published [here](wiki#readme).

## Building and Development Notes

See [detailed info](wiki/developers/Building-and-Development.md).

## Acknowledgments

This project was forked from [syncthing/syncthing-android](https://github.com/syncthing/syncthing-android).

Special thanks to the former maintainers:

- [Catfriend1](https://github.com/Catfriend1)
- [imsodin](https://github.com/imsodin)
- [nutomic](https://github.com/nutomic)

## Privacy Policy

See our document on privacy: [privacy-policy.md](privacy-policy.md).

## License

The project is licensed under [MPLv2](LICENSE).
