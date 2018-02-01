package com.photoeditorsdk.cordova;

import android.app.Activity;
import android.content.Intent;
import android.media.MediaScannerConnection;
import android.net.Uri;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CordovaWebView;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;

import ly.img.android.PESDK;
import ly.img.android.sdk.models.constant.Directory;
import ly.img.android.sdk.models.state.EditorLoadSettings;
import ly.img.android.sdk.models.state.EditorSaveSettings;
import ly.img.android.sdk.models.state.manager.SettingsList;
import ly.img.android.ui.activities.ImgLyIntent;
import ly.img.android.ui.activities.PhotoEditorBuilder;

public class PESDKPlugin extends CordovaPlugin {

    public static final int PESDK_EDITOR_RESULT = 1;
    private CallbackContext callback = null;

    @Override
    public void initialize(CordovaInterface cordova, CordovaWebView webView) {
        super.initialize(cordova, webView);
        
        PESDK.init(cordova.getActivity().getApplication(), "LICENSE_ANDROID");
    }
    
    @Override
    public boolean execute(String action, JSONArray data, CallbackContext callbackContext) throws JSONException {
        if (action.equals("present")) {
            // Extract image path
            JSONObject options = data.getJSONObject(0);
            String filepath = options.optString("path", "");

            Activity activity = this.cordova.getActivity();
            activity.runOnUiThread(this.present(activity, filepath, callbackContext));
            return true;
        } else {
            return false;
        }
    }

    private Runnable present(final Activity mainActivity, final String filepath, final CallbackContext callbackContext) {
        callback = callbackContext;
        final PESDKPlugin self = this;
        return new Runnable() {
            public void run() {
                if (mainActivity != null && filepath.length() > 0) {
                    SettingsList settingsList = new SettingsList();
                    settingsList
                        .getSettingsModel(EditorLoadSettings.class)
                        .setImageSourcePath(filepath.replace("file://", ""), true) // Load with delete protection true!
                        .getSettingsModel(EditorSaveSettings.class)
                        .setExportDir(Directory.DCIM, "test")
                        .setExportPrefix("result_")
                        .setSavePolicy(
                            EditorSaveSettings.SavePolicy.KEEP_SOURCE_AND_CREATE_ALWAYS_OUTPUT
                        );

                    cordova.setActivityResultCallback(self);
                    new PhotoEditorBuilder(mainActivity)
                            .setSettingsList(settingsList)
                            .startActivityForResult(mainActivity, PESDK_EDITOR_RESULT);
                } else {
                    // Just open the camera
                    Intent intent = new Intent(mainActivity, CameraActivity.class);
                    callback = callbackContext;
                    cordova.startActivityForResult(self, intent, PESDK_EDITOR_RESULT);
                }
            }
        };
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, android.content.Intent data) {
        if (requestCode == PESDK_EDITOR_RESULT) {
            switch (resultCode){
                case Activity.RESULT_OK:
                    success(data);
                    break;
                case Activity.RESULT_CANCELED:
                    callback.error(""); // empty string signals cancellation
                    break;
                default:
                    callback.error("Media error (code " + resultCode + ")");
                    break;
            }
        }
    }

    private void success(Intent data) {
        String path = data.getStringExtra(ImgLyIntent.RESULT_IMAGE_PATH);

        File mMediaFolder = new File(path);

        MediaScannerConnection.scanFile(cordova.getActivity().getApplicationContext(),
                new String[]{mMediaFolder.getAbsolutePath()},
                null,
                new MediaScannerConnection.OnScanCompletedListener() {
                    public void onScanCompleted(String path, Uri uri) {
                        if (uri == null) {
                            callback.error("Media saving failed.");
                        } else {
                            try {
                                JSONObject json = new JSONObject();
                                json.put("url", Uri.fromFile(new File(path)));
                                callback.success(json);
                            } catch (Exception e) {
                                callback.error(e.getMessage());
                            }
                        }
                    }
                }
        );
    }

}
