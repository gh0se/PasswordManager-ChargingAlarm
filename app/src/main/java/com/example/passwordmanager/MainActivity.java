package com.example.passwordmanager;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.PowerManager;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import android.Manifest;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.passwordmanager.data.model.Website;
import com.example.passwordmanager.data.model.WebsiteAdapter;
import com.example.passwordmanager.data.model.dao.WebsiteDao;
import com.example.passwordmanager.data.model.databae.AppDatabase;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private int biometricRetries = 0;
    private static final int MAX_BIOMETRIC_RETRIES = 3;
    private final int WRITE_EXTERNAL_STORAGE_REQUEST_CODE = 101;
    private static final int PICK_JSON_FILE_REQUEST = 102;
    private static final int READ_EXTERNAL_STORAGE_REQUEST_CODE = 103;
    private static final int MANAGE_EXTERNAL_STORAGE_REQUEST_CODE = 104;
    private static final int STORAGE_PERMISSION_REQUEST_CODE = 100;
    private static final int FOREGROUND_SERVICE_PERMISSION_REQUEST_CODE = 101;
    private static final int WAKE_LOCK_PERMISSION_REQUEST_CODE = 102;
    private static final int MY_PERMISSIONS_REQUEST_WRITE_EXTERNAL_STORAGE = 123;
    private boolean isExportPending = false;
    private Uri latestExportUri = null;
    private boolean isBiometricPromptShown = false;
    protected static boolean isPromptActive = false;
    public static class BiometricSuccessEvent {
    }

    private boolean shouldDisplayBiometricPrompt = true;

    private boolean wasAppInBackground = false;

    private boolean isFirstRun = true;

    private boolean justAuthenticated = false;
    public static MainActivity instance;
    private ChargingReceiver chargingReceiver;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        instance = this;
        setTheme(R.style.AppTheme);
        setContentView(R.layout.activity_main);

        requestManageExternalStoragePermission();

        SharedPreferences prefs = getSharedPreferences("app_prefs", MODE_PRIVATE);
        boolean isFirstRun = prefs.getBoolean("isFirstRun", true);

        if (isFirstRun) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                String packageName = getPackageName();
                PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
                if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                    Intent intent = new Intent();
                    intent.setAction(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                    intent.setData(Uri.parse("package:" + packageName));
                    startActivity(intent);
                }
            }
            prefs.edit().putBoolean("isFirstRun", false).apply();
        }

        String[] requiredPermissions = {
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.READ_MEDIA_AUDIO
        };

        boolean arePermissionsGranted = true;
        for (String permission : requiredPermissions) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                arePermissionsGranted = false;
                break;
            }
        }

        if (!arePermissionsGranted) {
            ActivityCompat.requestPermissions(this, requiredPermissions, STORAGE_PERMISSION_REQUEST_CODE);
        }

        recyclerView = findViewById(R.id.recycler_view);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        chargingReceiver = new ChargingReceiver();
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_POWER_CONNECTED);
        filter.addAction(Intent.ACTION_POWER_DISCONNECTED);
        registerReceiver(chargingReceiver, filter);

        WebsiteAdapter websiteAdapter = new WebsiteAdapter(this, new ArrayList<>(), website -> {

        }, recyclerView);
        recyclerView.setAdapter(websiteAdapter);


        EditText searchEditText = findViewById(R.id.searchEditText);
        searchEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                websiteAdapter.filter(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        Button addButton = findViewById(R.id.add_button);
        addButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showAddWebsiteDialog();
            }
        });


        new DatabaseAsyncTask(this).execute();

    }

    @Override
    protected void onResume() {
        super.onResume();
        EventBus.getDefault().register(this);

        if (!justAuthenticated && (isFirstRun || wasAppInBackground) && shouldDisplayBiometricPrompt) {
            Intent intent = new Intent(this, TransparentActivity.class);
            startActivity(intent);

            new Handler().postDelayed(() -> {
                if (!isPromptActive && BiometricManager.from(this).canAuthenticate() == BiometricManager.BIOMETRIC_SUCCESS) {
                    displayBiometricPrompt();
                }
            }, 500);
        }

        justAuthenticated = false;

        isFirstRun = false;

    }


    @Override
    protected void onStop() {
        super.onStop();
        wasAppInBackground = true;
    }

    @Override
    protected void onPause() {
        super.onPause();
        EventBus.getDefault().unregister(this);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        instance = null;
        unregisterReceiver(chargingReceiver);
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onBiometricSuccessEvent(TransparentActivity.BiometricSuccessEvent event) {
        justAuthenticated = true;
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onBiometricCancelled(TransparentActivity.BiometricCancelledEvent event) {
        if (isFirstRun) {
            finish();
        } else {
        }
    }


    protected void displayBiometricPrompt() {
        if (isPromptActive) return;

        isPromptActive = true;
        Executor newExecutor = Executors.newSingleThreadExecutor();

        final BiometricPrompt biometricPrompt = new BiometricPrompt(this, newExecutor, new BiometricPrompt.AuthenticationCallback() {
            @Override
            public void onAuthenticationError(int errorCode, @NonNull CharSequence errString) {
                super.onAuthenticationError(errorCode, errString);
                isPromptActive = false;
                isBiometricPromptShown = false;

                if (errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON || errorCode == BiometricPrompt.ERROR_CANCELED) {
                    EventBus.getDefault().post(new TransparentActivity.BiometricCancelledEvent());
                    TransparentActivity.closeActivity();
                    finish();
                } else {
                    shouldDisplayBiometricPrompt = true;
                }
            }

            @Override
            public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
                super.onAuthenticationSucceeded(result);
                biometricRetries = 0;
                isPromptActive = false;
                isBiometricPromptShown = false;
                justAuthenticated = true;
                TransparentActivity.closeActivity();
            }

            @Override
            public void onAuthenticationFailed() {
                super.onAuthenticationFailed();
                isPromptActive = false;
                isBiometricPromptShown = false;
                biometricRetries++;

                if (biometricRetries >= MAX_BIOMETRIC_RETRIES) {
                    shouldDisplayBiometricPrompt = false;
                    TransparentActivity.closeActivity();
                    finish();
                } else {
                    shouldDisplayBiometricPrompt = true;
                }
            }
        });

        BiometricPrompt.PromptInfo.Builder promptBuilder = new BiometricPrompt.PromptInfo.Builder()
                .setTitle("Biometric Authentication")
                .setSubtitle("Log in using your biometric credential");

        if (isFirstRun) {
            promptBuilder.setDescription("Please authenticate to proceed");
        } else {
            promptBuilder.setNegativeButtonText("Cancel");
        }

        final BiometricPrompt.PromptInfo promptInfo = promptBuilder.build();
        biometricPrompt.authenticate(promptInfo);
    }


    public static void scheduleBatteryCheck(Context context) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(context, BatteryCheckService.class);
        PendingIntent pendingIntent = PendingIntent.getService(context, 0, intent, PendingIntent.FLAG_IMMUTABLE);

        final long interval = 3 * 60 * 1000;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + interval, pendingIntent);
        } else {
            alarmManager.setRepeating(AlarmManager.RTC_WAKEUP, System.currentTimeMillis(), interval, pendingIntent);
        }
    }


    private void checkPermissionAndExport() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {

            Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("application/json");
            intent.putExtra(Intent.EXTRA_TITLE, "PM_passwords.json");
            startActivityForResult(intent, WRITE_EXTERNAL_STORAGE_REQUEST_CODE);
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, WRITE_EXTERNAL_STORAGE_REQUEST_CODE);
            } else {
                new ExportDatabaseTask(this, null).execute();
            }
        }
    }


    private void checkPermissionAndImport() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/json");
        startActivityForResult(intent, READ_EXTERNAL_STORAGE_REQUEST_CODE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        switch (requestCode) {
            case STORAGE_PERMISSION_REQUEST_CODE:
                List<String> grantedPermissions = new ArrayList<>();

                for (int i = 0; i < permissions.length; i++) {
                    if (grantResults[i] == PackageManager.PERMISSION_GRANTED) {
                        grantedPermissions.add(permissions[i]);
                    }
                }

                if (grantedPermissions.size() == permissions.length) {
                    if (isExportPending) {
                        new ExportDatabaseTask(MainActivity.this, latestExportUri).execute();
                        isExportPending = false;
                    }
                } else {
                    Log.d("DEBUG", "Some permissions were denied.");
                }
                break;

            case FOREGROUND_SERVICE_PERMISSION_REQUEST_CODE:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // Handle permission granted
                } else {
                    Toast.makeText(this, "Foreground Service Permission denied!", Toast.LENGTH_SHORT).show();
                }
                break;

            case WAKE_LOCK_PERMISSION_REQUEST_CODE:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // Handle permission granted
                } else {
                    Toast.makeText(this, "Wake Lock Permission denied!", Toast.LENGTH_SHORT).show();
                }
                break;

            default:
                break;
        }
    }


    private static class ExportDatabaseTask extends AsyncTask<Void, Void, List<Website>> {

        private final WeakReference<MainActivity> activityReference;
        private final AppDatabase appDatabase;
        private final Uri exportUri;

        ExportDatabaseTask(MainActivity context, Uri uri) {
            activityReference = new WeakReference<>(context);
            appDatabase = AppDatabase.getDatabase(context);
            this.exportUri = uri;
        }

        @Override
        protected List<Website> doInBackground(Void... voids) {
            MainActivity activity = activityReference.get();
            if (activity == null || activity.isFinishing()) return null;

            return appDatabase.websiteDao().getAllWebsites("%");
        }

        @Override
        protected void onPostExecute(List<Website> websites) {

            MainActivity activity = activityReference.get();
            if (activity != null) {
                Log.d("DEBUG", "Websites size: " + websites.size());
            }
            if (activity != null && websites != null) {

                activity.exportDatabaseToJsonFile(activity, exportUri, websites);
                Toast.makeText(activity, "Database exported to JSON!", Toast.LENGTH_SHORT).show();
            }
        }
    }


    private void showAddWebsiteDialog() {
        LayoutInflater inflater = getLayoutInflater();
        View view = inflater.inflate(R.layout.dialog_add_website, null);

        final EditText websiteNameInput = view.findViewById(R.id.website_name_input);
        final EditText emailInput = view.findViewById(R.id.email_input);
        final EditText passwordInput = view.findViewById(R.id.password_input);

        View customTitleView = inflater.inflate(R.layout.custom_dialog_title, null);
        TextView customTitleTextView = customTitleView.findViewById(R.id.customDialogTitle);
        customTitleTextView.setText("Add Website");

        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.MyAlertDialogTheme);
        builder.setCustomTitle(customTitleView)
                .setView(view)
                .setPositiveButton("Add", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String websiteName = websiteNameInput.getText().toString();
                        String email = emailInput.getText().toString();
                        String password = passwordInput.getText().toString();
                        insertWebsite(websiteName, email, password);
                    }
                })
                .setNegativeButton("Cancel", null);

        AlertDialog alertDialog = builder.create();
        alertDialog.show();

        Button positiveButton = alertDialog.getButton(DialogInterface.BUTTON_POSITIVE);
        positiveButton.setTextColor(Color.WHITE);

        Button negativeButton = alertDialog.getButton(DialogInterface.BUTTON_NEGATIVE);
        negativeButton.setTextColor(Color.WHITE);
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.action_export) {
            checkPermissionAndExport();
            return true;
        }

        if (id == R.id.action_import) {
            checkPermissionAndImport();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    public String convertListToJson(Context context, List<Website> websites) {
        Gson gson = new Gson();
        return gson.toJson(websites);
    }

    public void exportDatabaseToJsonFile(Activity activity, Uri uri, List<Website> websites) {
        String jsonString = convertListToJson(activity, websites);
        Log.d("DEBUG", "JSON Content: " + jsonString);

        try {
            Log.d("DEBUG", "Starting export...");
            OutputStream os = activity.getContentResolver().openOutputStream(uri);
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(os));
            writer.write(jsonString);
            writer.flush();
            writer.close();
            Log.d("DEBUG", "Export completed.");
        } catch (IOException e) {
            e.printStackTrace();
            Log.e("DEBUG", "Error while writing JSON: " + e.getMessage());
        }
    }

    private void openFilePicker() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("application/json");
        startActivityForResult(intent, PICK_JSON_FILE_REQUEST);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == Activity.RESULT_OK) {
            if (requestCode == WRITE_EXTERNAL_STORAGE_REQUEST_CODE) {

                latestExportUri = data.getData();
                new ExportDatabaseTask(this, latestExportUri).execute();
            } else if (requestCode == READ_EXTERNAL_STORAGE_REQUEST_CODE) {

                Uri uri = data.getData();
                importJsonFile(uri);
            } else if (requestCode == MANAGE_EXTERNAL_STORAGE_REQUEST_CODE) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    if (Environment.isExternalStorageManager()) {

                        if (latestExportUri != null) {
                            new ExportDatabaseTask(this, latestExportUri).execute();
                        }
                    } else {

                        Toast.makeText(this, "Permission not granted!", Toast.LENGTH_SHORT).show();
                    }
                }
            }
        }
    }


    private void importJsonFile(Uri fileUri) {
        try {
            InputStream inputStream = getContentResolver().openInputStream(fileUri);
            InputStreamReader inputStreamReader = new InputStreamReader(inputStream);
            BufferedReader bufferedReader = new BufferedReader(inputStreamReader);

            StringBuilder stringBuilder = new StringBuilder();
            String line;
            while ((line = bufferedReader.readLine()) != null) {
                stringBuilder.append(line);
            }

            String jsonString = stringBuilder.toString();

            Gson gson = new Gson();
            List<Website> importedWebsites = gson.fromJson(jsonString, new TypeToken<List<Website>>() {
            }.getType());

            if (importedWebsites != null) {
                new InsertFromJsonTask(this, importedWebsites).execute();
            } else {
                Toast.makeText(this, "Invalid JSON data or format", Toast.LENGTH_SHORT).show();
            }
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(this, "Error importing JSON data", Toast.LENGTH_SHORT).show();
        } catch (JsonSyntaxException e) {
            e.printStackTrace();
            Toast.makeText(this, "JSON format error: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private static class InsertFromJsonTask extends AsyncTask<Void, Void, Void> {

        private final WeakReference<MainActivity> activityReference;
        private final List<Website> websitesToInsert;

        InsertFromJsonTask(MainActivity context, List<Website> websites) {
            activityReference = new WeakReference<>(context);
            this.websitesToInsert = websites;
        }

        @Override
        protected Void doInBackground(Void... voids) {
            MainActivity activity = activityReference.get();
            if (activity == null || activity.isFinishing()) return null;

            AppDatabase.getDatabase(activity).websiteDao().insertAll(websitesToInsert);

            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            super.onPostExecute(aVoid);
            MainActivity activity = activityReference.get();
            if (activity != null) {
                Toast.makeText(activity, "JSON data imported successfully!", Toast.LENGTH_SHORT).show();
                activity.updateRecyclerView();
            }
        }
    }

    private static class UpdateRecyclerViewTask extends AsyncTask<Void, Void, List<Website>> {

        private final WeakReference<MainActivity> activityReference;

        UpdateRecyclerViewTask(MainActivity context) {
            activityReference = new WeakReference<>(context);
        }

        @Override
        protected List<Website> doInBackground(Void... voids) {
            MainActivity activity = activityReference.get();
            if (activity == null || activity.isFinishing()) return null;
            return AppDatabase.getDatabase(activity).websiteDao().getAllWebsites("%");
        }

        @Override
        protected void onPostExecute(List<Website> websites) {
            MainActivity activity = activityReference.get();
            if (activity != null && websites != null) {
                WebsiteAdapter adapter = (WebsiteAdapter) activity.recyclerView.getAdapter();
                if (adapter != null) {
                    adapter.setWebsites(websites);
                    adapter.notifyDataSetChanged();
                } else {
                    adapter = new WebsiteAdapter(
                            activity,
                            websites,
                            website -> Toast.makeText(activity, "Clicked " + website.getWebsiteName(), Toast.LENGTH_SHORT).show(),
                            activity.recyclerView
                    );
                    activity.recyclerView.setAdapter(adapter);
                }
            }
        }
    }

    private void updateRecyclerView() {
        new UpdateRecyclerViewTask(this).execute();
    }


    private void insertWebsite(final String websiteName, final String email, final String password) {
        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... voids) {
                Website website = new Website(websiteName, email, password);
                AppDatabase.getDatabase(MainActivity.this).websiteDao().insert(website);
                return null;
            }

            @Override
            protected void onPostExecute(Void aVoid) {
                super.onPostExecute(aVoid);

                new DatabaseAsyncTask(MainActivity.this).execute();
            }
        }.execute();
    }

    private void requestManageExternalStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                Uri uri = Uri.fromParts("package", getPackageName(), null);
                intent.setData(uri);
                startActivityForResult(intent, MANAGE_EXTERNAL_STORAGE_REQUEST_CODE);
            }
        } else {

            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                        MY_PERMISSIONS_REQUEST_WRITE_EXTERNAL_STORAGE);
            }
        }
    }


    private static class DatabaseAsyncTask extends AsyncTask<Void, Void, List<Website>> {

        private final WeakReference<MainActivity> activityReference;

        DatabaseAsyncTask(MainActivity context) {
            activityReference = new WeakReference<>(context);
        }

        @Override
        protected List<Website> doInBackground(Void... voids) {
            MainActivity activity = activityReference.get();
            if (activity == null || activity.isFinishing()) return null;

            WebsiteDao websiteDao = AppDatabase.getDatabase(activity).websiteDao();
            return websiteDao.getAllWebsites("%");
        }


        @Override
        protected void onPostExecute(List<Website> websites) {
            MainActivity activity = activityReference.get();
            if (activity == null || activity.isFinishing() || websites == null) return;

            if (activity.recyclerView.getAdapter() == null) {
                WebsiteAdapter adapter = new WebsiteAdapter(
                        activity,
                        websites,
                        website -> Toast.makeText(activity, "Clicked " + website.getWebsiteName(), Toast.LENGTH_SHORT).show(),
                        activity.recyclerView
                );
                activity.recyclerView.setAdapter(adapter);
            } else {
                ((WebsiteAdapter) activity.recyclerView.getAdapter()).setWebsites(websites);
            }
        }

    }

    private void generateDummyData() {
        List<Website> dummyWebsites = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            dummyWebsites.add(new Website("test " + i, "email" + i + "@example.com", "password" + i));
        }

        AsyncTask.execute(() -> {
            AppDatabase.getDatabase(MainActivity.this).websiteDao().insertAll(dummyWebsites);

        });
    }
}