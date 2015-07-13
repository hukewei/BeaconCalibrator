package com.amadeus.air.khu.beaconcalibrator;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Environment;
import android.os.RemoteException;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.CompoundButton;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import org.altbeacon.beacon.Beacon;
import org.altbeacon.beacon.BeaconConsumer;
import org.altbeacon.beacon.BeaconManager;
import org.altbeacon.beacon.BeaconParser;
import org.altbeacon.beacon.RangeNotifier;
import org.altbeacon.beacon.Region;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.text.DecimalFormat;
import java.util.Collection;
import java.util.Random;


public class MainActivity extends ActionBarActivity  implements BeaconConsumer {
    private static final String TAG = "MainActivity";
    private BeaconManager beaconManager;
    private TextView mainText;
    private ToggleButton toggle;
    private ToggleButton recording;
    KalmanFilter KFWithDeltaDistance;
    KalmanFilter KFNoDeltaDistance;
    private double measurementVariance = 0.64;
    SharedPreferences sharedPref;
    SharedPreferences.Editor editor;
    private boolean isStartedForRecording = false;
    private StringBuffer records = new StringBuffer();;
    private String divider = ",";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        //read settings from preference
        sharedPref = getPreferences(Context.MODE_PRIVATE);
        boolean defaultValue = false;
        boolean calibration_setting = sharedPref.getBoolean(getString(R.string.preference_device_calibration_key), defaultValue);
        editor = sharedPref.edit();
        editor.putBoolean(getString(R.string.preference_device_calibration_key), calibration_setting);
        editor.commit();


        //layout settings
        mainText = (TextView) findViewById(R.id.main_text);
        toggle = (ToggleButton) findViewById(R.id.togglebutton);
        toggle.setChecked(calibration_setting);
        toggle.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                editor.putBoolean(getString(R.string.preference_device_calibration_key), isChecked);
                editor.commit();
                Toast.makeText(MainActivity.this, "Please restart the application to apply the change", Toast.LENGTH_SHORT).show();
            }
        });

        recording = (ToggleButton) findViewById(R.id.recording);
        recording.setChecked(isStartedForRecording);
        if(recording.isChecked()) {
            recording.setBackgroundColor(Color.parseColor("#C1B8001D"));
        } else {
            recording.setBackgroundColor(Color.parseColor("#c100b80f"));
        }
        recording.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                isStartedForRecording = isChecked;
                if (isChecked) {
                    recording.setBackgroundColor(Color.parseColor("#C1B8001D"));
                    Toast.makeText(MainActivity.this, "Recording data", Toast.LENGTH_SHORT).show();
                } else {
                    recording.setBackgroundColor(Color.parseColor("#c100b80f"));
                    saveBufferToFile();
                }
            }
        });

        //beacon settings
        beaconManager = BeaconManager.getInstanceForApplication(this);
        beaconManager.setUseCalibratedDeviceProfile(calibration_setting);
        beaconManager.bind(this);
        beaconManager.setForegroundScanPeriod(500);
        beaconManager.getBeaconParsers().add(new BeaconParser().
                setBeaconLayout("m:2-3=0215,i:4-19,i:20-21,i:22-23,p:24-24"));
        KFWithDeltaDistance = new KalmanFilter(true);
        KFNoDeltaDistance = new KalmanFilter(false);



    }

    private void saveBufferToFile() {
        String root = Environment.getExternalStorageDirectory().toString();
        File myDir = new File(root + "/saved_beacon_records");
        myDir.mkdirs();
        long n = System.currentTimeMillis();
        String fname = "Record-"+ n +".csv";
        File file = new File (myDir, fname);
        if (file.exists ()) file.delete ();
        try {
            FileOutputStream out = new FileOutputStream(file);
            out.write(records.toString().getBytes());
            out.flush();
            out.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        //clean buffer
        records.setLength(0);
        Toast.makeText(MainActivity.this, "CSV file " + fname + " saved", Toast.LENGTH_SHORT).show();


    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
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

    @Override
    public void onBeaconServiceConnect() {
        beaconManager.setRangeNotifier(new RangeNotifier() {
            @Override
            public void didRangeBeaconsInRegion(Collection<Beacon> beacons, Region region) {

                if (beacons.size() > 0) {
                    for (Beacon beacon:beacons) {
                        if(beacon.getBluetoothAddress().equals("D8:CF:DD:50:4F:EB")) {
                            DecimalFormat df = new DecimalFormat("####0.000");
                            double rawDistance = beacon.getDistance();
                            Pair<Double,Double> filteredDistance = KFWithDeltaDistance.updatePrediction(rawDistance, measurementVariance);
                            Pair<Double,Double> filteredNoDeltaDistance = KFNoDeltaDistance.updatePrediction(rawDistance, measurementVariance);

                            final String text = "Raw Distance : " + df.format(rawDistance)
                                    + "\nFiltered Distance (with delta): " + df.format(filteredDistance.getFirst()) + " var: " + df.format(filteredDistance.getSecond())
                                    + "\nFiltered Distance (no delta): " + df.format(filteredNoDeltaDistance.getFirst()) + " var: " + df.format(filteredNoDeltaDistance.getSecond())
                                    + "\nRSSI = "  + beacon.getRssi() + "\n";
                            if(isStartedForRecording) {
                                if(records.length() == 0) {
                                    //if buffer is empty, write header at first
                                    records.append("Timestamp" + divider + "Raw Distance" + divider+"Filtered Distance (with delta)" + divider + "Variance"
                                            + divider + "Filtered Distance (no delta)" + divider + "Variance" + divider + "RSSI\n");
                                }
                                String csv_text = System.currentTimeMillis()
                                        + divider + df.format(rawDistance)
                                        + divider + df.format(filteredDistance.getFirst())
                                        + divider + df.format(filteredDistance.getSecond())
                                        + divider + df.format(filteredNoDeltaDistance.getFirst())
                                        + divider + df.format(filteredNoDeltaDistance.getSecond())
                                        + divider  + beacon.getRssi() + "\n";
                                records.append(csv_text);
                            }
                            Log.i(TAG, text);
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    mainText.setText(text);
                                }
                            });
                            break;
                        }
                    }

                }
            }
        });

        try {
            beaconManager.startRangingBeaconsInRegion(new Region("myRangingUniqueId", null, null, null));
        } catch (RemoteException e) {    }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        beaconManager.unbind(this);
    }
}
