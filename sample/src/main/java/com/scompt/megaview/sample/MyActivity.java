package com.scompt.megaview.sample;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.ToggleButton;

import com.scompt.megaview.R;
import com.scompt.megaview.library.MegaView;

import java.util.ArrayList;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import butterknife.ButterKnife;
import butterknife.InjectView;
import butterknife.OnClick;
import rx.Observable;
import rx.functions.Func1;

public class MyActivity extends Activity {

    private static final String STRINGS_STATE_KEY = "Strings_State";

    @InjectView(R.id.megaview)
    MegaView<String, RowViewHolder> megaView;

    @InjectView(R.id.immediate_response)
    ToggleButton mImmediateResponse;

    @InjectView(R.id.short_delay_response)
    ToggleButton mShortDelayResponse;

    @InjectView(R.id.error_response)
    ToggleButton mErrorButton;

    @InjectView(R.id.empty_response)
    ToggleButton mEmptyResponse;

    @InjectView(R.id.connected)
    ToggleButton mConnected;

    @InjectView(R.id.delay_group)
    RadioGroup mDelayGroup;

    @InjectView(R.id.content_group)
    RadioGroup mContentGroup;

    @InjectView(R.id.connectivity_group)
    RadioGroup mConnectivityGroup;
    private ArrayList<String> strings = new ArrayList<>();

    private Observable<String> getContentObservable(int page) {
        if (mEmptyResponse.isChecked()) {
            return Observable.empty();
        } else {
            return Observable.range(page * 10, 10).map(new Func1<Integer, String>() {
                @Override
                public String call(Integer integer) {
                    return String.valueOf(integer);
                }
            });
        }
    }

    private static final Random RANDOM = new Random();

    private Observable<String> getDelayedObservable(Observable<String> in) {
        Observable<String> delayed;

        if (mImmediateResponse.isChecked()) {
            delayed = in;
        } else {
            final int delay;
            if (mShortDelayResponse.isChecked()) {
                delay = RANDOM.nextInt(1000) + 500;
            } else {
                delay = RANDOM.nextInt(3000) + 3000;
            }
            delayed = in.delay(delay, TimeUnit.MILLISECONDS);
        }

        if (mErrorButton.isChecked()) {
            return delayed.flatMap(new Func1<String, Observable<String>>() {
                @Override
                public Observable<String> call(String strings) {
                    return Observable.error(new Exception());
                }
            });
        } else {
            return delayed;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity);
        ButterKnife.inject(this);

        mContentGroup.setOnCheckedChangeListener(ToggleListener);
        mDelayGroup.setOnCheckedChangeListener(ToggleListener);
        mConnectivityGroup.setOnCheckedChangeListener(ToggleListener);

        megaView.setDebug(true);
        megaView.setNoConnectionLayout(R.layout.full_no_connection,
                R.layout.full_no_connection,
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        megaView.reload();
                    }
                });
        megaView.setErrorLayout(R.layout.full_error,
                R.layout.full_error,
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        megaView.reload();
                    }
                });
        megaView.setEmptyLayout(R.layout.empty,
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        megaView.reload();
                    }
                });

        megaView.setBinder(new MegaView.ViewBinder<String, RowViewHolder>() {
            @Override
            public RowViewHolder onCreateViewHolder(ViewGroup parent) {
                return new RowViewHolder(new TextView(MyActivity.this));
            }

            @Override
            public void onBindViewHolder(RowViewHolder holder, final String item) {
                holder.itemView.setText(item);
                holder.itemView.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        TextView textView = (TextView) v;
                        Intent intent = new Intent(MyActivity.this, SecondActivity.class);
                        intent.putExtra(SecondActivity.LABEL_EXTRA, textView.getText());
                        // TODO: Play with activity transitions
                        startActivity(intent);
                    }
                });
            }
        });
        megaView.setConnected(mConnected.isChecked());
        megaView.setDataSource(new Func1<Integer, Observable<String>>() {
            @Override
            public Observable<String> call(Integer integer) {
                return getDelayedObservable(getContentObservable(integer));
            }
        });

        if (savedInstanceState != null && savedInstanceState.containsKey(STRINGS_STATE_KEY)) {
            strings = savedInstanceState.getStringArrayList(STRINGS_STATE_KEY);
        }

        megaView.setDataHolder(strings);

        if (savedInstanceState == null) {
            megaView.reload();
        }
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putStringArrayList(STRINGS_STATE_KEY, strings);
    }

    protected static class RowViewHolder extends MegaView.ViewHolder {
        private final TextView itemView;

        public RowViewHolder(TextView itemView) {
            super(itemView);
            this.itemView = itemView;
        }
    }

    @OnClick({R.id.immediate_response, R.id.short_delay_response, R.id.long_delay_response,
            R.id.success_response, R.id.error_response, R.id.empty_response,
            R.id.connected, R.id.disconnected})
    public void onToggle(View view) {
        ((RadioGroup)view.getParent()).check(0);
        ((RadioGroup) view.getParent()).check(view.getId());
    }

    @OnClick(R.id.connected)
    public void onConnected(ToggleButton v) {
        megaView.setConnected(v.isChecked());
    }

    @OnClick(R.id.disconnected)
    public void onDisconnected(ToggleButton v) {
        megaView.setConnected(!v.isChecked());
    }

    // http://stackoverflow.com/a/5837927/111777
    static final RadioGroup.OnCheckedChangeListener ToggleListener = new RadioGroup.OnCheckedChangeListener() {
        @Override
        public void onCheckedChanged(final RadioGroup radioGroup, final int i) {
            for (int j = 0; j < radioGroup.getChildCount(); j++) {
                final ToggleButton view = (ToggleButton) radioGroup.getChildAt(j);
                view.setChecked(view.getId() == i);
            }
        }
    };
}
