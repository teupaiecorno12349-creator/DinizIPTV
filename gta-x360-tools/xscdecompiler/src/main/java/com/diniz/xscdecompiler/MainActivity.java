package com.diniz.xscdecompiler;

import android.app.*;
import android.os.Bundle;
import android.content.*;
import android.database.Cursor;
import android.graphics.Typeface;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.view.*;
import android.widget.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final int REQ_OPEN=20, REQ_SAVE=21;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();

    private TextView info,status;
    private EditText source;
    private String currentSource, currentName="decompiled.c";

    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        buildUi();
        status.setText("Abra um .xsc do GTA V Xbox 360. O app gera C/C-like reconstruído sem inventar nomes que o bytecode perdeu.");
    }

    private void buildUi(){
        LinearLayout root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(10),dp(10),dp(10),dp(8));

        TextView title=new TextView(this);
        title.setText("XSC Decompiler 360");
        title.setTextSize(21);
        title.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        root.addView(title);

        TextView subtitle=new TextView(this);
        subtitle.setText("GTA V • Xbox 360 • .xsc → .c reconstruído");
        root.addView(subtitle);

        LinearLayout buttons=new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        Button open=button("Abrir .xsc",v->pickOpen());
        Button save=button("Salvar .c",v->pickSave());
        Button copy=button("Copiar código",v->copySource());
        buttons.addView(open);buttons.addView(save);buttons.addView(copy);
        root.addView(buttons);

        info=new TextView(this);
        info.setPadding(0,dp(7),0,dp(5));
        info.setTypeface(Typeface.MONOSPACE);
        root.addView(info);

        source=new EditText(this);
        source.setTypeface(Typeface.MONOSPACE);
        source.setTextSize(12);
        source.setGravity(Gravity.TOP|Gravity.START);
        source.setHorizontallyScrolling(true);
        source.setHorizontalScrollBarEnabled(true);
        source.setVerticalScrollBarEnabled(true);
        source.setSingleLine(false);
        source.setPadding(dp(8),dp(8),dp(8),dp(8));
        root.addView(source,new LinearLayout.LayoutParams(-1,0,1));

        status=new TextView(this);
        status.setPadding(0,dp(6),0,0);
        status.setMinLines(2);
        root.addView(status);
        setContentView(root);
    }

    private Button button(String text,View.OnClickListener l){
        Button b=new Button(this);
        b.setText(text);b.setAllCaps(false);b.setTextSize(12);b.setOnClickListener(l);
        b.setLayoutParams(new LinearLayout.LayoutParams(0,-2,1));
        return b;
    }

    private void pickOpen(){
        Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(i,REQ_OPEN);
    }
    private void pickSave(){
        if(currentSource==null){toast("Descompile um .xsc primeiro.");return;}
        Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT).setType("text/x-c").addCategory(Intent.CATEGORY_OPENABLE)
                .putExtra(Intent.EXTRA_TITLE,currentName);
        startActivityForResult(i,REQ_SAVE);
    }

    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data){
        super.onActivityResult(requestCode,resultCode,data);
        if(resultCode!=RESULT_OK||data==null||data.getData()==null)return;
        if(requestCode==REQ_OPEN)decompile(data.getData());
        else if(requestCode==REQ_SAVE)save(data.getData());
    }

    private void decompile(Uri uri){
        final String inputName=displayName(uri);
        status.setText("Lendo e reconstruindo "+inputName+"…");
        worker.execute(()->{
            try(InputStream in=getContentResolver().openInputStream(uri)){
                byte[] data=readAll(in);
                XscDecompiler.Result r=new XscDecompiler().decompile(data);
                currentSource=r.source;
                String base=r.scriptName==null||r.scriptName.isEmpty()?stripExt(inputName):r.scriptName;
                currentName=safeFile(base)+".c";
                runOnUiThread(()->{
                    info.setText(String.format(Locale.US,"%s  •  code=%d B  •  natives=%d  •  statics=%d  •  globals=%d",
                            r.scriptName,r.codeLength,r.nativesCount,r.staticsCount,r.globalsCount));
                    source.setText(r.source);
                    source.setSelection(0);
                    status.setText("Descompilação concluída. Use “Salvar .c” para exportar.");
                });
            }catch(Exception e){fail(e);}
        });
    }

    private void save(Uri uri){
        final String text=currentSource;
        worker.execute(()->{
            try(OutputStream out=getContentResolver().openOutputStream(uri,"w")){
                if(out==null)throw new IOException("Não foi possível abrir o destino.");
                out.write(text.getBytes(StandardCharsets.UTF_8));
                runOnUiThread(()->status.setText("Arquivo .c salvo com sucesso."));
            }catch(Exception e){fail(e);}
        });
    }

    private void copySource(){
        if(currentSource==null){toast("Nada para copiar.");return;}
        android.content.ClipboardManager cm=(android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
        cm.setPrimaryClip(ClipData.newPlainText("XSC C",currentSource));
        toast("Código copiado.");
    }

    private byte[] readAll(InputStream in)throws IOException{
        if(in==null)throw new IOException("Não foi possível abrir o arquivo.");
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        byte[] b=new byte[128*1024];int n;while((n=in.read(b))!=-1)out.write(b,0,n);
        return out.toByteArray();
    }

    private String displayName(Uri uri){
        try(Cursor c=getContentResolver().query(uri,new String[]{OpenableColumns.DISPLAY_NAME},null,null,null)){
            if(c!=null&&c.moveToFirst())return c.getString(0);
        }catch(Exception ignored){}
        String p=uri.getLastPathSegment();return p==null?"script.xsc":p;
    }

    private static String stripExt(String n){int p=n.lastIndexOf('.');return p>0?n.substring(0,p):n;}
    private static String safeFile(String s){return s.replaceAll("[^A-Za-z0-9._-]","_");}
    private void fail(Exception e){runOnUiThread(()->{status.setText("ERRO: "+e.getMessage());toast(e.getMessage());});}
    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
    private int dp(int v){return (int)(v*getResources().getDisplayMetrics().density+0.5f);}

    @Override protected void onDestroy(){super.onDestroy();worker.shutdownNow();}
}
