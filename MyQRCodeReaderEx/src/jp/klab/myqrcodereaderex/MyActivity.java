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

import java.io.IOException;
import java.util.Timer;
import java.util.TimerTask;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.hardware.Camera;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Toast;

import com.google.zxing.PlanarYUVLuminanceSource;

public class MyActivity extends Activity implements SurfaceHolder.Callback,
        Handler.Callback, Camera.PreviewCallback {

    private static final String TAG = "QR";
    // 連続 QR コードのデータ先頭の (NN/NN:AAAA) 形式のヘッダ長
    private static int HEADER_LENGTH = 12;
    private MyDecodeThread mDecodeThread = null;
    private MyCameraConfigurationManager mConfigManager;
    private MyFinderView mFinderView;
    private SurfaceView mSurfaceView;
    private Handler mHandler = null;
    private Camera mCamera = null;
    private Boolean mHasSurface;
    private Timer mTimerFocus;
    private MyCcommunicateThread mCommThread = null;
    private ProgressDialog mProgressDlg;
    private String mWriterAddress;
    private String mReadData = "";
    private int mPrevPage = 0;
    private boolean mActive = true;
    
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // フルスクリーンかつタイトル表示無し
        this.getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        this.requestWindowFeature(Window.FEATURE_NO_TITLE);
        mHasSurface = false;
        setContentView(R.layout.activity_main);
    }

    @Override
    protected void onResume() {
        //_Log.d(TAG, "MyActivity: onResume");
        super.onResume();
        mConfigManager = new MyCameraConfigurationManager(this);
        mFinderView = (MyFinderView) findViewById(R.id.finderView);
        mSurfaceView = (SurfaceView) findViewById(R.id.preview_view);
        if (mHandler == null) {
            mHandler = new Handler(this);
            // 通信用スレッドを開始
            mCommThread = new MyCcommunicateThread(this);
            mCommThread.start();
            Message.obtain(mCommThread.getHandler(), R.id.search_writer).sendToTarget();
            mProgressDlg = new ProgressDialog(this);
            mProgressDlg.setMessage("しばらくお待ち下さい");
            mProgressDlg.setCancelable(true);
            mProgressDlg.setProgressStyle(ProgressDialog.STYLE_SPINNER);
            mProgressDlg.setOnCancelListener(new DialogInterface.OnCancelListener() {
                public void onCancel(DialogInterface dialog) {          
                }});     
            mProgressDlg.show();

        }
        SurfaceHolder holder = mSurfaceView.getHolder();
        if (mHasSurface) {
            // surfaceCreated() ずみで surfaceDestroyed() が未了の状況
            try {
                openCamera(holder);
            } catch (IOException e) {
                Message.obtain(mHandler, R.id.error).sendToTarget();
            }
        } else {
            holder.addCallback(this);
            holder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
        }
    }

    @Override
    protected void onPause() {
        //_Log.d(TAG, "MyActivity: onPause");
        super.onPause();
        stopIt();
        finish();
    }

    @Override
    // SurfaceHolder.Callback
    public void surfaceCreated(SurfaceHolder holder) {
        if (!mHasSurface) {
            mHasSurface = true;
            try {
                openCamera(holder);
            } catch (IOException e) {
                Message.obtain(mHandler, R.id.error).sendToTarget();
            }
        }
    }

    @Override
    // SurfaceHolder.Callback
    public void surfaceDestroyed(SurfaceHolder holder) {
        mHasSurface = false;
    }

    @Override
    // SurfaceHolder.Callback
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
    }

    @Override
    // Handler.Callback
    public boolean handleMessage(Message msg) {
        switch (msg.what) {

        case R.id.error:
            showDialogMessage("エラーが発生しました", true);
            break;

        // ライター探索結果
        case R.id.search_writer_result:
            if (mProgressDlg != null) {
                mProgressDlg.dismiss();
                mProgressDlg = null;
            }
            String writers[] = (String[]) msg.obj;
            if (writers == null) {
                //_Log.d(TAG, "no servers found..");
                new AlertDialog.Builder(this).setTitle(R.string.app_name)
                .setIcon(R.drawable.ic_launcher).setMessage("QR コードライターが見つかりませんでした。非通信モードで開始しますか？")
                .setCancelable(false)
                .setPositiveButton("はい", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                        if (mCommThread != null) {
                            // 通信スレッドへ終了を指示
                            Message msg = Message.obtain(mCommThread.getHandler(), R.id.quit);
                            msg.sendToTarget();
                            try {
                                mCommThread.join(500L);
                            } catch (InterruptedException e) {
                            }
                            mCommThread = null;
                        }
                    }
                })
                .setNegativeButton("いいえ", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                        finish();
                    }
                }).show();
            } else {
                for (int i = 0; i < writers.length; i++) {
                    _Log.d(TAG, "MyActivity: found writer=[" + writers[i] + "]");
                }
                // とりあえず先頭のノードをライターとして扱う
                mWriterAddress = writers[0];
                Toast.makeText(this, "[ " + mWriterAddress + "] の QR コードライタ ーを見つけました", Toast.LENGTH_SHORT).show();
                Message.obtain(mCommThread.getHandler(), R.id.selected_writer, mWriterAddress).sendToTarget();
            }            
            // QR コード認識用スレッドを開始
            mDecodeThread = new MyDecodeThread(this, new MyResultPointCallback(mFinderView));
            mDecodeThread.start();
            if (mCamera != null) {
                mCamera.setOneShotPreviewCallback(this);
            }
            break;
        
        case R.id.decode_succeeded: // 認識 OK
            if (!mActive) {
                break;
            }
            String strQRCodeText = (String) msg.obj;
            //_Log.d(TAG, "MyActivity: decoded [" + text + "]");

            String strHead = null;
            try {
                strHead = strQRCodeText.substring(0, HEADER_LENGTH);
            } catch (Exception e) {
                _Log.d(TAG, "MyActivity: not found data header. err=" + e.toString());
            }
            // ヘッダが (NN/NN:AAAA) のパターンにマッチすれば連続 QR コードの一部とみなして処理
            // ヘッダ例: "(01/09:bKfQ)"
               // ヘッダの意味: ([現QRコードのページ]/[総QRコードページ数]:[ランダム4英文字キー])
            if (strHead != null && strHead.matches("\\([0-9]{2}\\/[0-9]{2}\\:[A-Za-z]{4}\\)")) {
                String data = strQRCodeText.substring(HEADER_LENGTH);
                String strCurPage = strHead.substring(1, 1+2);
                String strTotalPage = strHead.substring(4, 4+2);
                String strKey = strHead.substring(7, 7+4);
                int curPage = Integer.parseInt(strCurPage);
                int totalPage = Integer.parseInt(strTotalPage);
                //_Log.d(TAG, "MyActivity: curPage=" + curPage + " totalPages=" + totalPage + " key=" + strKey);
                if (mCommThread != null) {
                    // ヘッダ中のランダムキー文字列をライターへ送出
                    Message.obtain(mCommThread.getHandler(), R.id.sendto_writer, strKey).sendToTarget();
                }
                if (curPage != mPrevPage) {
                    _Log.d(TAG, "MyActivity: header=" + strHead);
                    //_Log.d(TAG, "MyActivity: data=[" + strQRCodeText + "]");
                    mReadData += data;
                    mPrevPage = curPage;
                    // 連続 QR コードの最後に到達したら終了
                    if (curPage >= totalPage) {
                        _Log.d(TAG, "MyActivity: mReadData=[" + mReadData +"]");
                        mActive = false;
                        stopIt();
                        showDialogMessage(mReadData, true);
                    } else {
                        // QR コード探索を継続
                        if (mCamera != null) {
                            mCamera.setOneShotPreviewCallback(this);
                        }
                    }
                } else { // 前回と同じページなら継続
                    if (mCamera != null) {
                        mCamera.setOneShotPreviewCallback(this);
                    }
                }
            } else { // ヘッダのない単発データが出現の場合は終了
                mActive = false;
                stopIt();
                mReadData += strQRCodeText;
                showDialogMessage(mReadData, true);
            }            
            break;
            
        // ライターからの新しい QR コード表示完了のメッセージ
        case R.id.writer_newpage:
            String peer = (String) msg.obj;
            _Log.d(TAG, "MyActivity: [" + peer + "] notified 'OK'");
            // 今は不使用
            //if (peer.equals(mWriterAddress)) {
            //    if (mCamera != null) {
            //        mCamera.setOneShotPreviewCallback(this);
            //    }
            //}
            break;
            
            // QR コード検出  NG
        case R.id.decode_failed:
            if (mCamera != null) {
                // PreviewCallback を発動させ フレームイメージ取得～認識 を繰り返す
                mCamera.setOneShotPreviewCallback(this);
            }
            break;
        }
        return false;
    }

    @Override
    // Camera.PreviewCallback
    public void onPreviewFrame(byte[] data, Camera camera) {
        if (mDecodeThread != null) {
            // プレビューイメージを認識スレッドへ渡しコードの読取りを指示
            Handler h = mDecodeThread.getHandler();
            Message msg = h.obtainMessage(R.id.decode,
                mConfigManager.getCameraResolution().x,
                mConfigManager.getCameraResolution().y, data);
            msg.sendToTarget();
        }
    }

    public Handler getHandler() {
        return mHandler;
    }

    public PlanarYUVLuminanceSource buildLuminanceSource(byte[] data, int w, int h) {
        PlanarYUVLuminanceSource src = null;
        int left = mFinderView.getLeft();
        int top = mFinderView.getTop();
        int width = mFinderView.getWidth();
        int height = mFinderView.getHeight();
        //_Log.d(TAG, "w=" + w + " h=" +h + " left=" + left + " top=" + top + " width=" + width + " height=" + height);
        try {
            src = new PlanarYUVLuminanceSource(data, w, h, left, top, width, height, false);
        } catch (Exception e) {
            _Log.i(TAG, "MyActivity: PlanarYUVLuminanceSource err=" + e.toString());
        }
        return src;
    }    
    
    private void stopIt() {
        mHandler = null;
        closeCamera();
        if (mDecodeThread != null) {
            // 認識スレッドを終了させる
            Message msg = Message.obtain(mDecodeThread.getHandler(), R.id.quit);
            msg.sendToTarget();
            try {
                mDecodeThread.join(500L);
            } catch (InterruptedException e) {
            }
            mDecodeThread = null;
        }
        if (mCommThread != null) {
            // 通信スレッドを終了させる
            Message msg = Message.obtain(mCommThread.getHandler(), R.id.quit);
            msg.sendToTarget();
            try {
                mCommThread.join(500L);
            } catch (InterruptedException e) {
            }
            mCommThread = null;
        }
        if (!mHasSurface) {
            SurfaceHolder holder = mSurfaceView.getHolder();
            holder.removeCallback(this);
        }        
    }

    private void openCamera(SurfaceHolder holder) throws IOException {
        if (mCamera == null) {
            mCamera = Camera.open();
            if (mCamera == null) {
                throw new IOException();
            }
        }
        mCamera.setPreviewDisplay(holder);
        mConfigManager.initFromCameraParameters(mCamera);
        mConfigManager.setDesiredCameraParameters(mCamera, false);
        mCamera.startPreview();
        if (mTimerFocus == null) {
            mTimerFocus = new Timer(false);
            mTimerFocus.schedule(new TimerTask() {
                @Override
                public void run() {
                    mCamera.autoFocus(null);
                }
            }, 500, 2000); // 2秒間隔でオートフォーカス
        }
    }

    private void closeCamera() {
        if (mTimerFocus != null) {
            mTimerFocus.cancel();
            mTimerFocus = null;
        }
        if (mCamera != null) {
            mCamera.stopPreview();
            mCamera.release();
            mCamera = null;
        }
    }

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
