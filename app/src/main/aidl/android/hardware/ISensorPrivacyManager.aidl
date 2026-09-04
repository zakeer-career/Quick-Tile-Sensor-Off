package android.hardware;

import android.hardware.ISensorPrivacyListener;

interface ISensorPrivacyManager {
    boolean supportsSensorToggle(int toggleType, int sensor);
    void addSensorPrivacyListener(in ISensorPrivacyListener listener);
    void addToggleSensorPrivacyListener(in ISensorPrivacyListener listener);
    void removeSensorPrivacyListener(in ISensorPrivacyListener listener);
    void removeToggleSensorPrivacyListener(in ISensorPrivacyListener listener);
    boolean isSensorPrivacyEnabled();
    boolean isToggleSensorPrivacyEnabled(int toggleType, int sensor);
    boolean isCombinedToggleSensorPrivacyEnabled(int sensor);
    void setSensorPrivacy(boolean enable);
    void setToggleSensorPrivacy(int userId, int source, int sensor, boolean enable);
    void setToggleSensorPrivacyForProfileGroup(int userId, int source, int sensor, boolean enable);
}
