#!/bin/bash
set -euo pipefail
cd "$(dirname "$0")/.."
SDK="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
NDK="${ANDROID_NDK_HOME:-$SDK/ndk/29.0.14206865}"
HOST_TAG=linux-x86_64; [[ "$(uname -s)" == "Darwin" ]] && HOST_TAG=darwin-x86_64
CLANG="$NDK/toolchains/llvm/prebuilt/$HOST_TAG/bin"
for ABI in arm64-v8a x86_64; do
 target=aarch64-linux-android30; [[ "$ABI" == x86_64 ]] && target=x86_64-linux-android30
 mkdir -p "build/native/lib/$ABI"
 "$CLANG/$target-clang++" -shared -fPIC -O2 -std=c++11 -static-libstdc++ -Wno-deprecated-register -Wno-narrowing -Wno-format -Wl,-z,max-page-size=16384 -Ithird_party/pinyin/include -Inative/compat native/pinyin_jni.cpp third_party/pinyin/share/*.cpp -o "build/native/lib/$ABI/libheyu_pinyin.so"
done
