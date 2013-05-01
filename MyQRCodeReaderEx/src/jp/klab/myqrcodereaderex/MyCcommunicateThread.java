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

package jp.klab.myqrcodereaderex;

import java.io.UnsupportedEncodingException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.util.concurrent.CountDownLatch;

import android.os.Handler;
import android.os.Looper;
import android.os.Message;

// ライターとのネットワーク通信処理用
public class MyCcommunicateThread extends Thread {
    private static final String TAG = "QR";
    private MyActivity activity;
    private Handler handler;
    private CountDownLatch handlerInitLatch;
    private UDPRecvThread mUDPRecvThread;

    MyCcommunicateThread(MyActivity act) {
        activity = act;
        handlerInitLatch = new CountDownLatch(1);
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
        // スレッドのハンドラを MyCcommunicateHandler とする
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

//MyCcommunicateThread のハンドラ
final class MyCcommunicateHandler extends Handler {
    private static final String TAG = "QR";
    private static final int SENDPORT = 19292;
    private MyActivity activity;
    private boolean running = true;
    private MyNetUty mNetUty;
    private String mServers[] = null;
    private String mWriterAddress;

    MyCcommunicateHandler(MyActivity act) {
        activity = act;
        mNetUty = new MyNetUty(activity);
        mWriterAddress = null;
    }

    public void handleMessage(Message msg) {
        if (!running) {
            return;
        }
        switch (msg.what) {
        // Activity からライター探索指示を受信
        case R.id.search_writer:
            String bcastAddr = mNetUty.getWifiBcastAddress();
            if (bcastAddr != null) {
                //_Log.d(TAG, "MyCcommunicateHandler: bcastAddr=" + bcastAddr);
                // "QR QUERY" をブロードキャスト～ "QR HELLO" を返信したノードのアドレスを得る
                mServers = mNetUty.searchNode(bcastAddr, SENDPORT,
                        "QR QUERY", "QR HELLO", 2000, 3);
            }
            // 反応のあったノードのアドレスを Activity へ通知する
            Message.obtain(activity.getHandler(), R.id.search_writer_result,
                    mServers).sendToTarget();
            break;

        // Activity 側で決定したライターの通知
        case R.id.selected_writer:
            mWriterAddress = (String) msg.obj;
            //_Log.d(TAG, "MyCcommunicateHandler: selected_writer=[" + mWriterAddress + "]");
            break;

        // Activity から ライターへのデータ送出指示を受信
        case R.id.sendto_writer:
            if (mWriterAddress == null) {
                break;
            }
            String text = (String) msg.obj;
            int sts = mNetUty.sendData(mWriterAddress, SENDPORT, text);
            //_Log.d(TAG, "MyCcommunicateHandler: sendto_writer=[" + text + "]");
            if (sts != 0) { // error
                //_Log.d(TAG, "MyCcommunicateHandler: sendto_writer error");
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

// ライターからの UDP データ受信用
final class UDPRecvThread extends Thread {
    private static final String TAG = "QR";
    private static final int RECVPORT = 29291;
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
                _Log.e(TAG, "UDPRecvThread DatagramSocket err=" + e.toString());
                return;
            }
        }
        byte rBuf[] = new byte[64];
        DatagramPacket rPacket = new DatagramPacket(rBuf, rBuf.length);

        while (!mDoQuit) {
            try {
                //_Log.d(TAG, "UDPRecvThread: wait for recv");
                mSocket.receive(rPacket);
            } catch (Exception e) {
                _Log.i(TAG, "UDPRecvThread: recv err=" + e.toString());
                break;
            }
            // メッセージを受信したら送信元アドレスと受信データを Activity へ通知
            InetAddress ia = rPacket.getAddress();
            int len = rPacket.getLength();
            String recvStr = "";
            try {
                recvStr = new String(rBuf, 0, len, "UTF-8");
            } catch (UnsupportedEncodingException e1) {
            }
            // ライターからの "OK" が新しい QR コード表示完了の符丁
            if (recvStr.equals("OK")) {
                //_Log.d(TAG, "UDPRecvThread got 'OK'");
                Message.obtain(activity.getHandler(), R.id.writer_newpage,
                        ia.getHostAddress()).sendToTarget();
            }
        }
        //_Log.d(TAG, "UDPRecvThread exiting..");
    }
}
