package com.diniz.iptv.player;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.widget.ImageView;
import java.io.File;
import java.io.FileOutputStream;
import java.security.MessageDigest;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

public final class ImageLoader {
    private static final int MEM_KB=(int)Math.max(2048,Runtime.getRuntime().maxMemory()/1024/8);
    private static final LruCache<String,Bitmap> CACHE=new LruCache<String,Bitmap>(MEM_KB){
        @Override protected int sizeOf(String k,Bitmap b){return Math.max(1,b.getByteCount()/1024);}
    };
    private static final ExecutorService IO=Executors.newFixedThreadPool(4);
    private static final Handler MAIN=new Handler(Looper.getMainLooper());
    private ImageLoader(){}

    public static void load(ImageView view,String url){
        if(url==null||url.trim().isEmpty()){view.setImageDrawable(null);return;}
        String key=url.trim();view.setTag(key);
        Bitmap cached=CACHE.get(key);if(cached!=null){view.setImageBitmap(cached);return;}
        IO.execute(()->{
            Bitmap bitmap=null;
            try{
                File file=cacheFile(view,key);
                if(file.isFile()&&file.length()>0)bitmap=decode(file);
                if(bitmap==null){
                    Request request=new Request.Builder().url(key).header("User-Agent","MarcinIPTV/1.0 (Android TV)").build();
                    try(Response response=NetworkStack.client(view.getContext()).newCall(request).execute()){
                        if(response.isSuccessful()){
                            ResponseBody body=response.body();
                            if(body!=null){
                                byte[] bytes=body.bytes();
                                if(bytes.length>0&&bytes.length<8*1024*1024){
                                    BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;
                                    BitmapFactory.decodeByteArray(bytes,0,bytes.length,bounds);
                                    BitmapFactory.Options opts=new BitmapFactory.Options();opts.inSampleSize=sample(bounds,360,520);opts.inPreferredConfig=Bitmap.Config.RGB_565;
                                    bitmap=BitmapFactory.decodeByteArray(bytes,0,bytes.length,opts);
                                    if(bitmap!=null)try(FileOutputStream out=new FileOutputStream(file)){bitmap.compress(Bitmap.CompressFormat.JPEG,88,out);}
                                }
                            }
                        }
                    }
                    trimDisk(file.getParentFile());
                }
                if(bitmap!=null){CACHE.put(key,bitmap);Bitmap finalBitmap=bitmap;MAIN.post(()->{if(key.equals(view.getTag()))view.setImageBitmap(finalBitmap);});}
            }catch(Exception ignored){}
        });
    }
    private static File cacheFile(ImageView view,String url)throws Exception{
        File dir=new File(view.getContext().getCacheDir(),"posters");if(!dir.exists())dir.mkdirs();
        MessageDigest md=MessageDigest.getInstance("SHA-256");byte[] b=md.digest(url.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        StringBuilder s=new StringBuilder();for(byte x:b)s.append(String.format(java.util.Locale.ROOT,"%02x",x));
        return new File(dir,s+".jpg");
    }
    private static int sample(BitmapFactory.Options o,int maxW,int maxH){
        int sample=1;while((o.outWidth/sample)>maxW*2||(o.outHeight/sample)>maxH*2)sample*=2;return Math.max(1,sample);
    }
    private static Bitmap decode(File f){
        try{
            BitmapFactory.Options b=new BitmapFactory.Options();b.inJustDecodeBounds=true;BitmapFactory.decodeFile(f.getAbsolutePath(),b);
            BitmapFactory.Options opts=new BitmapFactory.Options();opts.inSampleSize=sample(b,360,520);opts.inPreferredConfig=Bitmap.Config.RGB_565;
            return BitmapFactory.decodeFile(f.getAbsolutePath(),opts);
        }catch(Exception e){return null;}
    }
    private static void trimDisk(File dir){
        try{
            File[] files=dir.listFiles();if(files==null)return;long total=0;for(File f:files)total+=f.length();
            if(total<120L*1024L*1024L)return;
            java.util.Arrays.sort(files,java.util.Comparator.comparingLong(File::lastModified));
            for(File f:files){if(total<100L*1024L*1024L)break;long n=f.length();if(f.delete())total-=n;}
        }catch(Exception ignored){}
    }
}
