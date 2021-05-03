package tw.com.bte.launcher4;

import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.net.rtp.AudioStream;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;

public class TestModeActivity extends AppCompatActivity implements MediaPlayer.OnCompletionListener {
    private RelativeLayout ll_systeminfo;
    private LinearLayout ll_center;
    private TextView txtVersion, tv_center;
    private char KEY_START = 0, KEY_TABLE=0, FLIPPER_L=0, FLIPPER_R=0, KEY_LAUNCH=0;

    private final static int STEP_SCREEN_TEST_1 = 0;
    private final static int STEP_SCREEN_TEST_2 = 1;
    private final static int STEP_SCREEN_TEST_3 = 2;
    private final static int STEP_SCREEN_TEST_4 = 3;
    private final static int STEP_SCREEN_TEST_5 = 4;
    private final static int STEP_TEST_AUDIO_LEFT = 5;

    private InputStream mInputStream;
    private OutputStream mOutputStream;
    private SerialPort serialPort;
    private ReadThread mReadThread;
    private QueryKeyThread mQueryKeyThread;
    private boolean bQuitQueryKey = false;
    private byte[] keybuffer = new byte[64];
    private byte[] KeyStatus = new byte[14];
    private KeyHandler keyHandler = new KeyHandler();

    private int TEST_STEP = 0;
    private MediaPlayer mediaPlayer = new MediaPlayer();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_test_mode);
        UI_Init();
        FullScreen();
        Uart_Init();
        mQueryKeyThread = new QueryKeyThread();
        mQueryKeyThread.start();
    }

    private void Uart_Init(){
        try {
            serialPort = new SerialPort(new File("/dev/ttyS1"), 115200, 0);
            mInputStream = serialPort.getInputStream();
            mOutputStream = serialPort.getOutputStream();
            mReadThread = new ReadThread();
            mReadThread.start();
        }catch (Exception e){
            e.printStackTrace();
        }
    }

    private void CloseUart() {
        bQuitQueryKey = true;
        try{
            Thread.sleep(10);
        } catch (Exception e){
            e.printStackTrace();
        }

        if(serialPort != null){
            serialPort.close();
            serialPort = null;
        }
    }

    private class ReadThread extends Thread{
        @Override
        public void run() {
            super.run();
            while(!isInterrupted()){
                int size;
                try {
                    if (mInputStream == null) return;
                    size = mInputStream.read(keybuffer);
                    if ((keybuffer[0] == (byte) 0xA7) && keybuffer[1] == 0x10) {
                        keyHandler.sendEmptyMessage(0);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    return;
                }
            }
        }
    }

    private class KeyHandler extends Handler{
        @Override
        public void handleMessage(Message msg) {
            KeyStatus[0] = (byte)((keybuffer[4] >> 5)& 0x01); //Flipper L
            KeyStatus[1] = (byte) ((keybuffer[9] >> 5) & 0x01); //Flipper R
            KeyStatus[2] = (byte)(keybuffer[5] & 0x01); // Key Up
            KeyStatus[3] = (byte) ((keybuffer[5] >> 1) & 0x01); //Key Down
            KeyStatus[4] = (byte) ((keybuffer[5] >> 2) & 0x01); //Key Left
            KeyStatus[5] = (byte) ((keybuffer[5] >> 3) & 0x01); //Key Right
            KeyStatus[6] = (byte) ((keybuffer[5] >> 4) & 0x01); //Key C
            KeyStatus[7] = (byte) ((keybuffer[5] >> 5) & 0x01); //Key ESC
            KeyStatus[8] = (byte) ((keybuffer[8] >> 5) & 0x01); //Key VolDown
            KeyStatus[9] = (byte) (keybuffer[9] & 0x01); //Key Enter
            KeyStatus[10] = (byte) ((keybuffer[9] >> 1) & 0x01); //Key Thumber
            KeyStatus[11] = (byte) ((keybuffer[9] >> 2) & 0x01); //Key P
            KeyStatus[12] = (byte) ((keybuffer[9] >> 3) & 0x01); //Key Volup

            if(KeyStatus[9] == 1){
                KEY_LAUNCH = 1;
            } else {
                if(KEY_LAUNCH == 1){
                    ProcessStep();
                }
            }

            super.handleMessage(msg);
        }
    }

    private class QueryKeyThread extends Thread {
        private final byte[] data = new byte[]{(byte) 0xA6, 0x01, 0x00};
        @Override
        public void run() {
            while(!bQuitQueryKey){
                try{
                    mOutputStream.write(data);
                    Thread.sleep(10);
                } catch (Exception e){
                    e.printStackTrace();
                }
            }
        }
    }

    //process step
    private void ProcessStep(){
        switch (TEST_STEP){
            case STEP_SCREEN_TEST_1:
                //set screen red
                ll_center.setVisibility(View.GONE);
                ll_systeminfo.setBackgroundColor(getColor(R.color.colorRed));
                TEST_STEP ++;
                break;
            case STEP_SCREEN_TEST_2:
                ll_systeminfo.setBackgroundColor(getColor(R.color.colorGreen));
                TEST_STEP ++;
                break;
            case STEP_SCREEN_TEST_3:
                ll_systeminfo.setBackgroundColor(getColor(R.color.colorBlue));
                TEST_STEP ++;
                break;
            case STEP_SCREEN_TEST_4:
                ll_systeminfo.setBackgroundColor(getColor(R.color.colorWhite));
                TEST_STEP ++;
                break;
            case STEP_SCREEN_TEST_5:
                ll_systeminfo.setBackgroundColor(getColor(R.color.colorBlack));
                TEST_STEP ++;
                break;
            case STEP_TEST_AUDIO_LEFT:
                Uri mp3 = Uri.parse("android.resource://" + this.getPackageName() + "/raw/audio_left_1k.mp3");
                PlayAudio(mp3);
                break;
        }
    }

    private void PlayAudio(Uri uri){
        try {
            mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
            mediaPlayer.setDataSource(this, uri);
            mediaPlayer.prepare();
            mediaPlayer.setLooping(true);
            mediaPlayer.start();
            mediaPlayer.setOnCompletionListener(this);
        }catch (Exception e){
            e.printStackTrace();
        }
    }

    @Override
    public void onCompletion(MediaPlayer mp) {

    }

    private void UI_Init(){
        ll_systeminfo = (RelativeLayout) findViewById(R.id.ll_systeminfo);
        txtVersion = (TextView) findViewById(R.id.txtVersion);

        ll_center = (LinearLayout) findViewById(R.id.ll_center);
        tv_center = (TextView) findViewById(R.id.tv_center);
    }

    private void FullScreen() {
        View viewDecoder = getWindow().getDecorView();
        int uiOptions = View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                // Set the content to appear under the system bars so that the
                // content doesn't resize when the system bars hide and show.
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                // Hide the nav bar and status bar
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_FULLSCREEN;
        viewDecoder.setSystemUiVisibility(uiOptions);
    }
}