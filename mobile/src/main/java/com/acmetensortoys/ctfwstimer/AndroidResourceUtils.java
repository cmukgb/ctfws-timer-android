package com.acmetensortoys.ctfwstimer;

import android.content.res.Resources;
import android.os.Build;
import android.text.Html;
import android.text.Spanned;

public class AndroidResourceUtils {
    public static Spanned htmlFromStrResId(Resources rs, int id, Object... args) {
        if (Build.VERSION.SDK_INT >= 24) {
            return Html.fromHtml(String.format(rs.getString(id), args), 0);
        } else {
            //noinspection deprecation
            return Html.fromHtml(String.format(rs.getString(id), args));
        }
    }
}
