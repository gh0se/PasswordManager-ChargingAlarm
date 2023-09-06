package com.example.passwordmanager;

import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.view.WindowManager;
import android.widget.FrameLayout;

import androidx.appcompat.app.AppCompatActivity;
import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

public class TransparentActivity extends AppCompatActivity {

    public static TransparentActivity instance = null;
    public static class BiometricSuccessEvent {}


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_transparent);
        instance = this;

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE);

        FrameLayout rootView = findViewById(R.id.root_view);
        rootView.setBackgroundColor(Color.TRANSPARENT);
        rootView.setClickable(true);
        rootView.setOnClickListener(v -> {
            if (!MainActivity.isPromptActive) {
                MainActivity mainActivity = MainActivity.instance;
                if (mainActivity != null) {
                    mainActivity.displayBiometricPrompt();
                }
            }
        });

        rootView.postDelayed(() -> {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE);
            WindowManager wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
            if (wm != null) {
                wm.updateViewLayout(getWindow().getDecorView(), getWindow().getAttributes());
            }
        }, 500);

        EventBus.getDefault().register(this);
    }



    @Override
    protected void onDestroy() {
        super.onDestroy();
        EventBus.getDefault().unregister(this);
    }
    public static void closeActivity() {
        if (instance != null) {
            instance.finish();
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onBiometricSuccessEvent(TransparentActivity.BiometricSuccessEvent event) {
        finish();
    }

    public static class BiometricCancelledEvent {}
}
