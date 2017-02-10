package com.acmetensortoys.ctfwstimer;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.webkit.WebView;

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

        final WebView wv = (WebView) findViewById(R.id.about_text);
        wv.loadData(getResources().getString(R.string.about_text), "text/html", null);
    }
}
