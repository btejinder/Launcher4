package tw.com.bte.launcher4;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Application;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.media.MediaPlayer;
import android.media.SyncParams;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Parcel;
import android.os.RecoverySystem;
import android.os.RemoteException;
import android.os.storage.StorageManager;
import android.util.Log;
import android.view.Display;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.MediaController;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;
import android.os.storage.StorageVolume;
import static android.os.storage.StorageVolume.EXTRA_STORAGE_VOLUME;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class MainActivity extends Activity implements RecoverySystem.ProgressListener{

    //private final static String GAME_ID = "com.zenstudios.williamspinball";
    //private final static String GAME_ID = "com.zenstudios.marvelpinball";
    //private final static String GAME_ID = "com.zenstudios.starwarspinball";
    private final static String TAG = "LAUNCHER";
    private final static String GAME_ID_WILLIAMS = "com.zenstudios.williamspinball";
    private final static String GAME_ID_STARTWARS = "com.zenstudios.starwarspinball";
    private final static String GAME_ID_MARVEL = "com.zenstudios.marvelpinball";
    private PackageManager mPackageManger;
    private boolean bGameInstalled =false;

    private byte[] appInstalled = new byte[]{0, 0, 0};
    private byte[] keybuffer = new byte[64];
    private byte[] KeyStatus = new byte[14];

    private InputStream mInputStream;
    private OutputStream mOutputStream;
    private ReadThread mReadThread;
    private QueryKeyThread mQueryKeyThread;
    private HandlerKeyEvent mHandler;
    private SerialPort serialPort;
    private boolean bQuitQueryKey = false;
    private int select_index = 0;

    private GridView appLists;
    private final static String[] GAME_ID = new String[]{GAME_ID_WILLIAMS, GAME_ID_STARTWARS, GAME_ID_MARVEL};
    private final static String[] OTA_UPDATE_FILES = new String[]{"pinball_update_wms.zip", "pinball_update_swp.zip", "pinball_update_mvp.zip"};
    private ArrayList<InfoApp> appInstalledList = new ArrayList<InfoApp>();

    private boolean KEY_LEFT, KEY_RIGHT, KEY_ENTER, KEY_PAUSE;
    private AppAdapter mAdapter;

    private VideoView videoView;
    private MediaController mediaController;
    private RelativeLayout ll_videoview;

    private SamplePresentation mPresentation;

    private int BOOT_MODE = 0;
    private int GAME_INDEX = 0; //0: wms 1:swp 2:mvp when BOOT_MODE=0
    private String BOOT_GAME_ID = GAME_ID[GAME_INDEX];
    private final static String OTA_FILE_NAME = "pinball_update.zip";

    private static final int BOOT_RECOVERY = 3;
    private static final int BOOT_DESKTOP = 2;
    private static final int BOOT_GAME = 1;
    private static final int BOOT_TEST_MODE = 4;

    private int[] videoId = new int[176];
    private ImageView imageView;

    private StorageManager manager;
    private List<StorageVolume> localDevicesList;
    private boolean bCheckUpdate = false;

    private static final String PACKAGE_PATH = "/cache/update.zip";
    private static final String LAST_INSTALL_LOG_PATH = "/cache/recovery/last_install";
    private static final String USB_PACKAGE_PATH = "/mnt/media_rw/([^\\s]+)";
    private static String REAL_USB_PACKAGE_PATH = null;
    private static final int RECOVERY_PROGRESS = 1;

    public String updateUSBPath;//U盘中的update.zip路径
    int total_length = 0;//update.zip文件长度

    File updateZipF = null;//从U盘拷贝到flash的升级包
    File updateZipU = null;//u盘中的升级包

    private Dialog alertDialog;
    private ProgressDialog mProgressDialog;
    Context mContext;
    private int wait_till_uart_start = 0;
    private int boot_once = 0;
    private LinearLayout ll_installapps;
    private boolean bTestAppInstall = false;
    private String WMS_VERSION = "", SWP_VERSION = "", MVP_VERSION = "", SYS_VERSION = "";

    private ProcHanler procHandler = new ProcHanler();

    private class ProcHanler extends Handler{
        @Override
        public void handleMessage(Message msg) {
            switch(msg.what){
                case BOOT_GAME:
                    BootOnGame();
                    break;
                case BOOT_DESKTOP:
                    BootOnDesktop();
                    break;
                case BOOT_RECOVERY:
                    BootOnRecovery();
                    break;
                case BOOT_TEST_MODE:
                    BootOnTestMode();
                    break;
            }
            super.handleMessage(msg);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mPackageManger = this.getPackageManager();
        setContentView(R.layout.activity_main);
        mContext = this;
        FullScreen();
        ParseSetting("system/etc/pinball.cfg");

        String u_ota_file_name = OTA_FILE_NAME;
        ll_installapps = (LinearLayout) findViewById(R.id.ll_installapps);
        if(BOOT_MODE == 1) u_ota_file_name = "pinball_update_desktop.zip";

        ll_videoview = (RelativeLayout) findViewById(R.id.ll_videoview);
        manager = (StorageManager) this.getSystemService(Context.STORAGE_SERVICE);
        localDevicesList = manager.getStorageVolumes();
        StorageVolume storageVolume;
        bCheckUpdate = false;
        for(int i = 0; i < localDevicesList.size(); i ++){
            storageVolume = localDevicesList.get(i);
            String descr = storageVolume.getDescription(this);
            if(descr.contains("USB")){
                //String path = "/mnt/media_rw/2749-3F94" + storageVolume.getUuid();
                String path = "/storage/" + storageVolume.getUuid();
                File file = new File(path);
                if(file.isDirectory() && file.canRead()) {
                    File[] lists = file.listFiles();
                    for(int j =0; j < lists.length; j ++){
                        Log.d("Launcher5", "filename: " + lists[j].getName());
                        if(lists[j].getName().toLowerCase().contains(u_ota_file_name)){
                            bCheckUpdate = true;
                            updateUSBPath = lists[j].getAbsolutePath();
                            break;
                        }
                    }
                }
            }

            if(bCheckUpdate){
                break;
            }
        }

        Uart_Init();
        mHandler = new HandlerKeyEvent();
        mQueryKeyThread = new QueryKeyThread();
        mQueryKeyThread.start();
/*
        for(int i = 0; i < 176; i++){
            String res_name = String.format("intro%03d", i+1);
            videoId[i] = this.getResources().getIdentifier(res_name,"drawable", getPackageName());
        }

        imageView = findViewById(R.id.imageView);
*/
/*
        mMediaRouter = MediaRouter.getInstance(this);
        mMediaRouter.addCallback(
                new MediaRouteSelector.Builder()
                        .addControlCategory(MediaControlIntent.CATEGORY_LIVE_VIDEO)
                        .build(),
                mMediaRouterCallback
        );

        videoView= findViewById(R.id.videoView);
        mediaController = new MediaController(this);
        videoView.setMediaController(mediaController);
        LoadVideoView(getMP4Uri());
 */


    }

    private void CheckSum(){
        try{
            Process p = Runtime.getRuntime().exec("cksum /dev/block/by-name/system > /private/checksum.txt");
            BufferedReader in = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String line = in.readLine();
            Log.d(TAG, "CheckSum Result: " + line);
        } catch (Exception e){
            e.printStackTrace();
        }
    }

    private void ParseSetting(String settingpath){
        File config = new File(settingpath);
        if(!config.canRead()) return;
        try{
            BufferedReader bufferedReader = new BufferedReader(new FileReader(config));
            String line;
            while((line = bufferedReader.readLine()) != null ){
                if(!line.startsWith(";")){
                    if(line.contains("boot_mode")){
                        String[] aryField = line.split("=");
                        BOOT_MODE = Integer.parseInt(aryField[1].trim());
                        Log.d(TAG, "boot_mode is " + Integer.parseInt(aryField[1].trim()));
                    }

                    if(line.contains("boot_game")){
                        String[] aryField = line.split("=");
                        GAME_INDEX = Integer.parseInt(aryField[1].trim());
                        BOOT_GAME_ID = GAME_ID[GAME_INDEX];
                        //Log.d(TAG, "boot_game is " + Integer.parseInt(aryField[1].trim()));
                    }

                    if(line.contains("wms_version")){
                        String[] aryField = line.split("=");
                        WMS_VERSION = aryField[1].trim();
                    }

                    if(line.contains("swp_version")){
                        String[] aryField = line.split("=");
                        SWP_VERSION = aryField[1].trim();
                    }

                    if(line.contains("mvp_version")){
                        String[] aryField = line.split("=");
                        MVP_VERSION = aryField[1].trim();
                    }

                    if(line.contains("system_version")){
                        String[] aryField = line.split("=");
                        SYS_VERSION = aryField[1].trim();
                    }
                }
            }
        } catch (Exception e){
            e.printStackTrace();
        }
    }

    private void BootOnRecovery(){
        Log.d(TAG, "BootOnRecovery");
        new MyTask().execute(100);
    }

    private void BootOnGame(){
        Log.d(TAG, "BootOnGame");
        if(!isPackageInstalled(BOOT_GAME_ID, mPackageManger)){
            ll_installapps.setVisibility(View.VISIBLE);
            thread.start();
        } else {
            LauncherApp("ZenPinball", BOOT_GAME_ID);
        }
    }

    private void BootOnDesktop(){
        Log.d(TAG, "BootOnDesktop");
        appLists = (GridView) findViewById(R.id.appLists);
        mAdapter = new AppAdapter();
        appLists.setAdapter(mAdapter);
        ll_videoview.setVisibility(View.GONE);

        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_PACKAGE_ADDED);
        filter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        filter.addAction(Intent.ACTION_PACKAGE_FULLY_REMOVED);
        filter.addAction(Intent.ACTION_BOOT_COMPLETED);
        filter.addDataScheme("package");
        registerReceiver(mBroadcastReciever, filter);

        GetInstalledList();
    }

    private void BootOnTestMode(){

        if(isPackageInstalled("tw.com.bte.test", mPackageManger) && isPackageInstalled(BOOT_GAME_ID, mPackageManger)){
            startTestActivity();
        } else {
            ll_installapps.setVisibility(View.VISIBLE);
            new Thread(new Runnable() {
                @Override
                public void run() {
                    while(!bTestAppInstall){
                        if(isPackageInstalled("tw.com.bte.test", mPackageManger) && isPackageInstalled(BOOT_GAME_ID, mPackageManger)){
                            bTestAppInstall = true;
                        }

                        try{
                            Thread.sleep(100);
                        } catch (Exception e){
                            e.printStackTrace();
                        }
                    }

                    startTestActivity();
                }
            }).start();
        }
    }

    private void FactoryReset(){
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage("Do you want to reset to factory default?");

        Intent resetIntent = new Intent("android.intent.action.MASTER_CLEAR");
        sendBroadcast(resetIntent);
    }

    private void startTestActivity(){
        Intent intent = getPackageManager().getLaunchIntentForPackage("tw.com.bte.test");
        Bundle bundle = new Bundle();
        bundle.putInt("GAME_INDEX", GAME_INDEX);
        if(GAME_INDEX == 0){
            bundle.putString("GAME_VERSION", WMS_VERSION);
        } else if(GAME_INDEX == 1){
            bundle.putString("GAME_VERSION", SWP_VERSION);
        } else if(GAME_INDEX == 2){
            bundle.putString("GAME_VERSION", MVP_VERSION);
        }

        bundle.putString("GAME_VERSION_NAME", getApplicationName(BOOT_GAME_ID));
        bundle.putString("SYS_VERSION", SYS_VERSION);
        intent.putExtras(bundle);
        startActivity(intent);
    }

    private final DialogInterface.OnDismissListener mOnDismissListener =
            new DialogInterface.OnDismissListener() {
                @Override
                public void onDismiss(DialogInterface dialog) {
                    if (dialog == mPresentation) {
                        mPresentation = null;
                    }
                }
            };

    private void GetInstalledList(){
        appInstalledList.clear();
        final PackageManager pm = getPackageManager();
        List<ApplicationInfo> packages = pm.getInstalledApplications(PackageManager.GET_META_DATA);
        for (ApplicationInfo packageInfo : packages) {
            if(packageInfo.packageName.contains("com.zenstudios") || (packageInfo.packageName.contains("tw.com.bte") && !packageInfo.packageName.contains("launcher4"))){
                InfoApp item = new InfoApp();
                item.app_id = packageInfo.packageName;
                item.app_name = getApplicationName(packageInfo.packageName);
                item.app_icon = getApplicationIcon(packageInfo.packageName);
                appInstalledList.add(item);
            }
        }
        mAdapter.notifyDataSetChanged();
    }

    private void UpdateInstalledList(){
        appInstalledList.clear();
        for(int i = 0; i < GAME_ID.length; i ++){
            if(isPackageInstalled(GAME_ID[i], mPackageManger)){
                InfoApp item = new InfoApp();
                item.app_id = GAME_ID[i];
                item.app_name = getApplicationName(GAME_ID[i]);
                item.app_icon = getApplicationIcon(GAME_ID[i]);
                appInstalledList.add(item);
            }
        }

        mAdapter.notifyDataSetChanged();
    }

    @Override
    protected void onPause() {
        super.onPause();
        CloseUart();

        try{
            Thread.sleep(100);
        } catch (Exception e){
            e.printStackTrace();
        }
        Log.d("APP", "onPause");
        System.exit(1);

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

    @Override
    protected void onDestroy() {
        Log.d("APP", "onDestroy");
        if(BOOT_MODE == 1) {
            CloseUart();
            unregisterReceiver(mBroadcastReciever);
            super.onDestroy();
        }
    }

    private BroadcastReceiver mBroadcastReciever = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if(BOOT_MODE == 1) {
                Log.d("App", "Receiver install package!!");
                String action = intent.getAction();

                select_index = 0;
                GetInstalledList();
            }
        }
    };

    private BroadcastReceiver mBroadcastRecieverBoot = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            LoadVideoView(getMP4Uri());
        }
    };

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        Log.d("KEY", "onKey press:" + keyCode);
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onResume() {
        super.onResume();

    }

    void LauncherApp(String app_name, String bundle_id){
        try{
            Intent intent = this.getPackageManager().getLaunchIntentForPackage(bundle_id);
            startActivity(intent);
        } catch (Exception e){
            e.printStackTrace();
        }
    }

    private boolean isPackageInstalled(String packageName, PackageManager packageManager) {
        try {
            packageManager.getPackageInfo(packageName, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    private Drawable getApplicationIcon(String packagename){
        Drawable app_icon = null;
        try {
            app_icon = this.getPackageManager().getApplicationIcon(packagename);
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }

        return app_icon;
    }

    private String getApplicationName(String packagename){
        String app_name = "";
        try{
            PackageInfo info = mPackageManger.getPackageInfo(packagename, PackageManager.GET_META_DATA);
            app_name = info.applicationInfo.loadLabel(mPackageManger).toString();
        } catch (Exception e){
            e.printStackTrace();
        }

        return app_name;
    }

    private String getApplicationVersion(String packagename){
        String app_version = "";
        try{
            PackageInfo info = mPackageManger.getPackageInfo(packagename, PackageManager.GET_META_DATA);
            app_version = info.applicationInfo.loadLabel(mPackageManger).toString();
        } catch (Exception e){
            e.printStackTrace();
        }

        return app_version;
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
                | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.KEEP_SCREEN_ON;
        viewDecoder.setSystemUiVisibility(uiOptions);
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
                        mHandler.sendEmptyMessage(0);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    return;
                }
            }
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

    private class HandlerKeyEvent extends Handler {
        @Override
        public void handleMessage(Message msg) {
            //Log.d("KEY", String.format("%02X, %02X", keybuffer[4] & 0x20, keybuffer[9] & 0x20));
            /*
            int volumevalue = (byte)(keybuffer[2]);
            if(volumevalue == 0){
                Log.d("KEY", "Volume -------------");
            } else if(volumevalue == 2) {
                Log.d("KEY", "Volume +++++++++++++");
            }
             */

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

            if(KeyStatus[0] == 1){
                KEY_LEFT = true;
            } else {
                if(KEY_LEFT){
                    //Left button press
                    if(select_index > 0) select_index --;
                    else select_index = appInstalledList.size() -1;
                    mAdapter.notifyDataSetChanged();
                }
                KEY_LEFT = false;
            }

            if(KeyStatus[1] == 1){
                KEY_RIGHT = true;
            } else {
                if(KEY_RIGHT){
                    //Right button press
                    if(select_index == (appInstalledList.size() -1)) select_index = 0;
                    else select_index ++;
                    mAdapter.notifyDataSetChanged();
                }
                KEY_RIGHT = false;
            }

            if(KeyStatus[4] == 1){
                KEY_PAUSE = true;
            } else {
                KEY_PAUSE = false;
            }

            if(KeyStatus[9] == 1) {
                KEY_ENTER = true;
            } else {
                if(KEY_ENTER && BOOT_MODE == 1) {
                    //Enter press
                    CloseUart();
                    InfoApp app = appInstalledList.get(select_index);
                    LauncherApp(app.app_name, app.app_id);
                }
                KEY_ENTER = false;
            }

            //Log.d(TAG, String.format("KeyStatus: %d %d %d", KeyStatus[0], KeyStatus[1], KeyStatus[9]));

            if(boot_once == 0){
                boot_once = 1;
                if(KEY_PAUSE && KEY_LEFT && bCheckUpdate){
                    CloseUart();
                    Message procmsg = new Message();
                    procmsg.what = BOOT_RECOVERY;
                    procHandler.sendMessage(procmsg);

                } else if(KEY_ENTER == true && KEY_RIGHT == true && KEY_PAUSE){
                    CloseUart();
                    Message procmsg = new Message();
                    procmsg.what = BOOT_TEST_MODE;
                    procHandler.sendMessage(procmsg);
                } else {
                    if(BOOT_MODE == 0){
                        CloseUart();
                        Message procmsg = new Message();
                        procmsg.what = BOOT_GAME;
                        procHandler.sendMessage(procmsg);
                    } else {
                        Message procmsg = new Message();
                        procmsg.what = BOOT_DESKTOP;
                        procHandler.sendMessage(procmsg);
                    }
                }
            }
        }
    }

    private  class AppAdapter extends BaseAdapter{
        @Override
        public int getCount() {
            return appInstalledList.size();
        }

        @Override
        public Object getItem(int position) {
            return null;
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View cellView = convertView;
            if(cellView == null){
                cellView = getLayoutInflater().inflate(R.layout.cell_appicon, null);
            }

            if(select_index == position){
                cellView.setBackgroundResource(R.color.colorForcusApp);
            } else {
                cellView.setBackgroundResource(R.color.colorPrimaryDark);
            }

            final InfoApp  item = appInstalledList.get(position);
            ImageView app_icon = (ImageView) cellView.findViewById(R.id.app_icon);
            TextView app_name = (TextView) cellView.findViewById(R.id.app_name);
            app_icon.setImageDrawable(item.app_icon);
            app_name.setText(item.app_name);

            cellView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    CloseUart();
                    LauncherApp(item.app_name, item.app_id);
                }
            });

            return cellView;
        }
    }

    private void LoadVideoView(Uri uri){
        videoView.setVideoURI(uri);
        videoView.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
            @Override
            public void onPrepared(MediaPlayer mp) {
                mp.setLooping(false);
                mp.start();
                mediaController.hide();
            }
        });

        videoView.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer mp) {
                if(BOOT_MODE == 0){
                    if(isPackageInstalled(BOOT_GAME_ID, mPackageManger)){
                        videoView.setVisibility(View.GONE);
                        LauncherApp("ZenPinball", BOOT_GAME_ID);
                    }
                } else {
                    ll_videoview.setVisibility(View.GONE);
                }
            }
        });

        videoView.start();
    }

    private Uri getMP4Uri(){
        return Uri.parse("android.resource://" + getPackageName() +"/"+R.raw.boot_animate);
    }

    private Thread thread = new Thread(new Runnable() {
        @Override
        public void run() {
            while(!bGameInstalled){
                if(isPackageInstalled(BOOT_GAME_ID, mPackageManger)){
                    bGameInstalled = true;
                } else {
                    Log.d("GAME", "Waitting");
                }

                try{
                    Thread.sleep(500);
                } catch (Exception e){
                    e.printStackTrace();
                }
            }
            //videoView.setVisibility(View.GONE);
            LauncherApp("ZenPinball", BOOT_GAME_ID);
        }
    });

    class MyTask extends AsyncTask<Integer, Integer, String> {
        @Override
        protected String doInBackground(Integer... params){
            Log.e(TAG, "start do in back ground");
            int proLen = 100;
            InputStream is = null;
            OutputStream os = null;

            updateZipF = new File(PACKAGE_PATH);
            if(updateZipF.exists()) {
                updateZipF.delete();
            }

            updateZipU = new File(updateUSBPath);
            if(updateZipU != null){
                if(updateZipU.exists() && updateZipU.canRead()){
                    Log.d(TAG, "copy file.");
                    try{
                        is = new FileInputStream(updateZipU);
                        os = new FileOutputStream(updateZipF);
                        byte[] buffer = new byte[1024];
                        total_length = is.available();
                        int length = 0;
                        int sum = 0;
                        while((length = is.read(buffer)) > 0){
                            sum = sum + length;
                            os.write(buffer, 0 ,length);
                            if((sum / 1024 % 5) == 0){
                                float percent = (float)(sum)/total_length;
                                int count = (int)(percent * proLen);
                                publishProgress(count);
                            }
                        }
                    }
                    catch(IOException e){
                        e.printStackTrace();
                    }
                    finally{
                        try{
                            if(is != null){
                                is.close();
                            }
                            if(os != null){
                                os.close();
                            }
                        }
                        catch(IOException e){
                            e.printStackTrace();
                        }
                    }
                }
            }
            else{
                Log.e(TAG, "the updateU is null");
            }
            return "Task compleged";
        }
        @Override
        protected void onPostExecute(String result){
            if(!updateZipF.exists() || !updateZipF.canRead()) {
                Dialog alertDialog = new AlertDialog.Builder(mContext)
                        .setTitle("Error")
                        .setMessage("File reading error." + updateZipF.exists() + updateZipF.canRead())
                        .setPositiveButton(
                                "OK",
                                new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog,
                                                        int which) {
                                    }
                                }).create();
                alertDialog.show();
                mProgressDialog.dismiss();
                return;
            }

            Log.i(TAG, "/cache/update.zip ret = " + updateZipF.exists() + " ,can read? = " + updateZipF.canRead());
            Log.i(TAG, "USB update.zip ret = " + updateZipU.exists() + " ,can read? = " + updateZipU.canRead());

            //mProgressDialog.setProgress(100);
            mProgressDialog.dismiss();
            if(updateZipF.length() == total_length) {
                //拷贝文件成功，进行ota升级
                if(getZipFileProductionNo("/cache/update.zip") != GAME_INDEX){
                    Toast.makeText(mContext, "Update file not compatible with this version.", Toast.LENGTH_LONG).show();
                    new Handler().postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            BootOnGame();
                        }
                    }, 3000);
                } else {
                    if(startOTAUpgrade() == false){
                        Toast.makeText(mContext, "Upgrade System fail!!", Toast.LENGTH_LONG).show();
                        finish();
                    }
                }
            }else {
                Toast.makeText(mContext, "Fail to copy file into system!!", Toast.LENGTH_LONG).show();
                finish();
            }
        }

        @Override
        protected void onPreExecute(){
            //初始化拷贝update.zip对话框
            mProgressDialog = new ProgressDialog(mContext);
            mProgressDialog.setTitle("System Upgrade");
            mProgressDialog.setMessage("Copy file to system...");
            mProgressDialog.setCancelable(false);
            mProgressDialog.setMax(100);
            mProgressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
            mProgressDialog.show();
        }
        @Override
        protected void onProgressUpdate(Integer... values){
            mProgressDialog.setProgress(values[0]);
        }
    }

    private int getZipFileProductionNo(String zipFile){
        byte[] buf = new byte[256];
        int result = -1;
        try {
            FileInputStream fileInputStream = new FileInputStream(zipFile);
            ZipInputStream zin = new ZipInputStream(fileInputStream);
            try{
                ZipEntry ze = null;
                while((ze = zin.getNextEntry()) != null){
                    String filename = ze.getName();
                    if(filename.contains("pinball.cfg")){
                        int fsize = zin.read(buf);
                        String str = new String(buf);
                        String[] lines = str.split("\n");
                        result = parserArrayString(lines);
                        Log.d("ZIP", "GAME Index: " + result);
                        break;
                    }

                    zin.closeEntry();
                }
            } catch (Exception e){
                e.printStackTrace();
            }finally {
                zin.close();
            }
        }catch (Exception e){
            e.printStackTrace();
        }

        return result;
    }

    private int parserArrayString(String[] lines){
        int result  = -1;
        for(int i = 0; i < lines.length; i++){
            if(lines[i].contains("boot_game")){
                String[] fields = lines[i].split("=");
                if(fields.length > 0){
                    result = Integer.parseInt(fields[1].trim());
                    break;
                }
            }
        }

        return result;
    }

    private Handler myHandler = new Handler() {
        public void handleMessage(Message msg) {
            switch (msg.what) {
                //handle message
            }
            super.handleMessage(msg);
        }
    };

    private boolean startOTAUpgrade() {
        File OTAPackage = null;
        OTAPackage = new File(PACKAGE_PATH);
        if (OTAPackage.exists()) {
            try {
                Log.e(TAG, "start to verifyPackage and reboot recovery!!!");
                RecoverySystem.verifyPackage(OTAPackage, this, null);
                RecoverySystem.installPackage(this, OTAPackage);
                Toast.makeText(this, "Recovery install package done.", Toast.LENGTH_LONG).show();
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
                return false;
            } catch (GeneralSecurityException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
                return false;
            }
            return true;
        } else {
            Log.e(TAG, "OTA package does not exist");
            return false;
        }
    }

    @Override
    public void onProgress(int progress) {
        Message message = new Message();
        message.what = RECOVERY_PROGRESS;
        message.arg1 = progress;
        myHandler.sendMessage(message);
    }
}
