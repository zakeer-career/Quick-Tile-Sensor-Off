package android.hardware;

oneway interface ISensorPrivacyListener {
    void onSensorPrivacyChanged(int toggleType, int sensor, boolean enabled);
}
