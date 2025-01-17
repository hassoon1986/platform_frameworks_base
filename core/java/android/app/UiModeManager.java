/*
 * Copyright (C) 2010 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.app;

import android.annotation.IntDef;
import android.annotation.IntRange;
import android.annotation.Nullable;
import android.annotation.RequiresPermission;
import android.annotation.SystemApi;
import android.annotation.SystemService;
import android.annotation.TestApi;
import android.compat.annotation.UnsupportedAppUsage;
import android.content.Context;
import android.content.res.Configuration;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.ServiceManager.ServiceNotFoundException;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * This class provides access to the system uimode services.  These services
 * allow applications to control UI modes of the device.
 * It provides functionality to disable the car mode and it gives access to the
 * night mode settings.
 * 
 * <p>These facilities are built on top of the underlying
 * {@link android.content.Intent#ACTION_DOCK_EVENT} broadcasts that are sent when the user
 * physical places the device into and out of a dock.  When that happens,
 * the UiModeManager switches the system {@link android.content.res.Configuration}
 * to the appropriate UI mode, sends broadcasts about the mode switch, and
 * starts the corresponding mode activity if appropriate.  See the
 * broadcasts {@link #ACTION_ENTER_CAR_MODE} and
 * {@link #ACTION_ENTER_DESK_MODE} for more information.
 * 
 * <p>In addition, the user may manually switch the system to car mode without
 * physically being in a dock.  While in car mode -- whether by manual action
 * from the user or being physically placed in a dock -- a notification is
 * displayed allowing the user to exit dock mode.  Thus the dock mode
 * represented here may be different than the current state of the underlying
 * dock event broadcast.
 */
@SystemService(Context.UI_MODE_SERVICE)
public class UiModeManager {
    private static final String TAG = "UiModeManager";

    /**
     * Broadcast sent when the device's UI has switched to car mode, either
     * by being placed in a car dock or explicit action of the user.  After
     * sending the broadcast, the system will start the intent
     * {@link android.content.Intent#ACTION_MAIN} with category
     * {@link android.content.Intent#CATEGORY_CAR_DOCK}
     * to display the car UI, which typically what an application would
     * implement to provide their own interface.  However, applications can
     * also monitor this Intent in order to be informed of mode changes or
     * prevent the normal car UI from being displayed by setting the result
     * of the broadcast to {@link Activity#RESULT_CANCELED}.
     */
    public static String ACTION_ENTER_CAR_MODE = "android.app.action.ENTER_CAR_MODE";

    /**
     * Broadcast sent when the device's UI has switched to car mode, either by being placed in a car
     * dock or explicit action of the user.
     * <p>
     * In addition to the behavior for {@link #ACTION_ENTER_CAR_MODE}, this broadcast includes the
     * package name of the app which requested to enter car mode in the
     * {@link #EXTRA_CALLING_PACKAGE}.  If an app requested to enter car mode using
     * {@link #enableCarMode(int, int)} and specified a priority this will be specified in the
     * {@link #EXTRA_PRIORITY}.
     *
     * This is primarily intended to be received by other components of the Android OS.
     * <p>
     * Receiver requires permission: {@link android.Manifest.permission.HANDLE_CAR_MODE_CHANGES}
     * @hide
     */
    @SystemApi
    public static final String ACTION_ENTER_CAR_MODE_PRIORITIZED =
            "android.app.action.ENTER_CAR_MODE_PRIORITIZED";
    
    /**
     * Broadcast sent when the device's UI has switch away from car mode back
     * to normal mode.  Typically used by a car mode app, to dismiss itself
     * when the user exits car mode.
     */
    public static String ACTION_EXIT_CAR_MODE = "android.app.action.EXIT_CAR_MODE";

    /**
     * Broadcast sent when the device's UI has switched away from car mode back to normal mode.
     * Typically used by a car mode app, to dismiss itself when the user exits car mode.
     * <p>
     * In addition to the behavior for {@link #ACTION_EXIT_CAR_MODE}, this broadcast includes the
     * package name of the app which requested to exit car mode in {@link #EXTRA_CALLING_PACKAGE}.
     * If an app requested to enter car mode using {@link #enableCarMode(int, int)} and specified a
     * priority this will be specified in the {@link #EXTRA_PRIORITY} when exiting car mode.
     * <p>
     * If {@link #DISABLE_CAR_MODE_ALL_PRIORITIES} is used when disabling car mode (i.e. this is
     * initiated by the user via the persistent car mode notification), this broadcast is sent once
     * for each priority level for which car mode is being disabled.
     * <p>
     * This is primarily intended to be received by other components of the Android OS.
     * <p>
     * Receiver requires permission: {@link android.Manifest.permission.HANDLE_CAR_MODE_CHANGES}
     * @hide
     */
    @SystemApi
    public static final String ACTION_EXIT_CAR_MODE_PRIORITIZED =
            "android.app.action.EXIT_CAR_MODE_PRIORITIZED";
    
    /**
     * Broadcast sent when the device's UI has switched to desk mode,
     * by being placed in a desk dock.  After
     * sending the broadcast, the system will start the intent
     * {@link android.content.Intent#ACTION_MAIN} with category
     * {@link android.content.Intent#CATEGORY_DESK_DOCK}
     * to display the desk UI, which typically what an application would
     * implement to provide their own interface.  However, applications can
     * also monitor this Intent in order to be informed of mode changes or
     * prevent the normal desk UI from being displayed by setting the result
     * of the broadcast to {@link Activity#RESULT_CANCELED}.
     */
    public static String ACTION_ENTER_DESK_MODE = "android.app.action.ENTER_DESK_MODE";
    
    /**
     * Broadcast sent when the device's UI has switched away from desk mode back
     * to normal mode.  Typically used by a desk mode app, to dismiss itself
     * when the user exits desk mode.
     */
    public static String ACTION_EXIT_DESK_MODE = "android.app.action.EXIT_DESK_MODE";

    /**
     * String extra used with {@link #ACTION_ENTER_CAR_MODE_PRIORITIZED} and
     * {@link #ACTION_EXIT_CAR_MODE_PRIORITIZED} to indicate the package name of the app which
     * requested to enter or exit car mode.
     * @hide
     */
    @SystemApi
    public static final String EXTRA_CALLING_PACKAGE = "android.app.extra.CALLING_PACKAGE";

    /**
     * Integer extra used with {@link #ACTION_ENTER_CAR_MODE_PRIORITIZED} and
     * {@link #ACTION_EXIT_CAR_MODE_PRIORITIZED} to indicate the priority level at which car mode
     * is being disabled.
     * @hide
     */
    @SystemApi
    public static final String EXTRA_PRIORITY = "android.app.extra.PRIORITY";

    /** @hide */
    @IntDef(prefix = { "MODE_" }, value = {
            MODE_NIGHT_AUTO,
            MODE_NIGHT_NO,
            MODE_NIGHT_YES
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface NightMode {}

    /**
     * Constant for {@link #setNightMode(int)} and {@link #getNightMode()}:
     * automatically switch night mode on and off based on the time.
     */
    public static final int MODE_NIGHT_AUTO = Configuration.UI_MODE_NIGHT_UNDEFINED >> 4;
    
    /**
     * Constant for {@link #setNightMode(int)} and {@link #getNightMode()}:
     * never run in night mode.
     */
    public static final int MODE_NIGHT_NO = Configuration.UI_MODE_NIGHT_NO >> 4;
    
    /**
     * Constant for {@link #setNightMode(int)} and {@link #getNightMode()}:
     * always run in night mode.
     */
    public static final int MODE_NIGHT_YES = Configuration.UI_MODE_NIGHT_YES >> 4;

    private IUiModeManager mService;

    /**
     * Context required for getting the opPackageName of API caller; maybe be {@code null} if the
     * old constructor marked with UnSupportedAppUsage is used.
     */
    private @Nullable Context mContext;

    @UnsupportedAppUsage
    /*package*/ UiModeManager() throws ServiceNotFoundException {
        this(null /* context */);
    }

    /*package*/ UiModeManager(Context context) throws ServiceNotFoundException {
        mService = IUiModeManager.Stub.asInterface(
                ServiceManager.getServiceOrThrow(Context.UI_MODE_SERVICE));
        mContext = context;
    }

    /**
     * Flag for use with {@link #enableCarMode(int)}: go to the car
     * home activity as part of the enable.  Enabling this way ensures
     * a clean transition between the current activity (in non-car-mode) and
     * the car home activity that will serve as home while in car mode.  This
     * will switch to the car home activity even if we are already in car mode.
     */
    public static final int ENABLE_CAR_MODE_GO_CAR_HOME = 0x0001;

    /**
     * Flag for use with {@link #enableCarMode(int)}: allow sleep mode while in car mode.
     * By default, when this flag is not set, the system may hold a full wake lock to keep the
     * screen turned on and prevent the system from entering sleep mode while in car mode.
     * Setting this flag disables such behavior and the system may enter sleep mode
     * if there is no other user activity and no other wake lock held.
     * Setting this flag can be relevant for a car dock application that does not require the
     * screen kept on.
     */
    public static final int ENABLE_CAR_MODE_ALLOW_SLEEP = 0x0002;

    /** @hide */
    @IntDef(prefix = { "ENABLE_CAR_MODE_" }, value = {
            ENABLE_CAR_MODE_GO_CAR_HOME,
            ENABLE_CAR_MODE_ALLOW_SLEEP
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface EnableCarMode {}

    /**
     * Force device into car mode, like it had been placed in the car dock.
     * This will cause the device to switch to the car home UI as part of
     * the mode switch.
     * @param flags Must be 0.
     */
    public void enableCarMode(int flags) {
        enableCarMode(DEFAULT_PRIORITY, flags);
    }

    /**
     * Force device into car mode, like it had been placed in the car dock.  This will cause the
     * device to switch to the car home UI as part of the mode switch.
     * <p>
     * An app may request to enter car mode when the system is already in car mode.  The app may
     * specify a "priority" when entering car mode.  The device will remain in car mode
     * (i.e. {@link #getCurrentModeType()} is {@link Configuration#UI_MODE_TYPE_CAR}) as long as
     * there is a priority level at which car mode have been enabled.  For example assume app A
     * enters car mode at priority level 100, and then app B enters car mode at the default priority
     * (0).  If app A exits car mode, the device will remain in car mode until app B exits car mode.
     * <p>
     * Specifying a priority level when entering car mode is important in cases where multiple apps
     * on a device implement a car-mode {@link android.telecom.InCallService} (see
     * {@link android.telecom.TelecomManager#METADATA_IN_CALL_SERVICE_CAR_MODE_UI}).  The
     * {@link android.telecom.InCallService} associated with the highest priority app which entered
     * car mode will be bound to by Telecom and provided with information about ongoing calls on
     * the device.
     * <p>
     * System apps holding the required permission can enable car mode when the app determines the
     * correct conditions exist for that app to be in car mode.  The device maker should ensure that
     * where multiple apps exist on the device which can potentially enter car mode, appropriate
     * priorities are used to ensure that calls delivered by the
     * {@link android.telecom.InCallService} API are delivered to the highest priority app.
     * If app A and app B can both potentially enable car mode, and it is desired that app B is the
     * one which should receive call information, the priority for app B should be higher than the
     * one for app A.
     * <p>
     * When an app uses a priority to enable car mode, they can disable car mode at the specified
     * priority level using {@link #disableCarMode(int)}.  An app may only enable car mode at a
     * single priority.
     * <p>
     * Public apps are assumed to enter/exit car mode at {@link #DEFAULT_PRIORITY}.
     *
     * @param priority The declared priority for the caller.
     * @param flags Car mode flags.
     * @hide
     */
    @SystemApi
    @TestApi
    @RequiresPermission(android.Manifest.permission.ENTER_CAR_MODE_PRIORITIZED)
    public void enableCarMode(@IntRange(from = 0) int priority, @EnableCarMode int flags) {
        if (mService != null) {
            try {
                mService.enableCarMode(flags, priority,
                        mContext == null ? null : mContext.getOpPackageName());
            } catch (RemoteException e) {
                throw e.rethrowFromSystemServer();
            }
        }
    }

    /**
     * Flag for use with {@link #disableCarMode(int)}: go to the normal
     * home activity as part of the disable.  Disabling this way ensures
     * a clean transition between the current activity (in car mode) and
     * the original home activity (which was typically last running without
     * being in car mode).
     */
    public static final int DISABLE_CAR_MODE_GO_HOME = 0x0001;

    /**
     * Flag for use with {@link #disableCarMode(int)}: Disables car mode at ALL priority levels.
     * Primarily intended for use from {@link com.android.internal.app.DisableCarModeActivity} to
     * provide the user with a means to exit car mode at all priority levels.
     * @hide
     */
    public static final int DISABLE_CAR_MODE_ALL_PRIORITIES = 0x0002;

    /** @hide */
    @IntDef(prefix = { "DISABLE_CAR_MODE_" }, value = {
            DISABLE_CAR_MODE_GO_HOME
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface DisableCarMode {}

    /**
     * The default priority used for entering car mode.
     * <p>
     * Callers of the {@link UiModeManager#enableCarMode(int)} priority will be assigned the
     * default priority.
     * <p>
     * System apps can specify a priority other than the default priority when using
     * {@link UiModeManager#enableCarMode(int, int)} to enable car mode.
     * @hide
     */
    @SystemApi
    public static final int DEFAULT_PRIORITY = 0;
    
    /**
     * Turn off special mode if currently in car mode.
     * @param flags One of the disable car mode flags.
     */
    public void disableCarMode(@DisableCarMode int flags) {
        if (mService != null) {
            try {
                mService.disableCarModeByCallingPackage(flags,
                        mContext == null ? null : mContext.getOpPackageName());
            } catch (RemoteException e) {
                throw e.rethrowFromSystemServer();
            }
        }
    }

    /**
     * Return the current running mode type.  May be one of
     * {@link Configuration#UI_MODE_TYPE_NORMAL Configuration.UI_MODE_TYPE_NORMAL},
     * {@link Configuration#UI_MODE_TYPE_DESK Configuration.UI_MODE_TYPE_DESK},
     * {@link Configuration#UI_MODE_TYPE_CAR Configuration.UI_MODE_TYPE_CAR},
     * {@link Configuration#UI_MODE_TYPE_TELEVISION Configuration.UI_MODE_TYPE_TELEVISION},
     * {@link Configuration#UI_MODE_TYPE_APPLIANCE Configuration.UI_MODE_TYPE_APPLIANCE},
     * {@link Configuration#UI_MODE_TYPE_WATCH Configuration.UI_MODE_TYPE_WATCH}, or
     * {@link Configuration#UI_MODE_TYPE_VR_HEADSET Configuration.UI_MODE_TYPE_VR_HEADSET}.
     */
    public int getCurrentModeType() {
        if (mService != null) {
            try {
                return mService.getCurrentModeType();
            } catch (RemoteException e) {
                throw e.rethrowFromSystemServer();
            }
        }
        return Configuration.UI_MODE_TYPE_NORMAL;
    }

    /**
     * Sets the system-wide night mode.
     * <p>
     * The mode can be one of:
     * <ul>
     *   <li><em>{@link #MODE_NIGHT_NO}<em> sets the device into
     *       {@code notnight} mode</li>
     *   <li><em>{@link #MODE_NIGHT_YES}</em> sets the device into
     *       {@code night} mode</li>
     *   <li><em>{@link #MODE_NIGHT_AUTO}</em> automatically switches between
     *       {@code night} and {@code notnight} based on the device's current
     *       location and certain other sensors</li>
     * </ul>
     * <p>
     * <strong>Note:</strong> On API 22 and below, changes to the night mode
     * are only effective when the {@link Configuration#UI_MODE_TYPE_CAR car}
     * or {@link Configuration#UI_MODE_TYPE_DESK desk} mode is enabled on a
     * device. On API 23 through API 28, changes to night mode are always effective.
     * <p>
     * Starting in API 29, when the device is in car mode and this method is called, night mode
     * will change, but the new setting is not persisted and the previously persisted setting
     * will be restored when the device exits car mode.
     * <p>
     * Changes to night mode take effect globally and will result in a configuration change
     * (and potentially an Activity lifecycle event) being applied to all running apps.
     * Developers interested in an app-local implementation of night mode should consider using
     * {@link android.support.v7.app.AppCompatDelegate#setDefaultNightMode(int)} to manage the
     * -night qualifier locally.
     *
     * @param mode the night mode to set
     * @see #getNightMode()
     */
    public void setNightMode(@NightMode int mode) {
        if (mService != null) {
            try {
                mService.setNightMode(mode);
            } catch (RemoteException e) {
                throw e.rethrowFromSystemServer();
            }
        }
    }

    /**
     * Returns the currently configured night mode.
     * <p>
     * May be one of:
     * <ul>
     *   <li>{@link #MODE_NIGHT_NO}</li>
     *   <li>{@link #MODE_NIGHT_YES}</li>
     *   <li>{@link #MODE_NIGHT_AUTO}</li>
     *   <li>{@code -1} on error</li>
     * </ul>
     *
     * @return the current night mode, or {@code -1} on error
     * @see #setNightMode(int)
     */
    public @NightMode int getNightMode() {
        if (mService != null) {
            try {
                return mService.getNightMode();
            } catch (RemoteException e) {
                throw e.rethrowFromSystemServer();
            }
        }
        return -1;
    }

    /**
     * @return If UI mode is locked or not. When UI mode is locked, calls to change UI mode
     *         like {@link #enableCarMode(int)} will silently fail.
     * @hide
     */
    @TestApi
    public boolean isUiModeLocked() {
        if (mService != null) {
            try {
                return mService.isUiModeLocked();
            } catch (RemoteException e) {
                throw e.rethrowFromSystemServer();
            }
        }
        return true;
    }

    /**
     * Returns whether night mode is locked or not.
     * <p>
     * When night mode is locked, only privileged system components may change
     * night mode and calls from non-privileged applications to change night
     * mode will fail silently.
     *
     * @return {@code true} if night mode is locked or {@code false} otherwise
     * @hide
     */
    @TestApi
    public boolean isNightModeLocked() {
        if (mService != null) {
            try {
                return mService.isNightModeLocked();
            } catch (RemoteException e) {
                throw e.rethrowFromSystemServer();
            }
        }
        return true;
    }
}
