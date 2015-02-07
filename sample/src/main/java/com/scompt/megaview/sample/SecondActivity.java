package com.scompt.megaview.sample;

import android.app.Activity;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.TextView;

public class SecondActivity extends Activity {
    public static final String LABEL_EXTRA = "Label_Extra";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        String label = getIntent().getStringExtra(SecondActivity.LABEL_EXTRA);

        TextView textView = new TextView(this);
        textView.setGravity(Gravity.CENTER);
        textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 100);
        textView.setText(label);

        textView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onBackPressed();
            }
        });

        setContentView(textView);
    }
}
