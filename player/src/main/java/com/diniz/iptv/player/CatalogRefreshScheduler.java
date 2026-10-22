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
    private static final String STATE="marcin_catalog_state";
    private static final String LAST_ACTIVITY="last_user_activity_at";
    private static final long IDLE_MS=TimeUnit.HOURS.toMillis(INACTIVITY_HOURS);
    private static final long IMMEDIATE_GUARD_MS=TimeUnit.MINUTES.toMillis(10);
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

    private static SharedPreferences state(Context c){
        return c.getApplicationContext().getSharedPreferences(STATE,Context.MODE_PRIVATE);
    }

    private static boolean hasAccount(Context c){
        SharedPreferences a=c.getApplicationContext().getSharedPreferences("xtream_account",Context.MODE_PRIVATE);
        return !a.getString("server","").isEmpty()
                &&!a.getString("username","").isEmpty()
                &&!a.getString("password","").isEmpty();
    }

    public static void onAppLaunched(Context context){
        Context c=context.getApplicationContext();
        SharedPreferences prefs=state(c);
        long now=System.currentTimeMillis();
        if(!hasAccount(c)){
            prefs.edit().putLong(LAST_ACTIVITY,now).apply();
            scheduleIdle(c);
            return;
        }
        long lastActivity=prefs.getLong(LAST_ACTIVITY,0L);
        long lastFullRefresh=CatalogSync.lastFullRefresh(c);
        long idleFor=lastActivity>0L?now-lastActivity:(lastFullRefresh>0L?now-lastFullRefresh:IDLE_MS);
        long lastImmediate=prefs.getLong("immediate_refresh_request_at",0L);
        if(lastImmediate>lastFullRefresh&&now-lastImmediate<IMMEDIATE_GUARD_MS){
            prefs.edit().putLong(LAST_ACTIVITY,now).apply();
            return;
        }
        prefs.edit().putLong(LAST_ACTIVITY,now).apply();
        if(idleFor>=IDLE_MS)scheduleImmediateOnce(c);
        else scheduleIdle(c);
    }

    public static void onUserActivity(Context context){
        Context c=context.getApplicationContext();
        SharedPreferences prefs=state(c);
        long now=System.currentTimeMillis();
        prefs.edit().putLong(LAST_ACTIVITY,now).apply();
        if(!hasAccount(c)){
            scheduleIdle(c);
            return;
        }
        long lastImmediate=prefs.getLong("immediate_refresh_request_at",0L);
        long lastFullRefresh=CatalogSync.lastFullRefresh(c);
        if(lastImmediate>lastFullRefresh&&now-lastImmediate<IMMEDIATE_GUARD_MS)return;
        scheduleIdle(c);
    }

    private static void scheduleImmediateOnce(Context c){
        SharedPreferences prefs=state(c);
        long now=System.currentTimeMillis();
        long lastRequest=prefs.getLong("immediate_refresh_request_at",0L);
        long lastFullRefresh=CatalogSync.lastFullRefresh(c);
        if(lastRequest>lastFullRefresh&&now-lastRequest<IMMEDIATE_GUARD_MS)return;
        prefs.edit().putLong("immediate_refresh_request_at",now).apply();
        WorkManager.getInstance(c).enqueueUniqueWork(WORK_NAME,ExistingWorkPolicy.REPLACE,request(0L));
    }

    private static void scheduleIdle(Context c){
        WorkManager.getInstance(c).enqueueUniqueWork(WORK_NAME,ExistingWorkPolicy.REPLACE,request(IDLE_MS));
    }

    public static void scheduleNext(Context context){
        Context c=context.getApplicationContext();
        long lastActivity=state(c).getLong(LAST_ACTIVITY,0L);
        long idleFor=lastActivity>0L?System.currentTimeMillis()-lastActivity:0L;
        long remaining=Math.max(0L,IDLE_MS-idleFor);
        WorkManager.getInstance(c).enqueueUniqueWork(WORK_NAME,ExistingWorkPolicy.APPEND_OR_REPLACE,request(remaining));
    }
}
