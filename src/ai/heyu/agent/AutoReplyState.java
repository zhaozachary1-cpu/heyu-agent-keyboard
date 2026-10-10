package ai.heyu.agent;

import java.util.*;

/** Pure state machine. Keys are scoped digests, cache is RAM only, time is monotonic. */
final class AutoReplyState {
 static final long DEBOUNCE_MS=850, MIN_INTERVAL_MS=4000, TTL_MS=300000, RETRY_MS=30000;
 static final int MAX_PER_MINUTE=6;
 enum Phase { OFF, WAITING, LOADING, READY, ERROR, LIMITED }
 Phase phase=Phase.OFF;
 String key="",reply="",note="",error="";
 int ticket=0;
 long since=0,retryAt=0,readyAt=0;
 private final ArrayDeque<Long> requests=new ArrayDeque<>();
 private static final class Cached {final String reply,note;final long at;Cached(String r,String n,long t){reply=r;note=n;at=t;}}
 private final LinkedHashMap<String,Cached> cache=new LinkedHashMap<>();
 static boolean allowed(boolean consent,boolean visible,boolean whatsapp,boolean privateChat,boolean secure,boolean local,boolean delivery,boolean replySurface){
  return consent&&visible&&whatsapp&&privateChat&&!secure&&!local&&!delivery&&replySurface;
 }
 void stop(){if(phase!=Phase.OFF||!key.isEmpty()){ticket++;key="";reply="";note="";error="";phase=Phase.OFF;}}
 void observe(String next,long now){
  if(next==null||next.isEmpty()){stop();return;}
  if(next.equals(key)){
   if(phase==Phase.READY&&now-readyAt>=TTL_MS){cache.remove(key);ticket++;reply="";note="";phase=Phase.WAITING;since=now;}
   return;
  }
  ticket++;key=next;reply="";note="";error="";since=now;retryAt=0;phase=Phase.WAITING;
  Cached saved=cache.get(key);
  if(saved!=null&&now-saved.at<TTL_MS){reply=saved.reply;note=saved.note;readyAt=saved.at;phase=Phase.READY;}
  else if(saved!=null)cache.remove(key);
 }
 boolean canStart(long now){
  if(key.isEmpty()||phase==Phase.OFF||phase==Phase.LOADING||phase==Phase.READY||now-since<DEBOUNCE_MS||now<retryAt)return false;
  while(!requests.isEmpty()&&now-requests.peekFirst()>=60000)requests.removeFirst();
  if(requests.size()>=MAX_PER_MINUTE){phase=Phase.LIMITED;return false;}
  return requests.isEmpty()||now-requests.peekLast()>=MIN_INTERVAL_MS;
 }
 int start(long now){if(!canStart(now))return -1;requests.addLast(now);phase=Phase.LOADING;error="";return ++ticket;}
 boolean complete(int expected,String expectedKey,String text,String detail,String failure,long now){
  if(ticket!=expected||!key.equals(expectedKey)||phase!=Phase.LOADING)return false;
  if(failure!=null||text==null||text.isBlank()){phase=Phase.ERROR;error=failure==null?"模型没有返回可用建议":failure;retryAt=now+RETRY_MS;return true;}
  reply=text;note=detail==null?"":detail;phase=Phase.READY;readyAt=now;
  cache.put(key,new Cached(reply,note,now));while(cache.size()>16)cache.remove(cache.keySet().iterator().next());return true;
 }
}
