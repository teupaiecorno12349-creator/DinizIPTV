package com.diniz.orsayinstaller;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.Base64;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public final class OrsayTestWidget {
    private static final String CONFIG_B64 = "PD94bWwgdmVyc2lvbj0iMS4wIiBlbmNvZGluZz0iVVRGLTgiPz4KPHdpZGdldD4KICA8VGh1bWJJY29uIGl0ZW10eXBlPSJzdHJpbmciPmljb24vVGh1bWJJY29uLnBuZzwvVGh1bWJJY29uPgogIDxCaWdUaHVtYkljb24gaXRlbXR5cGU9InN0cmluZyI+aWNvbi9CaWdUaHVtYkljb24ucG5nPC9CaWdUaHVtYkljb24+CiAgPExpc3RJY29uIGl0ZW10eXBlPSJzdHJpbmciPmljb24vTGlzdEljb24ucG5nPC9MaXN0SWNvbj4KICA8QmlnTGlzdEljb24gaXRlbXR5cGU9InN0cmluZyI+aWNvbi9CaWdMaXN0SWNvbi5wbmc8L0JpZ0xpc3RJY29uPgogIDxjYXRlZ29yeSBpdGVtdHlwZT0ic3RyaW5nIj5nYW1lczwvY2F0ZWdvcnk+CiAgPHR5cGUgaXRlbXR5cGU9InN0cmluZyI+dXNlcjwvdHlwZT4KICA8Y3BuYW1lIGl0ZW10eXBlPSJzdHJpbmciPjwvY3BuYW1lPgogIDx2ZXIgaXRlbXR5cGU9InN0cmluZyI+MS4wMDA8L3Zlcj4KICA8bWdydmVyIGl0ZW10eXBlPSJzdHJpbmciPjwvbWdydmVyPgogIDxmdWxsd2lkZ2V0IGl0ZW10eXBlPSJib29sZWFuIj55PC9mdWxsd2lkZ2V0PgogIDxzcmNjdGwgaXRlbXR5cGU9ImJvb2xlYW4iPnk8L3NyY2N0bD4KICA8dGlja2VyIGl0ZW10eXBlPSJib29sZWFuIj5uPC90aWNrZXI+CiAgPGNoaWxkbG9jayBpdGVtdHlwZT0iYm9vbGVhbiI+bjwvY2hpbGRsb2NrPgogIDxhdWRpb211dGUgaXRlbXR5cGU9ImJvb2xlYW4iPm48L2F1ZGlvbXV0ZT4KICA8dmlkZW9tdXRlIGl0ZW10eXBlPSJib29sZWFuIj5uPC92aWRlb211dGU+CiAgPGRjb250IGl0ZW10eXBlPSJib29sZWFuIj55PC9kY29udD4KICA8bW91c2UgaXRlbXR5cGU9ImJvb2xlYW4iPnk8L21vdXNlPgogIDxrZXlib2FyZCBpdGVtdHlwZT0iYm9vbGVhbiI+eTwva2V5Ym9hcmQ+CiAgPHdpZGdldG5hbWUgaXRlbXR5cGU9InN0cmluZyI+RGluaXogT3JzYXkgVGVzdCBHYW1lPC93aWRnZXRuYW1lPgogIDxkZXNjcmlwdGlvbiBpdGVtdHlwZT0ic3RyaW5nIj5UZXN0ZSBkZSBpbnN0YWxhY2FvIGRvIFNhbXN1bmcgT3JzYXkuPC9kZXNjcmlwdGlvbj4KICA8d2lkdGggaXRlbXR5cGU9Im51bWJlciI+MTI4MDwvd2lkdGg+CiAgPGhlaWdodCBpdGVtdHlwZT0ibnVtYmVyIj43MjA8L2hlaWdodD4KICA8YXV0aG9yIGl0ZW10eXBlPSJncm91cCI+CiAgICA8bmFtZSBpdGVtdHlwZT0ic3RyaW5nIj5EaW5pejwvbmFtZT4KICAgIDxlbWFpbCBpdGVtdHlwZT0ic3RyaW5nIj48L2VtYWlsPgogICAgPGxpbmsgaXRlbXR5cGU9InN0cmluZyI+PC9saW5rPgogICAgPG9yZ2FuaXphdGlvbiBpdGVtdHlwZT0ic3RyaW5nIj5EaW5pejwvb3JnYW5pemF0aW9uPgogIDwvYXV0aG9yPgo8L3dpZGdldD4K";
    private static final String INFO_B64 = "QXBwbGljYXRpb24gUG9zaXRpb24gPSAwLDAKQXBwbGljYXRpb24gU2l6ZSA9IDEyODB4NzIwCgojIFNjcmVlbiBTZXR0aW5nClNjcmVlbiBOdW1iZXIgPSAxClNjcmVlbiBSZXNvbHV0aW9uID0gMTI4MHg3MjAKCiMgQWxwaGEgQmxlbmRpbmcgU2V0dGluZwpVc2UgQWxwaGEgYmxlbmRpbmc/ID0gWWVzCg==";
    private static final String HTML_B64 = "PCFET0NUWVBFIGh0bWw+CjxodG1sPgo8aGVhZD4KPG1ldGEgY2hhcnNldD0iVVRGLTgiPgo8dGl0bGU+RGluaXogT3JzYXkgVGVzdDwvdGl0bGU+CjxzdHlsZT4KaHRtbCxib2R5e21hcmdpbjowO3BhZGRpbmc6MDt3aWR0aDoxMjgwcHg7aGVpZ2h0OjcyMHB4O292ZXJmbG93OmhpZGRlbjtiYWNrZ3JvdW5kOiMxMDEwMTA7Y29sb3I6d2hpdGU7Zm9udC1mYW1pbHk6QXJpYWwsc2Fucy1zZXJpZn0KI2JveHtwb3NpdGlvbjphYnNvbHV0ZTtsZWZ0OjUwMHB4O3RvcDoyNjBweDt3aWR0aDoyODBweDtoZWlnaHQ6MTYwcHg7YmFja2dyb3VuZDojMmQ3Y2ZmO2JvcmRlcjo1cHggc29saWQgd2hpdGU7dGV4dC1hbGlnbjpjZW50ZXI7Zm9udC1zaXplOjI4cHg7bGluZS1oZWlnaHQ6MTYwcHg7Ym94LXNpemluZzpib3JkZXItYm94fQojaW5mb3twb3NpdGlvbjphYnNvbHV0ZTtsZWZ0OjQwcHg7dG9wOjM1cHg7Zm9udC1zaXplOjMwcHh9CiNzdWJ7cG9zaXRpb246YWJzb2x1dGU7bGVmdDo0MHB4O3RvcDo4NXB4O2ZvbnQtc2l6ZToyMnB4O2NvbG9yOiNiYmJiYmJ9Cjwvc3R5bGU+CjwvaGVhZD4KPGJvZHk+CjxkaXYgaWQ9ImluZm8iPkRJTklaIE9SU0FZIFRFU1QgR0FNRTwvZGl2Pgo8ZGl2IGlkPSJzdWIiPkVOVEVSID0gbXVkYXIgY29yIC0gc2V0YXMgPSBtb3ZlciBxdWFkcmFkbzwvZGl2Pgo8ZGl2IGlkPSJib3giPkZVTkNJT05BTkRPPC9kaXY+CjxzY3JpcHQ+CihmdW5jdGlvbigpewogIHZhciBiPWRvY3VtZW50LmdldEVsZW1lbnRCeUlkKCdib3gnKTsKICB2YXIgeD01MDAseT0yNjAsYz0wOwogIHZhciBjb2xvcnM9WycjMmQ3Y2ZmJywnIzIxYzc3YScsJyNmZjdhMjEnLCcjZDEyZGZmJ107CiAgZnVuY3Rpb24gcmVhZHkoKXsKICAgIHRyeXsKICAgICAgaWYod2luZG93LndpZGdldEFQSSAmJiB3aWRnZXRBUEkuc2VuZFJlYWR5RXZlbnQpIHdpZGdldEFQSS5zZW5kUmVhZHlFdmVudCgpOwogICAgfWNhdGNoKGUpe30KICB9CiAgZnVuY3Rpb24gcGFpbnQoKXsKICAgIGIuc3R5bGUubGVmdD14KydweCc7CiAgICBiLnN0eWxlLnRvcD15KydweCc7CiAgICBiLnN0eWxlLmJhY2tncm91bmQ9Y29sb3JzW2MlY29sb3JzLmxlbmd0aF07CiAgfQogIGRvY3VtZW50Lm9ua2V5ZG93bj1mdW5jdGlvbihlKXsKICAgIGU9ZXx8d2luZG93LmV2ZW50OwogICAgdmFyIGs9ZS5rZXlDb2RlOwogICAgaWYoaz09PTEzKXtjKys7cGFpbnQoKTtyZXR1cm4gZmFsc2U7fQogICAgaWYoaz09PTM3KXt4PU1hdGgubWF4KDAseC0yMCk7cGFpbnQoKTtyZXR1cm4gZmFsc2U7fQogICAgaWYoaz09PTM5KXt4PU1hdGgubWluKDEwMDAseCsyMCk7cGFpbnQoKTtyZXR1cm4gZmFsc2U7fQogICAgaWYoaz09PTM4KXt5PU1hdGgubWF4KDE0MCx5LTIwKTtwYWludCgpO3JldHVybiBmYWxzZTt9CiAgICBpZig9PTQwKXt5PU1hdGgubWluKDUwMCx5KzIwKTtwYWludCgpO3JldHVybiBmYWxzZTt9CiAgICByZXR1cm4gdHJ1ZTsKICB9OwogIHdpbmRvdy5vbmxvYWQ9cmVhZHk7Cn0pKCk7Cjwvc2NyaXB0Pgo8L2JvZHk+CjwvaHRtbD4K";

    private OrsayTestWidget() {}

    public static File ensure(File cacheDir) throws IOException {
        File out = new File(cacheDir, "DinizOrsayTestGame_1.000_BRA_20261006.zip");
        if (out.exists() && out.length() > 1000) return out;

        try (FileOutputStream fos = new FileOutputStream(out);
             ZipOutputStream zip = new ZipOutputStream(fos)) {
            String root = "DinizOrsayTestGame/";
            putBytes(zip, root + "config.xml", decode(CONFIG_B64));
            putBytes(zip, root + "widget.info", decode(INFO_B64));
            putBytes(zip, root + "index.html", decode(HTML_B64));
            putPng(zip, root + "icon/ThumbIcon.png", 106, 87);
            putPng(zip, root + "icon/BigThumbIcon.png", 115, 95);
            putPng(zip, root + "icon/ListIcon.png", 85, 70);
            putPng(zip, root + "icon/BigListIcon.png", 95, 78);
        }
        return out;
    }

    private static byte[] decode(String s) {
        return Base64.decode(s, Base64.NO_WRAP);
    }

    private static void putBytes(ZipOutputStream zip, String name, byte[] data) throws IOException {
        ZipEntry e = new ZipEntry(name);
        zip.putNextEntry(e);
        zip.write(data);
        zip.closeEntry();
    }

    private static void putPng(ZipOutputStream zip, String name, int width, int height) throws IOException {
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        File temp = File.createTempFile("orsay_icon", ".png");
        try {
            Canvas canvas = new Canvas(bitmap);
            canvas.drawColor(Color.rgb(35, 60, 110));
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            p.setColor(Color.WHITE);
            p.setTextAlign(Paint.Align.CENTER);
            p.setTextSize(Math.max(16, width / 6f));
            canvas.drawText("D", width / 2f, height / 2f - (p.ascent() + p.descent()) / 2f, p);

            try (FileOutputStream fos = new FileOutputStream(temp)) {
                if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)) {
                    throw new IOException("Falha ao gerar icone");
                }
            }

            ZipEntry e = new ZipEntry(name);
            zip.putNextEntry(e);
            try (FileInputStream in = new FileInputStream(temp)) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) != -1) zip.write(buf, 0, n);
            }
            zip.closeEntry();
        } finally {
            temp.delete();
            bitmap.recycle();
        }
    }
}
