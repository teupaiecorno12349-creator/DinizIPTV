package com.diniz.orsayinstaller;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public final class OrsayTestWidget {
    private OrsayTestWidget() {}

    public static File ensure(File cacheDir) throws IOException {
        File out = new File(cacheDir, "DinizOrsayTestGame_1.0_BRA_20261006.zip");
        if (out.exists() && out.length() > 1000) return out;

        try (FileOutputStream fos = new FileOutputStream(out);
             ZipOutputStream zip = new ZipOutputStream(fos)) {
            final String root = "DinizOrsayTestGame/";

            putText(zip, root + "config.xml",
                    "<?xml version="1.0" encoding="UTF-8"?>\n" +
                    "<widget>\n" +
                    "  <ThumbIcon itemtype="string">icon/ThumbIcon.png</ThumbIcon>\n" +
                    "  <BigThumbIcon itemtype="string">icon/BigThumbIcon.png</BigThumbIcon>\n" +
                    "  <ListIcon itemtype="string">icon/ListIcon.png</ListIcon>\n" +
                    "  <BigListIcon itemtype="string">icon/BigListIcon.png</BigListIcon>\n" +
                    "  <category itemtype="string">games</category>\n" +
                    "  <type itemtype="string">user</type>\n" +
                    "  <cpname itemtype="string"></cpname>\n" +
                    "  <cpimg itemtype="string"></cpimg>\n" +
                    "  <ver itemtype="string">1.000</ver>\n" +
                    "  <mgrver itemtype="string"></mgrver>\n" +
                    "  <fullwidget itemtype="boolean">y</fullwidget>\n" +
                    "  <srcctl itemtype="boolean">y</srcctl>\n" +
                    "  <ticker itemtype="boolean">n</ticker>\n" +
                    "  <childlock itemtype="boolean">n</childlock>\n" +
                    "  <audiomute itemtype="boolean">n</audiomute>\n" +
                    "  <videomute itemtype="boolean">n</videomute>\n" +
                    "  <dcont itemtype="boolean">y</dcont>\n" +
                    "  <mouse itemtype="boolean">y</mouse>\n" +
                    "  <keyboard itemtype="boolean">y</keyboard>\n" +
                    "  <widgetname itemtype="string">Diniz Orsay Test Game</widgetname>\n" +
                    "  <description itemtype="string">Teste de instalação do Samsung Orsay.</description>\n" +
                    "  <width itemtype="number">1280</width>\n" +
                    "  <height itemtype="number">720</height>\n" +
                    "  <author itemtype="group">\n" +
                    "    <name itemtype="string">Diniz</name>\n" +
                    "    <email itemtype="string"></email>\n" +
                    "    <link itemtype="string"></link>\n" +
                    "    <organization itemtype="string">Diniz</organization>\n" +
                    "  </author>\n" +
                    "</widget>\n");

            putText(zip, root + "widget.info",
                    "Application Position = 0,0\n" +
                    "Application Size = 1280x720\n\n" +
                    "# Screen Setting\n" +
                    "Screen Number = 1\n" +
                    "Screen Resolution = 1280x720\n\n" +
                    "# Alpha Blending Setting\n" +
                    "Use Alpha Blending? = Yes\n");

            putText(zip, root + "index.html", html());

            putPng(zip, root + "icon/ThumbIcon.png", 106, 87);
            putPng(zip, root + "icon/BigThumbIcon.png", 115, 95);
            putPng(zip, root + "icon/ListIcon.png", 85, 70);
            putPng(zip, root + "icon/BigListIcon.png", 95, 78);
        }
        return out;
    }

    private static String html() {
        return "<!DOCTYPE html>\n" +
                "<html><head><meta charset="UTF-8">" +
                "<title>Diniz Orsay Test</title>" +
                "<style>" +
                "html,body{margin:0;padding:0;width:1280px;height:720px;overflow:hidden;" +
                "background:#101010;color:white;font-family:Arial,sans-serif}" +
                "#box{position:absolute;left:500px;top:260px;width:280px;height:160px;" +
                "background:#2d7cff;border:5px solid white;text-align:center;font-size:28px;" +
                "line-height:160px;box-sizing:border-box}" +
                "#info{position:absolute;left:40px;top:35px;font-size:30px}" +
                "#sub{position:absolute;left:40px;top:85px;font-size:22px;color:#bbbbbb}" +
                "</style></head>" +
                "<body>" +
                "<div id="info">DINIZ ORSAY TEST GAME</div>" +
                "<div id="sub">ENTER = mudar cor • setas = mover quadrado</div>" +
                "<div id="box">FUNCIONANDO</div>" +
                "<script>" +
                "(function(){var b=document.getElementById('box');" +
                "var x=500,y=260,c=0;var colors=['#2d7cff','#21c77a','#ff7a21','#d12dff'];" +
                "function ready(){try{if(window.widgetAPI&&widgetAPI.sendReadyEvent)widgetAPI.sendReadyEvent();}catch(e){}}" +
                "function paint(){b.style.left=x+'px';b.style.top=y+'px';b.style.background=colors[c%colors.length];}" +
                "document.onkeydown=function(e){e=e||window.event;var k=e.keyCode;" +
                "if(k===13){c++;paint();return false;}" +
                "if(k===37){x=Math.max(0,x-20);paint();return false;}" +
                "if(k===39){x=Math.min(1000,x+20);paint();return false;}" +
                "if(k===38){y=Math.max(140,y-20);paint();return false;}" +
                "if(k===40){y=Math.min(500,y+20);paint();return false;}" +
                "};" +
                "window.onload=ready;" +
                "})();" +
                "</script></body></html>\n";
    }

    private static void putText(ZipOutputStream zip, String name, String text) throws IOException {
        ZipEntry e = new ZipEntry(name);
        zip.putNextEntry(e);
        byte[] data = text.getBytes(StandardCharsets.UTF_8);
        zip.write(data);
        zip.closeEntry();
    }

    private static void putPng(ZipOutputStream zip, String name, int width, int height) throws IOException {
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        canvas.drawColor(Color.rgb(35, 60, 110));
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(Color.WHITE);
        p.setTextAlign(Paint.Align.CENTER);
        p.setTextSize(Math.max(16, width / 6f));
        canvas.drawText("D", width / 2f, height / 2f - p.ascent() / 2f, p);
        File temp = File.createTempFile("orsay_icon", ".png");
        try (FileOutputStream fos = new FileOutputStream(temp)) {
            if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)) {
                throw new IOException("Falha ao gerar ícone");
            }
        } finally {
            bitmap.recycle();
        }
        ZipEntry e = new ZipEntry(name);
        zip.putNextEntry(e);
        java.io.FileInputStream in = new java.io.FileInputStream(temp);
        byte[] buf = new byte[8192];
        int n;
        try {
            while ((n = in.read(buf)) != -1) zip.write(buf, 0, n);
        } finally {
            in.close();
            temp.delete();
            zip.closeEntry();
        }
    }
}
