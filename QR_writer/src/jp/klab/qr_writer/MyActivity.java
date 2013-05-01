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

import java.util.Hashtable;
import java.util.Map;
import java.util.Random;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.Display;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

public class MyActivity extends Activity implements OnClickListener, Handler.Callback {
    private final static String TAG = "QR";
    private static final int PORT1 = 19292;
    private static final int PORT2 = 29291;
     // 一件の QR コードに格納するテキストのバイト長
    private static final int DATA_LENGTH = 200;
    // QR コードに設定するエラー修復レベル 
    // ErrorCorrectionLevel.L:7%/ M:15%/ Q:25%/ H:30%
    private static final Object ERRORCORRECTIONLEVEL = ErrorCorrectionLevel.L;

    private int mCurPage;
    private int mTotalPage;
    private Handler mHandler;
    private TextView mCounter;
    private ImageView mImageView;
    private Button mButtonNext;
    private Button mButtonTop;
    private int mSize;
    private String mKeyStr;
    private String mPeer = null;
    private MediaPlayer mPlayer = null;
    private MyCcommunicateThread mCommThread;
    private QRCodeCreator mQRCodeCreator;

    // テスト用 RSA 秘密鍵データ
    private String mTestData = "-----BEGIN RSA PRIVATE KEY-----\n"
            + "MIIEpAIBAAKCAQEAutxc0FJbM8p22rEPDV6meRbdPbHbDmumH+UAsBRU/oDJAOn3\n"
            + "3Kj1Jf5GlhiZpOZ86GzZAQWU/ZLCowUCGIZ72egxrqMXBtMlN0a2JFmQbo/Cq5rB\n"
            + "rXyl1UaPCQDn09pAK+HkZAz0LxdEQLEyQiEdbNWFGaRitRMwH1wzmBkx7mWnjWs+\n"
            + "F06+jWXrPhkgClCPiRZIT2FZYwZrSpizZ4UYUxFvXkf5y5uSG4ZWL/WbI8UVXAEO\n"
            + "S0wQcagbbNocarcFKug1codBP4JAyOeX0DnIi46FktXsPmiC1kH6qqW9RWTq0LMy\n"
            + "uPFMrFbA3G9uWyPm26MBZY1+0iwWFCEtiUhpfwIDAQABAoIBAQCkLeXmq9WyRZv0\n"
            + "PkmJZ7ZBAlPVVyWvH+pQb7cQ/mxHSQRSpz++Qz1R6n4+dtLYTiNFXA22lh74RTB6\n"
            + "Z3YV8mNzbE3qsSUUPASg8qIqgf8jBXD3sful4LUcFOir8n2+aC6l3836El+h2IGB\n"
            + "ja8o50uhSmGty/9mNbu8chhq9Qgn+uosW+u4lX6UWyRTQZgFeG5SRaZCzWfBRUCa\n"
            + "oYhOkUY4yAfHgcglP2KlKVL4UfSD0jgXx01FAbeS8qRv+VFVbKPAW4NzZnZ6jCKH\n"
            + "y3v48bSluQvJR86sRkFjyx39XpY848uaJeoMVHPf/LUu7TbpTfqVjF7o1LwlNosf\n"
            + "gXVu5wEhAoGBAPXL5cKmx+UoEcEhx98SlEL9yMR5yYQ1FvHSQ1XcEOc9uSPq2WF1\n"
            + "BUQitHoHyxx0GuVR6OvTgwX3wwARRMb1Yh0bv3krcdkemuKVlyEvFrI1bR7KLftJ\n"
            + "W/oF5iVucGuXO5sTzW70SWkXAOOR9S7c/jMUGX5Zu0X6/YOuoVvY1EApAoGBAMKe\n"
            + "JmKsjIsooGhS51kszIuFTRFqE5fnaUFdON9RdJRXP7Kolp42ByYydKgtg8Et19sL\n"
            + "QwO107VV4frPvsNfsseL/hU+XsN32AHcXNUueLdAYgBr5mM7ibjB7yar+50ntdeT\n"
            + "fb1/nBtL7Fd/5+IMRIn/5a5OxKPeqmeVN+GfHPFnAoGAEFZRGzMCrlSPeYrJQiZ1\n"
            + "/E3p/kUqA8OTltcm6poJ9ZTArYZ6lGO2yeSUolfKREXjU8Kx/Jq+ZrMlHugG7kJ5\n"
            + "Fv41J9SfaJMEMHNI7Ee49ndenWFK1Rz3JBMoTOyeREh1CcWzLeDG0FlbEcUtysOd\n"
            + "kb+QQjmsnOYl247L4tLyqKECgYBRL34dzZM3ffQkcOkXdyvAzBPRPun7hUqQPN47\n"
            + "spQqSZdF5TQnvawP0B6ABbSfwor4UmNbSd+OFsyVP1J52BMRUSHmJMWNTxIp/I4x\n"
            + "VBiBgXcga+KI5M5X53bnL/lfnrApxNpAUdqCPJLUEYRe9PUmiDx9EVjQb1OwsDvG\n"
            + "gASn4wKBgQC9jbKKXgWPtaGVSxSLkGJrOwAm2tUvWTe1GFrCkA3BcKP5/LzUwWgb\n"
            + "ry6rgFYRhv+3SdiMB1ECJZe54PndA6VQxsuYBkiGwumgmQ5jVu6A4fOkSY0hm9Xc\n"
            + "6ntrzwcpp3RO8w3l55HYRxkCi7L4udVxiWIhKF/B31rXkdMu6tTsQA==\n"
            + "-----END RSA PRIVATE KEY-----\n";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mImageView = (ImageView) findViewById(R.id.imageView1);
        mCounter = (TextView) findViewById(R.id.counter);
        mButtonNext = (Button) findViewById(R.id.buttonNext);
        mButtonTop = (Button) findViewById(R.id.buttonTop);
        mButtonNext.setOnClickListener(this);
        mButtonTop.setOnClickListener(this);
        mHandler = new Handler(this);
        // SE 再生用
        mPlayer = MediaPlayer.create(this, R.raw.se);
        mPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer mp) {
            }
        });
        mPlayer.setOnSeekCompleteListener(new MediaPlayer.OnSeekCompleteListener(){
            @Override
            public void onSeekComplete(MediaPlayer mp) {
            }
        });
        // QR コード生成用
        mQRCodeCreator = new QRCodeCreator("UTF-8", ERRORCORRECTIONLEVEL);
        // 生成する QR コードイメージの一辺の長さ
        WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        Display disp = wm.getDefaultDisplay();
        mSize = Math.min(disp.getWidth(), disp.getHeight());
        // 総 QR コード数
        mTotalPage = mTestData.length() / DATA_LENGTH + 1;
        // 最初の QR コードを表示
        mCurPage = 1;
        showQRCode();
        // リーダーとの通信用スレッドを開始
        mCommThread = new MyCcommunicateThread(this);
        mCommThread.start();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mCommThread != null) {
            // 通信用スレッドを終了させる
            Message msg = Message.obtain(mCommThread.getHandler(), R.id.quit);
            msg.sendToTarget();
            try {
                mCommThread.join(500L);
            } catch (InterruptedException e) {
            }
            mCommThread = null;
        }
        if (mPlayer != null) {
            mPlayer.release();
            mPlayer = null;
        }
    }

    @Override
    public void onClick(View v) {
        // 次のコードを表示
        if (v == (View) mButtonNext) {
            if (++mCurPage > mTotalPage) {
                mCurPage = mTotalPage;
                return;
            }
            showQRCode();
            mPlayer.start(); // SE を鳴らす
            // リーダーと応酬中なら ACK を送出
            if (mPeer != null) {
                String data[] = new String[2];
                data[0] = mPeer;
                data[1] = "OK";
                Message m = Message.obtain(mCommThread.getHandler(), R.id.sendto_reader, data);
                m.arg1 = PORT2;
                m.sendToTarget();
            }
        }
        // 先頭のコードを表示
        else if (v == (View) mButtonTop) {
            if (mCurPage != 1) {
                mCurPage = 1;
                showQRCode();
            }
        }
    }

    @Override
    public boolean handleMessage(Message msg) {
        switch (msg.what) {
        // リーダーからメッセージを受信
        case R.id.data_coming:
            String data[] = (String[]) msg.obj;
            String peer = data[0];
            String str = data[1];
            // _Log.d(TAG, "handleMessage: data_coming peer=" + peer + " str=" + str + " mKeyStr=" + mKeyStr);
            // ライター探索用ブロードキャストメッセージ への応答
            if (str.equals("QR QUERY")) {
                for (int i = 0; i < 3; i++) {
                    data[1] = "QR HELLO";
                    Message m = Message.obtain(mCommThread.getHandler(), R.id.sendto_reader, data);
                    m.arg1 = PORT1;
                    m.sendToTarget();
                }
            }
            // リーダーが正しいキーを提示の場合のみ次のページを表示
            else if (str.equals(mKeyStr)) {
                mPeer = peer;
                onClick((View) mButtonNext);
            }
            break;
        }
        return true;
    }

    public Handler getHandler() {
        return mHandler;
    }

    // QR コードを表示
    private void showQRCode() {
        if (mCurPage > mTotalPage || mCurPage < 1) {
            return;
        }
        // "（現ページ/総ページ数)" を表示
        String pageCount = String.format("(%02d/%02d)", mCurPage, mTotalPage);
        mCounter.setText(pageCount);
        // ヘッダ情報を生成
        mKeyStr = createRandomString(4); // ページキーとして英4文字のランダム文字列を使用
        String strHead = String.format("(%02d/%02d:%s)", mCurPage, mTotalPage, mKeyStr);
        String text;
        // エンコード対象とする文字列
        int pos = (mCurPage - 1) * DATA_LENGTH;
        try {
            text = mTestData.substring(pos, pos + DATA_LENGTH);
        } catch (Exception e) {
            text = mTestData.substring(pos);
        }
        // _Log.d(TAG, "showQRCode: curPage=" + mCurPage + " totalPage=" + mTotalPage + " text=[" + text + "]");
        // コード生成～表示
        Bitmap bmp = null;
        try {
            bmp = mQRCodeCreator.create(strHead + text, mSize);
        } catch (Exception e) {
            showDialogMessage("QR コードの生成に失敗しました。終了します", true);
        }
        mImageView.setImageBitmap(bmp);
    }

    // 指定長のランダム文字列を返す
    public static String createRandomString(int length) {
        String str = "";
        Random r = new Random();
        for (int i = 0; i < length;) {
            int n = r.nextInt(126);
            // A-Z a-z のみ
            if (!((n >= 0x41 && n <= 0x5A) || (n >= 0x61 && n <= 0x7A))) {
                continue;
            }
            str += (char) n; // chr(n)
            i++;
        }
        return str;
    }
    
    // ダイアログメッセージを表示
    private void showDialogMessage(String msg, final boolean bFinish) {
        new AlertDialog.Builder(this).setTitle(R.string.app_name)
                .setIcon(R.drawable.ic_launcher).setMessage(msg)
                .setCancelable(false)
                .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                        if (bFinish) {
                            finish();
                        }
                    }
                }).show();
    }
}

// QR コード生成用
final class QRCodeCreator {
    private Map<EncodeHintType, Object> mHint;

    public QRCodeCreator(String charSet, Object errorCorrectionLevel) {
        mHint = new Hashtable<EncodeHintType, Object>();
        mHint.put(EncodeHintType.CHARACTER_SET, charSet);
        // エラー修復レベル ErrorCorrectionLevel.L:7%/ M:15%/ Q:25%/ H:30%
        mHint.put(EncodeHintType.ERROR_CORRECTION, errorCorrectionLevel);
    }

    // 指定されたテキストをの QR コードビットマップを返す
    public Bitmap create(String text, int size) throws Exception {
        // zxing ライブラリでテキストを QR コードデータにエンコード
        QRCodeWriter writer = new QRCodeWriter();
        BitMatrix bm = writer.encode(text, BarcodeFormat.QR_CODE, size, size,
                mHint);
        int w = bm.getWidth();
        int h = bm.getHeight();
        // 取得したマトリックスデータを走査しピクセルごとに色情報を設定
        int[] pixels = new int[w * h];
        for (int y = 0; y < h; y++) {
            int currentY_startIdx = y * w;
            for (int x = 0; x < w; x++) {
                if (bm.get(x, y) == true) {
                    pixels[currentY_startIdx + x] = Color.BLACK;
                } else {
                    pixels[currentY_startIdx + x] = Color.WHITE;
                }
            }
        }
        // 16bit ビットマップにして返す
        Bitmap bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.RGB_565);
        bitmap.setPixels(pixels, 0, w, 0, 0, w, h);
        return bitmap;
    }
}
