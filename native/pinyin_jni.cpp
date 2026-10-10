#include <jni.h>
#include <string>
#include <cstring>
#include "pinyinime.h"
using namespace ime_pinyin;
extern "C" JNIEXPORT jboolean JNICALL Java_ai_heyu_agent_PinyinDecoder_open(JNIEnv* e,jclass,jstring dict,jstring user){
 const char* d=e->GetStringUTFChars(dict,nullptr);const char* u=e->GetStringUTFChars(user,nullptr);
 bool ok=im_open_decoder(d,u);e->ReleaseStringUTFChars(dict,d);e->ReleaseStringUTFChars(user,u);
 if(ok)im_set_max_lens(48,24);return ok;
}
extern "C" JNIEXPORT jobjectArray JNICALL Java_ai_heyu_agent_PinyinDecoder_search(JNIEnv* e,jclass,jstring input){
 const char* p=e->GetStringUTFChars(input,nullptr);std::string raw(p);e->ReleaseStringUTFChars(input,p);
 // Never expose native search to arbitrary strings or unbounded app input.
 if(raw.size()>48||raw.find_first_not_of("abcdefghijklmnopqrstuvwxyz'")!=std::string::npos)raw.clear();
 im_reset_search();size_t count=im_search(raw.c_str(),raw.size());if(count>24)count=24;
 jobjectArray result=e->NewObjectArray(count,e->FindClass("java/lang/String"),nullptr);
 for(size_t i=0;i<count;i++){char16 text[128]={0};im_get_candidate(i,text,128);size_t len=0;while(len<127&&text[len])len++;jstring value=e->NewString((jchar*)text,len);e->SetObjectArrayElement(result,i,value);e->DeleteLocalRef(value);}return result;
}
extern "C" JNIEXPORT jint JNICALL Java_ai_heyu_agent_PinyinDecoder_consumed(JNIEnv*,jclass,jint chars){
 const uint16* positions=nullptr;size_t count=im_get_spl_start_pos(positions);
 return positions && chars>0 && (size_t)chars<=count ? positions[chars] : 0;
}
extern "C" JNIEXPORT void JNICALL Java_ai_heyu_agent_PinyinDecoder_close(JNIEnv*,jclass){im_close_decoder();}
