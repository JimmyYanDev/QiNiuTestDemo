package com.michealyan.qiniutestdemo;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
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
 */
public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private static final int TAKE_PHOTO_REQUEST_CODE = 1;       // 拍摄照片的RequestCode
    private static final int UPLOAD_PHOTO_REQUEST_CODE = 2;     // 上传图片的RequestCode
    private static final int APPLY_TAKE_PHOTO_PERMISSION_REQUEST_CODE = 3;     // 申请拍照权限的RequestCode

    private Uri photoStoredUri;         // 拍摄图片存储位置的Uri

    @BindView(R2.id.iv_photo)
    ImageView photoImg;
    @BindView(R2.id.btn_take_photo)
    Button takePhotoBtn;
    @BindView(R2.id.btn_upload_photo)
    Button uploadPhotoBtn;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ButterKnife.bind(this);
    }

    @OnClick({R2.id.btn_take_photo, R2.id.btn_upload_photo})
    protected void onclick(View v) {
        switch (v.getId()) {
            case R.id.btn_take_photo:
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                    //权限还没有授予，需要在这里写申请权限的代码
                    ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, APPLY_TAKE_PHOTO_PERMISSION_REQUEST_CODE);
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
            case UPLOAD_PHOTO_REQUEST_CODE:

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
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyyMMddHHmmss");
        String fileName = simpleDateFormat.format(new Date()) + suffix;
        File photoFile = new File(filePath);
        // 判断文件的路径是否存在，不存在则创建
        if (!photoFile.exists()) {
            photoFile.mkdirs();
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

    private void uploadPhoto() {
        Configuration configuration = new Configuration.Builder().zone(AutoZone.autoZone).build();
        UploadManager uploadManager = new UploadManager(configuration);
        String filepath = photoStoredUri.getEncodedPath();          // 要上传的文件路径
        String key = filepath.substring(filepath.lastIndexOf("/") + 1); // 服务器上文件的唯一标识符，即文件名称
        String token = Auth.create("YCL7y6ML-tAyhTOE3h2SsdxmkAZ59U4wgVuuHsEr", "IPycePCJe7VlMj4IcjZMhk1r5K3Uqv7E8H8OI9qG").uploadToken("qiniutestdemo");
        UpCompletionHandler upCompletionHandler = new UpCompletionHandler() {

            @Override
            public void complete(String key, ResponseInfo info, JSONObject response) {
                if (info.isOK()) {
                    Log.i("qiniu", "Upload Success");
                } else {
                    Log.i("qiniu", "Upload Fail");
                }
            }
        };
        UpProgressHandler upProgressHandler = new UpProgressHandler() {

            @Override
            public void progress(String key, double percent) {
                Log.i("qiniu", key + ": " + percent);
            }
        };
        UpCancellationSignal upCancellationSignal = new UpCancellationSignal() {

            @Override
            public boolean isCancelled() {
                return false;
            }
        };
        UploadOptions uploadOptions = new UploadOptions(null, null, false, upProgressHandler, upCancellationSignal);
        uploadManager.put(filepath, key, token, upCompletionHandler, uploadOptions);
    }
}
