package com.diniz.iptv.admin;

import android.content.*;
import android.database.Cursor;
import android.database.sqlite.*;
import android.util.Base64;
import org.json.JSONArray;
import org.json.JSONObject;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.MessageDigest;
import java.security.SecureRandom;

public class DbHelper extends SQLiteOpenHelper {
    public DbHelper(Context c){super(c,"diniz_iptv.db",null,1);}
    @Override public void onCreate(SQLiteDatabase db){
        db.execSQL("CREATE TABLE accounts(id INTEGER PRIMARY KEY AUTOINCREMENT, username TEXT UNIQUE NOT NULL, salt TEXT NOT NULL, hash TEXT NOT NULL, expiresAt INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE content(id INTEGER PRIMARY KEY AUTOINCREMENT, type TEXT NOT NULL, title TEXT NOT NULL, episodeTitle TEXT, year TEXT, description TEXT, posterUrl TEXT, genres TEXT, streamUrl TEXT NOT NULL, season INTEGER DEFAULT 0, episode INTEGER DEFAULT 0)");
    }
    @Override public void onUpgrade(SQLiteDatabase db,int oldV,int newV){}

    public void createAccount(String username,String password,long expiresAt) throws Exception {
        byte[] salt=new byte[16];new SecureRandom().nextBytes(salt);byte[] hash=hash(password.toCharArray(),salt);
        ContentValues v=new ContentValues();v.put("username",username.trim());v.put("salt",Base64.encodeToString(salt,Base64.NO_WRAP));v.put("hash",Base64.encodeToString(hash,Base64.NO_WRAP));v.put("expiresAt",expiresAt);
        getWritableDatabase().insertOrThrow("accounts",null,v);
    }
    private byte[] hash(char[] password,byte[] salt) throws Exception {PBEKeySpec spec=new PBEKeySpec(password,salt,120000,256);return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();}
    public long authenticate(String user,String pass) throws Exception {
        try(Cursor c=getReadableDatabase().rawQuery("SELECT salt,hash,expiresAt FROM accounts WHERE username=?",new String[]{user})){
            if(!c.moveToFirst())return -1;long exp=c.getLong(2);if(System.currentTimeMillis()>exp)return -2;
            byte[] salt=Base64.decode(c.getString(0),Base64.NO_WRAP), expected=Base64.decode(c.getString(1),Base64.NO_WRAP), actual=hash(pass.toCharArray(),salt);
            return MessageDigest.isEqual(expected,actual)?exp:-1;
        }
    }
    public JSONArray listAccounts() throws Exception {JSONArray a=new JSONArray();try(Cursor c=getReadableDatabase().rawQuery("SELECT id,username,expiresAt FROM accounts ORDER BY username",null)){while(c.moveToNext()){JSONObject o=new JSONObject();o.put("id",c.getLong(0));o.put("username",c.getString(1));o.put("expiresAt",c.getLong(2));a.put(o);}}return a;}
    public void deleteAccount(long id){getWritableDatabase().delete("accounts","id=?",new String[]{String.valueOf(id)});}

    public long addContent(ContentValues v){return getWritableDatabase().insertOrThrow("content",null,v);}
    public void deleteContent(long id){getWritableDatabase().delete("content","id=?",new String[]{String.valueOf(id)});}
    public JSONArray listContent() throws Exception {
        JSONArray a=new JSONArray();String[] cols={"id","type","title","episodeTitle","year","description","posterUrl","genres","streamUrl","season","episode"};
        try(Cursor c=getReadableDatabase().query("content",cols,null,null,null,null,"type,title,season,episode")){
            while(c.moveToNext()){JSONObject o=new JSONObject();o.put("id",c.getLong(0));o.put("type",c.getString(1));o.put("title",nz(c.getString(2)));o.put("episodeTitle",nz(c.getString(3)));o.put("year",nz(c.getString(4)));o.put("description",nz(c.getString(5)));o.put("posterUrl",nz(c.getString(6)));o.put("genres",nz(c.getString(7)));o.put("streamUrl",nz(c.getString(8)));o.put("season",c.getInt(9));o.put("episode",c.getInt(10));a.put(o);}
        }return a;
    }
    private String nz(String s){return s==null?"":s;}
}
