package yangfentuozi.hiddenapi.compat;

import android.app.IActivityManager;
import android.app.IProcessObserver;
import android.app.IUidObserver;
import android.content.AttributionSource;
import android.content.IContentProvider;
import android.os.Bundle;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.system.Os;
import android.util.Log;

import androidx.annotation.Nullable;

public class ActivityManagerCompat {

    private static final String TAG = "ActivityManagerCompat";
    private static IActivityManager service;

    /** Flag for registerUidObserver: report changes in process state.
     *  AOSP value: 1<<0 */
    public static int UID_OBSERVER_PROCSTATE;

    /** Flag for registerUidObserver: report uid gone.
     *  AOSP value: 1<<1 */
    public static int UID_OBSERVER_GONE;

    /** Flag for registerUidObserver: report uid has become idle.
     *  AOSP value: 1<<2 */
    public static int UID_OBSERVER_IDLE;

    /** Flag for registerUidObserver: report uid has become active.
     *  AOSP value: 1<<3 */
    public static int UID_OBSERVER_ACTIVE;

    /** Flag for registerUidObserver: report uid cached state has changed.
     *  AOSP value: 1<<4 */
    public static int UID_OBSERVER_CACHED;

    /** Flag for registerUidObserver: report uid capability has changed.
     *  AOSP value: 1<<5 */
    public static int UID_OBSERVER_CAPABILITY;

    public static int FLAG_AND_UNLOCKED;

    public static int PROCESS_STATE_UNKNOWN;

    public static int PROCESS_STATE_TOP;

    private static void init() {
        if (service == null) {
            service = IActivityManager.Stub.asInterface(ServiceManager.getService("activity"));
        }
    }

    public static Bundle contentProviderCall(String authority, String method, @Nullable String arg, @Nullable Bundle extras)
            throws RemoteException {
        init();

        IContentProvider provider = null;
        try {
            var contentProviderHolder =
                    service.getContentProviderExternal(authority, 0, null, authority);
            provider = contentProviderHolder.provider;

            if (provider == null) {
                Log.e(TAG, "Provider is null");
                return null;
            }
            if (!provider.asBinder().pingBinder()) {
                Log.e(TAG, "Provider is dead");
            }

            final var result = provider.call(
                    (new AttributionSource.Builder(Os.getuid())).setPackageName(null).build(),
                    authority,
                    method,
                    arg,
                    extras
            );
            Log.i(TAG, "Did ContentProvider.call");
            return result;
        } catch (RemoteException e) {
            Log.e(TAG, "Failed to do ContentProvider.call", e);
            throw e;
        } finally {
            if (provider != null) {
                try {
                    service.removeContentProviderExternal(authority, null);
                } catch (Throwable tr) {
                    Log.w(TAG, "RemoveContentProviderExternal", tr);
                }
            }
        }
    }

    public static void registerProcessObserver(@Nullable IProcessObserver processObserver) throws RemoteException {
        init();

        service.registerProcessObserver(processObserver);
    }

    public static void unregisterProcessObserver(@Nullable IProcessObserver observer) throws RemoteException {
        init();

        service.unregisterProcessObserver(observer);
    }

    public static void registerUidObserver(@Nullable IUidObserver observer, int which, int cutpoint, @Nullable String callingPackage) throws RemoteException {
        init();

        service.registerUidObserver(observer, which, cutpoint, callingPackage);
    }

    public static void unregisterUidObserver(@Nullable IUidObserver observer) throws RemoteException {
        init();

        service.unregisterUidObserver(observer);
    }
}
