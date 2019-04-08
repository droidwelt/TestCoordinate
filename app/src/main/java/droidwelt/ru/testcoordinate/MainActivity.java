package droidwelt.ru.testcoordinate;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.design.widget.CoordinatorLayout;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.text.format.DateFormat;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.osmdroid.api.IMapController;
import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.ITileSource;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.ScaleBarOverlay;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;

import static java.lang.String.format;

public class MainActivity extends AppCompatActivity {

    private static final String S_YOU_ARE_HERE = "Вы здесь";
    private static final int RequestPermissionCode = 1;

    private MapView mapView = null;
    private Marker startMarker = null;
    private IMapController mapController;
    private boolean locationAuto;
    private boolean testMode;

    private LocationManager mLocationManager;
    private LocationListener mLocationListener;
    private TextView mLatitudeTextView, mLongitudeTextView;
    private TextView mLatitudeTextViewDelta, mLongitudeTextViewDelta;
    private CoordinatorLayout coordLayout;
    private MenuItem map_type_0;
    private MenuItem map_type_1;
    private MenuItem prov_gps;
    private MenuItem prov_net;
    private FloatingActionButton fab;
    private Toolbar toolbar;

    private Button track_start, track_stop;
    private static boolean track_enabled;

    private static double oLatitude = 0;
    private static double oLongitude = 0;
    private static double cLatitude, cLongitude, cAltitude, cSpeed, cBearing;
    private static final long MINIMUM_DISTANCE_FOR_UPDATES = 0; // в метрах
    private static final long MINIMUM_TIME_BETWEEN_UPDATES = 5000; // в мс

    private Timer mTimer;
    private MyTimerTask mMyTimerTask;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Context ctx = getApplicationContext();
        Configuration.getInstance().load(ctx, PreferenceManager.getDefaultSharedPreferences(ctx));

        setContentView(R.layout.activity_main);
        coordLayout = findViewById(R.id.coordLayout);

        track_start = findViewById(R.id.track_start);
        track_start.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                track_stop.setEnabled(true);
                track_start.setEnabled(false);
                track_enabled = true;
                mTimer = new Timer();
                mMyTimerTask = new MyTimerTask();
                mTimer.schedule(mMyTimerTask, 10, 10000);  // так сказано , без настроек
            }
        });

        track_stop = findViewById(R.id.track_stop);
        track_stop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                track_start.setEnabled(true);
                track_stop.setEnabled(false);
                track_enabled = false;
                if (mTimer != null)
                    mTimer.cancel();
            }
        });

        track_start.setEnabled(!track_enabled);
        track_stop.setEnabled(track_enabled);

        mLatitudeTextView = findViewById(R.id.textViewLatitude);
        mLongitudeTextView = findViewById(R.id.textViewLongitude);
        mLatitudeTextViewDelta = findViewById(R.id.textViewLatitudeDelta);
        mLongitudeTextViewDelta = findViewById(R.id.textViewLongitudeDelta);

        toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

      /*  mTimer = new Timer();  // если нет кнопок СТАРТ - СТОП
        mMyTimerTask = new MyTimerTask();
        mTimer.schedule(mMyTimerTask, 10,10000);  // так сказано , без настроек
*/
        fab = findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                getMyLocation();
            }
        });

        mapView = findViewById(R.id.mapView);

        FloatingActionButton fab = findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Snackbar.make(view, "Force send....", Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show();
                sendCoordTh();
            }
        });

        if (checkMyPremissoins()) {
            startMyActions();
        } else {
            ActivityCompat.requestPermissions(this, new String[]
                    {Manifest.permission.ACCESS_COARSE_LOCATION,
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_WIFI_STATE,
                            Manifest.permission.ACCESS_NETWORK_STATE,
                            Manifest.permission.INTERNET,
                            Manifest.permission.WRITE_EXTERNAL_STORAGE}, RequestPermissionCode);
        }
    }


    private boolean checkMyPremissoins() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M ||
                !((ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) ||
                        (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) ||
                        (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_WIFI_STATE) != PackageManager.PERMISSION_GRANTED) ||
                        (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_NETWORK_STATE) != PackageManager.PERMISSION_GRANTED) ||
                        (ContextCompat.checkSelfPermission(this, Manifest.permission.INTERNET) != PackageManager.PERMISSION_GRANTED) ||
                        (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED));
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String permissions[],
                                           @NonNull int[] grantResults) {
        switch (requestCode) {
            case RequestPermissionCode:
                if (grantResults.length > 0) {
                    boolean b0 = grantResults[0] == PackageManager.PERMISSION_GRANTED;
                    boolean b1 = grantResults[1] == PackageManager.PERMISSION_GRANTED;
                    boolean b2 = grantResults[1] == PackageManager.PERMISSION_GRANTED;
                    boolean b3 = grantResults[2] == PackageManager.PERMISSION_GRANTED;
                    boolean b4 = grantResults[3] == PackageManager.PERMISSION_GRANTED;
                    boolean b5 = grantResults[4] == PackageManager.PERMISSION_GRANTED;
                    if (b0 & b1 & b2 & b3 & b4 & b5) {
                        startMyActions();
                    } else {
                        finish();
                    }
                }
                break;
        }
    }

    @SuppressLint("RestrictedApi")
    private void startMyActions() {
        mapView.setTileSource(getMapsType());
        mapView.setBuiltInZoomControls(true);
        mapView.setMultiTouchControls(true);

        double alatitude = 60.012073; //+ 0.1;
        double alongitude = 30.333724; // + 0.1;
        double azoom = 17.0;

        Intent intent = getIntent();
        String name = intent.getStringExtra("name");
        if ((name != null) && (!name.equals(""))) {
            toolbar.setTitle(name);
        }

        alatitude = intent.getDoubleExtra("latitude", alatitude);
        alongitude = intent.getDoubleExtra("longitude", alongitude);
        azoom = intent.getDoubleExtra("zoom", azoom);

        locationAuto = getLocateAuto();
        testMode = getTestMode();
        LinearLayout addrTable = findViewById(R.id.addrTable);
        addrTable.setVisibility(View.GONE);


        mapController = mapView.getController();
        mapController.setZoom(azoom);
        GeoPoint startPoint = new GeoPoint(alatitude, alongitude);
        mapController.setCenter(startPoint);

        //  Маркер
        startMarker = new Marker(mapView);
        //   startMarker.setPosition(startPoint);
        startMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
        mapView.getOverlays().add(startMarker);

        // Иконка маркера
        //  startMarker.setIcon(getResources().getDrawable(R.mipmap.ic_launcher));

        // Подпись маркера
        if ((name != null) && (!name.equals(""))) {
            startMarker.setTitle(name);
            startMarker.setPosition(startPoint);
        }

        //  сетка
      /*   LatLonGridlineOverlay2 overlay = new LatLonGridlineOverlay2();
        mapView.getOverlays().add(overlay); */

        // вращение
     /*   RotationGestureOverlay mRotationGestureOverlay = new RotationGestureOverlay(mapView);
        mRotationGestureOverlay.setEnabled(true);
        mapView.setMultiTouchControls(true);
        mapView.getOverlays().add(mRotationGestureOverlay); */

        // ночной режим
        //mapView.getController().setInvertedTiles(true);

        //  штучка
        //mapView.setFlingEnabled(true);

        // компас
      /*  CompassOverlay mCompassOverlay = new CompassOverlay(this, new InternalCompassOrientationProvider(this), mapView);
        mCompassOverlay.enableCompass();
        mapView.getOverlays().add(mCompassOverlay); */

        // масштабная шкала
        final DisplayMetrics dm = this.getResources().getDisplayMetrics();
        ScaleBarOverlay mScaleBarOverlay = new ScaleBarOverlay(mapView);
        mScaleBarOverlay.setCentred(true);
        //play around with these values to get the location on screen in the right place for your application
        mScaleBarOverlay.setScaleBarOffset(dm.widthPixels / 2, 10);
        mapView.getOverlays().add(mScaleBarOverlay);

        //   можно работать по GPS и/или по станциям
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED ||
                ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            mLocationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
            mLocationListener = new MyLocationListener();
        }
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        map_type_0 = menu.findItem(R.id.map_type_0);
        map_type_1 = menu.findItem(R.id.map_type_1);
        prov_gps = menu.findItem(R.id.prov_gps);
        prov_net = menu.findItem(R.id.prov_net);
        MenuItem action_locate_auto = menu.findItem(R.id.action_locate_auto);
        action_locate_auto.setChecked(locationAuto);
        if (getMapsType().equals(TileSourceFactory.MAPNIK)) {
            map_type_0.setChecked(true);
            map_type_1.setChecked(false);
        } else {
            map_type_0.setChecked(false);
            map_type_1.setChecked(true);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.action_settings) {
            startActivity(new Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS));
            return true;
        }

        if (id == R.id.action_locate_auto) {
            boolean b = !getLocateAuto();
            locationAuto = b;
            setLocateAuto(b);
            item.setChecked(b);
            if (locationAuto) {
                startLocation();
            } else {
                stopLocation();
            }
            return true;
        }

        if (id == R.id.map_type_0) {
            item.setChecked(true);
            map_type_1.setChecked(false);
            setMapsType(TileSourceFactory.MAPNIK);
            mapView.setTileSource(TileSourceFactory.MAPNIK);
            return true;
        }

        if (id == R.id.map_type_1) {
            item.setChecked(true);
            map_type_0.setChecked(false);
            setMapsType(TileSourceFactory.HIKEBIKEMAP);//
            mapView.setTileSource(TileSourceFactory.HIKEBIKEMAP);
            return true;
        }

        if (id == R.id.action_testmode) {
            boolean b = !getTestMode();
            testMode = b;
            setTestMode(b);
            item.setChecked(b);
            return true;
        }

        if (id == R.id.prov_gps) {
            item.setChecked(true);
            prov_net.setChecked(false);
            setProviderType(LocationManager.GPS_PROVIDER);
            return true;
        }

        if (id == R.id.prov_net) {
            item.setChecked(true);
            prov_gps.setChecked(false);
            setProviderType(LocationManager.NETWORK_PROVIDER);
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onResume() {
        super.onResume();
        Configuration.getInstance().load(getApplicationContext(), PreferenceManager.getDefaultSharedPreferences(getApplicationContext()));
        if (mapView != null)
            mapView.onResume();

        if (!track_enabled)
            startLocation();
    }


    @Override
    public void onPause() {
        super.onPause();
        Configuration.getInstance().save(getApplicationContext(), PreferenceManager.getDefaultSharedPreferences(getApplicationContext()));
        if (mapView != null)
            mapView.onPause();
        stopLocation();
    }

    private void stopLocation() {
        if (!track_enabled)
            mLocationManager.removeUpdates(mLocationListener);
    }

    private void startLocation() {
        String sProvider = getProviderType();
        if (sProvider.equals(LocationManager.NETWORK_PROVIDER)) {
            if ((locationAuto) &&
                    (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED)) {
                mLocationManager.requestLocationUpdates(
                        sProvider,
                        MINIMUM_TIME_BETWEEN_UPDATES,
                        MINIMUM_DISTANCE_FOR_UPDATES,
                        mLocationListener);
            }
        } else {
            if ((locationAuto) && (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED)) {
                mLocationManager.requestLocationUpdates(
                        sProvider,
                        MINIMUM_TIME_BETWEEN_UPDATES,
                        MINIMUM_DISTANCE_FOR_UPDATES,
                        mLocationListener);
            }
        }
    }


    @SuppressLint("DefaultLocale")
    private String doubleToString(double a) {
        return format("%.7f", a);
    }

    private void showCurrentLocation(Location location) {
        if (location != null) {
            mLatitudeTextView.setText(doubleToString(cLatitude));
            mLongitudeTextView.setText(doubleToString(cLongitude));
            if (oLatitude != 0) {
                double dLatitude = cLatitude - oLatitude;
                mLatitudeTextViewDelta.setText(doubleToString(dLatitude));
            }
            if (oLongitude != 0) {
                double dLongitude = cLongitude - oLongitude;
                mLongitudeTextViewDelta.setText(doubleToString(dLongitude));
            }
            oLatitude = cLatitude;
            oLongitude = cLongitude;
        } else {
            mLatitudeTextView.setText("");
            mLongitudeTextView.setText("");
            mLatitudeTextViewDelta.setText("");
            mLongitudeTextViewDelta.setText("");
        }
    }

    private boolean getLocateAuto() {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        String s = sp.getString("locate_auto", "N");
        assert s != null;
        return (s.equals("Y"));
    }

    private void setLocateAuto(boolean b) {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        SharedPreferences.Editor editor = sp.edit();
        String s = "N";
        if (b) s = "Y";
        editor.putString("locate_auto", s);
        editor.apply();
    }

    private boolean getTestMode() {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        String s = sp.getString("test_mode", "N");
        assert s != null;
        return (s.equals("Y"));
    }

    private void setTestMode(boolean b) {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        SharedPreferences.Editor editor = sp.edit();
        String s = "N";
        if (b) s = "Y";
        editor.putString("test_mode", s);
        editor.apply();
    }


    private ITileSource getMapsType() {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        String s = sp.getString("maps_type", "0");
        ITileSource iTileSource = TileSourceFactory.MAPNIK;
        assert s != null;
        if (s.equals("1"))
            iTileSource = TileSourceFactory.HIKEBIKEMAP;
        return iTileSource;
    }

    private void setMapsType(ITileSource iTileSource) {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        SharedPreferences.Editor editor = sp.edit();
        String s = "0";
        if (iTileSource.equals(TileSourceFactory.HIKEBIKEMAP))
            s = "1";
        editor.putString("maps_type", s);
        editor.apply();
    }

    private String getProviderType() {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        String s = sp.getString("provider", LocationManager.NETWORK_PROVIDER);
        return s;
    }

    private void setProviderType(String s) {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        SharedPreferences.Editor editor = sp.edit();
        editor.putString("provider", s);
        editor.apply();
    }


    private void getMyLocation() {
        String sProvider = getProviderType();
        if (sProvider.equals(LocationManager.NETWORK_PROVIDER)) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                mLocationManager.requestLocationUpdates(
                        sProvider,
                        MINIMUM_TIME_BETWEEN_UPDATES,
                        MINIMUM_DISTANCE_FOR_UPDATES,
                        mLocationListener);
                Location location = mLocationManager.getLastKnownLocation(sProvider);
                prepareLocation(location);
            }
        } else {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                mLocationManager.requestLocationUpdates(
                        sProvider,
                        MINIMUM_TIME_BETWEEN_UPDATES,
                        MINIMUM_DISTANCE_FOR_UPDATES,
                        mLocationListener);
                Location location = mLocationManager.getLastKnownLocation(sProvider);
                prepareLocation(location);
            }
        }
    }

    private void prepareLocation(Location location) {
        if (location != null) {
            cLatitude = location.getLatitude();
            cLongitude = location.getLongitude();
            cAltitude = location.getAltitude();
            cSpeed = location.getSpeed();
            cBearing = location.getBearing();
            showCurrentLocation(location);
            GeoPoint startPoint = new GeoPoint(location.getLatitude(), location.getLongitude());
            mapController.setCenter(startPoint);

            startMarker.setTitle(S_YOU_ARE_HERE);
            startMarker.setPosition(startPoint);
            /* String message = "Новое местоположение \n " +
                    "Широта: " + doubleToString(location.getLatitude()) + "\n " +
                    "Долгота: " + doubleToString(location.getLongitude());
            Toast.makeText(MainActivity.this, message, Toast.LENGTH_LONG).show(); */
        }
    }

    // Прослушиваем изменения
    private class MyLocationListener implements LocationListener {

        public void onLocationChanged(Location location) {
            prepareLocation(location);
        }

        public void onStatusChanged(String s, int i, Bundle b) {
            Toast.makeText(MainActivity.this, "Статус провайдера изменился", Toast.LENGTH_LONG).show();
        }

        public void onProviderDisabled(String s) {
            Toast.makeText(MainActivity.this, "Провайдер заблокирован пользователем. Геолокация выключена", Toast.LENGTH_LONG).show();
        }

        public void onProviderEnabled(String s) {
            Toast.makeText(MainActivity.this, "Провайдер включен пользователем. Геолокация включёна", Toast.LENGTH_LONG).show();
        }
    }


    public void sendCoordTh() {
        Runnable runnable = new Runnable() {
            public void run() {
                sendCoord();
            }
        };
        Thread thread = new Thread(runnable);
        thread.start();
    }

    public void sendCoord() {
        Socket sc;
        try {
            sc = new Socket("5.9.136.109", Integer.parseInt("3359")); //  в настройки это :-)
        } catch (IOException e) {
            //   e.printStackTrace();
            //  Log.e("MyApp", "Socket");
            return;
        }
        try {
            sc.setKeepAlive(true);
        } catch (SocketException e) {
            //   e.printStackTrace();
            //  Log.e("MyApp", "setKeepAlive");
        }
        try {
            InputStream is = sc.getInputStream();
        } catch (IOException e) {
            //   e.printStackTrace();
            //  Log.e("MyApp", "InputStream");
        }
        try {
            OutputStream os = sc.getOutputStream();
            String sLatitude, sLongitude, sAltitude, sSpeed, sBearing, sBatteryStatus, sDate, sTime;
            if (testMode) {
                Random r = new Random();
                double randomValue = r.nextDouble() * 0.005;
                sLatitude = String.valueOf(cLatitude + randomValue);
                randomValue = r.nextDouble() * 0.005;
                sLongitude = String.valueOf(cLongitude + randomValue);
            } else {
                sLatitude = String.valueOf(cLatitude);
                sLongitude = String.valueOf(cLongitude);
            }
            sAltitude = String.valueOf(cAltitude);
            sSpeed = String.valueOf(cSpeed);
            sBearing  = String.valueOf(cBearing);
            sBatteryStatus = String.valueOf(getBatteryStatus());

            Date cDate = Calendar.getInstance().getTime();
            sDate = DateFormat.format("yyyyMMdd", cDate).toString();
            sTime = DateFormat.format("HHmmss", cDate).toString();

            String s = "rtt003,356217625371621," + sLatitude + "," + sLongitude + "+," + sSpeed + "," + sAltitude + ","+sBearing+","+
                    sBatteryStatus+","+sDate+","+sTime+",-11,24,60,A,0";
            os.write(s.getBytes());
            Log.e("MyApp", "SEND " + sLatitude + " " + sLongitude);

            //  ответ не проверяем...........
        } catch (IOException e) {
            //   e.printStackTrace();
            //   Log.e("MyApp", "OutputStream");
        }
    }


    private  int getBatteryStatus ()  {
        IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent batteryStatus = registerReceiver(null, ifilter);
        assert batteryStatus != null;
        return batteryStatus.getIntExtra("level", -1);
    }


    class MyTimerTask extends TimerTask {

        @Override
        public void run() {
            sendCoord();
         /*   runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    //  показ чего-нибудб, если надо
                    sendCoord();
                    Snackbar.make(coordLayout, "MyTimerTask", Snackbar.LENGTH_LONG).setAction("Action", null).show();
                }
            }); */
        }
    }


}
