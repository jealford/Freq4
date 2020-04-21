package com.example.freq4;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.ToggleButton;

import androidx.appcompat.widget.AppCompatToggleButton;
import androidx.core.app.ActivityCompat;

import java.text.DecimalFormat;
import java.util.LinkedList;
import java.util.List;

public class MainActivity extends Activity {

    private static final int sampleRate = 8000;
    private AudioRecord audio;
    private int bufferSize;
    private double lastLevel = 0;
    private Thread thread;
    private static final int SAMPLE_DELAY = 75;
    private boolean permissionToRecord;
    private TextView volumeText;
    private TextView freqText;
    private TextView ampText;
    private double volume = 0;
    private double freq = 0;
    private byte loop = 0;
    private double[] volumeArray = new double[5];
    private boolean quietSet = false;
    private double quietLevel = 0;
    private double[] freqArray = new double[5];
    private boolean freqFound = true;
    private int volumeLevel = 0;
    private ToggleButton startStopToggle;
    private boolean searching = false;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        //set up UI elements
        setContentView(R.layout.activity_main);
        volumeText = (TextView) findViewById(R.id.volumeText);
        freqText = (TextView) findViewById(R.id.freqText);
        ampText = (TextView) findViewById(R.id.ampText);
        startStopToggle = findViewById(R.id.startStop);

        //check for microphone permissions
        permissionToRecord = ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED;

        //get the size for the smallest buffer
        try {
            bufferSize = AudioRecord
                    .getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_MONO,
                            AudioFormat.ENCODING_PCM_16BIT);
        } catch (Exception e) {
            android.util.Log.e("TrackingFlow", "Exception", e);
        }
    }//onCreate

    protected void onResume() {
        super.onResume();

        freqText.setText("Ready To Scan");

        //set up toggle for start/stop scan
        startStopToggle.setChecked(false);
        startStopToggle.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {

            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                //if toggled on
                if (isChecked) {
                    audio = new AudioRecord(MediaRecorder.AudioSource.MIC, sampleRate,
                            AudioFormat.CHANNEL_IN_MONO,
                            AudioFormat.ENCODING_PCM_16BIT, bufferSize);

                    searching = true;

                    if (permissionToRecord) {
                        audio.startRecording();
                        thread = new Thread(new Runnable() {
                            public void run() {
                                while (thread != null && !thread.isInterrupted()) {
                                    //sleep thread for approximate sampling time to capture min
                                    //buffer amount of data
                                    try {
                                        Thread.sleep(SAMPLE_DELAY);
                                    } catch (InterruptedException ie) {
                                        ie.printStackTrace();
                                    }


                                    readAudioBuffer();

                                    //capture 5 sample iterations
                                    if (loop < 5){
                                        freqArray[loop] = freq;
                                        volumeArray[loop] = lastLevel;
                                        loop++;
                                    }

                                    //then average those samples for smoother volume measurement
                                    if (loop == 5) {
                                        lastLevel = 0;
                                        for(double i : volumeArray) {
                                            lastLevel += i;
                                        }
                                        lastLevel /= 5;

                                        //quietLevel needs to be set by first sample iteration
                                        if (quietSet && freqFound){
                                            volume = quietLevel - lastLevel;
                                            volumeLevel = setVolumeLevel(freq);
                                            loop = 0;
                                        }
                                        else{
                                            quietLevel = lastLevel;
                                            quietSet = true;
                                        }
                                    }// loop == 5

                                    //update the UI with relevant info
                                    runOnUiThread(new Runnable() {

                                        @Override
                                        public void run() {

                                            String volumeString = Integer.toString(volumeLevel);
                                            volumeText.setText(volumeString);

                                            String ampString = Double.toString(volume);
                                            ampText.setText(ampString);

                                            String frequecy = Double.toString(freq);
                                            if (!searching) freqText.setText("Ready to Search");
                                            else if (freq >= 375 && freq <= 425){
                                                freqText.setText("400 HZ");
                                            }
                                            else if (freq >= 575 && freq <= 625){
                                                freqText.setText("600 HZ");
                                            }
                                            else if (freq >= 775 && freq <= 825){
                                                freqText.setText("800 HZ");
                                            }
                                            else {
                                                freqText.setText("Searching...");
                                            }

                                        }//run
                                    });
                                }
                            }
                        });
                        thread.start();
                    }// if permissionToRecord

                }
                // if un-toggled
                else {
                    searching = false;
                    thread.interrupt();
                    thread = null;
                    try {
                        if (audio != null) {
                            audio.stop();
                            audio.release();
                            audio = null;
                        }
                    } catch (Exception e) {e.printStackTrace();}
                }

            }
            //freqText.setText("Ready To Scan");
        });
    }//onResume

    /**
    * Method that takes in a frequency and takes the volume level and returns an int
    * for the volume level requiement of 0,1,2, or 3
     **/

    private int setVolumeLevel(double freq){
        volume = Math.abs(volume);
        if (freq >= 375 && freq <= 425){ // 400Hz
            if(volume < 5.3) return 1;
            else if(volume < 10.3) return 2;
            else return 3;
        }
        else if (freq >= 575 && freq <= 625){// 600Hz
            if(volume < 3.0) return 1;
            else if(volume < 4.6) return 2;
            else return 3;
        }
        else if (freq >= 775 && freq <= 825){// 800Hz
            if(volume < 7) return 1;
            else if(volume < 16) return 2;
            else return 3;
        }
        else {
            return 0;
        }
    }

    /**
     * Method that gets the sound level out of the sample
     **/
    private void readAudioBuffer() {

        try {
            short[] buffer = new short[bufferSize];
            int[] samples = new int[bufferSize];
            int bufferReadResult = 1;

            if (audio != null) {
                // Sense sound level
                bufferReadResult = audio.read(buffer, 0, bufferSize);
                double maxLevel = 0;
                freq = 0;

                int negInd = 0;
                List cross = new LinkedList();

                for (int i = 0; i < bufferReadResult; i++) {
                    if(Math.abs(buffer[i]) > maxLevel){
                        maxLevel = Math.abs(buffer[i]);
                    }

                    //find where wave cross x axis from negative y's to positive  y's
                    if(i < (bufferReadResult - 1) && buffer[i] <= 0 && buffer[i+1] >= 0){
                        negInd = i;
                        cross.add(negInd);
                    }

                }

                for (int i = 0; i < cross.size() - 1; i++){
                    int numSamples = (int) cross.get(i + 1) - (int) cross.get(i);
                    double rate = 1.0/8000.0;
                    double period = numSamples * rate;
                    freq += (1.0/period);
                }

                lastLevel = Math.abs(maxLevel);
                freq /= (cross.size() - 1);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }//readAudioBuffer

    @Override
    protected void onPause() {
        super.onPause();
        if(thread != null){
            thread.interrupt();
        }
        thread = null;
        try {
            if (audio != null) {
                audio.stop();
                audio.release();
                audio = null;
            }
        } catch (Exception e) {e.printStackTrace();}
    }
}//MainActivity