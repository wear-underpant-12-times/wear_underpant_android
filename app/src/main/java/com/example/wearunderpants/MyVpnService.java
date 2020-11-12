package com.example.wearunderpants;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.VpnService;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import android.util.Log;
import android.widget.Toast;

import androidx.core.app.NotificationCompat;

import com.example.wearunderpants.utils.ByteUtil;
import com.example.wearunderpants.utils.LogUtil;

import org.json.JSONArray;

import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import mobile.PacketFlow;
import mobile.Mobile;

public class MyVpnService extends VpnService{
//    private static final ExecutorService EXECUTOR_SERVICE = Executors.newFixedThreadPool(5);
    private ExecutorService EXECUTOR_UDP_SERVICE = Executors.newFixedThreadPool(50);
    private static final int BUFSIZE = 4096;
    protected static final String TAG = "WearVpnService";
    public static final String VPN_ADDRESS = "10.0.0.1";
    private static final String VPN_ROUTE = "0.0.0.0";
    private static final String DNS_SERVER = "8.8.8.8";
    private ParcelFileDescriptor mInterface;
    private Future<?> deviceToTunnelFuture;
    private Future<?> tunnelToDeviceFuture;
    private Future<?> udpSendFuture;
    private String wearAddr;
    private int wearPort;
    private boolean STOP_FLAG = false;
    private String CHANNEL_ID = "CHANNEL_ID_111";
    List<String> appPackages = new ArrayList<>();
    private MyBroadcastReceiver msgReceiver;
    private LogUtil logUtil;
    @Override
    public void onCreate() {
        logUtil = new LogUtil(getExternalFilesDir("shared").getAbsolutePath()+"/wearlog.txt");
        logUtil.appendLog("hello");
        msgReceiver = new MyBroadcastReceiver();
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction("com.example.communication.RECEIVER");
        registerReceiver(msgReceiver, intentFilter);
        super.onCreate();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        Log.i(TAG, "onStartCommand1");
        EXECUTOR_UDP_SERVICE = Executors.newFixedThreadPool(50);
        createNotificationChannel();
        // 设置前台服务的参数
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this,
                0, notificationIntent, 0);
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Wear Underpants")
                .setContentText("正在运行")
                .setSmallIcon(R.drawable.ic_launcher_background)
                .setContentIntent(pendingIntent)
                .build();
        // 启动前台
        startForeground(1, notification);

//        STOP_FLAG = false;
        SharedPreferences preferences = getSharedPreferences("serverInfo",
                Activity.MODE_PRIVATE);
        wearPort = preferences.getInt("port", 8082);
        wearAddr = preferences.getString("ip", "");

        try {
            JSONArray jsonArray = new JSONArray(preferences.getString("packageList", ""));
            if (jsonArray.length() > 0) {
                appPackages.clear();
                for (int i = 0; i < jsonArray.length(); i++) {
                    appPackages.add(jsonArray.getString(i));
                }
            } else {
                appPackages.add("com.android.chrome");
                appPackages.add("com.google.android.youtube");
                appPackages.add("com.google.android.gsf.login");
                appPackages.add("com.goplaycn.googleinstall");
                appPackages.add("com.google.android.marvin.talkback");
                appPackages.add("com.google.android.instantapps.supervisor");
                appPackages.add("com.google.android.webview");
                appPackages.add("com.google.android.gms");
                appPackages.add("com.google.android.gsf");
                appPackages.add("com.google.android.gsf.login");
                appPackages.add("com.google.android.apps.translate");
                appPackages.add("org.telegram.messenger");
                appPackages.add("org.mozilla.firefox");
            }
        } catch (Exception e){
            Log.e("Vpn service", "json parse error");
        }
        Log.i(TAG, "onStartCommand2");
        configure();
        return START_NOT_STICKY;
    }

    public void stop() {
//        STOP_FLAG = true;
        System.out.println("停止service");
        if (tunnelToDeviceFuture != null)
            tunnelToDeviceFuture.cancel(true);
        if (deviceToTunnelFuture != null)
            deviceToTunnelFuture.cancel(true);
        if (udpSendFuture != null)
            udpSendFuture.cancel(true);
        EXECUTOR_UDP_SERVICE.shutdown();
//        EXECUTOR_SERVICE.shutdown();
        if (mInterface != null){
            try {
                mInterface.close();
                mInterface = null;
                Log.e(TAG,"Close fd success!");
            } catch (IOException e) {
                Log.e(TAG,"parcelFileDescriptor.close()", e);
            }
        }
        try {
            Mobile.stop();
        } catch (Exception e) {
            Log.e(TAG,"mobile stop", e);
        }
        stopForeground(true);
        stopSelf();
        System.out.println("停止service1");
    }

    @Override
    public void onDestroy() {
        Log.d("Service", "onDestroy");
        try {
            unregisterReceiver(msgReceiver);
        } catch (Exception e) {
            Log.e(TAG, "unregisterReceiver error");
        }

        super.onDestroy();
    }

    private void forwardVpnServiceToTunnel(FileDescriptor vpnFileDescriptor) {
        final FileOutputStream vpnOutput = new FileOutputStream(vpnFileDescriptor);

        Mobile.startSocks(new PacketFlow() {
            @Override
            public void writePacket(byte[] buffers) {
                try {
                    vpnOutput.write(buffers);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }, "127.0.0.1", 8083, wearAddr + ":" + Integer.toString(wearPort));
    }

    private void forwardTunnelToVpnService(FileDescriptor vpnFileDescriptor) {
        final FileInputStream vpnInput = new FileInputStream(vpnFileDescriptor);
        final FileOutputStream vpnOutput = new FileOutputStream(vpnFileDescriptor);
        int w;
        while (true) {
            w = -1;
            try {
                ByteBuffer bb = ByteBuffer.allocate(65535);
                w = vpnInput.read(bb.array());
                if (w > 0) {
                    bb.clear();
                    Packet packet = new Packet(bb, false);
                    String sIp = null;
                    if(packet.ip4Header.destinationAddress !=null)
                    {
                        sIp = packet.ip4Header.destinationAddress.getHostAddress();
                    }
                    if (packet.isTCP()) {
                        Mobile.inputPacket(bb.array());
                    } else if (packet.isUDP()) {
                        final byte[] data1 = new byte[w];
                        System.arraycopy(bb.array(), 0, data1, 0, w);
                        EXECUTOR_UDP_SERVICE.submit(new Runnable() {
                            @Override
                            public void run() {
                                sendUdp(data1, vpnOutput);
                            }
                        });
                    } else {
                        Log.i("unsupport protocol", packet.toString());
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
                logUtil.appendLog(e.toString());
                break;
            }
            if (w == -1) {
                logUtil.appendLog("w < 1");
                break;
            }
        }
    }

    private void configure()
    {
        Log.i(TAG, "Configure");
//        if (mInterface != null) {
//            Log.i(TAG, "Using the previous interface");
//            return;
//        }

        try {
            Builder builder = new Builder();
            PackageManager packageManager = getPackageManager();
            for (String appPackage: appPackages) {
                try {
                    packageManager.getPackageInfo(appPackage, 0);
                    builder.addAllowedApplication(appPackage);
//                    builder.addAllowedApplication()
                } catch (PackageManager.NameNotFoundException e) {
                    // The app isn't installed.
                }
            }
            builder.setSession("wear");
            builder.addAddress(VPN_ADDRESS, 24);
            builder.addRoute(VPN_ROUTE, 0);
            builder.setMtu(1500);//Util.tunVPN_MTU);
            builder.addDnsServer(DNS_SERVER);
//            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
//                builder.allowFamily(android.system.OsConstants.AF_INET6);
//            }

            mInterface = builder.establish();
//            protect(mInterface.getFd());
            // 处理返回包给vpn_service
            forwardVpnServiceToTunnel(mInterface.getFileDescriptor());
            new Thread(new Runnable() {
                @Override
                public void run() {
                    forwardTunnelToVpnService(mInterface.getFileDescriptor());
                }
            }).start();
        } catch (Exception e) {
            Log.e(TAG, e.toString());
            logUtil.appendLog(e.toString());
            this.onDestroy();
        }
        Log.i(TAG, "Configure1");
    }
    private DownloadBinder mBinder = new DownloadBinder();

    class DownloadBinder extends Binder {

        public void shutdown() {
            Log.d("MyService", "startDownload executed");
            stop();
//            onDestroy();
        }//在服务中自定义startDownload()方法，待会活动中调用此方法

        public int getProgress() {
            Log.d("MyService", "getProgress executed");
            return 0;
        }//在服务中自定义getProgress()方法，待会活动中调用此方法

    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }//普通服务的不同之处，onBind()方法不在打酱油，而是会返回一个实例

    public class MyBroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            //在这里写上相关的处理代码，一般来说，不要此添加过多的逻辑或者是进行任何的耗时操作
            //因为广播接收器中是不允许开启多线程的，过久的操作就会出现报错
            //因此广播接收器更多的是扮演一种打开程序其他组件的角色，比如创建一条状态栏通知，或者启动某个服务
            stop();
        }
    }

    private void sendUdp(byte[] bb, FileOutputStream vpnOutput) {
        try {
            byte[] data = new byte[bb.length-18];
            System.arraycopy(bb, 16, data, 4, 4); // addr
            System.arraycopy(bb, 22, data, 8, 2);   // port
            System.arraycopy(bb, 28, data, 10, bb.length-28);
            // 发送到udpserver
            /*
             * 向服务器端发送数据
             */
            //1.定义服务器的地址、端口号、数据
            InetAddress address = InetAddress.getByName(wearAddr);
            int port = wearPort;
            //2.创建数据报，包含发送的数据信息
            DatagramPacket spacket = new DatagramPacket(data, data.length, address, port);
            //3.创建DatagramSocket对象
            DatagramSocket socket = new DatagramSocket();
            //4.向服务器端发送数据报
            socket.send(spacket);
            /*
             * 接收服务器端响应的数据
             */
            //1.创建数据报，用于接收服务器端响应的数据
            byte[] data2 = new byte[1024];
            DatagramPacket packet2 = new DatagramPacket(data2, data2.length);
            //2.接收服务器响应的数据
            socket.receive(packet2);
            //3.读取数据
//            String reply = new String(data2, 0, packet2.getLength());
//            System.out.println("我是客户端，服务器说：" + reply);
//            byte[] decodedBytes = Base64.getDecoder().decode(reply);
//            Log.d("decode udp data", Arrays.toString(decodedBytes));
            byte[] decodedBytes = Arrays.copyOfRange(data2, 0, packet2.getLength());
            //4.关闭资源
            socket.close();
            byte[] res = new byte[decodedBytes.length + 28];
            // ip header
            System.arraycopy(bb, 0, res, 0, 12);
            System.arraycopy(bb, 12, res, 16, 4);
            System.arraycopy(bb, 16, res, 12, 4);
            // ip校验和
            res[10] = 0;
            res[11] = 0;
            // ipheader 长度
            res[2] = ByteUtil.intTo8bytes(decodedBytes.length+28)[0];
            res[3] = ByteUtil.intTo8bytes(decodedBytes.length+28)[1];
            byte[] ipHeader = new byte[20];
            System.arraycopy(res, 0, ipHeader, 0, 20);
            byte[] cn = ByteUtil.intTo8bytes(ByteUtil.calculateChecksum(ipHeader));
            res[10] = cn[0];
            res[11] = cn[1];
            // udp header
            System.arraycopy(bb, 20, res, 22, 2);
            System.arraycopy(bb, 22, res, 20, 2);
            System.arraycopy(bb, 24, res, 24, 4);
            res[24] = ByteUtil.intTo8bytes(decodedBytes.length+8)[0];
            res[25] = ByteUtil.intTo8bytes(decodedBytes.length+8)[1];
            res[26] = 0;
            res[27] = 0;
            // udp伪首部计算
            byte[] udpWdata = new byte[20 + decodedBytes.length];
            System.arraycopy(res, 12, udpWdata, 0, 8);
            udpWdata[8] = 0;
            udpWdata[9] = 17;
            System.arraycopy(res, 24, udpWdata, 10, 2);
            System.arraycopy(res, 20, udpWdata, 12, 8);
            System.arraycopy(data2, 0, udpWdata, 20, decodedBytes.length);
            cn = ByteUtil.intTo8bytes(ByteUtil.calculateChecksum(udpWdata));
            res[26] = cn[0];
            res[27] = cn[1];
            // udp data
            System.arraycopy(data2, 0, res, 28, decodedBytes.length);
            vpnOutput.write(res);
        } catch (Exception e) {
            Log.e("udp error", e.toString());
        }
    }

    private void createNotificationChannel() {
        // android8.1以上需要的设置
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID,
                    "Foreground Service Channel",
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(serviceChannel);
        }
    }
}
