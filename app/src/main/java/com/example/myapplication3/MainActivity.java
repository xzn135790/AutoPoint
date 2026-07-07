package com.example.myapplication3;

import android.content.ComponentName;
import android.content.Context;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.myapplication3.model.ClickProfile;
import com.example.myapplication3.model.ClickStep;
import com.example.myapplication3.service.AutoClickAccessibilityService;
import com.example.myapplication3.service.FloatingControlService;
import com.example.myapplication3.store.ClickProfileStore;
import com.example.myapplication3.util.AppLogger;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.switchmaterial.SwitchMaterial;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    private ClickProfileStore profileStore;
    private List<ClickProfile> profiles = new ArrayList<>();
    private ClickProfile currentProfile;
    private Spinner profileSpinner;
    private Spinner speedSpinner;
    private TextView statusText;
    private EditText loopCountInput;
    private SwitchMaterial infiniteSwitch;
    private LinearLayout stepContainer;
    private boolean bindingSpinner;
    private AutoClickAccessibilityService.StatusCallback statusCallback;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        profileStore = new ClickProfileStore(this);
        bindViews();
        bindActions();
        loadProfiles();
        updatePermissionStatus();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (profileStore != null) {
            loadProfiles();
        }
        updatePermissionStatus();
    }

    @Override
    protected void onPause() {
        saveCurrentProfileFromInputs();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        AutoClickAccessibilityService.removeStatusCallback(statusCallback);
        super.onDestroy();
    }

    private void bindViews() {
        statusText = findViewById(R.id.statusText);
        profileSpinner = findViewById(R.id.profileSpinner);
        speedSpinner = findViewById(R.id.speedSpinner);
        loopCountInput = findViewById(R.id.loopCountInput);
        infiniteSwitch = findViewById(R.id.infiniteSwitch);
        stepContainer = findViewById(R.id.stepContainer);
    }

    private void bindActions() {
        findViewById(R.id.accessibilityButton).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
            }
        });
        findViewById(R.id.overlayButton).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                AppLogger.i(MainActivity.this, "Open overlay permission settings");
                Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName()));
                startActivity(intent);
            }
        });
        findViewById(R.id.logButton).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showLogDialog();
            }
        });
        findViewById(R.id.addProfileButton).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showProfileNameDialog("新建方案", "方案 " + (profiles.size() + 1), new ProfileNameConsumer() {
                    @Override
                    public void accept(String name) {
                        ClickProfile profile = new ClickProfile(name);
                        profiles.add(profile);
                        currentProfile = profile;
                        saveProfiles();
                        refreshProfileSpinner();
                        bindCurrentProfile();
                    }
                });
            }
        });
        findViewById(R.id.copyProfileButton).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (currentProfile == null) {
                    return;
                }
                ClickProfile copy = currentProfile.copy(currentProfile.getName() + " 副本");
                profiles.add(copy);
                currentProfile = copy;
                saveProfiles();
                refreshProfileSpinner();
                bindCurrentProfile();
            }
        });
        findViewById(R.id.editProfileNameButton).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                renameCurrentProfile();
            }
        });
        findViewById(R.id.deleteProfileButton).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                deleteCurrentProfile();
            }
        });
        findViewById(R.id.launchProfileButton).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saveCurrentProfileFromInputs();
                startProfileBubble();
            }
        });
        findViewById(R.id.addClickStepButton).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                addStep(ClickStep.click());
            }
        });
        findViewById(R.id.addSwipeStepButton).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                addStep(ClickStep.swipe());
            }
        });
        findViewById(R.id.startFloatingButton).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saveCurrentProfileFromInputs();
                startFloatingControls();
            }
        });
        findViewById(R.id.saveRunConfigButton).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saveCurrentProfileFromInputs();
                Toast.makeText(MainActivity.this, "循环和倍速已保存", Toast.LENGTH_SHORT).show();
            }
        });
        infiniteSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> loopCountInput.setEnabled(!isChecked));
        setupSpeedSpinner();
        profileSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (bindingSpinner || position < 0 || position >= profiles.size()) {
                    return;
                }
                saveCurrentProfileFromInputs();
                currentProfile = profiles.get(position);
                bindCurrentProfile();
                saveProfiles();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
        statusCallback = new AutoClickAccessibilityService.StatusCallback() {
            @Override
            public void onStatusChanged(final boolean running, final boolean paused, final String message) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        statusText.setText(message);
                    }
                });
            }
        };
        AutoClickAccessibilityService.addStatusCallback(statusCallback);
    }

    private void loadProfiles() {
        profiles = profileStore.loadProfiles();
        String selectedId = profileStore.loadSelectedProfileId();
        currentProfile = profiles.get(0);
        for (ClickProfile profile : profiles) {
            if (profile.getId().equals(selectedId)) {
                currentProfile = profile;
                break;
            }
        }
        refreshProfileSpinner();
        bindCurrentProfile();
    }

    private void refreshProfileSpinner() {
        bindingSpinner = true;
        List<String> names = new ArrayList<>();
        int selectedIndex = 0;
        for (int i = 0; i < profiles.size(); i++) {
            ClickProfile profile = profiles.get(i);
            names.add(profile.getName());
            if (currentProfile != null && profile.getId().equals(currentProfile.getId())) {
                selectedIndex = i;
            }
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, names);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        profileSpinner.setAdapter(adapter);
        profileSpinner.setSelection(selectedIndex);
        bindingSpinner = false;
    }

    private void bindCurrentProfile() {
        if (currentProfile == null) {
            return;
        }
        loopCountInput.setText(String.valueOf(currentProfile.getLoopCount()));
        infiniteSwitch.setChecked(currentProfile.isInfiniteLoop());
        loopCountInput.setEnabled(!currentProfile.isInfiniteLoop());
        bindSpeedSelection(currentProfile.getSpeedMultiplier());
        renderSteps();
    }

    private void renderSteps() {
        stepContainer.removeAllViews();
        if (currentProfile == null) {
            return;
        }
        LayoutInflater inflater = LayoutInflater.from(this);
        List<ClickStep> steps = currentProfile.getSteps();
        if (steps.isEmpty()) {
            TextView emptyView = new TextView(this);
            emptyView.setText("还没有步骤，先添加一个点击或滑动。");
            emptyView.setTextColor(getColor(R.color.app_text_secondary));
            emptyView.setPadding(0, 10, 0, 10);
            stepContainer.addView(emptyView);
            return;
        }
        for (int i = 0; i < steps.size(); i++) {
            final int index = i;
            final ClickStep step = steps.get(i);
            View item = inflater.inflate(R.layout.item_click_step, stepContainer, false);
            item.setBackgroundResource(step.isSwipe() ? R.drawable.bg_step_swipe : R.drawable.bg_step_click);
            TextView title = item.findViewById(R.id.stepTitleText);
            TextView detail = item.findViewById(R.id.stepDetailText);
            title.setText((index + 1) + ". " + (step.isSwipe() ? "滑动" : "点击"));
            detail.setText(buildStepDetail(step));
            item.findViewById(R.id.editStepButton).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showStepDialog(step, new Runnable() {
                        @Override
                        public void run() {
                            saveProfiles();
                            renderSteps();
                        }
                    });
                }
            });
            item.findViewById(R.id.deleteStepButton).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    currentProfile.getSteps().remove(index);
                    saveProfiles();
                    renderSteps();
                }
            });
            stepContainer.addView(item);
        }
    }

    private String buildStepDetail(ClickStep step) {
        if (step.isSwipe()) {
            return "延时 " + step.getDelayMs() + "ms | " +
                    "(" + step.getStartX() + "," + step.getStartY() + ") -> " +
                    "(" + step.getEndX() + "," + step.getEndY() + ") | " +
                    "持续 " + step.getDurationMs() + "ms | 偏差 ±" + step.getRandomRadius() + "px";
        }
        return "延时 " + step.getDelayMs() + "ms | " +
                "(" + step.getStartX() + "," + step.getStartY() + ") | " +
                "持续 " + step.getDurationMs() + "ms | 偏差 ±" + step.getRandomRadius() + "px";
    }

    private void addStep(ClickStep step) {
        if (currentProfile == null) {
            return;
        }
        currentProfile.getSteps().add(step);
        saveProfiles();
        renderSteps();
    }

    private void deleteCurrentProfile() {
        if (profiles.size() <= 1) {
            Toast.makeText(this, "至少保留一个方案", Toast.LENGTH_SHORT).show();
            return;
        }
        profiles.remove(currentProfile);
        currentProfile = profiles.get(0);
        saveProfiles();
        refreshProfileSpinner();
        bindCurrentProfile();
    }

    private void renameCurrentProfile() {
        if (currentProfile == null) {
            return;
        }
        showProfileNameDialog("编辑方案名", currentProfile.getName(), new ProfileNameConsumer() {
            @Override
            public void accept(String name) {
                currentProfile.setName(name);
                saveProfiles();
                refreshProfileSpinner();
            }
        });
    }

    private void saveCurrentProfileFromInputs() {
        if (currentProfile == null) {
            return;
        }
        currentProfile.setLoopCount(parsePositiveInt(loopCountInput, 1));
        currentProfile.setInfiniteLoop(infiniteSwitch.isChecked());
        currentProfile.setSpeedMultiplier(parseSelectedSpeed());
        saveProfiles();
    }

    private void saveProfiles() {
        String selectedId = currentProfile == null ? "" : currentProfile.getId();
        profileStore.saveProfiles(profiles, selectedId);
    }

    private void showStepDialog(final ClickStep step, final Runnable onSaved) {
        final View view = LayoutInflater.from(this).inflate(R.layout.dialog_step_editor, null);
        final RadioGroup typeGroup = view.findViewById(R.id.typeGroup);
        final RadioButton clickRadio = view.findViewById(R.id.clickTypeRadio);
        final RadioButton swipeRadio = view.findViewById(R.id.swipeTypeRadio);
        final LinearLayout endPointGroup = view.findViewById(R.id.endPointGroup);
        final EditText startXInput = view.findViewById(R.id.startXInput);
        final EditText startYInput = view.findViewById(R.id.startYInput);
        final EditText endXInput = view.findViewById(R.id.endXInput);
        final EditText endYInput = view.findViewById(R.id.endYInput);
        final EditText delayInput = view.findViewById(R.id.delayInput);
        final EditText durationInput = view.findViewById(R.id.durationInput);
        final EditText randomRadiusInput = view.findViewById(R.id.randomRadiusInput);

        clickRadio.setChecked(!step.isSwipe());
        swipeRadio.setChecked(step.isSwipe());
        endPointGroup.setVisibility(step.isSwipe() ? View.VISIBLE : View.GONE);
        startXInput.setText(String.valueOf(step.getStartX()));
        startYInput.setText(String.valueOf(step.getStartY()));
        endXInput.setText(String.valueOf(step.getEndX()));
        endYInput.setText(String.valueOf(step.getEndY()));
        delayInput.setText(String.valueOf(step.getDelayMs()));
        durationInput.setText(String.valueOf(step.getDurationMs()));
        randomRadiusInput.setText(String.valueOf(step.getRandomRadius()));

        typeGroup.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                endPointGroup.setVisibility(checkedId == R.id.swipeTypeRadio ? View.VISIBLE : View.GONE);
            }
        });

        final AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("编辑步骤")
                .setView(view)
                .setNegativeButton("取消", null)
                .setPositiveButton("保存", null)
                .create();
        dialog.setOnShowListener(dialogInterface -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!validateRequired(startXInput, startYInput, delayInput, durationInput, randomRadiusInput)) {
                    return;
                }
                boolean swipe = typeGroup.getCheckedRadioButtonId() == R.id.swipeTypeRadio;
                if (swipe && !validateRequired(endXInput, endYInput)) {
                    return;
                }
                step.setType(swipe ? ClickStep.TYPE_SWIPE : ClickStep.TYPE_CLICK);
                step.setStartX(parsePositiveInt(startXInput, 0));
                step.setStartY(parsePositiveInt(startYInput, 0));
                step.setEndX(swipe ? parsePositiveInt(endXInput, 0) : step.getStartX());
                step.setEndY(swipe ? parsePositiveInt(endYInput, 0) : step.getStartY());
                step.setDelayMs(parsePositiveLong(delayInput, 0));
                step.setDurationMs(parsePositiveLong(durationInput, 1));
                step.setRandomRadius(parsePositiveInt(randomRadiusInput, 0));
                onSaved.run();
                dialog.dismiss();
            }
        }));
        dialog.show();
    }

    private void showProfileNameDialog(String title, String defaultName, final ProfileNameConsumer consumer) {
        final LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        int outerPadding = (int) (24 * getResources().getDisplayMetrics().density);
        container.setPadding(outerPadding, 8, outerPadding, 0);

        final EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setText(defaultName);
        input.selectAll();
        input.setHint("请输入方案名称");
        input.setMinHeight((int) (48 * getResources().getDisplayMetrics().density));
        int padding = (int) (12 * getResources().getDisplayMetrics().density);
        input.setPadding(padding, 8, padding, 8);
        container.addView(input, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        final AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(title)
                .setView(container)
                .setNegativeButton("取消", null)
                .setPositiveButton("确定", null)
                .create();
        dialog.setOnShowListener(dialogInterface -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String value = input.getText().toString().trim();
                if (value.isEmpty()) {
                    input.setError("请输入方案名称");
                    return;
                }
                consumer.accept(value);
                dialog.dismiss();
            }
        }));
        dialog.show();
    }

    private boolean validateRequired(EditText... inputs) {
        boolean valid = true;
        for (EditText input : inputs) {
            if (input.getText().toString().trim().isEmpty()) {
                input.setError("必填");
                valid = false;
            }
        }
        return valid;
    }

    private int parsePositiveInt(EditText input, int fallback) {
        try {
            return Math.max(0, Integer.parseInt(input.getText().toString().trim()));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private long parsePositiveLong(EditText input, long fallback) {
        try {
            return Math.max(0, Long.parseLong(input.getText().toString().trim()));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private void setupSpeedSpinner() {
        List<String> speeds = new ArrayList<>();
        for (int i = 5; i <= 40; i++) {
            speeds.add(String.format(java.util.Locale.CHINA, "%.1f 倍率", i / 10.0));
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, speeds);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        speedSpinner.setAdapter(adapter);
    }

    private void bindSpeedSelection(double speedMultiplier) {
        int index = (int) Math.round((Math.max(0.5, Math.min(4.0, speedMultiplier)) - 0.5) * 10);
        speedSpinner.setSelection(Math.max(0, Math.min(35, index)));
    }

    private double parseSelectedSpeed() {
        return 0.5 + speedSpinner.getSelectedItemPosition() / 10.0;
    }

    private void startFloatingControls() {
        boolean accessibility = isAccessibilityEnabled();
        boolean overlay = Settings.canDrawOverlays(this);
        AppLogger.i(this, "Start floating recording requested. accessibility=" + accessibility + ", overlay=" + overlay);
        if (!Settings.canDrawOverlays(this)) {
            AppLogger.i(this, "Overlay permission missing, open settings");
            Toast.makeText(this, R.string.overlay_permission_tip, Toast.LENGTH_SHORT).show();
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            startActivity(intent);
            return;
        }
        try {
            Intent intent = new Intent(this, FloatingControlService.class);
            startService(intent);
            AppLogger.i(this, "FloatingControlService startService called");
            Toast.makeText(this, "悬浮窗已打开，请切换界面后点开始录制", Toast.LENGTH_SHORT).show();
            moveTaskToBack(true);
        } catch (RuntimeException e) {
            AppLogger.e(this, "Failed to start FloatingControlService", e);
            Toast.makeText(this, "启动悬浮录制失败，请查看日志", Toast.LENGTH_LONG).show();
        }
    }

    private void startProfileBubble() {
        boolean accessibility = isAccessibilityEnabled();
        boolean overlay = Settings.canDrawOverlays(this);
        AppLogger.i(this, "Start profile bubble requested. accessibility=" + accessibility + ", overlay=" + overlay);
        if (!accessibility) {
            Toast.makeText(this, "请先开启无障碍服务", Toast.LENGTH_SHORT).show();
            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
            return;
        }
        if (!overlay) {
            Toast.makeText(this, R.string.overlay_permission_tip, Toast.LENGTH_SHORT).show();
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            startActivity(intent);
            return;
        }
        try {
            Intent intent = new Intent(this, FloatingControlService.class);
            intent.putExtra(FloatingControlService.EXTRA_SHOW_PROFILE_BUBBLE, true);
            startService(intent);
            Toast.makeText(this, "方案气泡已打开，点击气泡开始", Toast.LENGTH_SHORT).show();
            moveTaskToBack(true);
        } catch (RuntimeException e) {
            AppLogger.e(this, "Failed to start profile bubble", e);
            Toast.makeText(this, "启动方案气泡失败，请查看日志", Toast.LENGTH_LONG).show();
        }
    }

    private void updatePermissionStatus() {
        boolean accessibility = isAccessibilityEnabled();
        boolean overlay = Settings.canDrawOverlays(this);
        String service = accessibility ? "无障碍已开启" : "无障碍未开启";
        String floating = overlay ? "悬浮窗已开启" : "悬浮窗未开启";
        statusText.setText(service + " | " + floating);
    }

    private boolean isAccessibilityEnabled() {
        ComponentName expectedComponent = new ComponentName(this, AutoClickAccessibilityService.class);
        String enabledServices = Settings.Secure.getString(
                getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        );
        if (enabledServices == null) {
            return false;
        }
        TextUtils.SimpleStringSplitter splitter = new TextUtils.SimpleStringSplitter(':');
        splitter.setString(enabledServices);
        while (splitter.hasNext()) {
            ComponentName enabledComponent = ComponentName.unflattenFromString(splitter.next());
            if (expectedComponent.equals(enabledComponent)) {
                return true;
            }
        }
        return false;
    }

    private void showLogDialog() {
        final TextView logView = new TextView(this);
        logView.setText(AppLogger.read(this));
        logView.setTextIsSelectable(true);
        int padding = (int) (12 * getResources().getDisplayMetrics().density);
        logView.setPadding(padding, padding, padding, padding);

        final android.widget.ScrollView scrollView = new android.widget.ScrollView(this);
        scrollView.addView(logView);

        new AlertDialog.Builder(this)
                .setTitle("运行日志")
                .setView(scrollView)
                .setPositiveButton("复制", (dialog, which) -> {
                    ClipboardManager manager = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                    if (manager != null) {
                        manager.setPrimaryClip(ClipData.newPlainText("轻点连点器日志", logView.getText()));
                        Toast.makeText(this, "日志已复制", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("清空", (dialog, which) -> {
                    AppLogger.clear(this);
                    Toast.makeText(this, "日志已清空", Toast.LENGTH_SHORT).show();
                })
                .setNeutralButton("关闭", null)
                .show();
    }

    private interface ProfileNameConsumer {
        void accept(String name);
    }
}
