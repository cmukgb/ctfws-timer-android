package com.acmetensortoys.ctfwstimer;

import android.content.res.AssetManager;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.VectorDrawable;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.DividerItemDecoration;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.widget.Button;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

public class HandbookActivity extends AppCompatActivity {

    private class HandbookAdapter extends RecyclerView.Adapter<HandbookAdapter.MyVH> {

        class MyVH extends RecyclerView.ViewHolder {
            Button b;
            MyVH(Button b) {
                super(b);
                this.b = b;
            }
        }

        final String[] fns;
        final View.OnClickListener ocl;

        HandbookAdapter(String[] fns, View.OnClickListener ocl) {
            this.fns = fns;
            this.ocl = ocl;
        }



        @Override
        public MyVH onCreateViewHolder(ViewGroup parent, int tipe) {
            Button b = new Button(HandbookActivity.this, null, R.style.Widget_AppCompat_Button_Borderless);
            b.setOnClickListener(ocl);
            return new MyVH(b);
        }

        @Override
        public void onBindViewHolder(MyVH vh, int pos) {
            try {
                InputStream is = getAssets().open("handico/" + fns[pos] + ".png");
                Drawable d = BitmapDrawable.createFromStream(is, fns[pos]);
                vh.b.setBackground(d);
            } catch (IOException ignored) {
                String ch = fns[pos];
                int ix = ch.indexOf('-');
                if (ix >= 0) { ch = ch.substring(ix); }
                vh.b.setText(ch);
            }
        }

        @Override
        public int getItemCount() {
            return fns.length;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.d("CtFwSHandbook", "onCreate");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_handbook);

        final WebView wv = findViewById(R.id.hand_wv);

        final String[] sections;
        try {
            InputStream is = getAssets().open("handord.txt", AssetManager.ACCESS_STREAMING);
            BufferedReader br = new BufferedReader(new InputStreamReader(is));
            List<String> lines = new ArrayList<>();
            String line;
            while ((line = br.readLine()) != null) {
                lines.add(line);
            }
            sections = lines.toArray(new String[0]);

            final RecyclerView rv = findViewById(R.id.hand_tabs);
            rv.setHasFixedSize(true);

            LinearLayoutManager lm = new LinearLayoutManager(this);
            rv.setLayoutManager(lm);

            Drawable divdr = ContextCompat.getDrawable(this, R.drawable.hand_tab_div);
            if (divdr != null) {
                DividerItemDecoration divde = new DividerItemDecoration(rv.getContext(),
                        lm.getOrientation());
                divde.setDrawable(divdr);
                rv.addItemDecoration(divde);
            }

            final View.OnClickListener ocl = new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    int ix = rv.getChildAdapterPosition(v);
                    Log.d("CtFwSHandbook", "onClick avix=" + ix);
                    if (ix != RecyclerView.NO_POSITION) {
                        Log.d("CtFwSHandbook" , "onClick avs=" + sections[ix]);
                        wv.loadUrl("file:///android_asset/hand.html#" + sections[ix]);
                    }
                }
            };

            RecyclerView.Adapter a = new HandbookAdapter(sections, ocl);
            rv.setAdapter(a);
        } catch (IOException ioe) {
            Log.d("CtFwSHandbook", "IOException?", ioe);
        }

        wv.loadUrl("file:///android_asset/hand.html");
    }
}
