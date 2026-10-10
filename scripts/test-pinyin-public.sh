#!/bin/bash
set -euo pipefail
cd "$(dirname "$0")/.."
# A host-only build, separate from APK output. Keep assertions enabled.
CXX="${CXX:-/usr/bin/clang++}"
mkdir -p .runtime
RUN=$(mktemp -d .runtime/pinyin-regression.XXXXXX)
trap 'rm -rf "$RUN"' EXIT
ulimit -c 0
COMMON=(-std=c++11 -O1 -g -Wno-deprecated-register -Wno-narrowing -Wno-format
        -Ithird_party/pinyin/include -Ipublic-tests/compat)
SOURCES=(public-tests/pinyin_regression.cpp third_party/pinyin/share/*.cpp)
"$CXX" "${COMMON[@]}" "${SOURCES[@]}" -o "$RUN/check"
"$RUN/check" assets/dict_pinyin.dat "$RUN/plain.tmp" "$@"
"$CXX" "${COMMON[@]}" -fsanitize=address,undefined -fno-omit-frame-pointer \
  -fno-sanitize-recover=all "${SOURCES[@]}" -o "$RUN/sanitized"
ASAN_OPTIONS=halt_on_error=1 UBSAN_OPTIONS=halt_on_error=1 \
  "$RUN/sanitized" assets/dict_pinyin.dat "$RUN/sanitized.tmp" "$@"
