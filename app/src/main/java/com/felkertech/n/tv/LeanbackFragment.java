/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package com.felkertech.n.tv;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.IntentSender;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.media.tv.TvContract;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.support.v17.leanback.app.BackgroundManager;
import android.support.v17.leanback.app.BrowseFragment;
import android.support.v17.leanback.widget.ArrayObjectAdapter;
import android.support.v17.leanback.widget.HeaderItem;
import android.support.v17.leanback.widget.ImageCardView;
import android.support.v17.leanback.widget.ListRow;
import android.support.v17.leanback.widget.ListRowPresenter;
import android.support.v17.leanback.widget.OnItemViewClickedListener;
import android.support.v17.leanback.widget.OnItemViewSelectedListener;
import android.support.v17.leanback.widget.Presenter;
import android.support.v17.leanback.widget.Row;
import android.support.v17.leanback.widget.RowPresenter;
import android.support.v4.app.ActivityOptionsCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import com.afollestad.materialdialogs.MaterialDialog;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.drawable.GlideDrawable;
import com.bumptech.glide.request.animation.GlideAnimation;
import com.bumptech.glide.request.target.SimpleTarget;
import com.example.android.sampletvinput.syncadapter.SyncUtils;
import com.felkertech.n.ActivityUtils;
import com.felkertech.n.boilerplate.Utils.SettingsManager;
import com.felkertech.n.cumulustv.ChannelDatabase;
import com.felkertech.n.cumulustv.JSONChannel;
import com.felkertech.n.cumulustv.MainActivity;
import com.felkertech.n.cumulustv.R;
import com.felkertech.n.cumulustv.TvManager;
import com.felkertech.n.cumulustv.xmltv.Program;
import com.felkertech.n.cumulustv.xmltv.XMLTVParser;
import com.felkertech.n.plugins.CumulusTvPlugin;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.drive.Drive;
import com.google.android.gms.drive.DriveApi;
import com.google.android.gms.drive.DriveFile;
import com.google.android.gms.drive.DriveId;
import com.google.android.gms.drive.MetadataChangeSet;
import com.google.android.gms.drive.OpenFileActivityBuilder;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;

import org.json.JSONException;
import org.xmlpull.v1.XmlPullParserException;

public class LeanbackFragment extends BrowseFragment
        implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {
    private static final String TAG = "LeanbackFragment";

    private static final int BACKGROUND_UPDATE_DELAY = 300;
    private static final int GRID_ITEM_WIDTH = 200;
    private static final int GRID_ITEM_HEIGHT = 200;

    public static final int RESOLVE_CONNECTION_REQUEST_CODE = 100;
    public static final int REQUEST_CODE_CREATOR = 102;

    private final Handler mHandler = new Handler();
    private ArrayObjectAdapter mRowsAdapter;
    private Drawable mDefaultBackground;
    private DisplayMetrics mMetrics;
    private Timer mBackgroundTimer;
    private URI mBackgroundURI;
    private BackgroundManager mBackgroundManager;

    private SettingsManager sm;
    public GoogleApiClient gapi;

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        Log.i(TAG, "onCreate");
        super.onActivityCreated(savedInstanceState);
        //TODO Get the slides set up
        sm = new SettingsManager(getActivity());
        gapi = new GoogleApiClient.Builder(getActivity())
                .addApi(Drive.API)
                .addScope(Drive.SCOPE_FILE)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .build();
        gapi.connect();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (null != mBackgroundTimer) {
            Log.d(TAG, "onDestroy: " + mBackgroundTimer.toString());
            mBackgroundTimer.cancel();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshUI();
    }
    public void refreshUI() {
        prepareBackgroundManager();
        setupUIElements();
        loadRows();
        setupEventListeners();
    }

    private void loadRows() {
        //HERE ARE MY ROWS
        mRowsAdapter = new ArrayObjectAdapter(new ListRowPresenter());

        //ROW 1: MY CHANNELS
        ChannelDatabase cd = new ChannelDatabase(getActivity());
        try {
            CardPresenter channelCardPresenter = new CardPresenter();
//            GridItemPresenter channelCardPresenter = new GridItemPresenter();
            ArrayObjectAdapter channelRowAdapter = new ArrayObjectAdapter(channelCardPresenter);
            int index = 0;
            for(TvManager.ChannelInfo channelInfo: cd.getChannels()) {
                Log.d(TAG, "Got channels " + channelInfo.name);
                Log.d(TAG, channelInfo.logoUrl);
                Log.d(TAG, new JSONChannel(cd.getJSONChannels().getJSONObject(index)).toString()+"");
                channelRowAdapter.add(MovieList.buildMovieInfo(
                        "channel",
                        channelInfo.name,
                        "",
                        channelInfo.number,
                        new JSONChannel(cd.getJSONChannels().getJSONObject(index)).getUrl(),
                        channelInfo.logoUrl,
                        "android.resource://com.felkertech.n.tv/drawable/c_background5"
                ));
                Log.d(TAG, MovieList.buildMovieInfo(
                        "channel",
                        channelInfo.name,
                        "",
                        channelInfo.number,
                        new JSONChannel(cd.getJSONChannels().getJSONObject(index)).getUrl(),
                        channelInfo.logoUrl,
                        "android.resource://com.felkertech.n.tv/drawable/c_background5"
                ).toString());
//                channelRowAdapter.add(channelInfo.name);
                index++;
            }
            HeaderItem header = new HeaderItem(0, "My Channels");
            mRowsAdapter.add(new ListRow(header, channelRowAdapter));
        } catch (JSONException e) {
            e.printStackTrace();
        }

        //Second row is suggested channels (not really yet)
        CardPresenter suggestedChannelPresenter = new CardPresenter();
        ArrayObjectAdapter suggestedChannelAdapter = new ArrayObjectAdapter(suggestedChannelPresenter);
        HeaderItem suggestedChannelsHeader = new HeaderItem(1, "Suggested Channels");
        JSONChannel[] suggestedChannels = ActivityUtils.getSuggestedChannels();
        for(JSONChannel jsonChannel: suggestedChannels) {
            suggestedChannelAdapter.add(MovieList.buildMovieInfo(
                    "channel",
                    jsonChannel.getName(),
                    "",
                    jsonChannel.getNumber(),
                    jsonChannel.getUrl(),
                    jsonChannel.getLogo(),
                    "android.resource://com.felkertech.n.tv/drawable/c_background5"
            ));
        }
        mRowsAdapter.add(new ListRow(suggestedChannelsHeader, suggestedChannelAdapter));

        //Third row is Drive
        HeaderItem driveHeader = new HeaderItem(1, "Google Drive Sync");
        GridItemPresenter drivePresenter = new GridItemPresenter();
        ArrayObjectAdapter driveAdapter = new ArrayObjectAdapter(drivePresenter);
//        driveAdapter.add("Connect to Google Drive");
        driveAdapter.add(getString(R.string.settings_refresh_cloud_local));
//        driveAdapter.add("Upload to cloud");
        driveAdapter.add(getString(R.string.settings_switch_google_drive));
        driveAdapter.add(getString(R.string.settings_sync_file));
        mRowsAdapter.add(new ListRow(driveHeader, driveAdapter));

        //Fourth row are actions
        HeaderItem gridHeader = new HeaderItem(1, "Manage");
        GridItemPresenter mGridPresenter = new GridItemPresenter();
        ArrayObjectAdapter gridRowAdapter = new ArrayObjectAdapter(mGridPresenter);
        gridRowAdapter.add(getString(R.string.manage_livechannels));
//        gridRowAdapter.add(getString(R.string.manage_add_suggested));
        gridRowAdapter.add(getString(R.string.manage_add_new));
        gridRowAdapter.add("Empty Plugin");
        gridRowAdapter.add("Settings");
        mRowsAdapter.add(new ListRow(gridHeader, gridRowAdapter));

        //Settings will become its own activity
        HeaderItem gridHeader2 = new HeaderItem(1, "Settings");
        GridItemPresenter mGridPresenter2 = new GridItemPresenter();
        ArrayObjectAdapter gridRowAdapter2 = new ArrayObjectAdapter(mGridPresenter2);
        gridRowAdapter2.add(getString(R.string.settings_browse_plugins));
        gridRowAdapter2.add(getString(R.string.settings_view_licenses));
        gridRowAdapter2.add(getString(R.string.settings_reset_channel_data));
        gridRowAdapter2.add(getString(R.string.about_app));
//        gridRowAdapter2.add(getString(R.string.settings_read_xmltv));
        mRowsAdapter.add(new ListRow(gridHeader2, gridRowAdapter2));

        setAdapter(mRowsAdapter);
    }

    private void prepareBackgroundManager() {
        try {
            mBackgroundManager = BackgroundManager.getInstance(getActivity());
            mBackgroundManager.attach(getActivity().getWindow());
            mDefaultBackground = getResources().getDrawable(R.drawable.c_background5);
            mBackgroundManager.setDrawable(getResources().getDrawable(R.drawable.c_background5));
            mMetrics = new DisplayMetrics();
            getActivity().getWindowManager().getDefaultDisplay().getMetrics(mMetrics);
        } catch(Exception e) {
            e.printStackTrace();
            //Do nothing
        }
    }

    private void setupUIElements() {
        try {
            // setBadgeDrawable(getActivity().getResources().getDrawable(
            // R.drawable.videos_by_google_banner));
            setTitle(getString(R.string.app_name)); // Badge, when set, takes precedent
            // over title
            setHeadersState(HEADERS_ENABLED);
            setHeadersTransitionOnBackEnabled(true);

            // set fastLane (or headers) background color
            setBrandColor(getResources().getColor(R.color.colorPrimary));
            // set search icon color
//        setSearchAffordanceColor(getResources().getColor(R.color.search_opaque));
        } catch(Exception e) {
            //Shrug.gif
        }
    }

    private void setupEventListeners() {
        /*setOnSearchClickedListener(new View.OnClickListener() {

            @Override
            public void onClick(View view) {
                Toast.makeText(getActivity(), "Implement your own in-app search", Toast.LENGTH_LONG)
                        .show();
            }
        });*/

        setOnItemViewClickedListener(new ItemViewClickedListener());
        setOnItemViewSelectedListener(new ItemViewSelectedListener());
    }

    @Override
    public void onConnected(Bundle bundle) {
        Log.d(TAG, "onConnected");

        sm.setGoogleDriveSyncable(gapi, new SettingsManager.GoogleDriveListener() {
            @Override
            public void onActionFinished(boolean cloudToLocal) {
                Log.d(TAG, "Sync req after drive action");
                final String info = TvContract.buildInputId(new ComponentName("com.felkertech.n.cumulustv", ".SampleTvInput"));
                SyncUtils.requestSync(info);
                if (cloudToLocal) {
                    Toast.makeText(getActivity(), "Download complete", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(getActivity(), "Upload complete", Toast.LENGTH_SHORT).show();
                }
            }
        }); //Enable GDrive
        Log.d(TAG, sm.getString(R.string.sm_google_drive_id) + "<< for onConnected");
        if(sm.getString(R.string.sm_google_drive_id).isEmpty()) {
            //We need a new file
            ActivityUtils.createDriveData((AppCompatActivity) getActivity(), gapi, driveContentsCallback);
        } else {
            //Great, user already has sync enabled, let's resync
            ActivityUtils.readDriveData(getActivity(), gapi);
            Handler h = new Handler(Looper.myLooper()){
                @Override
                public void handleMessage(Message msg) {
                    super.handleMessage(msg);
                    refreshUI();
                }
            };
            h.sendEmptyMessageDelayed(0, 4000);
        }
    }
    ResultCallback<DriveApi.DriveContentsResult> driveContentsCallback =
            new ResultCallback<DriveApi.DriveContentsResult>() {
                @Override
                public void onResult(DriveApi.DriveContentsResult result) {
                    MetadataChangeSet metadataChangeSet = new MetadataChangeSet.Builder()
                            .setTitle("cumulustv_channels.json")
                            .setDescription("JSON list of channels that can be imported using CumulusTV to view live streams")
                            .setMimeType("application/json").build();
                    IntentSender intentSender = Drive.DriveApi
                            .newCreateFileActivityBuilder()
                            .setActivityTitle("cumulustv_channels.json")
                            .setInitialMetadata(metadataChangeSet)
                            .setInitialDriveContents(result.getDriveContents())
                            .build(gapi);
                    try {
                        getActivity().startIntentSenderForResult(
                                intentSender, REQUEST_CODE_CREATOR, null, 0, 0, 0);
                    } catch (IntentSender.SendIntentException e) {
                        Log.w(TAG, "Unable to send intent", e);
                    }
                }
            };


    @Override
    public void onConnectionSuspended(int i) {

    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {
        Log.d(TAG, "Error connecting " + connectionResult.getErrorCode());

        Log.d(TAG, "oCF " + connectionResult.toString());
        if (connectionResult.hasResolution()) {
            try {
                connectionResult.startResolutionForResult(getActivity(), 900);
            } catch (IntentSender.SendIntentException e) {
                // Unable to resolve, message user appropriately
            }
        } else {
            GooglePlayServicesUtil.getErrorDialog(connectionResult.getErrorCode(), getActivity(), 0).show();
        }
    }

    private final class ItemViewClickedListener implements OnItemViewClickedListener {
        @Override
        public void onItemClicked(Presenter.ViewHolder itemViewHolder, Object item,
                                  RowPresenter.ViewHolder rowViewHolder, Row row) {

            if (item instanceof Movie) {
                Movie movie = (Movie) item;
                Log.d(TAG, "Item: " + item.toString());
                Intent intent = new Intent(getActivity(), DetailsActivity.class);
                intent.putExtra(DetailsActivity.MOVIE, movie);

                Bundle bundle = ActivityOptionsCompat.makeSceneTransitionAnimation(
                        getActivity(),
                        ((ImageCardView) itemViewHolder.view).getMainImageView(),
                        DetailsActivity.SHARED_ELEMENT_NAME).toBundle();
                getActivity().startActivity(intent, bundle);
            } else if (item instanceof String) {
                String title = (String) item;
                if (((String) item).indexOf(getString(R.string.error_fragment)) >= 0) {
                    Intent intent = new Intent(getActivity(), BrowseErrorActivity.class);
                    startActivity(intent);
                } else if(title.equals(getString(R.string.manage_livechannels))) {
                    ActivityUtils.launchLiveChannels(getActivity());
                } else if(title.equals(getString(R.string.manage_add_suggested))) {
                   ActivityUtils.openSuggestedChannels((AppCompatActivity) getActivity(), gapi);
                } else if(title.equals(getString(R.string.manage_add_new))) {
                    ActivityUtils.openPluginPicker(true, getActivity());
                } else if(title.equals(getString(R.string.settings_sync_file))) {
                    ActivityUtils.syncFile(getActivity(), gapi);
                } else if(title.equals(getString(R.string.settings_browse_plugins))) {
                    ActivityUtils.browsePlugins(getActivity());
                } else if(title.equals(getString(R.string.settings_switch_google_drive))) {
                    ActivityUtils.switchGoogleDrive(getActivity(), gapi);
                } else if(title.equals(getString(R.string.settings_refresh_cloud_local))) {
                    ActivityUtils.readDriveData(getActivity(), gapi);
                    Handler h = new Handler(Looper.myLooper()) {
                        @Override
                        public void handleMessage(Message msg) {
                            super.handleMessage(msg);
                            refreshUI();
                        }
                    };
                    h.sendEmptyMessageDelayed(0, 4000);
                } else if(title.equals(getString(R.string.settings_view_licenses))) {
                    ActivityUtils.oslClick(getActivity());
                } else if(title.equals(getString(R.string.settings_reset_channel_data))) {
                    ActivityUtils.deleteChannelData(getActivity(), gapi);
                } else if(title.equals(getString(R.string.about_app))) {
                    ActivityUtils.openAbout(getActivity());
                } else if(title.equals(getString(R.string.settings_read_xmltv))) {
                    final OkHttpClient client = new OkHttpClient();
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            Request request = new Request.Builder()
                                    .url("http://felkerdigitalmedia.com/sampletv.xml")
                                    .build();

                            Response response = null;
                            try {
                                response = client.newCall(request).execute();
//                                            Log.d(TAG, response.body().string().substring(0,36));
                                String s = response.body().string();
                                List<Program> programs = XMLTVParser.parse(s);
                                            /*Log.d(TAG, programs.toString());
                                            Log.d(TAG, "Parsed "+programs.size());
                                            Log.d(TAG, "Program 1: "+ programs.get(0).getTitle());*/
                            } catch (IOException e) {
                                e.printStackTrace();
                            } catch (XmlPullParserException e) {
                                e.printStackTrace();
                            }
                        }
                    }).start();
                } else {
                    Toast.makeText(getActivity(), ((String) item), Toast.LENGTH_SHORT)
                            .show();
                }
            }
        }
    }

    private final class ItemViewSelectedListener implements OnItemViewSelectedListener {
        @Override
        public void onItemSelected(Presenter.ViewHolder itemViewHolder, Object item,
                                   RowPresenter.ViewHolder rowViewHolder, Row row) {
            if (item instanceof Movie) {
//                mBackgroundURI = ((Movie) item).getBackgroundImageURI();
                startBackgroundTimer();
            }

        }
    }

    protected void updateBackground(String uri) {
        int width = mMetrics.widthPixels;
        int height = mMetrics.heightPixels;
        /*Glide.with(getActivity())
                .load(uri)
                .centerCrop()
                .error(mDefaultBackground)
                .into(new SimpleTarget<GlideDrawable>(width, height) {
                    @Override
                    public void onResourceReady(GlideDrawable resource,
                                                GlideAnimation<? super GlideDrawable>
                                                        glideAnimation) {
                        mBackgroundManager.setDrawable(resource);
                    }
                });*/
        mBackgroundTimer.cancel();
    }

    private void startBackgroundTimer() {
        if (null != mBackgroundTimer) {
            mBackgroundTimer.cancel();
        }
        mBackgroundTimer = new Timer();
        mBackgroundTimer.schedule(new UpdateBackgroundTask(), BACKGROUND_UPDATE_DELAY);
    }

    private class UpdateBackgroundTask extends TimerTask {

        @Override
        public void run() {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    if (mBackgroundURI != null) {
                        updateBackground(mBackgroundURI.toString());
                    }
                }
            });

        }
    }

    private class GridItemPresenter extends Presenter {
        @Override
        public ViewHolder onCreateViewHolder(ViewGroup parent) {
            TextView view = new TextView(parent.getContext());
            view.setLayoutParams(new ViewGroup.LayoutParams(GRID_ITEM_WIDTH, GRID_ITEM_HEIGHT));
            view.setFocusable(true);
            view.setFocusableInTouchMode(true);
            view.setBackgroundColor(getResources().getColor(R.color.default_background));
            view.setTextColor(Color.WHITE);
            view.setGravity(Gravity.CENTER);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(ViewHolder viewHolder, Object item) {
            ((TextView) viewHolder.view).setText((String) item);
        }

        @Override
        public void onUnbindViewHolder(ViewHolder viewHolder) {
        }
    }
}
