/*
 * Copyright 2016 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.cloudwalk.audiodummy;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Environment;
import android.view.View;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Sample that demonstrates how to record a device's microphone using {@link AudioRecord}.
 */
public class AudioRecordActivity extends AppCompatActivity {

    private static final int SAMPLING_RATE_IN_HZ = 16000;

    private static final int CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO;

    private static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;

    private static final int PERMISSION_VD_REQUEST_CODE = 0x00000004;

    /**
     * Factor by that the minimum buffer size is multiplied. The bigger the factor is the less
     * likely it is that samples will be dropped, but more memory will be used. The minimum buffer
     * size is determined by {@link AudioRecord#getMinBufferSize(int, int, int)} and depends on the
     * recording settings.
     */
    private static final int BUFFER_SIZE_FACTOR = 2;

    /**
     * Size of the buffer where the audio data is stored by Android
     */
    private static final int BUFFER_SIZE = AudioRecord.getMinBufferSize(SAMPLING_RATE_IN_HZ,
            CHANNEL_CONFIG, AUDIO_FORMAT) * BUFFER_SIZE_FACTOR;

    /**
     * Signals whether a recording is in progress (true) or not (false).
     */
    private final AtomicBoolean recordingInProgress = new AtomicBoolean(false);

    private AudioRecord recorder = null;

    private Thread recordingThread = null;

    private Button startButton;

    private Button stopButton;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.audio);


        AudioManager audioManager = (AudioManager) this.getSystemService(Context.AUDIO_SERVICE);
        audioManager.setSpeakerphoneOn(true);

//        boolean isSpeakerphoneOn = audioManager.isSpeakerphoneOn();
//        if (!isSpeakerphoneOn)
//        {
//            audioManager.abandonAudioFocus(null);
//            audioManager.setMode(AudioManager.MODE_NORMAL);
//            audioManager.setSpeakerphoneOn(false);
//        }
//        else
//        {
//            audioManager.requestAudioFocus(null, AudioManager.STREAM_VOICE_CALL, AudioManager.AUDIOFOCUS_GAIN);
//            audioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);
//            audioManager.setSpeakerphoneOn(true);
//        }


        startButton = (Button) findViewById(R.id.btnStart);
        startButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startRecording();
                startButton.setEnabled(false);
                stopButton.setEnabled(true);
            }
        });

        stopButton = (Button) findViewById(R.id.btnStop);
        stopButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stopRecording();
                startButton.setEnabled(true);
                stopButton.setEnabled(false);
            }
        });
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CAMERA,
                            Manifest.permission.RECORD_AUDIO,
                            Manifest.permission.READ_EXTERNAL_STORAGE,
                            Manifest.permission.WRITE_EXTERNAL_STORAGE
                    },
                    PERMISSION_VD_REQUEST_CODE);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        startButton.setEnabled(true);
        stopButton.setEnabled(false);
    }

    @Override
    protected void onPause() {
        super.onPause();

        stopRecording();
    }

    @SuppressLint("MissingPermission")
    private void startRecording() {
        recorder = new AudioRecord(MediaRecorder.AudioSource.VOICE_COMMUNICATION, SAMPLING_RATE_IN_HZ,
                CHANNEL_CONFIG, AUDIO_FORMAT, BUFFER_SIZE);

        recorder.startRecording();

        recordingInProgress.set(true);

        recordingThread = new Thread(new RecordingRunnable(), "Recording Thread");
        recordingThread.start();
    }

    private void stopRecording() {
        if (null == recorder) {
            return;
        }

        recordingInProgress.set(false);

        recorder.stop();

        recorder.release();

        recorder = null;

        recordingThread = null;
    }

    private class RecordingRunnable implements Runnable {

        @Override
        public void run() {
            final File folder = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).getAbsolutePath()
                    + "/cloudwalk");
            File file = null;
            try {
                file = File.createTempFile("recording_", ".pcm", folder);
            } catch (IOException e) {
                e.printStackTrace();
            }
            final ByteBuffer buffer = ByteBuffer.allocateDirect(BUFFER_SIZE);

            try (final FileOutputStream outStream = new FileOutputStream(file)) {
                while (recordingInProgress.get()) {
                    int result = recorder.read(buffer, BUFFER_SIZE);
                    if (result < 0) {
                        throw new RuntimeException("Reading of audio buffer failed: " +
                                getBufferReadFailureReason(result));
                    }
                    outStream.write(buffer.array(), 0, BUFFER_SIZE);
                    buffer.clear();
                }
            } catch (IOException e) {
                throw new RuntimeException("Writing of recorded audio failed", e);
            }
        }

        private String getBufferReadFailureReason(int errorCode) {
            switch (errorCode) {
                case AudioRecord.ERROR_INVALID_OPERATION:
                    return "ERROR_INVALID_OPERATION";
                case AudioRecord.ERROR_BAD_VALUE:
                    return "ERROR_BAD_VALUE";
                case AudioRecord.ERROR_DEAD_OBJECT:
                    return "ERROR_DEAD_OBJECT";
                case AudioRecord.ERROR:
                    return "ERROR";
                default:
                    return "Unknown (" + errorCode + ")";
            }
        }
    }
}
