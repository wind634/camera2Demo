package com.xiangsu.camera2demo;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.widget.TextView;

import com.pixtalks.detect.DetectResult;
import com.pixtalks.facekitsdk.FaceKit;
import com.xiangsu.camera2demo.camera.CameraConnectionFragment;

import java.io.IOException;
import java.util.ArrayList;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    private static final int CODE_FOR_WRITE_PERMISSION = 10001;

    private FaceKit faceKit = new FaceKit();

    // 是否应该进行比对线程
    public  static boolean  shouldRunCompare= true;


    // 待比较的bitmap
    Bitmap compareBitmap;
    // 待比较的bitmap的特征值, 要预先提取
    float[] comparefea = null;

    @SuppressLint("HandlerLeak")
    Handler uiHandler= new Handler(){
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            switch (msg.what){
                case 1:
                    float score = (float)msg.obj;
                    scoreTextView.setText("比对得分"+score);
                    shouldRunCompare = true;
                    break;
                case 2:
                    scoreTextView.setText("未检测到人脸");
                    break;
                default:break;
            }
        }
    };

    private TextView scoreTextView;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        compareBitmap = getBitmapByAssets("m1.jpg");
        scoreTextView = findViewById(R.id.score);

        // camera2
        if (null == savedInstanceState) {
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.container, CameraConnectionFragment.newInstance())
                    .commit();
        }

        if(judageHasPermission()){
            new Thread(new Runnable() {
                @Override
                public void run() {
                    // 初始化facekit
                    initFacekit();
                }
            }).start();
        }

    }

    private void initFacekit(){
        // 设置验证参数
        faceKit.setAuth("" , "");

        int ret = faceKit.initModel(this);
        if(ret == 0) {
            Log.i(TAG, "模型初始化成功！");

            ArrayList<DetectResult> compareDetectResult = faceKit.detectFace(compareBitmap);
            comparefea= faceKit.getFeatureByDetectResult(compareBitmap, compareDetectResult.get(0));

//            // 比对分值示例代码
//            Bitmap bitmap1 = getBitmapByAssets("m1.jpg");
//            Bitmap bitmap2 = getBitmapByAssets("m2.jpg");
//
//            ArrayList<DetectResult> detectResult1 = faceKit.detectFace(bitmap1);
//            float fea1[] = faceKit.getFeatureByDetectResult(bitmap1, detectResult1.get(0));
//            ArrayList<DetectResult> detectResult2 = faceKit.detectFace(bitmap2);
//            float fea2[] = faceKit.getFeatureByDetectResult(bitmap2, detectResult2.get(0));
//
//            System.out.println("f1.length:" + fea1.length);
//            System.out.println("f2.length:" + fea2.length);
//
//            float score = faceKit.compareScore(fea1, fea2);
//            Log.i(TAG, "score:" + String.valueOf(score));
//
//
//            boolean isLive = faceKit.isLive(bitmap1, detectResult1.get(0));
//            Log.i(TAG, "isLiveness:" + String.valueOf(isLive));

        }else{
            Log.i(TAG, "模型初始化失败！errorCode=" + String.valueOf(ret));
        }
    }


    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == CODE_FOR_WRITE_PERMISSION) {
            initFacekit();
        }
    }

    /**
     * 判断是否有权限
     *
     * @return
     */
    public boolean judageHasPermission() {
        boolean hasPermission = true;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            int hasWriteContactsPermission = checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE);

            if (hasWriteContactsPermission != PackageManager.PERMISSION_GRANTED) {
                hasPermission = false;
                ActivityCompat.requestPermissions(this, new String[]{
                                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                                Manifest.permission.CAMERA,
                        },
                        CODE_FOR_WRITE_PERMISSION);
            } else {

            }

        } else {
//            TODO("VERSION.SDK_INT < M")
        }
        return hasPermission;
    }


    /**
     * 判断是否有人脸
     * @param bitmap
     * @return
     */
    public  ArrayList<DetectResult> judgeHasFaceImage(final Bitmap bitmap) {
        if (faceKit == null) {
            return null;
        }

        long begin = System.currentTimeMillis();
        ArrayList<DetectResult> faceDetectResults = faceKit.detectFace(bitmap);
        long end = System.currentTimeMillis();
        Log.e(TAG, "judgeHasFaceImage ues time: " + (end - begin));
        if(faceDetectResults==null){
            Message message = new Message();
            message.what = 2;
            uiHandler.sendMessage(message);
        }
        return faceDetectResults;
    }


    /**
     * 开始人脸比对线程
     * @return
     */
    public void startThreadToCompare(final Bitmap cameraBitmap, final ArrayList<DetectResult> cameraBitmapResult) {
//        Log.i(TAG, "startThreadToCompare....");
        synchronized(this) {
            if( faceKit == null ){
                return;
            }
            shouldRunCompare = false;

            // 把预先提取出来的特征值和解析后的特征值进行比对
            float[] cameraBitmapFeature = faceKit.getFeatureByDetectResult(cameraBitmap, cameraBitmapResult.get(0));
            final float score = faceKit.compareScore(comparefea, cameraBitmapFeature);
            Message message = new Message();
            message.what = 1;
            message.obj = score;
            uiHandler.sendMessage(message);

            //  ***** 识别活体 *****
            try {
                Boolean isLiveness = faceKit.isLive(cameraBitmap, cameraBitmapResult.get(0));
                Log.i(TAG, "isLiveness:" + isLiveness);
            }catch (Exception e){
                Log.i(TAG, "isLiveness Error:" + e.toString());
            }
            //  ***** 识别活体 *****

        }
    }


    private Bitmap getBitmapByAssets (String imageName){
        Bitmap bitmap=null;
        try {
            bitmap = BitmapFactory.decodeStream(getAssets().open("m1.jpg"));
        } catch (IOException e) {
            e.printStackTrace();
        }
        return bitmap;
    }
}

