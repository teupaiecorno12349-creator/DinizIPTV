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
import java.util.Arrays;
import java.util.Comparator;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import okhttp3.Call;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

public final class ImageLoader {
    private static final int MEM_KB=(int)Math.max(2048,Runtime.getRuntime().maxMemory()/1024/8);
    private static final LruCache<String,Bitmap> CACHE=new LruCache<String,Bitmap>(MEM_KB){
        @Override protected int sizeOf(String k,Bitmap b){return Math.max(1,b.getByteCount()/1024);}
    };
    // Cover traffic is independent of ExoPlayer's streaming client and uses a small,
    // bounded pool so several visible posters can appear in parallel.
    private static final ExecutorService IO=Executors.newFixedThreadPool(6);
    private static final Handler MAIN=new Handler(Looper.getMainLooper());
    private ImageLoader(){}

    public static void load(ImageView view,String url){
        if(url==null||url.trim().isEmpty()){view.setImageDrawable(null);return;}
        String key=url.trim();view.setTag(key);
        Bitmap cached=CACHE.get(key);
        if(cached!=null){view.setImageBitmap(cached);return;}
        final android.content.Context context=view.getContext().getApplicationContext();
        IO.execute(()->{
            Bitmap bitmap=null;
            File cacheFile=null;
            boolean downloaded=false;
            try{
                cacheFile=cacheFile(context,key);
                if(cacheFile.isFile()&&cacheFile.length()>0)bitmap=decode(cacheFile);
                if(bitmap==null){
                    Request request=new Request.Builder().url(key)
                            .header("User-Agent","MarcinIPTV/1.0 (Android TV)")
                            .header("Accept","image/avif,image/webp,image/*,*/*;q=0.8")
                            .build();
                    Call call=NetworkStack.client(context).newCall(request);
                    call.timeout().timeout(12,TimeUnit.SECONDS);
                    try(Response response=call.execute()){
                        if(response.isSuccessful()){
                            ResponseBody body=response.body();
                            if(body!=null){
                                byte[] bytes=body.bytes();
                                if(bytes.length>0&&bytes.length<8*1024*1024){
                                    BitmapFactory.Options bounds=new BitmapFactory.Options();
                                    bounds.inJustDecodeBounds=true;
                                    BitmapFactory.decodeByteArray(bytes,0,bytes.length,bounds);
                                    if(bounds.outWidth>0&&bounds.outHeight>0){
                                        BitmapFactory.Options opts=new BitmapFactory.Options();
                                        opts.inSampleSize=sample(bounds,360,520);
                                        opts.inPreferredConfig=Bitmap.Config.RGB_565;
                                        bitmap=BitmapFactory.decodeByteArray(bytes,0,bytes.length,opts);
                                        downloaded=bitmap!=null;
                                    }
                                }
                            }
                        }
                    }
                }
                if(bitmap!=null){
                    CACHE.put(key,bitmap);
                    Bitmap finalBitmap=bitmap;
                    // Put pixels on screen before JPEG disk caching, so slow flash storage
                    // does not delay the first visible poster.
                    MAIN.post(()->{if(key.equals(view.getTag()))view.setImageBitmap(finalBitmap);});
                    if(downloaded&&cacheFile!=null){
                        try(FileOutputStream out=new FileOutputStream(cacheFile)){
                            bitmap.compress(Bitmap.CompressFormat.JPEG,84,out);
                        }catch(Exception ignored){}
                        trimDisk(cacheFile.getParentFile());
                    }
                }
            }catch(Exception ignored){}
        });
    }

    private static File cacheFile(android.content.Context context,String url)throws Exception{
        File dir=new File(context.getCacheDir(),"posters");
        if(!dir.exists())dir.mkdirs();
        MessageDigest md=MessageDigest.getInstance("SHA-256");
        byte[] b=md.digest(url.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        StringBuilder s=new StringBuilder();
        for(byte x:b)s.append(String.format(Locale.ROOT,"%02x",x));
        return new File(dir,s+".jpg");
    }

    private static int sample(BitmapFactory.Options options,int maxW,int maxH){
        int sample=1;
        while((options.outWidth/sample)>maxW*2||(options.outHeight/sample)>maxH*2)sample*=2;
        return Math.max(1,sample);
    }

    private static Bitmap decode(File file){
        try{
            BitmapFactory.Options bounds=new BitmapFactory.Options();
            bounds.inJustDecodeBounds=true;
            BitmapFactory.decodeFile(file.getAbsolutePath(),bounds);
            BitmapFactory.Options opts=new BitmapFactory.Options();
            opts.inSampleSize=sample(bounds,360,520);
            opts.inPreferredConfig=Bitmap.Config.RGB_565;
            return BitmapFactory.decodeFile(file.getAbsolutePath(),opts);
        }catch(Exception error){return null;}
    }

    private static void trimDisk(File dir){
        try{
            File[] files=dir.listFiles();if(files==null)return;
            long total=0L;for(File file:files)total+=file.length();
            if(total<120L*1024L*1024L)return;
            Arrays.sort(files,Comparator.comparingLong(File::lastModified));
            for(File file:files){
                if(total<100L*1024L*1024L)break;
                long size=file.length();if(file.delete())total-=size;
            }
        }catch(Exception ignored){}
    }
}
