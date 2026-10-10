package ai.heyu.agent;
import android.content.Context;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import java.security.KeyStore;
import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;
/** Per-install BYOK. The user key never enters assets, logs, clipboard or backups. */
final class ApiCredentials {
 private static final String ALIAS="heyu.provider.key.v1";
 private static javax.crypto.SecretKey encryptionKey()throws Exception{
  KeyStore store=KeyStore.getInstance("AndroidKeyStore");store.load(null);
  if(!store.containsAlias(ALIAS)){KeyGenerator gen=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");gen.init(new KeyGenParameterSpec.Builder(ALIAS,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());return gen.generateKey();}
  return (javax.crypto.SecretKey)store.getKey(ALIAS,null);
 }
 static boolean configured(Context c){return c.getSharedPreferences("provider-key-v1",0).contains("ciphertext");}
 static void save(Context c,String raw)throws Exception{
  String key=raw==null?"":raw.trim();if(key.length()<16||key.length()>2048||key.matches("(?s).*\\s.*"))throw new Exception("请输入有效的 DeepSeek API Key");
  Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,encryptionKey());byte[] data=cipher.doFinal(key.getBytes(java.nio.charset.StandardCharsets.UTF_8));
  if(!c.getSharedPreferences("provider-key-v1",0).edit().putString("ciphertext",Base64.encodeToString(data,Base64.NO_WRAP)).putString("iv",Base64.encodeToString(cipher.getIV(),Base64.NO_WRAP)).commit())throw new Exception("密钥保存失败");
  DeepSeekClient.resetHealth();
 }
 static String read(Context c)throws Exception{
  android.content.SharedPreferences p=c.getSharedPreferences("provider-key-v1",0);if(!p.contains("ciphertext"))throw new Exception("请先在禾语设置中填写自己的 DeepSeek API Key");
  try{Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,encryptionKey(),new GCMParameterSpec(128,Base64.decode(p.getString("iv",""),0)));return new String(cipher.doFinal(Base64.decode(p.getString("ciphertext",""),0)),java.nio.charset.StandardCharsets.UTF_8);}
  catch(Exception e){throw new Exception("本机密钥无法解密，请在设置中重新填写");}
 }
 static void clear(Context c){c.getSharedPreferences("provider-key-v1",0).edit().clear().commit();DeepSeekClient.resetHealth();}
}
