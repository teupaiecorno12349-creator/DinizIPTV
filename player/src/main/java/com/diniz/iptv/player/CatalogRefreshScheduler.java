package com.diniz.iptv.player;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.work.Constraints;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.BackoffPolicy;
import java.util.concurrent.TimeUnit;

public final class CatalogRefreshScheduler {
    public static final long INACTIVITY_HOURS=19L;
    private static final String WORK_NAME="marcin-catalog-refresh-after-idle";
    private static final long IDLE_MS=TimeUnit.HOURS.toMillis(INACTIVITY_HOURS);
    private CatalogRefreshScheduler(){}

    private static OneTimeWorkRequest request(long delayMillis){
        Constraints constraints=new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build();
        return new OneTimeWorkRequest.Builder(CatalogRefreshWorker.class)
                .setInitialDelay(Math.max(0,delayMillis),TimeUnit.MILLISECONDS)
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL,30,TimeUnit.MINUTES)
                .addTag(WORK_NAME)
                .build();
    }
    private static boolean hasAccount(Context c){
        SharedPreferences a=c.getApplicationContext().getSharedPreferences("xtream_account",Context.MODE_PRIVATE);
        return !a.getString("server","").isEmpty()&&!a.getString("username","").isEmpty()&&!a.getString("password","").isEmpty();
    }
    public static void onAppLaunched(Context context){
        Context c=context.getApplicationContext();
        if(!hasAccount(c)){scheduleIdle(c);return;}
        long last=CatalogSync.lastFullRefresh(c);
        long age=last==0?Long.MAX_VALUE:System.currentTimeMillis()-last;
        if(age>=IDLE_MS){
            WorkManager.getInstance(c).enqueueUniqueWork(WORK_NAME,ExistingWorkPolicy.REPLACE,request(0));
        }else scheduleIdle(c);
    }
    public static void onUserActivity(Context context){
        Context c=context.getApplicationContext();
        if(!hasAccount(c)){scheduleIdle(c);return;}
        long last=CatalogSync.lastFullRefresh(c);
        long age=last==0?Long.MAX_VALUE:System.currentTimeMillis()-last;
        if(age>=IDLE_MS){
            WorkManager.getInstance(c).enqueueUniqueWork(WORK_NAME,ExistingWorkPolicy.REPLACE,request(0));
        }else scheduleIdle(c);
    }
    private static void scheduleIdle(Context c){
        WorkManager.getInstance(c).enqueueUniqueWork(WORK_NAME,ExistingWorkPolicy.REPLACE,request(IDLE_MS));
    }
    public static void scheduleNext(Context context){
        Context c=context.getApplicationContext();
        WorkManager.getInstance(c).enqueueUniqueWork(WORK_NAME,ExistingWorkPolicy.APPEND_OR_REPLACE,request(IDLE_MS));
    }
}