package com.example.zhuji.applemessage;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.net.Uri;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.telephony.SmsManager;
import android.telephony.SmsMessage;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.StringReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.text.SimpleDateFormat;
import java.util.Date;

public class MainActivity extends Activity {
    ServerSocket serverSocket ;
    String phoneNum="";
    String messge="";
    String log="";
    Handler handler = new Handler(){
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            switch (msg.what){
                case 1:
                    send2(phoneNum,messge);
                    break;
                case 2:

                    EditText editText = (EditText)findViewById(R.id.ET_Log);
                    editText.setMovementMethod(ScrollingMovementMethod.getInstance());
                    editText.setSelection(editText.getText().length(), editText.getText().length());
                    editText.getText().append("\n"+log);
                    break;

                default:
                    break;
            }
        }
    };
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        sentPI = PendingIntent.getBroadcast(this, 0, sentIntent, 0);
        deliverPI = PendingIntent.getBroadcast(this, 0, deliverIntent, 0);
        showIP();


        Button button = (Button)findViewById(R.id.button);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

              showIP();
            }
        });

        startThread();

    }


    public void showIP()
    {
        WifiManager wifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
        //判断wifi是否开启
        if (!wifiManager.isWifiEnabled()) {
            wifiManager.setWifiEnabled(true);
        }
        WifiInfo wifiInfo = wifiManager.getConnectionInfo();
        int ipAddress = wifiInfo.getIpAddress();
        String ip = intToIp(ipAddress);
        TextView ipaddress = (TextView)findViewById(R.id.TV_IP);
        ipaddress.setText("本机IP地址： "+ip);
    }
    private String intToIp(int i) {

        return (i & 0xFF ) + "." +
                ((i >> 8 ) & 0xFF) + "." +
                ((i >> 16 ) & 0xFF) + "." +
                ( i >> 24 & 0xFF) ;
    }

    @Override
    protected void onResume() {
        super.onResume();

        this.registerReceiver(sentPIReceive, new IntentFilter(SENT_SMS_ACTION));
        this.registerReceiver(deliverPIReceive, new IntentFilter(DELIVERED_SMS_ACTION));
        this.registerReceiver(SMSReceive, new IntentFilter("android.provider.Telephony.SMS_RECEIVED"));

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        this.unregisterReceiver(sentPIReceive);
        this.unregisterReceiver(deliverPIReceive);
        this.unregisterReceiver(SMSReceive);
        try {
            serverSocket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    public static final String SMS_URI_INBOX = "content://sms/inbox";
    public String getSmsInfo() {

        String[] projection = new String[] { "_id", "address", "person", "body", "date", "type"};
        Cursor cusor = this.managedQuery(Uri.parse(SMS_URI_INBOX), projection, null, null, "date desc");

        int nameColumn = cusor.getColumnIndex("person");
        int phoneNumberColumn = cusor.getColumnIndex("address");
        int smsbodyColumn = cusor.getColumnIndex("body");
        int dateColumn = cusor.getColumnIndex("date");
        int typeColumn = cusor.getColumnIndex("type");
        String res = "";
        if (cusor != null) {
            while (cusor.moveToNext()) {
                if (!cusor.getString(phoneNumberColumn).contains(phoneNum.substring(3)))
                    continue;

                SmsInfo smsinfo = new SmsInfo();
                smsinfo.setName(cusor.getString(nameColumn));
                smsinfo.setDate(cusor.getString(dateColumn));
                smsinfo.setPhoneNumber(cusor.getString(phoneNumberColumn));
                smsinfo.setSmsbody(cusor.getString(smsbodyColumn));
                smsinfo.setType(cusor.getString(typeColumn));
                Log.e("################>", smsinfo.toString());
                res =smsinfo.getDate()+" "+ smsinfo.getSmsbody();
                break;
            }

            if(Build.VERSION.SDK_INT < 14) {
                cusor.close();
            }

        }
        return  res;
    }

    String SENT_SMS_ACTION = "SENT_SMS_ACTION";
    String DELIVERED_SMS_ACTION = "DELIVERED_SMS_ACTION";

    Intent sentIntent = new Intent(SENT_SMS_ACTION);
    PendingIntent sentPI=null;

    Intent deliverIntent = new Intent(DELIVERED_SMS_ACTION);
    PendingIntent deliverPI=null;

    private void send2(String phoneNumber, String message){
        showLog("====>", "start sendMessage");
        SmsManager sms = SmsManager.getDefault();
        sms.sendTextMessage(phoneNumber, null, message, sentPI, deliverPI);
        showLog("====>", "end sendMessage");
    }

    BroadcastReceiver deliverPIReceive =  new BroadcastReceiver() {
        @Override
        public void onReceive(Context _context, Intent _intent) {
            showLog("====>", "收信人已经成功接收");
        }
    };

    BroadcastReceiver sentPIReceive = new BroadcastReceiver() {
        @Override
        public void onReceive(Context _context, Intent _intent) {
            switch (getResultCode()) {
                case Activity.RESULT_OK:
                    showLog("====>", "短信发送成功");
                    break;
                case SmsManager.RESULT_ERROR_GENERIC_FAILURE:
                    break;
                case SmsManager.RESULT_ERROR_RADIO_OFF:
                    break;
                case SmsManager.RESULT_ERROR_NULL_PDU:
                    break;
            }
        }
    };

    BroadcastReceiver SMSReceive = new BroadcastReceiver()
    {
        @Override
        public void onReceive(Context context, Intent intent) {
            Object [] pdus= (Object[]) intent.getExtras().get("pdus");
            for(Object pdu:pdus){
                SmsMessage smsMessage=SmsMessage.createFromPdu((byte [])pdu);
                String sender=smsMessage.getDisplayOriginatingAddress();
                String content=smsMessage.getMessageBody();
                long date=smsMessage.getTimestampMillis();
                Date timeDate=new Date(date);
                SimpleDateFormat simpleDateFormat=new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                String time=simpleDateFormat.format(timeDate);

                showLog("=====>", "短信来自:" + sender);
                showLog("=====>", "短信内容:" + content);
                showLog("=====>", "短信时间:" + time);

            }
        }
    };


    public void startThread(){
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    serverSocket = new ServerSocket(8080);
                    while (true)
                    {
                        final Socket socket  = serverSocket.accept();
                        showLog("--------->", "new socket connection");

                        Thread thread1 = new Thread(new Runnable() {
                            @Override
                            public void run()
                            {
                                showLog("--------->", "new socket connection start run");
                                try
                                {
                                    BufferedReader  reader = new BufferedReader(new InputStreamReader(socket.getInputStream())) ;
                                    BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
                                    String string ="";

                                    while(true)
                                    {
                                        string = reader.readLine();
                                        if (string != null){
                                            showLog("--------->", "received command "+string);
                                            if (string.contains("bye")){
                                                socket.close();
                                                break;
                                            }

                                            if (string.contains("phone")){

                                                phoneNum = string.replace("phone:","");
                                                phoneNum.replace("\n", "");
                                                writer.write("ok : " + phoneNum);
                                            }

                                            if (string.contains("message:")){

                                                messge = string.replace("message:","");
                                                messge.replace("\n","");
                                                writer.write("ok : " + messge);

                                            }

                                            if (string.contains("send")){

                                                handler.sendEmptyMessage(1);
                                                writer.write("ok : send");
                                            }

                                            if (string.contains("get")){


                                                writer.write("ok :" + getSmsInfo());
                                            }

                                            writer.flush();

                                            writer.write("ok : " +string);
                                            writer.flush();
                                                /*if (string.contains("pphoneNum")){

                                                }*/
                                        }
                                    }
                                }
                                catch (IOException e)
                                {
                                    e.printStackTrace();
                                }
                            }
                        });

                        thread1.start();

                    }
                }
                catch (IOException e)
                {
                    e.printStackTrace();
                }

            }
        });

        thread.start();
    }

    public void showLog(String o,String Log){
        Date timeDate=new Date();
        SimpleDateFormat simpleDateFormat=new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        String time=simpleDateFormat.format(timeDate);
        log=time+"\n"+Log+"\n--------------------------------";
        handler.sendEmptyMessage(2);
    }
}
