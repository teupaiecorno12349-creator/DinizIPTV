package com.diniz.iptv.player;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.*;

public class SettingsActivity extends Activity {
    private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}
    private TextView text(String s,int sp){TextView t=new TextView(this);t.setText(s);t.setTextSize(sp);t.setTextColor(Color.WHITE);return t;}
    private GradientDrawable bg(int c,int r){GradientDrawable d=new GradientDrawable();d.setColor(c);d.setCornerRadius(dp(r));return d;}
    private RadioGroup dnsGroup,resGroup,bufferGroup;
    private CheckBox autoplay;
    private android.content.SharedPreferences prefs;

    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().getDecorView().setSystemUiVisibility(5894|1024|512);
        prefs=getSharedPreferences(NetworkStack.PREFS,MODE_PRIVATE);
        ScrollView scroll=new ScrollView(this);scroll.setBackgroundColor(Color.rgb(7,10,16));
        LinearLayout page=new LinearLayout(this);page.setOrientation(LinearLayout.VERTICAL);page.setPadding(dp(34),dp(24),dp(34),dp(30));scroll.addView(page);
        LinearLayout head=new LinearLayout(this);head.setGravity(Gravity.CENTER_VERTICAL);page.addView(head,new LinearLayout.LayoutParams(-1,dp(56)));
        TextView title=text("⚙  Configurações",28);title.setTypeface(Typeface.DEFAULT_BOLD);head.addView(title,new LinearLayout.LayoutParams(0,-2,1));
        Button done=button("CONCLUÍDO");head.addView(done,new LinearLayout.LayoutParams(dp(155),dp(48)));done.setOnClickListener(v->finish());
        TextView intro=text("Ajuste a rede e a reprodução para a sua TV Box.",15);intro.setTextColor(Color.rgb(160,172,191));page.addView(intro,new LinearLayout.LayoutParams(-1,dp(42)));
        section(page,"DNS PRIVADO DO APLICATIVO","Vale apenas para as conexões feitas pelo Marcin IPTV; não altera o DNS global do Android.");
        dnsGroup=new RadioGroup(this);dnsGroup.setOrientation(RadioGroup.VERTICAL);page.addView(dnsGroup);
        addRadio(dnsGroup,"system","Automático (DNS do Android)");
        addRadio(dnsGroup,"cloudflare","Cloudflare DNS · 1.1.1.1");
        addRadio(dnsGroup,"google","Google DNS · 8.8.8.8");
        dnsGroup.setOnCheckedChangeListener((g,id)->{
            RadioButton r=findRadio(g,id);if(r!=null)NetworkStack.apply(this,String.valueOf(r.getTag()));
        });
        checkGroup(dnsGroup,NetworkStack.mode(this));

        section(page,"RESOLUÇÃO DE VÍDEO","Automática usa a melhor qualidade oferecida pelo servidor e suportada pelo dispositivo. As opções abaixo limitam a resolução máxima.");
        resGroup=new RadioGroup(this);resGroup.setOrientation(RadioGroup.VERTICAL);page.addView(resGroup);
        addRadio(resGroup,"auto","Automática · melhor disponível");
        addRadio(resGroup,"720","Até 720p");
        addRadio(resGroup,"1080","Até 1080p / Full HD");
        addRadio(resGroup,"2160","Até 2160p / 4K, quando disponível");
        checkGroup(resGroup,prefs.getString(NetworkStack.RESOLUTION,"auto"));
        resGroup.setOnCheckedChangeListener((g,id)->{RadioButton r=findRadio(g,id);if(r!=null)prefs.edit().putString(NetworkStack.RESOLUTION,String.valueOf(r.getTag())).apply();});

        section(page,"ESTABILIDADE DO PLAYER","Buffer maior pode reduzir pausas, mas aumenta o tempo inicial para começar.");
        bufferGroup=new RadioGroup(this);bufferGroup.setOrientation(RadioGroup.VERTICAL);page.addView(bufferGroup);
        addRadio(bufferGroup,"stable","Estável · buffer maior (recomendado)");
        addRadio(bufferGroup,"balanced","Equilibrado");
        addRadio(bufferGroup,"low","Baixa latência · buffer menor");
        checkGroup(bufferGroup,prefs.getString(NetworkStack.BUFFER_MODE,"stable"));
        bufferGroup.setOnCheckedChangeListener((g,id)->{RadioButton r=findRadio(g,id);if(r!=null)prefs.edit().putString(NetworkStack.BUFFER_MODE,String.valueOf(r.getTag())).apply();});

        autoplay=new CheckBox(this);autoplay.setText("Avançar automaticamente para o próximo episódio");autoplay.setTextColor(Color.WHITE);autoplay.setTextSize(16);autoplay.setButtonTintList(android.content.res.ColorStateList.valueOf(Color.rgb(79,130,255)));
        autoplay.setChecked(prefs.getBoolean(NetworkStack.AUTOPLAY_NEXT,true));LinearLayout.LayoutParams ap=new LinearLayout.LayoutParams(-1,dp(54));ap.setMargins(0,dp(10),0,dp(14));page.addView(autoplay,ap);
        autoplay.setOnCheckedChangeListener((v,checked)->prefs.edit().putBoolean(NetworkStack.AUTOPLAY_NEXT,checked).apply());

        section(page,"CATÁLOGO E CONTA","O catálogo é armazenado localmente para abrir mais rápido na próxima vez.");
        Button refresh=button("↻  ATUALIZAR LISTA E METADADOS");page.addView(refresh,new LinearLayout.LayoutParams(dp(410),dp(54)));
        refresh.setOnClickListener(v->{setResult(RESULT_OK,new Intent().putExtra("refresh",true));finish();});
        Button clear=button("LIMPAR CAPAS E CATÁLOGO EM CACHE");LinearLayout.LayoutParams cl=new LinearLayout.LayoutParams(dp(410),dp(54));cl.setMargins(0,dp(10),0,0);page.addView(clear,cl);
        clear.setOnClickListener(v->new AlertDialog.Builder(this).setTitle("Limpar o cache?")
            .setMessage("As informações locais e as capas serão removidas. A próxima abertura precisará baixar o catálogo novamente.")
            .setNegativeButton("Cancelar",null).setPositiveButton("Limpar",(d,w)->{CatalogStore.clearCache(this);Toast.makeText(this,"Cache removido.",Toast.LENGTH_SHORT).show();}).show());
        Button logout=button("SAIR DA CONTA");LinearLayout.LayoutParams ll=new LinearLayout.LayoutParams(dp(260),dp(54));ll.setMargins(0,dp(10),0,0);page.addView(logout,ll);
        logout.setOnClickListener(v->new AlertDialog.Builder(this).setTitle("Sair da conta")
            .setMessage("Remover usuário e senha guardados neste dispositivo?")
            .setNegativeButton("Cancelar",null).setPositiveButton("Sair",(d,w)->{
                getSharedPreferences("xtream_account",MODE_PRIVATE).edit().clear().apply();CatalogStore.clear();
                setResult(RESULT_OK,new Intent().putExtra("logout",true));finish();
            }).show());
        setContentView(scroll);
    }
    private void section(LinearLayout page,String title,String help){
        TextView t=text(title,17);t.setTextColor(Color.rgb(130,167,255));t.setTypeface(Typeface.DEFAULT_BOLD);LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(-1,dp(35));tp.setMargins(0,dp(18),0,0);page.addView(t,tp);
        TextView h=text(help,13);h.setTextColor(Color.rgb(160,172,191));h.setPadding(0,0,0,dp(6));page.addView(h,new LinearLayout.LayoutParams(-1,-2));
    }
    private void addRadio(RadioGroup group,String tag,String label){
        RadioButton r=new RadioButton(this);r.setTag(tag);r.setText(label);r.setTextSize(15);r.setTextColor(Color.WHITE);r.setPadding(dp(8),0,dp(8),0);
        r.setButtonTintList(android.content.res.ColorStateList.valueOf(Color.rgb(79,130,255)));group.addView(r,new RadioGroup.LayoutParams(-1,dp(43)));
    }
    private RadioButton findRadio(RadioGroup g,int id){return g.findViewById(id);}
    private void checkGroup(RadioGroup g,String desired){
        for(int i=0;i<g.getChildCount();i++){RadioButton r=(RadioButton)g.getChildAt(i);if(desired.equals(r.getTag())){r.setChecked(true);return;}}
        if(g.getChildCount()>0)((RadioButton)g.getChildAt(0)).setChecked(true);
    }
    private Button button(String s){Button b=new Button(this);b.setText(s);b.setTextSize(14);b.setTextColor(Color.WHITE);b.setAllCaps(false);b.setFocusable(true);b.setBackground(bg(Color.rgb(20,31,48),9));b.setOnFocusChangeListener((v,f)->v.setBackground(bg(f?Color.rgb(79,130,255):Color.rgb(20,31,48),9)));return b;}
}