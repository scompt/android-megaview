package com.scompt.megaview.library;

import android.annotation.TargetApi;
import android.content.Context;
import android.os.Build;
import android.support.annotation.LayoutRes;
import android.support.annotation.Nullable;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ProgressBar;

import com.scompt.library.R;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import rx.Observable;
import rx.Subscriber;
import rx.android.schedulers.AndroidSchedulers;
import rx.functions.Func1;
import rx.schedulers.Schedulers;

public class MegaView<T, U extends MegaView.ViewHolder> extends FrameLayout {

    private static final int ITEM_TYPE_ROW = 0;
    private static final int ITEM_TYPE_PROGRESS = 1;
    private static final String LOG_TAG = MegaView.class.getSimpleName();
    private final Func1<Integer, Observable<List<T>>> EMPTY_FUNCTION = new Func1<Integer, Observable<List<T>>>() {
        @Override
        public Observable<List<T>> call(Integer integer) {
            return Observable.just(Collections.<T>emptyList());
        }
    };

    private Func1<Integer, Observable<List<T>>> pageFunction = EMPTY_FUNCTION;
    private ViewBinder<T, U> binder;

    private ArrayList<T> mItems = new ArrayList<>();

    private static final FrameLayout.LayoutParams LAYOUT_PARAMS = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT);

    private SwipeRefreshLayout mSwipeRefreshLayout;
    private Adapter adapter;
    private int mCurrentPage;
    private boolean mLoading;
    private boolean mReachedEnd;
    private MySubscriber subscriber1 = new MySubscriber();
    private View noConnectionView;
    private View errorView;
    private View emptyView;
    private boolean mConnected = true;
    private boolean mDebug;



    public MegaView(Context context) {
        super(context);
        initialize(context);
    }

    public MegaView(Context context, AttributeSet attrs) {
        super(context, attrs);
        initialize(context);
    }

    public MegaView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        initialize(context);
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public MegaView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        initialize(context);
    }

    @SuppressWarnings("UnusedDeclaration")
    public void setSupportsPullToRefresh(boolean supportsPullToRefresh) {
        mSwipeRefreshLayout.setEnabled(supportsPullToRefresh);
    }

    public void setConnected(boolean connected) {
        mConnected = connected;
    }

    public void setDebug(boolean debug) {
        this.mDebug = debug;
    }

    public void setData(Func1<Integer, Observable<List<T>>> pageFunction, ViewBinder<T, U> binder) {
        this.pageFunction = pageFunction;
        this.binder = binder;
    }

    public void reload() {
        if (mDebug) Log.d(LOG_TAG, "reload()");
        subscriber1.unsubscribe();
        mReachedEnd = false;
        onStopLoading();

        int itemCount = mItems.size();
        mItems.clear();
        adapter.notifyItemRangeRemoved(0, itemCount);
        mSwipeRefreshLayout.setRefreshing(false);
        mCurrentPage = 0;
        load();
    }

    private void load() {
        if (mDebug) Log.d(LOG_TAG, String.format("load(mLoading=%s, mReachedEnd=%s, mConnected=%s, mCurrentPage=%d)",
                mLoading, mReachedEnd, mConnected, mCurrentPage));

        if (mLoading || mReachedEnd) {
            return;
        }
        subscriber1.unsubscribe();

        if (!mConnected) {
            showNoConnection();
            return;
        }

        ensureRecyclerVisible();



        onStartLoading();

        subscriber1 = new MySubscriber();
        pageFunction.call(mCurrentPage).subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(subscriber1);
    }

    private void ensureRecyclerVisible() {
        if (mSwipeRefreshLayout.getVisibility() == GONE) {
            mSwipeRefreshLayout.setVisibility(VISIBLE);
            noConnectionView.setVisibility(GONE);
            emptyView.setVisibility(GONE);
            errorView.setVisibility(GONE);
        }
    }

    private void showNoConnection() {
        int itemCount = mItems.size();
        mItems.clear();
        adapter.notifyItemRangeRemoved(0, itemCount);

        Runnable runnable = new Runnable() {
            public void run() {
                mSwipeRefreshLayout.setVisibility(GONE);
                errorView.setVisibility(GONE);
                emptyView.setVisibility(GONE);
                noConnectionView.setVisibility(VISIBLE);
            }
        };
        post(runnable);
    }

    private class MySubscriber extends Subscriber<List<T>> {
        private List<T> mItemsToAdd = new ArrayList<>();

        @Override
        public void onCompleted() {
            mCurrentPage += 1;
            onStopLoading();

            if (mItemsToAdd.isEmpty()) {
                if (mItems.isEmpty()) {
                    showEmpty();
                } else {
                    mReachedEnd = true;
                }
            } else {

                onStopLoading();
                int position = mItems.size();
                int count = mItemsToAdd.size();
                mItems.addAll(mItemsToAdd);
                adapter.notifyItemRangeInserted(position, count);
            }
            Log.v("asdf", "completed");
        }

        @Override
        public void onError(Throwable e) {
            onStopLoading();

            if (mItems.isEmpty()) {
                showError();
            }

            Log.v("asdf", "error: " + e.getMessage(), e);
        }

        @Override
        public void onNext(List<T> t) {
            mItemsToAdd.addAll(t);
        }
    }

    private void showError() {
        mSwipeRefreshLayout.setVisibility(GONE);
        noConnectionView.setVisibility(GONE);
        emptyView.setVisibility(GONE);
        errorView.setVisibility(VISIBLE);
    }

    private void showEmpty() {
        mSwipeRefreshLayout.setVisibility(GONE);
        noConnectionView.setVisibility(GONE);
        errorView.setVisibility(GONE);
        emptyView.setVisibility(VISIBLE);
    }

    private void initialize(Context context) {
        if (isInEditMode()) {
            return;
        }

        mSwipeRefreshLayout = new SwipeRefreshLayout(context);
        mSwipeRefreshLayout.setOnRefreshListener(new RefreshListener());

        RecyclerView mRecyclerView = new RecyclerView(context);
        mRecyclerView.setId(R.id.recycler_view);
        mRecyclerView.setLayoutManager(new LinearLayoutManager(context));
        adapter = new Adapter();
        mRecyclerView.setAdapter(adapter);

        noConnectionView = new View(context);
        noConnectionView.setVisibility(GONE);
        addView(noConnectionView, LAYOUT_PARAMS);

        errorView = new View(context);
        errorView.setVisibility(GONE);
        addView(errorView, LAYOUT_PARAMS);

        emptyView = new View(context);
        emptyView.setVisibility(GONE);
        addView(emptyView, LAYOUT_PARAMS);

//        mRecyclerView.setOnScrollListener(new RecyclerView.OnScrollListener() {
//            @Override
//            public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
//                super.onScrollStateChanged(recyclerView, newState);
//            }
//
//            @Override
//            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
//                super.onScrolled(recyclerView, dx, dy);
//            }
//        });


        mSwipeRefreshLayout.addView(mRecyclerView, LAYOUT_PARAMS);
        addView(mSwipeRefreshLayout, LAYOUT_PARAMS);
    }

    public interface ViewBinder<U, V extends RecyclerView.ViewHolder> {
        public V onCreateViewHolder(ViewGroup parent);

        public void onBindViewHolder(V holder, U item);
    }

    private class RefreshListener implements SwipeRefreshLayout.OnRefreshListener {

        @Override
        public void onRefresh() {
            if (mDebug) Log.d(LOG_TAG, "onRefresh()");
            subscriber1.unsubscribe();
            mReachedEnd = false;
            onStopLoading();

            int itemCount = mItems.size();
            mItems.clear();
            adapter.notifyItemRangeRemoved(0, itemCount);
            mSwipeRefreshLayout.setRefreshing(false);
            mCurrentPage = 0;
            load();
        }
    }

    private void onStartLoading() {
        Log.v("asdf", "onStartLoading");
        Runnable runnable = new Runnable() {
            public void run() {
                mLoading = true;
                adapter.notifyItemInserted(mItems.size());
            }
        };
        post(runnable);
    }

    private void onStopLoading() {
        Log.v("asdf", "onStopLoading");
        Runnable runnable = new Runnable() {
            public void run() {
                mLoading = false;
                adapter.notifyItemRemoved(mItems.size());
            }
        };
        post(runnable);
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {

        public ViewHolder(View itemView) {
            super(itemView);
        }
    }

    private static class ProgressViewHolder extends ViewHolder {

        public ProgressViewHolder(Context context) {
            super(new ProgressBar(context));
        }
    }

    private class Adapter extends RecyclerView.Adapter<ViewHolder> {

        @Override
        public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            if (viewType == ITEM_TYPE_ROW) {
                return binder.onCreateViewHolder(parent);
            } else {
                return new ProgressViewHolder(getContext());
            }
        }

        @SuppressWarnings("unchecked")
        @Override
        public void onBindViewHolder(ViewHolder holder, int position) {
            if (position == mItems.size() - 1) {
                post(new Runnable() {
                    @Override
                    public void run() {
                        load();
                    }
                });
            }
            if (adapter.getItemViewType(position) == ITEM_TYPE_ROW) {
                binder.onBindViewHolder((U) holder, mItems.get(position));
            }
        }

        @Override
        public int getItemCount() {
            if (mLoading && !mReachedEnd) {
                return mItems.size() + 1;
            } else {
                return mItems.size();
            }
        }

        @Override
        public int getItemViewType(int position) {
            if (mLoading && !mReachedEnd && position == mItems.size()) {
                return ITEM_TYPE_PROGRESS;
            } else {
                return ITEM_TYPE_ROW;
            }
        }
    }

    public void setNoConnectionLayout(@LayoutRes int fullNoConnectionLayout,
                                      @LayoutRes int rowNoConnectionLayout,
                                      @Nullable OnClickListener onClickListener) {

        removeView(noConnectionView);
        noConnectionView = LayoutInflater.from(getContext()).inflate(fullNoConnectionLayout, this, false);

        if (onClickListener != null) {
            View viewById = noConnectionView.findViewById(android.R.id.button1);
            if (viewById != null) {
                viewById.setOnClickListener(onClickListener);
            }
        }

        noConnectionView.setVisibility(GONE);
        addView(noConnectionView, LAYOUT_PARAMS);
//        this.rowNoConnectionLayout = rowNoConnectionLayout;
    }

    public void setErrorLayout(@LayoutRes int fullErrorLayout,
                                      @LayoutRes int rowErrorLayout,
                                      @Nullable OnClickListener onClickListener) {

        removeView(errorView);
        errorView = LayoutInflater.from(getContext()).inflate(fullErrorLayout, this, false);

        if (onClickListener != null) {
            View viewById = errorView.findViewById(android.R.id.button1);
            if (viewById != null) {
                viewById.setOnClickListener(onClickListener);
            }
        }

        errorView.setVisibility(GONE);
        addView(errorView, LAYOUT_PARAMS);
//        this.rowNoConnectionLayout = rowNoConnectionLayout;
    }

    public void setEmptyLayout(@LayoutRes int fullEmptyLayout,
                                      @Nullable OnClickListener onClickListener) {

        removeView(emptyView);
        emptyView = LayoutInflater.from(getContext()).inflate(fullEmptyLayout, this, false);

        if (onClickListener != null) {
            View viewById = emptyView.findViewById(android.R.id.button1);
            if (viewById != null) {
                viewById.setOnClickListener(onClickListener);
            }
        }

        emptyView.setVisibility(GONE);
        addView(emptyView, LAYOUT_PARAMS);
//        this.rowNoConnectionLayout = rowNoConnectionLayout;
    }
}
