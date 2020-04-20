package com.example.freq4;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.core.app.ActivityCompat;

import java.text.DecimalFormat;
import java.util.LinkedList;
import java.util.List;

public class MainActivity extends Activity {

    private static final int sampleRate = 8000;
    private AudioRecord audio;
    private int bufferSize; //512 on emulator
    private double lastLevel = 0;
    private Thread thread;
    private static final int SAMPLE_DELAY = 75; //was 75
    private boolean permissionToRecord;
    TextView volumeText;
    TextView freqText;
    double volume = 0;
    double freq = 0;
    byte loop = 0;
    List volumeList = new LinkedList();
    double[] volumeArray = new double[5];
    boolean quietSet = false;
    double quietLevel = 0;
    boolean freqFound = true; //set to true for testing, code needs to change this to true only when one of the freqs are found

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        volumeText = (TextView) findViewById(R.id.volumeText);
        freqText = (TextView) findViewById(R.id.freqText);

        permissionToRecord = ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED;

        try {
            bufferSize = AudioRecord
                    .getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_MONO,
                            AudioFormat.ENCODING_PCM_16BIT);
        } catch (Exception e) {
            android.util.Log.e("TrackingFlow", "Exception", e);
        }



    }

    protected void onResume() {
        super.onResume();
        audio = new AudioRecord(MediaRecorder.AudioSource.MIC, sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT, bufferSize);

        if (permissionToRecord) {
            audio.startRecording();
            thread = new Thread(new Runnable() {
                public void run() {
                    while (thread != null && !thread.isInterrupted()) {
                        //sleep thread for approximate sampling time
                        try {
                            Thread.sleep(SAMPLE_DELAY);
                        } catch (InterruptedException ie) {
                            ie.printStackTrace();
                        }
                        readAudioBuffer();

                        runOnUiThread(new Runnable() {

                            @Override
                            public void run() {

                                //capture 5 sample iterations
                                if (loop < 5){
                                    volumeArray[loop]=lastLevel;
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
                                        String volumeString = Double.toString(volume);
                                        volumeText.setText(volumeString);
                                        loop = 0;
                                        //code for soft, medium, loud
                                        if (volume > 2){
                                            //soft
                                        }
                                        else if(volume >=2 && volume < 10){
                                            //medium

                                        }
                                        else if (volume >= 10){
                                            //loud
                                        }
                                    }
                                    else{
                                        quietLevel = lastLevel;
                                        quietSet = true;
                                    }


                                }
                                //String volume = Double.toString(lastLevel);
                                String frequecy = Double.toString(freq);


                                //volumeText.setText(volume);

                                if (lastLevel > 2.0){
                                    freqText.setText(frequecy);
                                }






                            }
                        });
                    }
                }
            });
            thread.start();
        }// if permissionToRecord
    }

    /**
     * Functionality that gets the sound level out of the sample
     */
    private void readAudioBuffer() {

        try {
            short[] buffer = new short[bufferSize];
            int[] samples = new int[bufferSize];
            int bufferReadResult = 1;

            if (audio != null) {



                // Sense sound level
                bufferReadResult = audio.read(buffer, 0, bufferSize);
                double sumLevel = 0;
                freq = 0;

                int negInd = 0;
                int posInd = 0;

                List cross = new LinkedList();
                //System.out.print("buffer: ");
                //System.out.println(bufferReadResult);
                for (int i = 0; i < bufferReadResult; i++) {
                    System.out.println(buffer[i]);
                    if(Math.abs(buffer[i]) > sumLevel){
                        sumLevel = Math.abs(buffer[i]);
                        System.out.print("max: ");
                        System.out.println(sumLevel);
                    }
                    //sumLevel += buffer[i];
                    //System.out.println(buffer[i]);

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
                    //System.out.println(freq);
                }



                System.out.println("--------------------------------------");

                lastLevel = Math.abs((sumLevel / bufferReadResult));
                freq /= (cross.size() - 1);
                if (lastLevel > 2) {
                    System.out.println(freq);
                }

            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }//readAudioBuffer

    @Override
    protected void onPause() {
        super.onPause();
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

                /*
                int index = 0;

                for (int i = 0; i < bufferReadResult;) {
                    int low = (int) buffer[i];
                    i++;
                    int high = (int) buffer[i];
                    i++;
                    int sample = (high << 8) + (low & 0x00ff);
                    samples[index] = sample;
                    System.out.println(sample);
                    index++;
                }
                */

//System.out.println(negInd);
//System.out.print("posInd: ");
//System.out.println(posInd);
