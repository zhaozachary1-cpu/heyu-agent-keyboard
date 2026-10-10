package ai.heyu.agent;
import android.content.*;
import android.database.*;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import java.io.*;
import java.nio.charset.StandardCharsets;

/** Read-only content URIs, scoped grants, no path traversal or arbitrary local file access. */
public final class ArtifactProvider extends ContentProvider {
 static final String MIME="application/vnd.openxmlformats-officedocument.presentationml.presentation";
 static String mime(String ext){switch(ext){case "pdf":return "application/pdf";case "docx":return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";case "xlsx":return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";case "png":return "image/png";case "jpg":return "image/jpeg";case "ics":return "text/calendar";default:return MIME;}}
 @Override public boolean onCreate(){return true;}
 private File file(Uri u)throws FileNotFoundException{
  String name=u.getLastPathSegment();if(name==null||!u.getPath().equals("/"+name)||!name.matches("[0-9a-f]{32}\\.(pptx|docx|pdf|xlsx|png|jpg|ics)"))throw new FileNotFoundException();
  File f=new File(getContext().getFilesDir(),"exports/"+name);if(!f.isFile()||System.currentTimeMillis()-f.lastModified()>86400000)throw new FileNotFoundException("文件已过期");return f;
 }
 @Override public String getType(Uri uri){String name=uri.getLastPathSegment();return name==null?"application/octet-stream":mime(name.substring(name.lastIndexOf('.')+1));}
 @Override public ParcelFileDescriptor openFile(Uri u,String mode)throws FileNotFoundException{if(!"r".equals(mode))throw new FileNotFoundException("Read only");return ParcelFileDescriptor.open(file(u),ParcelFileDescriptor.MODE_READ_ONLY);}
 @Override public Cursor query(Uri u,String[] projection,String selection,String[] args,String sort){
  try{File f=file(u);String id=f.getName().substring(0,32),name=f.getName();File label=new File(f.getParentFile(),id+".name");if(label.isFile())name=new String(java.nio.file.Files.readAllBytes(label.toPath()),StandardCharsets.UTF_8);
   String[] columns=projection==null?new String[]{OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE}:projection;MatrixCursor cursor=new MatrixCursor(columns);Object[] row=new Object[columns.length];for(int i=0;i<columns.length;i++)row[i]=OpenableColumns.DISPLAY_NAME.equals(columns[i])?name:OpenableColumns.SIZE.equals(columns[i])?f.length():null;cursor.addRow(row);return cursor;
  }catch(Exception e){return null;}
 }
 @Override public Uri insert(Uri u,ContentValues v){throw new UnsupportedOperationException();}
 @Override public int update(Uri u,ContentValues v,String s,String[] a){throw new UnsupportedOperationException();}
 @Override public int delete(Uri u,String s,String[] a){throw new UnsupportedOperationException();}
}
