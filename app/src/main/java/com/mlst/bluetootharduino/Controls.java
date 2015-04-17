package com.mlst.bluetootharduino;

import java.io.IOException;
import java.io.OutputStream;
import java.util.UUID;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.Toast;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.ToggleButton;

public class Controls extends Activity {

    private static final UUID MAGIC_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"); // magic
												 // UUID
    private static final String TAG = Main.class.getName();

    private Activity activity;
    private BluetoothAdapter bluetoothAdapter;
    private BTConnectionThread btConnectionThread;

    private ProgressDialog pd;
    private int nowDir=0;


    private boolean canceledConnect = false;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.controls);
        getActionBar().setDisplayHomeAsUpEnabled(true);
        activity = this;
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        bindJoystick();

        BluetoothDevice device = (BluetoothDevice) getIntent().getParcelableExtra("device");
        if(device != null){
            connectToBTDevice(device);
        }
        else{
            Toast.makeText(this, "請選擇裝置", Toast.LENGTH_SHORT).show();
        }
    }

    private void bindJoystick(){
        JoystickView joystick = (JoystickView)findViewById(R.id.joystick);
        joystick.setOnJoystickMoveListener(new JoystickView.OnJoystickMoveListener() {
            @Override
            public void onValueChanged(int angle, int power, int direction) {
                if(power==0 && nowDir!=0){
                    nowDir=0;
                    btConnectionThread.write(("0").getBytes());
                }
                else if(angle>=-45 && angle<=45 && nowDir!=1){//up
                    nowDir=1;
                    btConnectionThread.write(("1").getBytes());
                }
                else if(angle>=45 && angle<=135 && nowDir!=3){//right
                    nowDir=3;
                    btConnectionThread.write(("3").getBytes());
                }
                else if(angle<=-45 && angle>=-135 && nowDir!=4){//left
                    nowDir=4;
                    btConnectionThread.write(("4").getBytes());
                }
                else if(angle>=135 || angle<-135 && nowDir!=2){//down
                    nowDir=2;
                    btConnectionThread.write(("2").getBytes());
                }
            }
        },JoystickView.DEFAULT_LOOP_INTERVAL);

    }

    protected void connectToBTDevice(final BluetoothDevice device) {
        pd = new ProgressDialog(Controls.this);
        pd.setMessage("連線到藍芽裝置...");
        pd.setCancelable(true);
        pd.setOnCancelListener(new OnCancelListener() {

            @Override
            public void onCancel(DialogInterface dialog) {
                canceledConnect = true;
                if (btConnectionThread != null) {
                    btConnectionThread.cancel();
                }
                onBackPressed();
            }
        });
        pd.show();

        btConnectionThread = new BTConnectionThread(device);
        btConnectionThread.start();
    }

    class BTChannelThread extends Thread {

        private boolean keepAlive = true;
        private OutputStream outStream;
        private BluetoothSocket btSocket;

        public BTChannelThread(BluetoothSocket btSocket) {
            this.btSocket = btSocket;
            try {
                outStream = btSocket.getOutputStream();
            }
            catch (IOException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void run() {
            while (keepAlive){
                try {
                    Thread.sleep(100);                }
                catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }

        public void sendCommand(byte[] bytes) {
            try {
                outStream.write(bytes);
            }
            catch (IOException e) {
                e.printStackTrace();
                new AlertDialog.Builder(activity)
                        .setMessage("無法連線到藍芽裝置")
                        .setPositiveButton("重新連線", new OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                Intent intent = new Intent(activity,Main.class);
                                activity.startActivity(intent);
                            }
                        })
                        .setNegativeButton("關閉", null)
                        .create()
                        .show();
            }
        }

        public void cancel() {
            keepAlive = false;
            try {
                btSocket.close();
            }
            catch (IOException e) {}
        }
    }

    class BTConnectionThread extends Thread {

        private BluetoothDevice device;
        private BluetoothSocket btSocket;
        private BTChannelThread btChannelThread;

        public BTConnectionThread(BluetoothDevice device) {
            this.device = device;
        }

        @Override
        public void run() {
            if (bluetoothAdapter.isDiscovering()) {
                 bluetoothAdapter.cancelDiscovery();
            }

            try {
                btSocket = device.createRfcommSocketToServiceRecord(MAGIC_UUID);
                btChannelThread = new BTChannelThread(btSocket);
                btSocket.connect();
                if (pd != null) {
                    pd.dismiss();
                }
                btChannelThread.start();
            }
            catch (IOException e) {
                Log.e("","on catch, flag="+canceledConnect);
                pd.dismiss();
                btConnectionThread = null;
                e.printStackTrace();
                if(!canceledConnect) {
                    activity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            new AlertDialog.Builder(activity)
                                    .setMessage("無法連線到藍芽裝置 " + device.getName())
                                    .setPositiveButton("重新連線", new OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialogInterface, int i) {
                                            connectToBTDevice(device);
                                        }
                                    })
                                    .setNegativeButton("取消", new OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialogInterface, int i) {
                                            onBackPressed();
                                        }
                                    })
                                    .create()
                                    .show();
                        }
                    });
                    canceledConnect = false;
                }
                try {
                    btSocket.close();
                }
                catch (IOException e1) {
                    // supress
                }
            }

        }

        public void cancel() {
            try {
                btSocket.close();
            } catch (IOException e) {
            // supress
            }
        }

        public void write(byte[] bytes) {
            btChannelThread.sendCommand(bytes);
        }
    }

    @Override
    protected void onDestroy() {
	if (btConnectionThread != null) {
	    btConnectionThread.cancel();
	}
	super.onDestroy();
    }

    @Override
    public boolean onMenuItemSelected(int featureId, MenuItem item) {
        int id = item.getItemId();
        switch(id){
            case android.R.id.home:
                onBackPressed();
                break;
        }
        return super.onMenuItemSelected(featureId, item);
    }
}
