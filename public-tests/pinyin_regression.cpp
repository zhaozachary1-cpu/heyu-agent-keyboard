// Synthetic-only regression runner for the exact bundled dictionary and decoder.
#include "pinyinime.h"
#include <algorithm>
#include <cstdio>
#include <cstdlib>
#include <cstdint>
#include <string>
using namespace ime_pinyin;
static size_t checks = 0;
static const bool trace = std::getenv("HEYU_SYNTHETIC_TRACE") != NULL;
static void require(bool pass, const char* name, const std::string& value) {
  if (!pass) { std::fprintf(stderr, "FAIL %s: synthetic spelling [%s]\n", name, value.c_str()); std::abort(); }
}
static void search(const std::string& value, bool reset = true) {
  if (trace) { std::printf("PROBE [%s]\n", value.c_str()); std::fflush(stdout); }
  require(value.size() <= 48, "test input bound", value);
  if (reset) im_reset_search();
  size_t count = im_search(value.c_str(), value.size());
  for (size_t i = 0; i < std::min(count, size_t(24)); i++) {
    char16 text[128] = {};
    require(im_get_candidate(i, text, 128) != NULL, "candidate available", value);
    size_t len = 0; while (len < 128 && text[len]) ++len;
    require(len <= 48, "candidate bounded and terminated", value);
  }
  const uint16* positions = NULL;
  size_t syllables = im_get_spl_start_pos(positions);
  if (positions) for (size_t i = 0; i <= syllables; i++)
    require(positions[i] <= value.size(), "consumed spelling is within input", value);
  ++checks;
}
static void editPath(const std::string& value) {
  // Production re-searches after each key/deletion; no im_choose/learning APIs.
  for (size_t i = 0; i <= value.size(); i++) search(value.substr(0, i));
  for (size_t i = value.size(); i > 0; i--) search(value.substr(0, i - 1));
  search(value);
}
int main(int argc, char** argv) {
  if (argc < 3) return 2;
  std::remove(argv[2]);
  if (!im_open_decoder(argv[1], argv[2])) return 3;
  im_set_max_lens(48, 24);
  if (argc > 3) { for (int i = 3; i < argc; i++) search(argv[i]); }
  else {
    // "ss" aborts on the unpatched decoder with the production dictionary.
    search("ss"); search("nss"); search("nihao");
    char16 greeting[128] = {};
    require(im_get_candidate(0, greeting, 128) && greeting[0] == 0x4f60 && greeting[1] == 0x597d,
            "real Chinese candidate, not a disabled decoder fallback", "nihao");
    for (char a = 'a'; a <= 'z'; a++) {
      search(std::string(1, a));
      for (char b = 'a'; b <= 'z'; b++) {
        search(std::string(1, a) + b);
        for (char c = 'a'; c <= 'z'; c++) search(std::string(1, a) + b + c);
      }
    }
    const char* corpus[] = {"", "'", "''", "'a'", "a''a", "xi'an", "chang'an", "nihao",
      "zhongguo", "shijie", "shishi", "ss", "sss", "ssss", "xian", "shanghaizhongguo",
      "nihaowomenmingtianjiandengwoquerenyixia", "woxianggenjuniyaoqiuxiugaiwendang"};
    for (const char* phrase : corpus) editPath(phrase);
    for (char c = 'a'; c <= 'z'; c++) editPath(std::string(48, c));
    editPath(std::string(48, '\''));
    // Fixed seed, including apostrophes and lengths up to the JNI limit.
    uint32_t seed = 0x719EA1;
    const std::string alphabet = "abcdefghijklmnopqrstuvwxyz'";
    for (size_t n = 0; n < 2000; n++) {
      seed = 1664525 * seed + 1013904223;
      size_t length = 1 + seed % 48; std::string value;
      for (size_t i = 0; i < length; i++) {
        seed = 1664525 * seed + 1013904223;
        value += alphabet[seed % alphabet.size()];
      }
      editPath(value);
    }
  }
  im_close_decoder(); std::remove(argv[2]);
  std::printf("PASS %zu native synthetic searches, zero learning and zero network\n", checks);
}
