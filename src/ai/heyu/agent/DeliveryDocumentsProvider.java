package ai.heyu.agent;
import android.database.*;
import android.os.*;
import android.provider.*;
import java.io.*;
import org.json.JSONObject;

/** System SAF gateway: one pending document; no broad storage permission or public copies. */
public final class DeliveryDocumentsProvider extends DocumentsProvider {
 static final String AUTH="ai.heyu.agent.delivery",ROOT="current",TITLE="禾语本次发送";
 private static final String[] ROOT_COLUMNS={DocumentsContract.Root.COLUMN_ROOT_ID,DocumentsContract.Root.COLUMN_DOCUMENT_ID,DocumentsContract.Root.COLUMN_TITLE,DocumentsContract.Root.COLUMN_FLAGS,DocumentsContract.Root.COLUMN_MIME_TYPES};
 private static final String[] DOC_COLUMNS={DocumentsContract.Document.COLUMN_DOCUMENT_ID,DocumentsContract.Document.COLUMN_DISPLAY_NAME,DocumentsContract.Document.COLUMN_MIME_TYPE,DocumentsContract.Document.COLUMN_SIZE,DocumentsContract.Document.COLUMN_FLAGS,DocumentsContract.Document.COLUMN_LAST_MODIFIED};
 @Override public boolean onCreate(){return true;}
 static void changed(android.content.Context c){c.getContentResolver().notifyChange(DocumentsContract.buildRootsUri(AUTH),null);c.getContentResolver().notifyChange(DocumentsContract.buildChildDocumentsUri(AUTH,ROOT),null);}
 @Override public Cursor queryRoots(String[] projection){
  MatrixCursor cursor=new MatrixCursor(projection==null?ROOT_COLUMNS:projection);
  // Stable root avoids stale system root caches; no document exists until an explicit tap.
  MatrixCursor.RowBuilder r=cursor.newRow();put(r,cursor,DocumentsContract.Root.COLUMN_ROOT_ID,ROOT);put(r,cursor,DocumentsContract.Root.COLUMN_DOCUMENT_ID,ROOT);
  put(r,cursor,DocumentsContract.Root.COLUMN_TITLE,TITLE);put(r,cursor,DocumentsContract.Root.COLUMN_FLAGS,DocumentsContract.Root.FLAG_LOCAL_ONLY);put(r,cursor,DocumentsContract.Root.COLUMN_MIME_TYPES,"*/*");return cursor;
 }
 @Override public Cursor queryDocument(String id,String[] projection)throws FileNotFoundException{MatrixCursor c=new MatrixCursor(projection==null?DOC_COLUMNS:projection);add(c,id);return c;}
 @Override public Cursor queryChildDocuments(String parent,String[] projection,String sort)throws FileNotFoundException{
  if(!ROOT.equals(parent))throw new FileNotFoundException();MatrixCursor c=new MatrixCursor(projection==null?DOC_COLUMNS:projection);
  String token=DeliveryStore.pending;if(!token.isEmpty())try{add(c,token);}catch(FileNotFoundException ignored){}
  c.setNotificationUri(getContext().getContentResolver(),DocumentsContract.buildChildDocumentsUri(AUTH,ROOT));return c;
 }
 private void add(MatrixCursor c,String id)throws FileNotFoundException{
  boolean root=ROOT.equals(id);JSONObject m=root?null:DeliveryStore.read(getContext(),id);MatrixCursor.RowBuilder r=c.newRow();
  put(r,c,DocumentsContract.Document.COLUMN_DOCUMENT_ID,id);put(r,c,DocumentsContract.Document.COLUMN_DISPLAY_NAME,root?TITLE:m.optString("fileName"));
  put(r,c,DocumentsContract.Document.COLUMN_MIME_TYPE,root?DocumentsContract.Document.MIME_TYPE_DIR:ArtifactProvider.mime(m.optString("ext")));
  put(r,c,DocumentsContract.Document.COLUMN_SIZE,root?0:m.optLong("bytes"));put(r,c,DocumentsContract.Document.COLUMN_FLAGS,0);put(r,c,DocumentsContract.Document.COLUMN_LAST_MODIFIED,0);
 }
 private static void put(MatrixCursor.RowBuilder r,MatrixCursor c,String key,Object value){if(c.getColumnIndex(key)>=0)r.add(key,value);}
 @Override public ParcelFileDescriptor openDocument(String id,String mode,CancellationSignal signal)throws FileNotFoundException{
  if(signal!=null)signal.throwIfCanceled();ParcelFileDescriptor fd=DeliveryStore.open(getContext(),id,mode);
  String[] packages=getContext().getPackageManager().getPackagesForUid(Binder.getCallingUid());
  if(DeliveryBuild.DRY_RUN)android.util.Log.i("HeyuDeliveryQA","provider read pending="+id.equals(DeliveryStore.pending)+" caller="+java.util.Arrays.toString(packages));
  if(id.equals(DeliveryStore.pending)&&packages!=null)for(String p:packages)if("com.whatsapp".equals(p))DeliveryStore.opened=id;
  return fd;
 }
 @Override public boolean isChildDocument(String parent,String id){return ROOT.equals(parent)&&id.equals(DeliveryStore.pending);}
}
