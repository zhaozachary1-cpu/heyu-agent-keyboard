# AOSP PinyinIME
Source: https://android.googlesource.com/platform/packages/inputmethods/PinyinIME/
Pinned commit: 49aebad1c1cfbbcaa9288ffed5161e79e57c3679
Source paths: jni/include, jni/share; dictionary: res/raw/dict_pinyin.dat.
License: Apache-2.0; original source headers retained.
Heyu adds a separate JNI adapter and compatibility cutils header.
Local patch (0.7.1): `share/matrixsearch.cpp` uses an independent bounded vector for multi-syllable candidate deduplication. The upstream tail-buffer scratch assumption aborts on dense initial combinations such as `ss` with the bundled dictionary. Candidate data and scratch no longer overlap; candidates are not silently truncated. A second regression (`nss`) excludes every copy of the full sentence before deduplication, avoiding a `remain_num - 1` underflow when the first item was skipped; duplicates keep the best retained score. A third regression (27 repeated `a` characters) handles dictionary parsing-mark exhaustion as an unavailable extension instead of asserting; undecoded input is retained. `share/dicttrie.cpp` counts only actually allocated parsing marks in both deeper extension paths, preventing out-of-bounds milestone writes when the bounded pool fills (found with AddressSanitizer). Original license headers are retained.
Regression: `bash scripts/test-pinyin-public.sh` exercises synthetic spellings with assertions and AddressSanitizer/UndefinedBehaviorSanitizer. No user typing is logged by the production decoder.
No Gboard private binaries, assets, logo, or trademarks are packaged.
Decoder searches only; im_choose and learning APIs are not called. Scratch user dictionary is cleared at startup and close.
