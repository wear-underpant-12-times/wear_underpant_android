package com.example.wearunderpants;

import androidx.appcompat.app.AppCompatActivity;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.ResolveInfo;
import android.net.VpnService;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.List;

import mobile.Mobile;
import mobile.PacketFlow;


public class MainActivity extends AppCompatActivity {

    private static final int VPN_REQUEST_CODE = 0x0F;
    boolean START_FLAG = false;

    EditText etIp;
    EditText etPort;
//    EditText etSockPort;
//    EditText etDns;
//    SocksThread thread = new SocksThread();
    Intent vpn;
//    private MyVpnService.DownloadBinder downloadBinder;
    private final String PKGLIST_TAG = "packageList";
    private Intent intent = new Intent("com.example.communication.RECEIVER");

//    private ServiceConnection stopConnection = new ServiceConnection() {
//        //可交互的后台服务与普通服务的不同之处，就在于这个connection建立起了两者的联系
//        @Override
//        public void onServiceDisconnected(ComponentName name) {
//        }
//
//        @Override
//        public void onServiceConnected(ComponentName name, IBinder service) {
//            downloadBinder = (MyVpnService.DownloadBinder) service;
//            downloadBinder.shutdown();
////            downloadBinder.getProgress();
//        }//onServiceConnected()方法关键，在这里实现对服务的方法的调用
//    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        this.firstStartUpSetUp();
        this.init();
        final Button startBtn = (Button) findViewById(R.id.startBtn);
        final Button listPkgBtn = (Button) findViewById(R.id.listPkgBtn);
        listPkgBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startActivity(new Intent(MainActivity.this, ListPackageActivity.class));
            }
        });
        startBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                if (START_FLAG) {
                    try {
//                        bindService(vpn, stopConnection, BIND_AUTO_CREATE);
                        stopService(vpn);
                        sendBroadcast(intent);
                        Toast.makeText(MainActivity.this, "stop", Toast.LENGTH_SHORT).show();
                        startBtn.setText("start");
                        START_FLAG = false;
                    } catch (Exception e) {
                        System.out.println("stop error: " + e.toString());
                        Toast.makeText(MainActivity.this, e.toString(), Toast.LENGTH_SHORT).show();
                    }
                } else{
                    SharedPreferences sharedPreferences = getSharedPreferences("serverInfo", Context.MODE_PRIVATE);
                    SharedPreferences.Editor editor = sharedPreferences.edit();//获取编辑器
                    editor.putString("ip", etIp.getText().toString());
                    editor.putInt("port", Integer.parseInt(etPort.getText().toString()));
                    editor.apply();//提交修改
                    // vpnservice
                    Intent vpnIntent = VpnService.prepare(MainActivity.this);
                    if (vpnIntent != null) {
                        startActivityForResult(vpnIntent, VPN_REQUEST_CODE);
                    } else {
                        onActivityResult(VPN_REQUEST_CODE, RESULT_OK, null);
                    }
                    startBtn.setText("stop");
                    START_FLAG = true;
                }
            }
        });
    }

    private void firstStartUpSetUp() {
        //创建一个线程
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    if (checkIsFirstInstall()) {
                        try {
                            SharedPreferences preferences = getSharedPreferences("serverInfo",
                                    Activity.MODE_PRIVATE);
                            String[] appPackages = {
                                    "com.android.chrome",
                                    "com.google.android.youtube",
                                    "com.google.android.gsf.login",
                                    "com.goplaycn.googleinstall",
                                    "com.google.android.marvin.talkback",
                                    "com.google.android.instantapps.supervisor",
                                    "com.google.android.webview",
                                    "com.google.android.gms",
                                    "com.google.android.gsf",
                                    "com.google.android.gsf.login",
                                    "com.google.android.apps.translate",
                                    "org.telegram.messenger",
                                    "org.mozilla.firefox"
                            };
                            JSONArray jsonArray = new JSONArray(appPackages);
                            SharedPreferences.Editor editor = preferences.edit();//获取编辑器
                            editor.putString(PKGLIST_TAG, jsonArray.toString());
                            editor.apply();
                        } catch (JSONException e) {
                            Log.e("json parse error", "json parse error");
                        } catch (Exception e) {
                            Log.e("json parse error1", "json parse error1");
                        }
                    }
                }  catch  (Exception e) {
                    e.printStackTrace();
                }
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(getApplication(), "初始化完成" , Toast.LENGTH_SHORT).show();
                    }
                });

            }
        }).start();
    }

    private void init() {
        SharedPreferences preferences = getSharedPreferences("serverInfo",
                Activity.MODE_PRIVATE);
        etIp = (EditText) findViewById(R.id.etIP);
        etPort = (EditText) findViewById(R.id.etPort);
        etIp.setText(preferences.getString("ip", "127.0.0.1"));
        etPort.setText(Integer.toString(preferences.getInt("port", 8082)));
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data)
    {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == VPN_REQUEST_CODE)
        {
            if (resultCode == RESULT_OK) {
                vpn = new Intent(this, MyVpnService.class);
                startService(vpn);
//                Log.i("MyVpnService", "start");
                Toast.makeText(this, "启动成功", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "启动服务失败", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    protected void onDestroy() {
        //停止服务
        stopService(vpn);
        super.onDestroy();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            Intent home = new Intent(Intent.ACTION_MAIN);
            home.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            home.addCategory(Intent.CATEGORY_HOME);
            startActivity(home);
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    private boolean checkIsFirstInstall() {
        try {
            PackageInfo info = getPackageManager().getPackageInfo(getApplication().getPackageName(), 0);
            return info.firstInstallTime == info.lastUpdateTime;
        } catch (Exception e) {
            return true;
        }
    }
}