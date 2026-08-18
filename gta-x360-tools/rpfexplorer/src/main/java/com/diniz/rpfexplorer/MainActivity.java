package com.diniz.rpfexplorer;

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
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final int REQ_OPEN_RPF=10, REQ_KEY=11, REQ_EXTRACT=12, REQ_ADD=13, REQ_REPLACE=14, REQ_SAVE=15;

    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final ArrayDeque<ArchiveCtx> archives = new ArrayDeque<>();

    private TextView pathView, statusView;
    private ListView listView;
    private ArrayAdapter<Rpf7Archive.Node> adapter;
    private final ArrayList<Rpf7Archive.Node> visible = new ArrayList<>();
    private Rpf7Archive.Node currentDir, selected;
    private Rpf7Archive.Node pendingExtract;
    private byte[] key;

    private static final class ArchiveCtx {
        final Rpf7Archive archive; final File file; final String title;
        ArchiveCtx(Rpf7Archive a, File f, String t){archive=a;file=f;title=t;}
    }

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        setTitle("RPF7 Explorer 360");
        buildUi();
        status("Abra uma update.rpf. Se a tabela estiver criptografada, importe sua chave AES-256.");
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(10),dp(10),dp(10),dp(8));

        TextView title = new TextView(this);
        title.setText("RPF7 Explorer 360 • GTA V Xbox 360");
        title.setTextSize(20);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        root.addView(title);

        TextView hint = new TextView(this);
        hint.setText("Leitura RPF7, árvore completa, extração LZX, AES por chave do usuário e reconstrução conservadora.");
        root.addView(hint);

        LinearLayout row1 = row();
        row1.addView(button("Abrir RPF", v -> pickRpf()));
        row1.addView(button("Chave AES", v -> pickKey()));
        row1.addView(button("↑ Voltar", v -> goUp()));
        root.addView(row1);

        pathView = new TextView(this);
        pathView.setPadding(0,dp(8),0,dp(5));
        pathView.setTypeface(Typeface.MONOSPACE);
        pathView.setText("/");
        root.addView(pathView);

        listView = new ListView(this);
        adapter = new ArrayAdapter<Rpf7Archive.Node>(this, android.R.layout.simple_list_item_activated_1, visible) {
            @Override public View getView(int position, View convertView, android.view.ViewGroup parent) {
                TextView tv=(TextView)super.getView(position,convertView,parent);
                Rpf7Archive.Node n=getItem(position);
                String meta = n.directory ? "" : "   [" + human(n.logicalSize()) + (n.resource ? ", resource" : n.compressedSize>0 ? ", LZX" : "") + "]";
                tv.setText((n.directory ? "📁 " : n.resource ? "◆ " : "📄 ") + n.name + meta);
                tv.setSingleLine(true);
                return tv;
            }
        };
        listView.setAdapter(adapter);
        listView.setChoiceMode(ListView.CHOICE_MODE_SINGLE);
        listView.setOnItemClickListener((p,v,pos,id)->{
            Rpf7Archive.Node n=visible.get(pos);
            if (n.directory) {
                currentDir=n; selected=null; refresh();
            } else {
                selected=n;
                listView.setItemChecked(pos,true);
                showNode(n);
            }
        });
        root.addView(listView, new LinearLayout.LayoutParams(-1,0,1));

        LinearLayout row2=row();
        row2.addView(button("Extrair", v -> extractSelected()));
        row2.addView(button("Abrir RPF interno", v -> openNested()));
        row2.addView(button("Substituir", v -> pickReplace()));
        root.addView(row2);

        LinearLayout row3=row();
        row3.addView(button("+ Adicionar", v -> pickAdd()));
        row3.addView(button("Remover", v -> removeSelected()));
        row3.addView(button("Salvar novo RPF", v -> pickSave()));
        root.addView(row3);

        statusView=new TextView(this);
        statusView.setPadding(0,dp(7),0,0);
        statusView.setMinLines(2);
        root.addView(statusView);

        setContentView(root);
    }

    private LinearLayout row() {
        LinearLayout r=new LinearLayout(this); r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        return r;
    }
    private Button button(String s, View.OnClickListener l) {
        Button b=new Button(this); b.setText(s); b.setTextSize(12); b.setAllCaps(false); b.setOnClickListener(l);
        b.setLayoutParams(new LinearLayout.LayoutParams(0,-2,1));
        return b;
    }

    private void pickRpf() {
        Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(i,REQ_OPEN_RPF);
    }
    private void pickKey() {
        Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(i,REQ_KEY);
    }
    private void extractSelected() {
        if (selected==null) { toast("Selecione um arquivo."); return; }
        pendingExtract=selected;
        Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT).setType("application/octet-stream").addCategory(Intent.CATEGORY_OPENABLE)
                .putExtra(Intent.EXTRA_TITLE, selected.name);
        startActivityForResult(i,REQ_EXTRACT);
    }
    private void pickAdd() {
        if (currentArchive()==null) { toast("Abra um RPF primeiro."); return; }
        Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(i,REQ_ADD);
    }
    private void pickReplace() {
        if (selected==null) { toast("Selecione um arquivo."); return; }
        if (selected.resource) { toast("Recurso RSC/XSC: substituição direta foi bloqueada para não corromper metadados."); return; }
        Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(i,REQ_REPLACE);
    }
    private void pickSave() {
        ArchiveCtx c=currentArchive(); if(c==null){toast("Abra um RPF primeiro.");return;}
        Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT).setType("application/octet-stream").addCategory(Intent.CATEGORY_OPENABLE)
                .putExtra(Intent.EXTRA_TITLE, "modified_" + c.title);
        startActivityForResult(i,REQ_SAVE);
    }

    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data) {
        super.onActivityResult(requestCode,resultCode,data);
        if(resultCode!=RESULT_OK || data==null || data.getData()==null) return;
        Uri uri=data.getData();
        if(requestCode==REQ_OPEN_RPF) loadArchiveFromUri(uri, displayName(uri));
        else if(requestCode==REQ_KEY) importKey(uri);
        else if(requestCode==REQ_EXTRACT) writeExtract(uri);
        else if(requestCode==REQ_ADD) addFile(uri);
        else if(requestCode==REQ_REPLACE) replaceFile(uri);
        else if(requestCode==REQ_SAVE) saveArchive(uri);
    }

    private void loadArchiveFromUri(Uri uri,String title) {
        status("Copiando e abrindo " + title + "…");
        worker.execute(()->{
            try {
                File f=copyUriToCache(uri, ".rpf");
                Rpf7Archive a=Rpf7Archive.open(f,key);
                runOnUiThread(()->{
                    closeAll();
                    archives.push(new ArchiveCtx(a,f,title));
                    currentDir=a.root(); selected=null; refresh();
                    status("Aberto. TOC " + (a.isTocEncrypted()?"criptografado":"sem criptografia detectada") + " • filenameShift=" + a.filenameShift());
                });
            } catch(Exception e){ fail(e); }
        });
    }

    private void importKey(Uri uri) {
        worker.execute(()->{
            try(InputStream in=getContentResolver().openInputStream(uri)){
                byte[] raw=readAll(in);
                key=Rpf7Archive.parseKey(raw);
                runOnUiThread(()->status("Chave AES-256 importada ("+key.length+" bytes). Reabra o RPF se ele havia falhado."));
            } catch(Exception e){fail(e);}
        });
    }

    private void writeExtract(Uri dest) {
        final Rpf7Archive.Node n=pendingExtract; if(n==null)return;
        status("Extraindo " + n.name + "…");
        worker.execute(()->{
            try {
                byte[] bytes=currentArchive().archive.extract(n);
                try(OutputStream out=getContentResolver().openOutputStream(dest,"w")){out.write(bytes);}
                runOnUiThread(()->status("Extraído: "+n.path()+" • "+human(bytes.length)));
            } catch(Exception e){fail(e);}
        });
    }

    private void openNested() {
        if(selected==null){toast("Selecione um arquivo RPF interno.");return;}
        final Rpf7Archive.Node n=selected;
        status("Extraindo e abrindo " + n.name + "…");
        worker.execute(()->{
            try {
                File f=currentArchive().archive.extractToTemp(n,getCacheDir());
                Rpf7Archive a=Rpf7Archive.open(f,key);
                runOnUiThread(()->{
                    archives.push(new ArchiveCtx(a,f,n.name));
                    currentDir=a.root(); selected=null; refresh();
                    status("RPF interno aberto. Use ↑ Voltar na raiz para retornar ao arquivo externo.");
                });
            } catch(Exception e){fail(e);}
        });
    }

    private void addFile(Uri uri) {
        status("Adicionando arquivo…");
        worker.execute(()->{
            try(InputStream in=getContentResolver().openInputStream(uri)){
                byte[] bytes=readAll(in); String name=displayName(uri);
                Rpf7Archive.Node n=currentArchive().archive.addFile(currentDir,name,bytes);
                runOnUiThread(()->{selected=n;refresh();status("Adicionado em memória: "+n.path()+". Use “Salvar novo RPF” para gravar.");});
            } catch(Exception e){fail(e);}
        });
    }

    private void replaceFile(Uri uri) {
        final Rpf7Archive.Node n=selected;
        status("Substituindo " + n.name + "…");
        worker.execute(()->{
            try(InputStream in=getContentResolver().openInputStream(uri)){
                byte[] bytes=readAll(in);
                currentArchive().archive.replaceFile(n,bytes);
                runOnUiThread(()->{refresh();status("Substituição em memória concluída. O arquivo será armazenado sem compressão ao salvar.");});
            }catch(Exception e){fail(e);}
        });
    }

    private void removeSelected() {
        if(selected==null){toast("Selecione uma entrada.");return;}
        try {
            String p=selected.path();
            currentArchive().archive.remove(selected);
            selected=null; refresh(); status("Removido em memória: "+p);
        } catch(Exception e){fail(e);}
    }

    private void saveArchive(Uri dest) {
        ArchiveCtx c=currentArchive(); if(c==null)return;
        status("Reconstruindo RPF7 com backup implícito (o original não é sobrescrito)…");
        worker.execute(()->{
            File tmp=null;
            try {
                tmp=File.createTempFile("rebuilt_",".rpf",getCacheDir());
                c.archive.writeRebuilt(tmp);
                try(InputStream in=new FileInputStream(tmp); OutputStream out=getContentResolver().openOutputStream(dest,"w")) {
                    copy(in,out);
                }
                long len=tmp.length();
                runOnUiThread(()->status("Novo RPF salvo • "+human(len)+". O original permaneceu intacto."));
            }catch(Exception e){fail(e);}
            finally{if(tmp!=null)tmp.delete();}
        });
    }

    private void goUp() {
        if(currentDir!=null && currentDir.parent!=null){currentDir=currentDir.parent;selected=null;refresh();return;}
        if(archives.size()>1){
            ArchiveCtx old=archives.pop();
            try{old.archive.close();}catch(Exception ignored){}
            old.file.delete();
            currentDir=currentArchive().archive.root(); selected=null; refresh(); status("Retornou ao RPF externo.");
        }
    }

    private void refresh() {
        visible.clear();
        if(currentDir!=null) visible.addAll(currentDir.children);
        adapter.notifyDataSetChanged();
        listView.clearChoices();
        ArchiveCtx c=currentArchive();
        pathView.setText((c==null?"":c.title+"  ") + (currentDir==null?"/":currentDir.path()));
    }

    private void showNode(Rpf7Archive.Node n) {
        status(n.path()+"\nTamanho lógico: "+human(n.logicalSize())+" • compressed="+n.compressedSize+
                " • resource="+n.resource+" • offset=0x"+Long.toHexString(n.offsetUnits<<9));
    }

    private ArchiveCtx currentArchive(){return archives.peek();}

    private File copyUriToCache(Uri uri,String suffix)throws IOException{
        File f=File.createTempFile("opened_",suffix,getCacheDir());
        try(InputStream in=getContentResolver().openInputStream(uri);OutputStream out=new FileOutputStream(f)){copy(in,out);}
        return f;
    }

    private byte[] readAll(InputStream in)throws IOException{
        if(in==null)throw new IOException("Não foi possível abrir o arquivo.");
        ByteArrayOutputStream b=new ByteArrayOutputStream();
        copy(in,b); return b.toByteArray();
    }
    private void copy(InputStream in,OutputStream out)throws IOException{
        byte[] b=new byte[128*1024]; int n; while((n=in.read(b))!=-1)out.write(b,0,n);
    }

    private String displayName(Uri uri){
        try(Cursor c=getContentResolver().query(uri,new String[]{OpenableColumns.DISPLAY_NAME},null,null,null)){
            if(c!=null&&c.moveToFirst())return c.getString(0);
        }catch(Exception ignored){}
        String p=uri.getLastPathSegment(); return p==null?"arquivo.bin":p;
    }

    private void fail(Exception e){runOnUiThread(()->{status("ERRO: "+e.getMessage());toast(e.getMessage());});}
    private void status(String s){statusView.setText(s);}
    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
    private int dp(int v){return (int)(v*getResources().getDisplayMetrics().density+0.5f);}
    private static String human(long v){
        if(v<1024)return v+" B";
        if(v<1024L*1024)return String.format(Locale.US,"%.1f KB",v/1024.0);
        if(v<1024L*1024*1024)return String.format(Locale.US,"%.1f MB",v/(1024.0*1024));
        return String.format(Locale.US,"%.2f GB",v/(1024.0*1024*1024));
    }

    private void closeAll(){
        while(!archives.isEmpty()){
            ArchiveCtx c=archives.pop(); try{c.archive.close();}catch(Exception ignored){} c.file.delete();
        }
    }
    @Override protected void onDestroy(){super.onDestroy();worker.shutdownNow();closeAll();}
}
