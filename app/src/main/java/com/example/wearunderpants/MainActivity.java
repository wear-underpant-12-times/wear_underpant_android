package com.example.wearunderpants;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.net.VpnService;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import mobile.Mobile;
import mobile.PacketFlow;


public class MainActivity extends AppCompatActivity {

    private static final int VPN_REQUEST_CODE = 0x0F;
    boolean START_FLAG = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        final Button startBtn = (Button) findViewById(R.id.startBtn);
        final MyThread thread = new MyThread();
        startBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (START_FLAG) {
                    thread.interrupt();
                    Toast.makeText(MainActivity.this, "内裤被扒了", Toast.LENGTH_SHORT).show();
                    startBtn.setText("start");
                } else{
                    thread.start();
                    Toast.makeText(MainActivity.this, "穿上内裤了", Toast.LENGTH_SHORT).show();
                    startBtn.setText("stop");
                }
                Intent vpnIntent = VpnService.prepare(MainActivity.this);
                if (vpnIntent != null)
                {
                    startActivityForResult(vpnIntent, VPN_REQUEST_CODE);
                }
                else
                {
                    onActivityResult(VPN_REQUEST_CODE, RESULT_OK, null);
                }
            }
        });

//        startService(new Intent(this, MyVpnService.class));


    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data)
    {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == VPN_REQUEST_CODE)
        {
            if (resultCode == RESULT_OK) {
//            startService(new Intent(this, MyVpnService.class));
                final Intent vpn = new Intent(getApplicationContext(), MyVpnService.class);
                getApplicationContext().startService(vpn);
                Log.i("MyVpnService", "start");
                Toast.makeText(this, "穿上内裤了", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "内裤没穿上", Toast.LENGTH_SHORT).show();
            }
        }
    }

}

class MyThread extends Thread {

    public void run(){
        Log.i("socks server", "start");
        Mobile.startServer("127.0.0.1:8083", "23.106.157.33:8085");
    }
}