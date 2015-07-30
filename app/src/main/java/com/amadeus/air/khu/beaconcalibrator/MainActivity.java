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
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CompoundButton;
import android.widget.Spinner;
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
    private ToggleButton moving;
    KalmanFilter KFWithDeltaDistance;
    KalmanFilter KFNoDeltaDistance;
    private double measurementVariance = 0.64;
    SharedPreferences sharedPref;
    SharedPreferences.Editor editor;
    private boolean isStartedForRecording = false;
    private StringBuffer records = new StringBuffer();;
    private String divider = ",";
    private long basic_timestamp = 0;
    private String monitoringBeacon = "B4:99:4C:74:2B:5A";
    Spinner spinner;
    private boolean isMoving = false;

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

        //mobile config file setting
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


        //beacon select spinner
        spinner = (Spinner) findViewById(R.id.beacons_spinner);
        // Create an ArrayAdapter using the string array and a default spinner layout
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this,
                R.array.beacons_array, android.R.layout.simple_spinner_item);
        // Specify the layout to use when the list of choices appears
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        // Apply the adapter to the spinner
        spinner.setAdapter(adapter);

        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                monitoringBeacon = parent.getItemAtPosition(position).toString();
                Toast.makeText(MainActivity.this, "Current monitoring beacon " + monitoringBeacon, Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });
        monitoringBeacon = spinner.getSelectedItem().toString();

        moving = (ToggleButton) findViewById(R.id.moving);
        moving.setChecked(isStartedForRecording);
        if(moving.isChecked()) {
            moving.setBackgroundColor(Color.parseColor("#C1B8001D"));
        } else {
            moving.setBackgroundColor(Color.parseColor("#c100b80f"));
        }

        moving.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    moving.setBackgroundColor(Color.parseColor("#C1B8001D"));
                    isMoving = true;
                    Toast.makeText(MainActivity.this, "Now you are moving", Toast.LENGTH_SHORT).show();
                } else {
                    moving.setBackgroundColor(Color.parseColor("#c100b80f"));
                    isMoving = false;
                    Toast.makeText(MainActivity.this, "Now you are stopped", Toast.LENGTH_SHORT).show();
                }
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
                    spinner.setEnabled(false);
                    recording.setBackgroundColor(Color.parseColor("#C1B8001D"));
                    Toast.makeText(MainActivity.this, "Recording data", Toast.LENGTH_SHORT).show();
                } else {
                    spinner.setEnabled(true);
                    recording.setBackgroundColor(Color.parseColor("#c100b80f"));
                    saveBufferToFile();
                }
            }
        });





        //beacon settings
        beaconManager.setUseCalibratedDeviceProfile(calibration_setting);
        beaconManager = BeaconManager.getInstanceForApplication(this);
        beaconManager.setDebug(true);
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
        String fname = "Record-" + monitoringBeacon+ "-"+ n +".csv";
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
                        if(beacon.getBluetoothAddress().equals(monitoringBeacon)) {
                            DecimalFormat df = new DecimalFormat("####0.000");
                            double rawDistance = beacon.getDistance();
                            Pair<Double,Double> filteredDistance = KFWithDeltaDistance.updatePrediction(rawDistance, measurementVariance);
                            Pair<Double,Double> filteredNoDeltaDistance = KFNoDeltaDistance.updatePrediction(rawDistance, measurementVariance);

                            final String text = "Raw Distance : " + df.format(rawDistance)
                                    + "\nFiltered Distance (with delta): " + df.format(filteredDistance.getFirst()) + " var: " + df.format(filteredDistance.getSecond())
                                    + "\nFiltered Distance (no delta): " + df.format(filteredNoDeltaDistance.getFirst()) + " var: " + df.format(filteredNoDeltaDistance.getSecond())
                                    + "\nRSSI = "  + beacon.getRssi() + "\n"
                                    + "\nTx power = "  + beacon.getTxPower() + "\n"
                                    + "\nName = "  + beacon.getBluetoothName() + "\n";


                            if(isStartedForRecording) {
                                if(records.length() == 0) {
                                    basic_timestamp = System.currentTimeMillis();
                                    //if buffer is empty, write header at first
                                    records.append("Timestamp" + divider + "Raw Distance" + divider+"Filtered Distance (with delta)" + divider + "Variance"
                                            + divider + "Filtered Distance (no delta)" + divider + "Variance" + divider + "RSSI"+ divider + "isMoving\n");
                                }
                                String moving_text;
                                if (isMoving) {
                                    moving_text = "Moving";
                                } else {
                                    moving_text = "Stopped";
                                }
                                String csv_text = System.currentTimeMillis() - basic_timestamp
                                        + divider + df.format(rawDistance)
                                        + divider + df.format(filteredDistance.getFirst())
                                        + divider + df.format(filteredDistance.getSecond())
                                        + divider + df.format(filteredNoDeltaDistance.getFirst())
                                        + divider + df.format(filteredNoDeltaDistance.getSecond())
                                        + divider  + beacon.getRssi()
                                        + divider + moving_text + "\n";
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
