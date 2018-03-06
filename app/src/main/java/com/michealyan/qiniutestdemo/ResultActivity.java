package com.michealyan.qiniutestdemo;

import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.TextView;

import com.bumptech.glide.Glide;

import butterknife.BindView;
import butterknife.ButterKnife;

/**
 * 上传结果查看页面
 *
 * @author michealyan
 * @version v1.0.0
 */
public class ResultActivity extends AppCompatActivity {

    @BindView(R2.id.iv_photo)
    ImageView photoImg;             // 图片结果显示框
    @BindView(R2.id.tv_address)
    TextView photoAddressText;      // 图片的七牛云存储地址

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_result);
        ButterKnife.bind(this);

        // 从intent中获取图片的URL
        Intent intent = getIntent();
        String addr = intent.getStringExtra("addr");
        Glide.with(this).load(addr).crossFade().into(photoImg);
        photoAddressText.setText("图片地址：" + addr);
    }
}
