package com.blah0x1e6f.androidsample;

import android.annotation.TargetApi;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Point;
import android.os.IBinder;
import android.support.v4.app.FragmentActivity;
import android.graphics.Color;
import android.location.Location;
import android.os.Build;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;

import android.view.View;
import android.util.Log;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapFragment;
import com.google.android.gms.maps.Projection;
import com.google.android.gms.maps.model.*;

import junit.framework.Assert;

public class NewtonCreekActivity extends FragmentActivity {
    /*
     * CONSTANTS
     * todo: maybe move out into resources
     */
    private static final String TAG = "NewtonCreek_ACTIVITY";
    private static final int MAP_PADDING_PX = 40;

    private GoogleMap mMap;

    private Path mRedPath, mBluePath;
    private SlidingFilterPath mGreenPath, mOrangePath, mPurplePath;

    private float mLastZoom = -1;
    private Projection mCurProjection;

    private TextView mStatusBox;

    private LatLngBounds.Builder mBoundsBuilder;

    // The logic for pausing autopanning of map while user is interacting with it, then resuming on Resume btn click
    private boolean mOkToMoveMap = true;
    private boolean mLastCamChangeByCode = true;

    private NewtonCreekService mService;
    private int mLastKnownLocCount = 0;
    private boolean mIsSvceBound;
    private ServiceConnection mSvceConnection = new ServiceConnection() {

        public void onServiceConnected(ComponentName name, IBinder binder) {
            Log.d(TAG, "onServiceConnected");
            mService = ((NewtonCreekService.MyBinder)binder).getService();
            mService.setActivity(NewtonCreekActivity.this);
            mIsSvceBound = true;

            // Service has new locations since activity was destroyed or stopped; update activity's state w/ new data
            if (mService.getLocations().size() > mLastKnownLocCount)
                newLocationsAvail();
        }

        // note: this doesn't get called after unbindService.  Instead, this is called on service crash.
        public void onServiceDisconnected(ComponentName name) {
            Log.d(TAG, "onServiceDisconnected");
            mIsSvceBound = false;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate");

        setContentView(R.layout.activity_android_sample);

        mBoundsBuilder = LatLngBounds.builder();

        setUpMapIfNeeded();

        mRedPath = new Path("Red path",
                MyColors.DEEP_RED_ST,
                R.drawable.dot3_444444,
                getResources().getDisplayMetrics().density
            );
        mGreenPath = new SlidingFilterPath("Green path",
                MyColors.DEEP_GREEN_ST,
                R.drawable.dot3_444444,
                getResources().getDisplayMetrics().density,
                new MedianFilter(9)
        );
        mOrangePath = new SlidingFilterPath("Orange path",
                MyColors.DEEP_ORANGE_ST,
                R.drawable.dot3_444444,
                getResources().getDisplayMetrics().density,
                new SharkToothFilter(9)
        );
        mPurplePath = new SlidingFilterPath("Purple path",
                MyColors.DEEP_PURPLE_ST,
                R.drawable.dot3_444444,
                getResources().getDisplayMetrics().density,
                new MeanFilter(9)
        );
        mBluePath = new Path("Blue path",
                MyColors.DEEP_BLUE_ST,
                R.drawable.dot3_444444,
                getResources().getDisplayMetrics().density
            );

        // Start my background service...
        Intent intent = new Intent(this, NewtonCreekService.class);
        startService(intent);

        mStatusBox = (TextView)findViewById(R.id.statusBox);
        updateStatusBox();
    }

    @Override
    protected void onRestart() { // Called after user navigates back to activity after onStop
        super.onRestart();
        Log.d(TAG, "onRestart");
    }

    @Override
    protected void onStart() { // Called after onCreate or onRestart
        super.onStart();
        Log.d(TAG, "onStart");

        Intent intent = new Intent(this, NewtonCreekService.class);
        bindService(intent, mSvceConnection, Context.BIND_AUTO_CREATE);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        Log.d(TAG, "onSaveInstanceState");
        super.onSaveInstanceState(outState);
        outState.putBoolean("red is on", mRedPath.isPathOn());
        outState.putBoolean("green is on", mGreenPath.isPathOn());
        outState.putBoolean("orange is on", mOrangePath.isPathOn());
        outState.putBoolean("purple is on", mPurplePath.isPathOn());
        outState.putBoolean("blue is on", mBluePath.isPathOn());
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        Log.d(TAG, "onRestoreInstanceState");
        super.onRestoreInstanceState(savedInstanceState);
        mRedPath.setOn(savedInstanceState.getBoolean("red is on"));
        mGreenPath.setOn(savedInstanceState.getBoolean("green is on"));
        mOrangePath.setOn(savedInstanceState.getBoolean("orange is on"));
        mPurplePath.setOn(savedInstanceState.getBoolean("purple is on"));
        mBluePath.setOn(savedInstanceState.getBoolean("blue is on"));
    }

    @Override
    protected void onResume() { // Called after onRestoreInstanceState or after activity stops being obscured after onPause
        super.onResume();
        Log.d(TAG, "onResume");

        setUpMapIfNeeded();
        updateStatusBox();
    }

    @Override
    protected void onPause() { // Called after another activity obscures this one, after onResume
        super.onPause();
        Log.d(TAG, "onPause");
    }

    @Override
    protected void onStop() { // Called after this activity is no longer visible, after onPause
        if (mIsSvceBound) {
            Log.d(TAG, "onStop: unbinding service & removing activity reference");

            mService.setActivity(null);
            unbindService(mSvceConnection);
            mIsSvceBound = false;
        } else {
            Log.d(TAG, "onStop: service already unbound somehow!");
        }

        super.onStop();
    }

    @Override
    protected void onDestroy() { // Called after onStop
        Log.d(TAG, "onDestroy");
        super.onDestroy();
    }

    public void newLocationsAvail() {
        ArrayList<Location> locations = mService.getLocations();
        int newLocCount = locations.size() - mLastKnownLocCount;
        mLastKnownLocCount += newLocCount;
        Log.d(TAG, "newLocationsAvail: detected " + newLocCount + " new locations");

        mRedPath.addPoints(locations, newLocCount);
        int greenCtAdded = mGreenPath.addPoints(locations, newLocCount);
        int orangeCtAdded = mOrangePath.addPoints(locations, newLocCount);
        int purpleCtAdded = mPurplePath.addPoints(locations, newLocCount);
        mBluePath.addPoints(locations, newLocCount);

        // Reposition map to fit all points, but only if we're not in the mode where the user is interacting with the map
        if (mOkToMoveMap) {
            Log.d(TAG, "newLocationsAvail: will pan map to all points");
            panMapToAllPoints();

            // Detect if this pan caused a zoom and grab the current projection
            final float curZoom = mMap.getCameraPosition().zoom;
            if (curZoom != mLastZoom) {
                Log.d(TAG, "newLocationsAvail: zoom level changed: " + MyUtil.formatFloat2(mLastZoom) + " -> " + MyUtil.formatFloat2(curZoom));
                // Getting Projection is an expensive op, so let's do it only once per zoom level
                mCurProjection = mMap.getProjection();
                mLastZoom = curZoom; // We already handle zooms caused by pan here, so no need to handle in onCameraChange later...
            }
        }

        //todo!!! xx uncomment out later
        //mBluePath.adjustResolutionIfNeeded();

        if (mRedPath.isPathOn())
            mRedPath.draw();

        if (greenCtAdded > 0 && mGreenPath.isPathOn())
            mGreenPath.draw();

        if (orangeCtAdded > 0 && mOrangePath.isPathOn())
            mOrangePath.draw();

        if (purpleCtAdded > 0 && mPurplePath.isPathOn())
            mPurplePath.draw();

        if (mBluePath.isPathOn())
            mBluePath.draw();

        updateStatusBox();
    }

    //todo: mk added this, so that despite my build.gradle specifying a min supported API version of 15, I can use Location.getElapsedRealtimeNanos, which is in API v 17
    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
    private void updateStatusBox() {
        if (!mIsSvceBound || mService.getLocations().isEmpty()) {
            mStatusBox.setText("Awaiting location...");
            mStatusBox.setTextColor(Color.BLACK);
            return;
        }

        //todo: Below optimize the string craziness and minimize proliferation of stringbuffer objects

        // Svce is bound and has at least one location...
        ArrayList<Location> locations = mService.getLocations();
        Location location = locations.get(locations.size() - 1);

        // Display accuracy in meters
        StringBuffer msg = new StringBuffer(Math.round(location.getAccuracy()) + "m ");

        if (locations.size() > 1) {
            Location prevLocation = locations.get(locations.size()-2);

            // Distance & time deltas
            float distDelta = prevLocation.distanceTo(location);
            float timeDelta = (float) ((location.getElapsedRealtimeNanos() - prevLocation.getElapsedRealtimeNanos()) / 1000000000.0);
            msg.append(MyUtil.formatFloat1(distDelta) + "m " + MyUtil.formatFloat1(timeDelta) + "s ");
        }

        // # of points, locations, and fixes
        msg.append(mRedPath.numVertices() + "/" + locations.size() + "pts/" + mService.getNumFixes() + "fx");

        // Line 2 of the msg
        StringBuffer msg2 = new StringBuffer();
        msg2.append(mBluePath.oorCount() + "oor > " + mBluePath.resAdjCount() + "rev " + mBluePath.numVertices() + "/" + locations.size() + "pts " + MyUtil.formatFloat1(mBluePath.avgSegLenPx()) + "px/seg");

        mStatusBox.setText(msg + "\n" + msg2);
        mStatusBox.setTextColor(MyUtil.getAccuracyColor(Math.round(location.getAccuracy())));
    }

    // Starts a fresh path
    public void onResetBtnClick(View v) {
        Log.d(TAG, "onResetBtnClick");
        if (mIsSvceBound) {
            mService.reset();
            mLastKnownLocCount = 0;

            //todo: Note: the below doesn't really have to reside in the if block, but I want to keep it so that if the user clicked the reset btn and we haven't yet conneted to the service, nothing changes.  User will intuitively have to click again later.
            mRedPath.reset();
            mGreenPath.reset();
            mOrangePath.reset();
            mPurplePath.reset();
            mBluePath.reset();

            mBoundsBuilder = LatLngBounds.builder(); // Replace w/ a new, empty builder

            //no point in calling this, since we don't have any points yet to pan to
            //panMapToAllPoints();
            mOkToMoveMap = true;
            mLastCamChangeByCode = true;

            updateStatusBox();
        }
    }

    // Returns to the mode where the map is auto-panned to include the entire path
    public void onResumeBtnClick(View v) {
        Log.d(TAG, "onResumeBtnClick");
        panMapToAllPoints();
        mOkToMoveMap = true;
    }

    public void onToggleRedPathBtnClick(View v) {
        Log.d(TAG, "onToggleRedPathBtnClick");
        mRedPath.toggle();
    }

    public void onToggleGreenPathBtnClick(View v) {
        Log.d(TAG, "onToggleGreenPathBtnClick");
        mGreenPath.toggle();
    }

    public void onToggleOrangePathBtnClick(View v) {
        Log.d(TAG, "onToggleOrangePathBtnClick");
        mOrangePath.toggle();
    }

    public void onTogglePurplePathBtnClick(View v) {
        Log.d(TAG, "onTogglePurplePathBtnClick");
        mPurplePath.toggle();
    }

    public void onToggleBluePathBtnClick(View v) {
        Log.d(TAG, "onToggleBluePathBtnClick");
        mBluePath.toggle();
    }

    @Override
    public void onBackPressed() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage("Exit app?")
                .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        Log.d(TAG, "onBackPressed: will unbind and kill service, then exit app");

                        // 1. unbind from svce
                        if (mIsSvceBound) {
                            mService.setActivity(null);
                            unbindService(mSvceConnection);
                            mIsSvceBound = false;
                        }

                        // 2. kill svce
                        Intent intent = new Intent(NewtonCreekActivity.this, NewtonCreekService.class);
                        stopService(intent);

                        NewtonCreekActivity.this.finish();
                    }
                })
                .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        dialog.cancel();
                    }
                });
        AlertDialog alert = builder.create();
        alert.show();
    }

    private void panMapToAllPoints() {
        try {
            mMap.moveCamera(CameraUpdateFactory.newLatLngBounds(mBoundsBuilder.build(), MAP_PADDING_PX));
        }
        catch (IllegalStateException e) {
            // There's no way to test builder() for presence of points, other than catching the exception
            Log.i(TAG, "panMapToAllPoints: can't pan map; bounds still empty");
        }
        mLastCamChangeByCode = true;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_android_sample, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /**
     * Sets up the map if it is possible to do so (i.e., the Google Play services APK is correctly
     * installed) and the map has not already been instantiated.. This will ensure that we only ever
     * call {@link #setUpMap()} once when {@link #mMap} is not null.
     * <p/>
     * If it isn't installed {@link com.google.android.gms.maps.SupportMapFragment} (and
     * {@link com.google.android.gms.maps.MapView MapView}) will show a prompt for the user to
     * install/update the Google Play services APK on their device.
     * <p/>
     * A user can return to this FragmentActivity after following the prompt and correctly
     * installing/updating/enabling the Google Play services. Since the FragmentActivity may not
     * have been completely destroyed during this process (it is likely that it would only be
     * stopped or paused), {@link #onCreate(Bundle)} may not be called again so we should call this
     * method in {@link #onResume()} to guarantee that it will be called.
     */
    private void setUpMapIfNeeded() {
        // Do a null check to confirm that we have not already instantiated the map.
        if (mMap == null) {
            // Try to obtain the map from the MapFragment.
            mMap = ((MapFragment) getFragmentManager().findFragmentById(R.id.map)).getMap();
            // Check if we were successful in obtaining the map.
            if (mMap != null) {
                setUpMap();
            }
        }
    }

    /**
     * This is where we can add markers or lines, add listeners or move the camera. In this case, we
     * just add a marker near Africa.
     * <p/>
     * This should only be called once and when we are sure that {@link #mMap} is not null.
     */
    private void setUpMap() {
        mMap.setMapType(GoogleMap.MAP_TYPE_NORMAL);//..._TERRAIN
        mMap.setMyLocationEnabled(true); // Shows current location, and the button to pan to current location
        mMap.getUiSettings().setRotateGesturesEnabled(false);
        mMap.getUiSettings().setCompassEnabled(false);
        mMap.getUiSettings().setTiltGesturesEnabled(false);

        mMap.setOnCameraChangeListener(new GoogleMap.OnCameraChangeListener() {
            @Override
            public void onCameraChange(CameraPosition camPos) {
                Log.d(TAG, "onCameraChange: " + (mLastCamChangeByCode ? "by code" : "by user"));
                if (mLastCamChangeByCode) {
                    mLastCamChangeByCode = false;
                }
                else { // We're here due to user interaction with map, so [continue to] disallow autopanning
                    mOkToMoveMap = false;
                }

                // Detect if this camera change is due to a zoom (vs. drag etc.)
                // Note: user-caused zoom, not zoom after we added point(s) -- since we handle those zooms there
                if (camPos.zoom != mLastZoom) {
                    mLastZoom = camPos.zoom;
                    // Getting Projection is an expensive op, so let's do it only once per zoom level
                    mCurProjection = mMap.getProjection();

                    //todo!!! xx uncomment out later
//                    final boolean resolutionAdjusted = mBluePath.adjustResolutionIfNeeded();
//                    if (resolutionAdjusted && mBluePath.isPathOn())
//                        mBluePath.draw();

                    updateStatusBox();
                }
            }
        });
    }

    /**
     * Class representing a path on a map, with all its markers, line, etc.
     */
    public class Path {
        /*
         * Constants
         */
        // Zoom density stuff: don't make points too crowded on screen
        private static final int IDEAL_DP_PER_SEGMENT = 30; // How many device-independent pixels we'd love per segment
        private static final float IDEAL_DP_RANGE_MULTIPLIER = 1.5f; // from 1/2 of ideal to 2x ideal
        private static final int POLYLINE_WIDTH_DP = 3;
        private int mIdealSegLenPx; // In px/segment
        private int mIdealMinSegLenPx;
        private int mIdealMaxSegLenPx;

        /*
         * Member variables
         */
        protected String mTitle;
        protected ArrayList<LatLng> mPoints;
        private Polyline mPath;
        private LinkedList<Marker> mMarkers;
        private PolylineOptions mPolyOpts;
        private MarkerOptions mMarkerOpts;
        private boolean mPathIsOn = true;
        private int mCountOutsideIdealRange = 0;
        private int mCountResolutionAdj = 0;
        private float mAvgSegLenPx = -1;

        /*
         * Constructors
         */
        private Path() {} // Hide default constructor

        public Path(String title, String color, int iconResourceId, float screenDensity) {
            // Convert the dps to pixels, based on density scale
            mIdealSegLenPx = (int) (IDEAL_DP_PER_SEGMENT * screenDensity + 0.5f);
            mIdealMaxSegLenPx = (int)(IDEAL_DP_PER_SEGMENT * IDEAL_DP_RANGE_MULTIPLIER * screenDensity + 0.5f);
            mIdealMinSegLenPx = (int)(IDEAL_DP_PER_SEGMENT / IDEAL_DP_RANGE_MULTIPLIER * screenDensity + 0.5f);

            mTitle = title;

            mPoints = new ArrayList<LatLng>();
            mMarkerOpts = new MarkerOptions()
                    .icon(BitmapDescriptorFactory.fromResource(iconResourceId))
                    .flat(true)
                    .anchor(0.5f, 0.5f);
            mMarkers = new LinkedList<Marker>();

            mPolyOpts = new PolylineOptions()
                    .geodesic(true)
                    .width(POLYLINE_WIDTH_DP * screenDensity)
                    .color(Color.parseColor(color));
            mPath = mMap.addPolyline(mPolyOpts);
        }

        public int addPoints(final ArrayList<Location> locations, final int numToAdd) {
            // Update mPoints and bounds builder
            int count = 0;
            for (int i = locations.size() - numToAdd; i < locations.size(); i++, count++) {
                LatLng point = locToLatLng(locations.get(i));
                mPoints.add(point);
                mBoundsBuilder.include(point);
            }
            Log.d(mTitle, "addPoints: added " + count + " pts");
            return count;
        }

        // The reason we need to first check how many markers to add is that this function is called from
        // different contexts, e.g.:
        //  - in addPoints, where we're adding just a few markers
        //  - in adjustResolutionIfNeeded, where we're replacing all markers wholesale
        protected void draw() {
            int numNewMarkers = mPoints.size() - mMarkers.size();
            Assert.assertTrue(numNewMarkers >= 0); // Draw must not be called without first updating mPoints

            for (int i = mPoints.size() - numNewMarkers; i < mPoints.size(); i++) {
                // Add marker to map
                mMarkerOpts.position(mPoints.get(i));
                mMarkers.add(mMap.addMarker(mMarkerOpts));
            }
            // todo: redrawing entire path each time may be inefficient. May be more efficient to add separate line segments..?
            mPath.setPoints(mPoints);
        }

        public void setOn(final boolean isOn) { mPathIsOn = isOn; }

        public boolean isPathOn() { return mPathIsOn; }

        public int numVertices() { return mPoints.size(); }

        public int oorCount() { return mCountOutsideIdealRange; }

        public int resAdjCount() { return mCountResolutionAdj; }

        public float avgSegLenPx() { return mAvgSegLenPx; }

        public void reset() {
            clear();
            mCountOutsideIdealRange = 0;
            mCountResolutionAdj = 0;
        }

        private void clear() {
            removeVisuals();

            // Then clear out the points that represent the polyline
            mPoints.clear();
        }

        public void toggle() {
            mPathIsOn = !mPathIsOn;
            if (mPathIsOn)
                show();
            else
                hide();
        }

        // Remove the polyline and the markers, but keep mPoints intact for when we need to show the path again
        public void hide() {
            removeVisuals();
        }

        // Add back the polyline and the markers, rebuilding them from mPoints
        public void show() {
            draw();
        }

        private void removeVisuals() {
            // Remove each marker from map
            for (Marker marker : mMarkers)
                marker.remove();

            // Then clear out the marker references
            mMarkers.clear();

            // Remove polyline from map
            mPath.remove();
            // Since after removal from map, Android Documentation says the polyline object has undefined behavior, let's get a new one
            mPath = mMap.addPolyline(mPolyOpts);
        }

        public boolean adjustResolutionIfNeeded() {
            if (mCurProjection != null)
                mAvgSegLenPx = calcAvgSegLenPx();
            else
                Log.d(mTitle, "adjustResolutionIfNeeded: ______skipping avg calc due to mCurProjection still being null______");

            Log.d(mTitle, "adjustResolutionIfNeeded: avg " + MyUtil.formatFloat1(mAvgSegLenPx) + " px/seg (ideal: " + mIdealMinSegLenPx + "_" + mIdealSegLenPx + "_" + mIdealMaxSegLenPx + ")");

            if (mAvgSegLenPx < 0)
                return false; // No segments yet (path still has 0 or 1 pts)

            // Cur segment lengtgetNewh is outside of desired range; need to try to change resolution
            // todo: this logic is questionable bec. when we redo pts, we're not guaranteed that the new avg will be in the range either...
            if (mAvgSegLenPx < mIdealMinSegLenPx || mAvgSegLenPx > mIdealMaxSegLenPx) {
                mCountOutsideIdealRange++;

                ArrayList<LatLng> newPoints = getNewPointsIfPossible();

                if (newPoints != null) {
                    mCountResolutionAdj++;

                    // Clear visual elements of this path
                    removeVisuals();

                    // Replace the points
                    mPoints = newPoints;
                    for (LatLng point : mPoints)
                        mBoundsBuilder.include(point);

                    mAvgSegLenPx = calcAvgSegLenPx(); // todo: can optimize by calculating this in the loop above

                    Log.d(mTitle, "adjustResolutionIfNeeded: *** RESOLUTION CHANGED: " + mPoints.size() + " pts, " + MyUtil.formatFloat1(mAvgSegLenPx) + " px/seg ***");

                    return true;
                }
                else {
                    Log.d(mTitle, "adjustResolutionIfNeeded: Resolution outside of ideal range, but *NOT CHANGING* resolution");
                    return false;
                }
            }

            return false;
        }

        /**
         * Recreate mPoints from Locations so that path resolution is in range again
         * NOTE: there are cases where resolution is outside of range, yet we can't change resolution -
         * Example: in the beginning, we have few points, and each new one causes a zoom, and each
         * segment is longer than desired length, yet we can't change reso bec don't have enough pts.
         */
        private ArrayList<LatLng> getNewPointsIfPossible() {
            ArrayList<Location> locations = mService.getLocations();
            ArrayList<LatLng> newPts = new ArrayList<LatLng>();

            int start = 0; // Begin at first element of Locations array
            newPts.add(locToLatLng(locations.get(start)));

            int last = locations.size() - 1;
            while (start < last) {
                int next = findNextNearestToIdeal(start);
                newPts.add(locToLatLng(locations.get(next)));
                start = next;
            }
            //todo: calc new average and Log.d it

            // If old and new points are same len, means we can't change reso
            if (newPts.size() != mPoints.size())
                return newPts;
            else
                return null; // Couldn't reshuffle points
        }

        int findNextNearestToIdeal(int start) {
            ArrayList<Location> locations = mService.getLocations();
            int last = locations.size() - 1;
            Assert.assertTrue(start < last); // If start == last, means we'd have to return an index *past* the end array.

            // If cur is one before last, return last
            if (start == last - 1)
                return last;

            // Find first element whose dist from cur is over the ideal
            LatLng startPt = locToLatLng(locations.get(start));
            for (int cur = start + 1; cur <= last; cur++) {
                LatLng curPt = locToLatLng(locations.get(cur));
                float pxToCur = pxBetween(startPt, curPt);

                if (pxToCur >= mIdealSegLenPx) {
                    int prev = cur - 1;

                    // Determine which - cur or prev? - is closer to ideal (unless prev == start)
                    if (prev == start)
                        return cur;

                    LatLng prevPt = locToLatLng(locations.get(prev));
                    float pxToPrev = pxBetween(startPt, prevPt);

                    // Compare pxToPrev & pxToCur: whichever is closer to mIdeal is returned
                    if (pxToCur - mIdealSegLenPx >= mIdealSegLenPx - pxToPrev)
                        return cur;
                    else
                        return prev;
                }
                // else we haven't reached ideal seg len, so continue to next element...
            }
            return last; // ideal is somewhere past last, so return last
        }

        private float calcAvgSegLenPx() {
            if (mPoints.size() < 2)
                return -1;

            float totalPx = 0;
            int numSegments = mPoints.size() - 1; // Because there 1 fewer segments than points
            for (int i = 1; i < mPoints.size(); i++) {
                totalPx += pxBetween(mPoints.get(i - 1), mPoints.get(i));
            }

            return totalPx / numSegments;
        }

        private float pxBetween(LatLng latLngA, LatLng latLngB) {
            Point a = mCurProjection.toScreenLocation(latLngA);
            Point b = mCurProjection.toScreenLocation(latLngB);
            return (float)Math.sqrt(Math.pow(b.y - a.y, 2) + Math.pow(b.x - a.x, 2));
        }

        protected LatLng locToLatLng(Location loc) { return new LatLng(loc.getLatitude(), loc.getLongitude()); }
    }

    /*
     * See http://en.wikipedia.org/wiki/Moving_average#Moving_median
     * Let's try using this for getting rid of spikes, and hopefully preserving edges, i.e. when we turn a corner.
     */
//todo xx later, after testing, remove the commented out class

//    public class MovingMedianPath extends Path {
//        /*
//         * Constant(s)
//         */
//        private int mWindowSize; // Must be odd and >= 3 (since we'll be using a symmetrical window around a location)
//
//        private MovingMedianPath() {} // Hide default constructor
//
//        public MovingMedianPath(String title, String color, int iconResourceId, float screenDensity, int windowSize) {
//            super(title, color, iconResourceId, screenDensity);
//            Assert.assertTrue(windowSize % 2 == 1); // mWindowSize is odd
//            Assert.assertTrue(windowSize >= 3); // We need at least one element on either side of the center
//            mWindowSize = windowSize;
//        }
//
//        @Override
//        public int addPoints(ArrayList<Location> locations, int numToAdd) {
//            // Update mPoints and bounds builder
//            int count = 0;
//            for (int i = locations.size() - numToAdd; i < locations.size(); i++) {
//                if (i < mWindowSize - 1) {
//                    Log.d(mTitle, "addPoints: skipping locations[" + i + "] (window size=" + mWindowSize + ")");
//                    continue;
//                }
//
//                LatLng point = calcWindowMedian(locations, i);
//                mPoints.add(point);
//                mBoundsBuilder.include(point);
//                count++;
//            }
//            Log.d(mTitle, "addPoints: added " + count + " pts");
//            return count;
//        }
//
//        private LatLng calcWindowMedian(ArrayList<Location> locations, final int cur) {
//            Assert.assertTrue(cur >= mWindowSize - 1);
//
//            double[] lats = new double[mWindowSize];
//            double[] lngs = new double[mWindowSize];
//
//            for (int i = cur - mWindowSize + 1, j = 0; i <= cur; i++, j++) {
//                lats[j] = locations.get(i).getLatitude();
//                lngs[j] = locations.get(i).getLongitude();
//            }
//
//            Arrays.sort(lats);
//            Arrays.sort(lngs);
//
//            return new LatLng(lats[(int)(mWindowSize/2)], lngs[(int)(mWindowSize/2)]);
//        }
//    }

    public class SlidingFilterPath extends Path {
        Filter mFilter;

        private SlidingFilterPath() {} // Hide default constructor

        public SlidingFilterPath(String title, String color, int iconResourceId, float screenDensity, Filter filter) {
            super(title, color, iconResourceId, screenDensity);
            mFilter = filter;
        }

        @Override
        public int addPoints(ArrayList<Location> locations, int numToAdd) {
            // Update mPoints and bounds builder
            int count = 0;
            for (int i = locations.size() - numToAdd; i < locations.size(); i++) {
                LatLng point = mFilter.applyFilter(locations, i);
                if (point != null) {
                    mPoints.add(point);
                    mBoundsBuilder.include(point);
                    count++;
                }
            }
            Log.d(mTitle, "addPoints: added " + count + " pts");
            return count;
        }
    }

    /*
     * Abstract base class for various filters
     */
    public abstract class Filter {
        protected int mWindowSize;

        private Filter() {} // Hide default constructor

        public Filter(final int windowSize) {
            Assert.assertTrue(windowSize % 2 == 1); // mWindowSize is odd
            Assert.assertTrue(windowSize >= 3); // We need at least one element on either side of the center
            mWindowSize = windowSize;
        }

        abstract public LatLng applyFilter(ArrayList<Location> locations, final int cur);
    }

    public class MeanFilter extends Filter {
        private static final String TAG = "MeanFilter";

        public MeanFilter(final int windowSize) { super(windowSize); }

        @Override
        public LatLng applyFilter(ArrayList<Location> locations, int cur) {
            if (cur < mWindowSize - 1) {
                Log.d(TAG, "applyFilter: skipping locations[" + cur + "] (window size=" + mWindowSize + ")");
                return null;
            }

            double latTotal = 0, lngTotal = 0;
            for (int i = cur - mWindowSize + 1; i <= cur; i++) {
                latTotal += locations.get(i).getLatitude();
                lngTotal += locations.get(i).getLongitude();
            }

            return new LatLng(latTotal/mWindowSize, lngTotal/mWindowSize);
        }
    }

    public class MedianFilter extends Filter {
        private static final String TAG = "MedianFilter";

        public MedianFilter(final int windowSize) { super(windowSize); }

        @Override
        public LatLng applyFilter(ArrayList<Location> locations, int cur) {
            if (cur < mWindowSize - 1) {
                Log.d(TAG, "applyFilter: skipping locations[" + cur + "] (window size=" + mWindowSize + ")");
                return null;
            }

            double[] lats = new double[mWindowSize];
            double[] lngs = new double[mWindowSize];

            for (int i = cur - mWindowSize + 1, j = 0; i <= cur; i++, j++) {
                lats[j] = locations.get(i).getLatitude();
                lngs[j] = locations.get(i).getLongitude();
            }

            Arrays.sort(lats);
            Arrays.sort(lngs);

            return new LatLng(lats[(int)(mWindowSize/2)], lngs[(int)(mWindowSize/2)]);
        }
    }

    public class SharkToothFilter extends Filter {
        private static final String TAG = "SharkToothFilter";

        private double[] mWeights;

        public SharkToothFilter(final int windowSize) {
            super(windowSize);

            // Create the filter
            mWeights = new double[mWindowSize];
            double total = 0;
            int middle = (int)(mWindowSize / 2);

            // All weights should add up to 1
            //          *
            //        * * *
            //      * * * * *
            // Delta is the height of each star; mWindowSize is # of columns. Therefore, the identity to preserve
            // is:
            //      ((mWindowSize + 1)/2)^2 * delta = 1
            // whic is same as:
            //      ( floor(mWindowSize/2) + 1 )^2 * delta = 1
            // or:
            //      (middle + 1)^2 * delta = 1
            // or:
            //      delta = 1 / (middle + 1)^2
            //
            double delta = 1.0 / (middle + 1) / (middle + 1);

            // Left side and middle
            for (int i = 0; i <= middle; i++) {
                mWeights[i] = delta * (i + 1);
                total += mWeights[i];
            }

            // Right side
            for (int i = middle + 1; i < mWindowSize; i++) {
                mWeights[i] = delta * (mWindowSize - i);
                total += mWeights[i];
            }

            // Total should be 1, but due to rounding I don't think we can expect that precision
            Assert.assertTrue(0.99 < total && total < 1.01);
        }

        public LatLng applyFilter(ArrayList<Location> locations, final int cur) {
            if (cur < mWindowSize - 1) {
                Log.d(TAG, "applyFilter: skipping locations[" + cur + "] (window size=" + mWindowSize + ")");
                return null;
            }

            double lat = 0, lng = 0;
            for (int i = cur - mWindowSize + 1, j = 0; i <= cur; i++, j++) {
                lat += locations.get(i).getLatitude() * mWeights[j];
                lng += locations.get(i).getLongitude() * mWeights[j];
            }

            return new LatLng(lat, lng);
        }
    }
}
