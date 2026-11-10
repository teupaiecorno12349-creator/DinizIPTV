package com.lkplay.panelhost;

import android.app.Activity;
import android.content.*;
import android.os.Bundle;
import android.graphics.Typeface;
import android.view.Gravity;
import android.widget.*;
import android.net.Uri;

public final class MainActivity extends Activity {
    private EditText mac, key;
    private TextView address, state;
    private PanelServer server;
    private int dp(float v) { return (int)(v * getResources().getDisplayMetrics().density + .5f); }
    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().getDecorView().setBackgroundColor(0xff101522);
        server = new PanelServer(this);
        try { server.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false); } catch (Exception ignored) {}
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(1); page.setPadding(dp(24),dp(22),dp(24),dp(22));
        ScrollView scroll = new ScrollView(this); scroll.addView(page);
        TextView title = text("LK PLAY",28,0xffffffff,true); page.addView(title);
        page.addView(text("PAINEL LOCAL DE LISTAS",13,0xff8fa1ba,true));
        TextView help=text("O próprio TV Box hospeda o painel HTML. Acesse pelo celular usando o endereço abaixo, na mesma rede Wi-Fi/Ethernet.",15,0xffe6ebf3,false);
        LinearLayout.LayoutParams h=lp(-1,-2);h.topMargin=dp(18);page.addView(help,h);
        page.addView(text("MAC EXIBIDO NO LK PLAY",12,0xffaab8cc,true));
        mac=edit("00:11:22:33:44:55",false);page.addView(mac,lp(-1,dp(52)));
        LinearLayout.LayoutParams kl=lp(-1,-2);kl.topMargin=dp(10);page.addView(text("CHAVE DO DISPOSITIVO",12,0xffaab8cc,true),kl);
        key=edit("Digite a chave do dispositivo",true);page.addView(key,lp(-1,dp(52)));
        Button save=button("SALVAR MAC E CHAVE");LinearLayout.LayoutParams sl=lp(-1,dp(50));sl.topMargin=dp(12);page.addView(save,sl);
        save.setOnClickListener(v->save());
        state=text("Servidor local iniciando...",14,0xff8fe0b1,false);LinearLayout.LayoutParams st=lp(-1,-2);st.topMargin=dp(18);page.addView(state,st);
        address=text("",16,0xffffffff,true);LinearLayout.LayoutParams al=lp(-1,-2);al.topMargin=dp(8);page.addView(address,al);
        Button copy=button("COPIAR ENDEREÇO");LinearLayout.LayoutParams cl=lp(-1,dp(50));cl.topMargin=dp(12);page.addView(copy,cl);
        copy.setOnClickListener(v->{((ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("LK Play",address.getText()));Toast.makeText(this,"Endereço copiado",Toast.LENGTH_SHORT).show();});
        Button open=button("ABRIR PAINEL");LinearLayout.LayoutParams ol=lp(-1,dp(50));ol.topMargin=dp(8);page.addView(open,ol);
        open.setOnClickListener(v->startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse("http://127.0.0.1:8765/"))));
        TextView warning=text("ATENÇÃO: esta versão é um gerenciador auxiliar. Ela armazena listas no TV Box e oferece uma API local, mas ainda não altera nem conecta automaticamente o player do APK LK Play original. O aplicativo precisa permanecer aberto enquanto o painel é usado. Não exponha esta porta à internet.",13,0xffffd18a,false);
        LinearLayout.LayoutParams wl=lp(-1,-2);wl.topMargin=dp(22);page.addView(warning,wl);
        mac.setText(getPreferences(0).getString("mac",""));
        address.setText("http://"+PanelServer.localIp()+":8765/");
        state.setText("Servidor local: "+(server.isAlive()?"ATIVO":"iniciando"));
        setContentView(scroll);
    }
    private void save(){
        String m=mac.getText().toString().trim().toUpperCase().replace('-',':');
        String k=key.getText().toString();
        if(!m.matches("^[0-9A-F]{2}(:[0-9A-F]{2}){5}$")){mac.setError("MAC inválido");return;}
        if(k.trim().length()<6){key.setError("Chave muito curta");return;}
        getPreferences(0).edit().putString("mac",m).putString("key_hash",PanelServer.sha256(k)).apply();
        key.setText("");state.setText("Credenciais salvas. Use o mesmo MAC e chave no navegador.");
        Toast.makeText(this,"Salvo",Toast.LENGTH_SHORT).show();
    }
    private TextView text(String s,int size,int color,boolean bold){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);t.setGravity(Gravity.CENTER_VERTICAL);if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return t;}
    private EditText edit(String hint,boolean secret){EditText e=new EditText(this);e.setSingleLine(true);e.setTextSize(16);e.setHint(hint);e.setTextColor(-1);e.setHintTextColor(0xff77879e);e.setPadding(dp(12),0,dp(12),0);if(secret)e.setInputType(129);return e;}
    private Button button(String s){Button b=new Button(this);b.setText(s);b.setTextColor(-1);b.setTextSize(13);b.setTypeface(Typeface.DEFAULT,Typeface.BOLD);b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xff16877d));return b;}
    private LinearLayout.LayoutParams lp(int w,int h){return new LinearLayout.LayoutParams(w,h);}
}
