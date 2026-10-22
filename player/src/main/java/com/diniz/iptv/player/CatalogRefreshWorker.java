package com.diniz.iptv.player;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

public final class CatalogRefreshWorker extends Worker {
    public CatalogRefreshWorker(@NonNull Context context,@NonNull WorkerParameters params){super(context,params);}
    @NonNull @Override public Result doWork(){
        Context c=getApplicationContext();
        SharedPreferences account=c.getSharedPreferences("xtream_account",Context.MODE_PRIVATE);
        String server=account.getString("server",""),user=account.getString("username",""),pass=account.getString("password","");
        if(server.isEmpty()||user.isEmpty()||pass.isEmpty()){
            CatalogRefreshScheduler.scheduleNext(c);
            return Result.success();
        }
        try{
            CatalogSync.refreshAll(c,new XtreamClient(c,server,user,pass));
            CatalogRefreshScheduler.scheduleNext(c);
            return Result.success();
        }catch(Exception error){
            return Result.retry();
        }
    }
}