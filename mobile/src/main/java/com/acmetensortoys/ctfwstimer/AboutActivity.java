package com.acmetensortoys.ctfwstimer;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.webkit.WebView;
import android.widget.TabHost;

public class AboutActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_about);

        View iv = findViewById(R.id.about_image);
        iv.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW,
                            Uri.parse("http://www.cmukgb.org/")));
                } catch (ActivityNotFoundException anfe) {
                    // NOP
                }
            }
        });

        {
            final WebView wv = (WebView) findViewById(R.id.about_text);
            wv.loadData(getResources().getString(R.string.about_text),
                    "text/html", null);
        }

        {
            final WebView wv = (WebView) findViewById(R.id.about_licenses);
            wv.loadUrl("file:///android_asset/licenses.html");
        }

        TabHost th = (TabHost) findViewById(R.id.about_tab_host);
        th.setup();

        th.addTab(th.newTabSpec("TagProg")
                .setContent(R.id.about_tab_program)
                .setIndicator("Program"));

        th.addTab(th.newTabSpec("TagLic")
                .setContent(R.id.about_tab_lic)
                .setIndicator("Licenses"));
    }
}
