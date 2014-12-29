package com.blah0x1e6f.androidsample;

import android.annotation.TargetApi;
import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.location.Location;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import android.widget.Toast;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesClient;
import com.google.android.gms.location.LocationClient;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import java.util.ArrayList;

public class NewtonCreekService extends Service implements
        GooglePlayServicesClient.ConnectionCallbacks,
        GooglePlayServicesClient.OnConnectionFailedListener,
        LocationListener {
    private static final String TAG = "NewtonCreek_SERVICE";
    private static final int UPDATE_INTERVAL_MILLIS = 10 * 1000;
    private static final int FASTEST_INTERVAL_MILLIS = 3 * 1000;

    private LocationRequest mLocationRequest;
    //todo the *right* way to do this is via GoogleApiClient, not LocationClient - here's an example: http://stackoverflow.com/questions/25047910/using-googleapiclient-locationservices-not-updating and here's the Google Developers page: http://developer.android.com/google/auth/api-client.html
    private LocationClient mLocationClient;
    private ArrayList<Location> mLocations;

    private int mNumFixes = 0;

    public int getNumFixes() { return mNumFixes; }

    private final IBinder mBinder = new MyBinder();
    private NewtonCreekActivity mActivity;

    // For when testing app at home
    private LocationSimulator mSimulator;

    public class MyBinder extends Binder {
        NewtonCreekService getService() {
            return NewtonCreekService.this;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "onCreate");

        mLocations = new ArrayList<Location>();

        mLocationRequest = LocationRequest.create();
        mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        mLocationRequest.setInterval(UPDATE_INTERVAL_MILLIS);
        mLocationRequest.setFastestInterval(FASTEST_INTERVAL_MILLIS);

        mLocationClient = new LocationClient(this, this, this);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this)
                        .setSmallIcon(R.drawable.ic_launcher)
                        .setContentTitle("Newton Creek")
                        .setContentText("Location tracker blah blah xx");
        Intent targetIntent = new Intent(this, NewtonCreekActivity.class);
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0, targetIntent, PendingIntent.FLAG_UPDATE_CURRENT);
        builder.setContentIntent(contentIntent);
        Notification notification = builder.build();
        startForeground(1977, notification);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "onStartCommand");

        if (!mLocationClient.isConnected() && !mLocationClient.isConnecting()) {
            mLocationClient.connect();
        }

        return START_STICKY; // see http://developer.android.com/guide/components/services.html
    }

    @Override
    public IBinder onBind(Intent intent) { Log.d(TAG, "onBind"); return mBinder; }

    public void setActivity(NewtonCreekActivity parent) {
        mActivity = parent;
    }

    public ArrayList<Location> getLocations() {
        return mLocations;
    }

    public void reset() {
        mLocations.clear();
        mNumFixes = 0;
        if (mSimulator != null)
            mSimulator = null;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        Log.d(TAG, "onUnbind");
        return super.onUnbind(intent);
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "onDestroy");

        if (mLocationClient.isConnected()) {
            mLocationClient.removeLocationUpdates(this);
        }
        mLocationClient.disconnect();

        stopForeground(true);

        super.onDestroy();
    }

    @Override
    public void onConnected(Bundle dataBundle) {
        Log.i(TAG, "Location client connected");
        Toast.makeText(this, "Location client connected", Toast.LENGTH_SHORT).show();
        mLocationClient.requestLocationUpdates(mLocationRequest, this);
    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {
        Log.e(TAG, "Location client connection failed");
        Toast.makeText(this, "Location client connection failed", Toast.LENGTH_SHORT).show();
    }

    //todo: mk added this, so that despite my build.gradle specifying a min supported API version of 15, I can use Location.getElapsedRealtimeNanos, which is in API v 17
    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
    @Override
    public void onLocationChanged(Location location) {
        mNumFixes++;

        // ------------------- location simulator ----------------------
//        if (null == mSimulator) {
//            mSimulator = new LocationSimulator();
//            Log.d(TAG, "Creating simulator...");
//        }
//        mSimulator.randomizeLocation(location);
        // -------------------------------------------------------------

        // todo: handle the desired accuracy number
        if (location.getAccuracy() <= 50) {
            mLocations.add(location); // Just keep adding locations
            Log.d(TAG, "onLocationChanged: " + MyUtil.formatFloat6((float)location.getLatitude()) + " (" + mLocations.size() + " locs)");
            // Notify Activity if it is available. No big deal if it's not avail, because when it becomes available, it'll grab all locations itself
            if (mActivity != null) {
                mActivity.newLocationsAvail();
            }
        }
    }

    @Override
    public void onDisconnected() {
        Log.d(TAG, "Location client disconnected");
    }
}
