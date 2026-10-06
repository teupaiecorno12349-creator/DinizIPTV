package com.diniz.config;
import android.content.*;
public final class ConfigPrefs{
 private final SharedPreferences p;
 public ConfigPrefs(Context c){p=c.getSharedPreferences("diniz_config",Context.MODE_PRIVATE);}
 public void save(String h,String u,String pw,String dns){p.edit().putString("host",h).putString("user",u).putString("pass",pw).putString("dns",dns).apply();}
 public String host(){return p.getString("host","");} public String user(){return p.getString("user","");} public String pass(){return p.getString("pass","");} public String dns(){return p.getString("dns","SYSTEM");}
 public boolean configured(){return !host().isEmpty()&&!user().isEmpty()&&!pass().isEmpty();}
}
