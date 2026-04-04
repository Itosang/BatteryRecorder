package yangfentuozi.hiddenapi.compat;

import android.app.IUidObserver;
import android.os.Parcel;
import android.os.RemoteException;

import androidx.annotation.NonNull;

public class UidObserverAdapter extends IUidObserver.Stub {

    public final void onUidGone(int uid) throws RemoteException {
        onUidGone(uid, false);
    }

    /**
     * Report that there are no longer any processes running for a uid.
     *
     * @param disabled Added from 8.0
     */
    public void onUidGone(int uid, boolean disabled) throws RemoteException {

    }

    /**
     * Report that a uid is now active (no longer idle).
     */
    public void onUidActive(int uid) throws RemoteException {

    }

    public final void onUidIdle(int uid) throws RemoteException {
        onUidIdle(uid, false);
    }

    /**
     * Report that a uid is idle -- it has either been running in the background for
     * a sufficient period of time, or all of its processes have gone away.
     *
     * @param disabled Added from 8.0
     */
    public void onUidIdle(int uid, boolean disabled) throws RemoteException {

    }

    public final void onUidStateChanged(int uid, int procState) throws RemoteException {
        onUidStateChanged(uid, procState, 0);
    }

    public final void onUidStateChanged(int uid, int procState, long procStateSeq) throws RemoteException {
        onUidStateChanged(uid, procState, procStateSeq, 0);
    }

    /**
     * General report of a state change of an uid.
     *
     * @param uid          The uid for which the state change is being reported.
     * @param procState    The updated process state for the uid.
     * @param procStateSeq The sequence no. associated with process state change of the uid,
     *                     see UidRecord.procStateSeq for details.
     *                     Added from API 26 (8.0)
     * @param capability   the updated process capability for the uid.
     *                     Added from API 30 (11)
     */
    public void onUidStateChanged(int uid, int procState, long procStateSeq, int capability) throws RemoteException {

    }

    /**
     * Report when the cached state of a uid has changed.
     * If true, a uid has become cached -- that is, it has some active processes that are
     * all in the cached state.  It should be doing as little as possible at this point.
     * If false, that a uid is no longer cached.  This will only be called after
     * onUidCached() has been reported true.  It will happen when either one of its actively
     * running processes is no longer cached, or it no longer has any actively running processes.
     *
     * @since API 27 (8.1)
     */
    public void onUidCachedChanged(int uid, boolean cached) throws RemoteException {

    }

    @Override
    public boolean onTransact(int code, @NonNull Parcel data, Parcel reply, int flags) throws RemoteException {
        try {
            return super.onTransact(code, data, reply, flags);
        } catch (Throwable tr) {
            return true;
        }
    }
}
