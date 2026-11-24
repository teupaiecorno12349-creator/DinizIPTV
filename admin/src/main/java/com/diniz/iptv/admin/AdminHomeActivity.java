package com.diniz.iptv.admin;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public class AdminHomeActivity extends Activity {
    private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}
    private GradientDrawable bg(int c){GradientDrawable g=new GradientDrawable();g.setColor(c);g.setCornerRadius(dp(14));return g;}
    private TextView text(String s,int sp){TextView t=new TextView(this);t.setText(s);t.setTextColor(Color.WHITE);t.setTextSize(sp);return t;}
    private Button button(String s,int color){Button b=new Button(this);b.setText(s);b.setTextColor(Color.WHITE);b.setTextSize(16);b.setBackground(bg(color));return b;}

    @Override public void onCreate(Bundle b){
        super.onCreate(b);startServer();
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setGravity(Gravity.CENTER_HORIZONTAL);root.setPadding(dp(22),dp(42),dp(22),dp(30));root.setBackgroundColor(Color.rgb(11,14,20));
        TextView title=text("DINIZ IPTV",30);title.setTypeface(null,1);title.setGravity(Gravity.CENTER);root.addView(title,new LinearLayout.LayoutParams(-1,dp(58)));
        TextView sub=text("Admin Mobile v1.4",16);sub.setTextColor(Color.rgb(165,174,190));sub.setGravity(Gravity.CENTER);root.addView(sub,new LinearLayout.LayoutParams(-1,dp(44)));
        TextView info=text("Controle usuários, catálogo e sua conta TeraBox pelo celular. O Player da TV Box conecta a este aparelho pela rede local.",15);info.setTextColor(Color.rgb(190,198,212));info.setGravity(Gravity.CENTER);info.setLineSpacing(0,1.15f);LinearLayout.LayoutParams ip=new LinearLayout.LayoutParams(-1,-2);ip.setMargins(0,0,0,dp(28));root.addView(info,ip);

        Button panel=button("PAINEL ADMIN COMPLETO",Color.rgb(124,77,255));
        Button tera=button("TERABOX / SINCRONIZAR CATÁLOGO",Color.rgb(35,43,57));
        LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(-1,dp(62));bp.setMargins(0,dp(7),0,dp(7));root.addView(panel,bp);root.addView(tera,new LinearLayout.LayoutParams(bp));
        panel.setOnClickListener(v->startActivity(new Intent(this,MainActivity.class)));
        tera.setOnClickListener(v->startActivity(new Intent(this,TeraBoxActivity.class)));
        setContentView(root);
    }

    private void startServer(){
        try{Intent i=new Intent(this,HttpServerService.class);if(Build.VERSION.SDK_INT>=26)startForegroundService(i);else startService(i);}catch(Exception ignored){}
    }
}
