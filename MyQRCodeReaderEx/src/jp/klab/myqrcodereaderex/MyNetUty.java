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
import java.net.InetSocketAddress;
import java.util.HashSet;
import java.util.Iterator;

import android.content.Context;
import android.net.DhcpInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;

public class MyNetUty {
    private static final String TAG = "QR";
    private static Context mContext = null;

    public MyNetUty(Context ctx) {
        mContext = ctx;
    }

    private String addrIntToStr(int addr) {
        return (addr & 0xFF) + "." + ((addr >> 8) & 0xFF) + "."
                + ((addr >> 16) & 0xFF) + "." + ((addr >> 24) & 0xFF);
    }

    // WI-FI ネットワーク上の自アドレスを得る
    public String getWifiMyAddress() {
        WifiManager wm = (WifiManager) mContext
                .getSystemService(mContext.WIFI_SERVICE);
        WifiInfo wi = wm.getConnectionInfo();
        int ip = wi.getIpAddress();
        return addrIntToStr(ip);
    }

    // WI-FI ネットワークのブロードキャストアドレスを得る
    public String getWifiBcastAddress() {
        WifiManager wm = (WifiManager) mContext
                .getSystemService(mContext.WIFI_SERVICE);
        DhcpInfo di = wm.getDhcpInfo();
        if (di == null) {
            return null;
        }
        int bcast = (di.ipAddress & di.netmask) | ~di.netmask;
        return addrIntToStr(bcast);
    }

    // UDP データ送信
    public int sendData(String targetAddr, int port, String data) {
        InetSocketAddress isa = new InetSocketAddress(targetAddr, port);
        byte[] buf = data.getBytes();
        DatagramPacket packet = null;
        try {
            packet = new DatagramPacket(buf, buf.length, isa);
            new DatagramSocket().send(packet);
        } catch (Exception e) {
            return -1;
        }
        return 0;
    }

    // 所定のメッセージをブロードキャストし期待する応答を返したノードのアドレス群を返す
    // broadCastAddr, port: ブロードキャストアドレス, ポート
    // queryString: ブロードキャストするメッセージ文字列
    // expectAnswer: 期待する応答メッセージ文字列
    // msecTimeOut: 返信待ちタイムアウト msec
    // retryCount: 返信なしの場合のリトライ回数
    public String[] searchNode(String broadCastAddr, int port, String queryString,
            String expectAnswer, int msecTimeOut, int retryCount) {
        InetSocketAddress isa;
        HashSet<String> serverHash;
        DatagramPacket packet;
        DatagramSocket socket;

        serverHash = new HashSet<String>();
        isa = new InetSocketAddress(broadCastAddr, port);
        try {
            socket = new DatagramSocket(port);
            socket.setReuseAddress(true);
            socket.setSoTimeout(msecTimeOut);
        } catch (Exception e) {
            _Log.e(TAG, "searchNode: DatagramSocket err=+" + e.toString());
            return null;
        }
        byte rBuf[] = new byte[1024];
        DatagramPacket rPacket = new DatagramPacket(rBuf, rBuf.length);

        for (int i = 0; i < retryCount; i++) {
            byte[] query = queryString.getBytes();
            try {
                packet = new DatagramPacket(query, query.length, isa);
                new DatagramSocket().send(packet); // ブロードキャスト
            } catch (Exception e) {
                _Log.e(TAG, "searchNode: DatagramPacket err=" + e.toString());
                continue;
            }
            // 応答を収集
            while (true) {
                try {
                    socket.receive(rPacket);
                } catch (Exception e) {
                    _Log.i(TAG, "searchNode: recv err=" + e.toString());
                    break;
                }
                int len = rPacket.getLength();
                String recvData = "";
                try {
                    recvData = new String(rPacket.getData(), 0, len, "UTF-8");
                } catch (UnsupportedEncodingException e) {
                }
                InetAddress ia = rPacket.getAddress();
                //_Log.d(TAG, "searchNode: recv [" + rData + "] from: " + ia.getHostAddress());
                // 期待する応答と一致すれば送信元アドレスをコピー
                if (recvData.equals(expectAnswer)) {
                    serverHash.add(ia.getHostAddress());
                }
            }
            // 一件でも応答があれば抜ける
            if (serverHash.size() > 0) {
                break;
            }
        }
        socket.close();
        
        // 応答のあったノードのアドレスを文字列配列で返す
        String[] servers = null;
        int num = serverHash.size();
        if (num > 0) {
            Iterator it = serverHash.iterator();
            servers = new String[num];
            int cnt = 0;
            while (it.hasNext()) {
                servers[cnt++] = (String) it.next();
            }
        }
        return servers;
    }
}
