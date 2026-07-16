/*
 * This file adapts HyperCeiler's ProvisionAnimHelper for an embedding app.
 * The original implementation is Copyright (C) 2023-2026 HyperCeiler Contributions
 * and licensed under AGPL-3.0-or-later.
 */
package fan.provision;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;

import com.sevtinge.hyperceiler.provision.IAnimCallback;
import com.sevtinge.hyperceiler.provision.IProvisionAnim;

public class ProvisionAnimHelper {

    private static final String TAG = "ProvisionAnimHelper";
    private static final String ANIM_END_ACTION = "fan.action.PROVISION_ANIM_END";
    private static final String SERVICE_CLASS =
        "com.sevtinge.hyperceiler.provision.service.ProvisionAnimService";

    private int animY;
    private int skipOrNext;
    private final Context context;
    private final Handler handler;
    private AnimListener listener;
    private IProvisionAnim proxy;
    private boolean receiverRegistered;
    private boolean serviceBound;

    private final IAnimCallback callback = new IAnimCallback.Stub() {
        @Override
        public void onNextAminStart() {
            handler.post(() -> {
                if (listener == null) return;
                if (skipOrNext == 0) listener.onNextAminStart();
                else if (skipOrNext == 1) listener.onSkipAminStart();
            });
        }

        @Override
        public void onBackAnimStart() {
            handler.postDelayed(() -> {
                if (listener != null) listener.onBackAnimStart();
            }, 30L);
        }
    };

    private final ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            proxy = IProvisionAnim.Stub.asInterface(service);
            try {
                proxy.registerRemoteCallback(callback);
                if (listener != null) listener.onAminServiceConnected();
            } catch (Exception error) {
                Log.w(TAG, "Unable to register animation callback", error);
                proxy = null;
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            proxy = null;
        }
    };

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context ignored, Intent intent) {
            if (intent != null && ANIM_END_ACTION.equals(intent.getAction()) && listener != null) {
                listener.onAminEnd();
            }
        }
    };

    public interface AnimListener {
        void onAminServiceConnected();
        void onBackAnimStart();
        void onNextAminStart();
        void onSkipAminStart();
        void onAminEnd();
    }

    public ProvisionAnimHelper(Context context, Handler handler) {
        this.context = context;
        this.handler = handler;
    }

    public void setAnimListener(AnimListener listener) {
        this.listener = listener;
    }

    public boolean isAnimEnded() {
        if (proxy == null) return true;
        try {
            return proxy.isAnimEnd();
        } catch (Exception error) {
            Log.w(TAG, "Unable to read animation state", error);
            return true;
        }
    }

    public boolean goNextStep(int step) {
        skipOrNext = step;
        if (proxy == null) {
            dispatchNextFallback(step);
            return false;
        }
        try {
            proxy.playNextAnim(animY);
            return true;
        } catch (Exception error) {
            Log.w(TAG, "Unable to play next animation", error);
            dispatchNextFallback(step);
            return false;
        }
    }

    public boolean goBackStep() {
        if (proxy == null) {
            handler.post(() -> {
                if (listener != null) listener.onBackAnimStart();
            });
            return false;
        }
        try {
            proxy.playBackAnim(animY);
            return true;
        } catch (Exception error) {
            Log.w(TAG, "Unable to play back animation", error);
            handler.post(() -> {
                if (listener != null) listener.onBackAnimStart();
            });
            return false;
        }
    }

    public void setAnimY(int animY) {
        this.animY = animY;
    }

    public void registerAnimService() {
        if (context == null) return;
        try {
            context.registerReceiver(
                receiver,
                new IntentFilter(ANIM_END_ACTION),
                Context.RECEIVER_EXPORTED
            );
            receiverRegistered = true;
        } catch (Exception error) {
            Log.w(TAG, "Unable to register animation receiver", error);
        }

        Intent intent = new Intent();
        intent.setClassName(context.getPackageName(), SERVICE_CLASS);
        try {
            serviceBound = context.bindService(intent, connection, Context.BIND_AUTO_CREATE);
        } catch (Exception error) {
            serviceBound = false;
            Log.w(TAG, "Unable to bind embedded animation service", error);
        }
    }

    public void unregisterAnimService() {
        if (proxy != null) {
            try {
                proxy.unregisterRemoteCallback(callback);
            } catch (Exception error) {
                Log.w(TAG, "Unable to unregister animation callback", error);
            }
        }
        proxy = null;
        if (context != null && serviceBound) {
            try {
                context.unbindService(connection);
            } catch (Exception error) {
                Log.w(TAG, "Unable to unbind animation service", error);
            }
        }
        serviceBound = false;
        if (context != null && receiverRegistered) {
            try {
                context.unregisterReceiver(receiver);
            } catch (Exception error) {
                Log.w(TAG, "Unable to unregister animation receiver", error);
            }
        }
        receiverRegistered = false;
    }

    private void dispatchNextFallback(int step) {
        handler.post(() -> {
            if (listener == null) return;
            if (step == 0) listener.onNextAminStart();
            else if (step == 1) listener.onSkipAminStart();
        });
    }
}
