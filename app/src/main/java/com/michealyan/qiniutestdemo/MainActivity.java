package com.michealyan.qiniutestdemo;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.qiniu.android.common.AutoZone;
import com.qiniu.android.http.ResponseInfo;
import com.qiniu.android.storage.Configuration;
import com.qiniu.android.storage.UpCancellationSignal;
import com.qiniu.android.storage.UpCompletionHandler;
import com.qiniu.android.storage.UpProgressHandler;
import com.qiniu.android.storage.UploadManager;
import com.qiniu.android.storage.UploadOptions;
import com.qiniu.util.Auth;

import org.json.JSONObject;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;

/**
 * 拍照上传主界面
 *
 * @author michealyan
 * @version v1.0.0
 */
public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    /**
     * 拍摄照片的RequestCode
     */
    private static final int TAKE_PHOTO_REQUEST_CODE = 1;
    /**
     * 申请拍照权限的RequestCode
     */
    private static final int APPLY_TAKE_PHOTO_PERMISSION_REQUEST_CODE = 2;
    /**
     * 更新进度条的Message
     */
    private static final int UPLOAD_PROGRESS_MESSAGE = 3;

    /**
     * 拍摄图片存储位置的Uri
     */
    private Uri photoStoredUri;
    private Handler mHandler;

    /**
     * 拍照图片预览框
     */
    @BindView(R2.id.iv_photo)
    ImageView photoImg;
    /**
     * 拍照按钮
     */
    @BindView(R2.id.btn_take_photo)
    Button takePhotoBtn;
    /**
     * 上传按钮
     */
    @BindView(R2.id.btn_upload_photo)
    Button uploadPhotoBtn;
    /**
     * 上传进度条
     */
    @BindView(R2.id.pr_upload)
    ProgressBar uploadProgressBar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ButterKnife.bind(this);
        mHandler = new Handler() {

            @Override
            public void handleMessage(Message msg) {
                switch (msg.what) {
                    case UPLOAD_PROGRESS_MESSAGE:
                        uploadProgressBar.setProgress(msg.arg1);
                        break;
                    default:
                        break;
                }
            }
        };
    }

    @OnClick({R2.id.btn_take_photo, R2.id.btn_upload_photo})
    protected void onclick(View v) {
        switch (v.getId()) {
            case R.id.btn_take_photo:
                // 动态权限申请
                if (ContextCompat.checkSelfPermission(this
                        , Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                    //权限还没有授予，需要在这里写申请权限的代码
                    ActivityCompat.requestPermissions(this
                            , new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}
                            , APPLY_TAKE_PHOTO_PERMISSION_REQUEST_CODE);
                } else {
                    //权限已经被授予，在这里直接写要执行的相应方法即可
                    takePhoto();
                }
                break;
            case R.id.btn_upload_photo:
                if (null != photoStoredUri) {
                    uploadPhoto();
                }
                break;
            default:
                break;
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case TAKE_PHOTO_REQUEST_CODE:
                if (resultCode == RESULT_OK) {
                    Glide.with(this).load(photoStoredUri).crossFade().into(photoImg);
                } else {
                    Glide.with(this).load(R.mipmap.ic_launcher).crossFade().into(photoImg);
                    photoStoredUri = null;
                }
                break;
            default:
                break;
        }
    }

    /**
     * 根据指定前缀和后缀创建文件
     *
     * @param prefix 前缀
     * @param suffix 后缀
     * @return 文件的Uri
     */
    private Uri generatePhotoStoredUri(String prefix, String suffix) {
        String filePath = Environment.getExternalStorageDirectory().getAbsolutePath() + File.separator + prefix + File.separator;
        @SuppressLint("SimpleDateFormat")
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyyMMddHHmmss");
        String fileName = simpleDateFormat.format(new Date()) + suffix;
        File photoFile = new File(filePath);
        // 判断文件的路径是否存在，不存在则创建
        if (!photoFile.exists()) {
            if (!photoFile.mkdirs()) {
                Toast.makeText(this, "创建储存路径失败", Toast.LENGTH_SHORT).show();
            }
        }
        photoFile = new File(filePath, fileName);
        Log.d(TAG, filePath + fileName);
        return Uri.fromFile(photoFile);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == APPLY_TAKE_PHOTO_PERMISSION_REQUEST_CODE) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                takePhoto();
            } else {
                Toast.makeText(MainActivity.this, "获取拍照权限失败", Toast.LENGTH_SHORT).show();
            }
        }
    }

    /**
     * 调用系统照相机进行拍照
     */
    private void takePhoto() {
        Intent takePhotoIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        // 设置图片存储的位置 /storage/emulated/0/QiNiuTestDemo/*.png
        photoStoredUri = generatePhotoStoredUri("QiNiuTestDemo", ".png");
        takePhotoIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoStoredUri);
        startActivityForResult(takePhotoIntent, TAKE_PHOTO_REQUEST_CODE);
    }

    /**
     * 调用七牛云sdk上传图片
     */
    private void uploadPhoto() {
        // 显示上传进度对话框
        uploadProgressBar.setVisibility(View.VISIBLE);
        Configuration configuration = new Configuration.Builder()
                // 配置自动选择上传区域
                .zone(AutoZone.autoZone)
                .build();
        UploadManager uploadManager = new UploadManager(configuration);
        // 要上传的文件路径
        String filepath = photoStoredUri.getEncodedPath();
        // 服务器上文件的唯一标识符，即文件名称
        String key = filepath.substring(filepath.lastIndexOf("/") + 1);
        // 获取token
        String token = Auth.create("YCL7y6ML-tAyhTOE3h2SsdxmkAZ59U4wgVuuHsEr"
                , "IPycePCJe7VlMj4IcjZMhk1r5K3Uqv7E8H8OI9qG")
                .uploadToken("qiniutestdemo");
        // 上传结果处理
        UpCompletionHandler upCompletionHandler = new UpCompletionHandler() {

            @Override
            public void complete(String key, ResponseInfo info, JSONObject response) {
                if (info.isOK()) {
                    // 隐藏上传进度对话框
                    uploadProgressBar.setVisibility(View.GONE);
                    photoStoredUri = null;
                    Glide.with(MainActivity.this).load(R.mipmap.ic_launcher).crossFade().into(photoImg);
                    // 上传成功，跳转到结果显示页面
                    Intent intent = new Intent(MainActivity.this, ResultActivity.class);
                    // 传递图片的URL
                    intent.putExtra("addr", "http://p54ayw2dx.bkt.clouddn.com/" + key);
                    startActivity(intent);
                } else {
                    Toast.makeText(MainActivity.this, "上传失败", Toast.LENGTH_SHORT).show();
                }
            }
        };
        // 上传进度处理
        UpProgressHandler upProgressHandler = new UpProgressHandler() {

            @Override
            public void progress(String key, double percent) {
                // 使用handler来更新进度条的进度
                int progress = (int) (percent * 100);
                Message message = new Message();
                message.what = UPLOAD_PROGRESS_MESSAGE;
                message.arg1 = progress;
                mHandler.sendMessage(message);
            }
        };
        // 取消上传处理
        UpCancellationSignal upCancellationSignal = new UpCancellationSignal() {

            @Override
            public boolean isCancelled() {
                return false;
            }
        };
        UploadOptions uploadOptions = new UploadOptions(null, null, false, upProgressHandler, upCancellationSignal);
        // 执行上传
        uploadManager.put(filepath, key, token, upCompletionHandler, uploadOptions);
    }
}
