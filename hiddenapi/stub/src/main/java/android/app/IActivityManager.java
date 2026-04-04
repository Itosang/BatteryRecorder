package android.app;

import android.os.Binder;
import android.os.IBinder;
import android.os.IInterface;
import android.os.RemoteException;

public interface IActivityManager extends IInterface {

    ContentProviderHolder getContentProviderExternal(String name, int userId, IBinder token, String tag)
            throws RemoteException;

    void removeContentProviderExternal(String name, IBinder token)
            throws RemoteException;

    void registerProcessObserver(IProcessObserver observer)
            throws RemoteException;

    void unregisterProcessObserver(IProcessObserver observer)
            throws RemoteException;

    void registerUidObserver(IUidObserver observer, int which, int cutpoint, String callingPackage)
            throws RemoteException;

    void unregisterUidObserver(IUidObserver observer)
            throws RemoteException;

    abstract class Stub extends Binder implements IActivityManager {

        public static IActivityManager asInterface(IBinder obj) {
            throw new RuntimeException("STUB");
        }
    }
}
