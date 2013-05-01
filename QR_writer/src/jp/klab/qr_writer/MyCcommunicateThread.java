/*
 * Copyright (C) 2013 KLab Inc.
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

package jp.klab.qr_writer;

import java.io.UnsupportedEncodingException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.util.concurrent.CountDownLatch;

import android.os.Handler;
import android.os.Looper;
import android.os.Message;

// リーダーとのネットワーク通信処理用
public class MyCcommunicateThread extends Thread {
    private static final String TAG = "QR";
    MyActivity activity;
    private Handler handler;
    private CountDownLatch handlerInitLatch;
    private UDPRecvThread mUDPRecvThread;

    MyCcommunicateThread(MyActivity act) {
        activity = act;
        handlerInitLatch = new CountDownLatch(1);
        // UDP 受信スレッドを開始
        mUDPRecvThread = new UDPRecvThread(activity);
        mUDPRecvThread.start();
    }

    Handler getHandler() {
        try {
            handlerInitLatch.await(); // countDown() 待ち
        } catch (InterruptedException ie) {
        }
        return handler;
    }

    @Override
    public void run() {
        Looper.prepare();
        // スレッドのハンドラを  MyCcommunicateHandler とする
        handler = new MyCcommunicateHandler(activity);
        handlerInitLatch.countDown();
        Looper.loop();
        //_Log.d(TAG, "MyCcommunicateThread Looper done");
        
        // UDP 受信スレッドを終了させる
        mUDPRecvThread.doQuit();
        try {
            mUDPRecvThread.join(500L);
        } catch (InterruptedException e) {
        }
    }    
}

// MyCcommunicateThread のハンドラ
final class MyCcommunicateHandler extends Handler {
    private static final String TAG = "QR";
    private boolean running = true;
    private MyActivity activity;

    MyCcommunicateHandler(MyActivity act) {
        activity = act;
    }
    
    public void handleMessage(Message msg) {
        if (!running) {
            return;
        }
        switch (msg.what) {
        // Activity からデータ送出指示を受信
        case R.id.sendto_reader:
            String data[] = (String[])msg.obj; // アドレス, データ
            int port = msg.arg1;
            //_Log.d(TAG, "MyCcommunicateHandler sendto data[0]=" + data[0] + " data[1]="+ data[1]);
               InetSocketAddress isa = new InetSocketAddress(data[0], port);
               byte[] text = data[1].getBytes();
               DatagramPacket packet = null;
               try {
                   packet = new DatagramPacket(text, text.length, isa);
                   new DatagramSocket().send(packet);
               } catch (Exception e) {
                   _Log.e(TAG, "MyCcommunicateHandler: sendto_reader err=" + e.toString());
               }
               break;
               
           // スレッド終了指示
        case R.id.quit:
            //_Log.d(TAG, "MyCcommunicateHandler exiting..");
            running = false;
            Looper.myLooper().quit();
            break;
        }
    }
}

//UDP データ受信用
final class UDPRecvThread extends Thread {
    private static final String TAG = "QR";
    private static final int RECVPORT = 19292;
    private MyActivity activity;
    private boolean mDoQuit = false;
    private DatagramSocket mSocket = null;

    UDPRecvThread(MyActivity act) {
        activity = act;
    }
    
    // UDP データ受信スレッドを終了させる
    public void doQuit() {
        if (mSocket != null) {
            mSocket.close(); // ブロッキングを解除
        }
        mDoQuit = true;
    }
    
    // UDP データ受信スレッド
    @Override
    public void run() {
        if (mSocket == null) {
            try {
                mSocket = new DatagramSocket(RECVPORT);
            } catch (SocketException e) {
                _Log.e(TAG, "UDPRecvThread: DatagramSocket err=" + e.toString());
                return;
            }
        }
        byte rBuf[] = new byte[64];
        DatagramPacket dPacket = new DatagramPacket(rBuf, rBuf.length);
        
        while (!mDoQuit) {
            try {
                //_Log.d(TAG, "UDPRecvThread: wait for recv");
                mSocket.receive(dPacket);
            } catch (Exception e) {
                _Log.i(TAG, "UDPRecvThread: recv err=" + e.toString());
                break;
            }
            // メッセージを受信したら送信元アドレスと受信データを Activity へ通知
            InetAddress ia = dPacket.getAddress();
            int len = dPacket.getLength();
            String recvStr = "";
            try {
                recvStr = new String(rBuf, 0, len, "UTF-8");
            } catch (UnsupportedEncodingException e1) {
            }
            if (recvStr.length() > 0) {
                _Log.d(TAG, "UDPRecvThread: recv data=[" + recvStr + "] len=" + recvStr.length());
                String data[] = new String[2];
                data[0] = ia.getHostAddress(); 
                data[1] = recvStr;
                Message.obtain(activity.getHandler(), R.id.data_coming, data).sendToTarget();
            }
        }
        //_Log.d(TAG, "UDPRecvThread exiting..");
    }
}
