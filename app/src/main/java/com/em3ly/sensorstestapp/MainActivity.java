package com.em3ly.sensorstestapp;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.TextView;

import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.series.DataPoint;
import com.jjoe64.graphview.series.LineGraphSeries;

public class MainActivity extends AppCompatActivity {
    private final String TAG = "SensorsTestApp";
    TextView tvHr, tvAcc, tvProximity, tvCalibration, tvOffBodyE;
    GraphView graph;
    LineGraphSeries<DataPoint> series_hr;
    LineGraphSeries<DataPoint> series_ppg_offbody_enhanced;
    LineGraphSeries<DataPoint> series_ppg_offbody;

    private final int PPG_OFB_ENHANCED_OFFSET = 100;
    private final int PPG_OFB_OFFSET = 150;

    private int timeAxis = 0;
    private final int MAX_TIME_AXIS = 40;
    private final int MAX_HR_AXIS = 300;
    private final int MIN_HR_AXIS = 0;

    //The sensor identifier can be seen with adb shell dumpsys sensorservice
    private static final int PPG_OFFBODY_CALIBRATION_ID= 33171007;
    private static final int PPG_OFFBPDY_ENHANCED_ID= 33171008;

    private Sensor heartRateSensor;
    private Sensor offbodySensor;
    private Sensor offBoddySensorCalibration;
    private Sensor offBoddyEnhancedSensor;
    private SensorManager sensorManager;

    private Ringtone mAlarmNotification;
    private boolean soundsOn = false;
    private boolean sensorsOn = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        initGraphs();
        if (null != graph) registerForContextMenu(graph);

        getPermission();
        if (checkSelfPermission(Manifest.permission.BODY_SENSORS) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "body sensor permission denied");
        }

        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        heartRateSensor = sensorManager.getDefaultSensor(Sensor.TYPE_HEART_RATE);
        offbodySensor = sensorManager.getDefaultSensor(Sensor.TYPE_LOW_LATENCY_OFFBODY_DETECT);

        offBoddySensorCalibration = sensorManager.getDefaultSensor(PPG_OFFBODY_CALIBRATION_ID);
        offBoddyEnhancedSensor = sensorManager.getDefaultSensor(PPG_OFFBPDY_ENHANCED_ID);

        Uri notification = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
        mAlarmNotification = RingtoneManager.getRingtone(getApplicationContext(), notification);
    }

    private void initGraphs() {
        final int HR_COLOR = Color.rgb(244, 152, 173);
        final int PROX_COLOR = Color.CYAN;
        final int OFBE_COLOR = Color.GREEN;

        graph = findViewById(R.id.idGraphView);

        tvHr = findViewById(R.id.tv_hr);
        tvHr.setBackgroundColor(HR_COLOR);

        tvAcc = findViewById(R.id.tv_acc);
        tvAcc.setBackgroundColor(HR_COLOR);

        tvProximity = findViewById(R.id.tv_proximity);
        tvProximity.setBackgroundColor(PROX_COLOR);

        tvCalibration = findViewById(R.id.tv_calibration);

        tvOffBodyE = findViewById(R.id.tv_offbody_enhanced);
        tvOffBodyE.setBackgroundColor(OFBE_COLOR);

        graph.removeAllSeries();
        graph.getViewport().setXAxisBoundsManual(true);
        graph.getViewport().setMinX(0);
        graph.getViewport().setMaxX(MAX_TIME_AXIS);
        graph.getViewport().setYAxisBoundsManual(true);
        graph.getViewport().setMinY(MIN_HR_AXIS);
        graph.getViewport().setMaxY(MAX_HR_AXIS);

        series_hr = new LineGraphSeries<DataPoint>();
        series_hr.setColor(HR_COLOR);

        series_ppg_offbody_enhanced = new LineGraphSeries<DataPoint>();
        series_ppg_offbody_enhanced.setColor(OFBE_COLOR);

        series_ppg_offbody = new LineGraphSeries<DataPoint>();
        series_ppg_offbody.setColor(PROX_COLOR);

        graph.addSeries(series_hr);
        graph.addSeries(series_ppg_offbody_enhanced);
        graph.addSeries(series_ppg_offbody);
    }
    public void getPermission() {
        requestPermissions(new String[]{Manifest.permission.BODY_SENSORS}, 1);
    }
    private void createMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.actions, menu);
        menu.findItem(R.id.allowSounds).setChecked(soundsOn);
        menu.findItem(R.id.restartPPG).setChecked(sensorsOn);
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        menu.findItem(R.id.allowSounds).setChecked(soundsOn);
        menu.findItem(R.id.restartPPG).setChecked(sensorsOn);
        return true;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        createMenu(menu);
        return true;
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        if (v.getId() == R.id.idGraphView) {
            createMenu(menu);
        }
    }

    @Override
    public boolean onContextItemSelected(@NonNull MenuItem item) {
        onOptionsItemSelected(item);
        return true;
    }
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.calibratePPG:
                // PPG sensors should not be in use while calibration is being done.
                unregisterBiometrics();
                sensorManager.registerListener(offBodyCalibrationListener, offBoddySensorCalibration, SensorManager.SENSOR_DELAY_NORMAL);
                tvCalibration.setText("Cal: --");
                break;
            case R.id.restartPPG:
                if (item.isChecked()) {
                    unregisterBiometrics();
                } else {
                    registerBiometrics();
                }
                item.setChecked(!item.isChecked());
                sensorsOn = !sensorsOn;
                break;
            case R.id.allowSounds:
                    soundsOn = !soundsOn;
                    item.setChecked(!item.isChecked());
                break;
        }
        return true;
    }

    private SensorEventListener offBodyCalibrationListener = new SensorEventListener() {
        @Override
        public void onSensorChanged(SensorEvent event) {
            String value = String.valueOf(event.values[0]);
            Log.d(TAG, "PPG offbody calibration: " + value);
            tvCalibration.setText("Cal: " + value);
            sensorManager.unregisterListener(offBodyCalibrationListener);
            registerBiometrics();
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {

        }
    };

    private void unregisterBiometrics() {
        sensorManager.unregisterListener(mHRSensorListener);
        sensorManager.unregisterListener(mPPGOffbodyEnhancedSensorListener);
    }

    private void registerBiometrics() {
        if (null != heartRateSensor && null != mHRSensorListener) {
            sensorManager.registerListener(mHRSensorListener, heartRateSensor, SensorManager.SENSOR_DELAY_NORMAL);
        }
        if (null != offbodySensor && null != mHRSensorListener) {
            sensorManager.registerListener(mHRSensorListener, offbodySensor, SensorManager.SENSOR_DELAY_NORMAL);
        }
        if (null != offBoddyEnhancedSensor && null != mPPGOffbodyEnhancedSensorListener) {
            sensorManager.registerListener(mPPGOffbodyEnhancedSensorListener, offBoddyEnhancedSensor, SensorManager.SENSOR_DELAY_NORMAL);
        }
    }

    private SensorEventListener mPPGOffbodyEnhancedSensorListener = new SensorEventListener() {
        @Override
        public void onSensorChanged(SensorEvent event) {
            if (event.sensor.getType() != PPG_OFFBPDY_ENHANCED_ID)
                return;

            float ofb_enhanced = event.values[0];
            tvOffBodyE.setText("OFB_E: " + ofb_enhanced);
            series_ppg_offbody_enhanced.appendData(new DataPoint(timeAxis++, ofb_enhanced * PPG_OFB_ENHANCED_OFFSET), true, MAX_TIME_AXIS);
            String log = "Pixart PPG Offbody Enhanced measurement: (" + ofb_enhanced + "," + event.accuracy + "," +
                    event.timestamp + ") : value: " + ofb_enhanced + " Accuracy: " + event.accuracy + " timestamp: " + event.timestamp;
            Log.d(TAG, log);
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {}
    };
    private SensorEventListener mHRSensorListener = new SensorEventListener() {
        @Override
        public void onSensorChanged(SensorEvent event) {
            if (event.sensor.getType() != Sensor.TYPE_HEART_RATE && event.sensor.getType() != Sensor.TYPE_LOW_LATENCY_OFFBODY_DETECT)
                return;
            if (event.sensor.getType() == Sensor.TYPE_HEART_RATE) {
                float hr = event.values[0];
                tvHr.setText("HR: " + hr);
                series_hr.appendData(new DataPoint(timeAxis++, hr), true, MAX_TIME_AXIS);
                String log = "Phillips PPG HR measurement: (" + hr + "," + event.accuracy + "," + event.timestamp + ") : value: " + hr + " Accuracy: " + event.accuracy + " timestamp: " + event.timestamp;
                Log.d(TAG, log);
                if (event.accuracy == 0 && soundsOn) {
                    if (!mAlarmNotification.isPlaying()) {
                        mAlarmNotification.play();
                    }
                }
            } else if (event.sensor.getType() == Sensor.TYPE_LOW_LATENCY_OFFBODY_DETECT) {
                float offbody = event.values[0];
                Log.d(TAG, "Phillips PPG Proximity / Off-Body State changed to: " + offbody);
                tvProximity.setText("Prox: " + offbody);
                series_ppg_offbody.appendData(new DataPoint(timeAxis++, offbody * PPG_OFB_OFFSET), true, MAX_TIME_AXIS);
                try {
                    if (soundsOn) {
                        Uri notification = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
                        Ringtone r = RingtoneManager.getRingtone(getApplicationContext(), notification);
                        r.play();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
            if (sensor.getType() != Sensor.TYPE_HEART_RATE) return;
            Log.d(TAG, "Phillips PPG HR accuracy changed to: " + accuracy);
            tvAcc.setText("Acc : " + accuracy);
        }
    };

    @Override
    protected void onResume() {
        super.onResume();
        registerBiometrics();
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterBiometrics();
    }
}