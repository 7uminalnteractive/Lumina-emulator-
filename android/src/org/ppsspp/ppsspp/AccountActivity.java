package org.ppsspp.ppsspp;

import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

public class AccountActivity extends AppCompatActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_account);

        AccountStore accountStore = new AccountStore(this);
        LocalAccount active = accountStore.getActiveAccount();

        TextView emailView = findViewById(R.id.account_email);
        emailView.setText(active != null ? active.email : "");

        TextView nameView = findViewById(R.id.account_name);
        nameView.setText(active != null ? active.displayName : "Bem-vindo(a)");

        findViewById(R.id.btn_change_library_folder).setOnClickListener(v -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                intent.setData(Uri.parse("package:" + getPackageName()));
                startActivity(intent);
            }
        });

        findViewById(R.id.btn_logout).setOnClickListener(v -> {
            accountStore.clearActiveAccount();
            Intent intent = new Intent(this, ProfileSelectorActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        });

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
    }
}
