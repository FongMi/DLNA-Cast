package com.neulion.android.upnpcast.controller;

import android.os.Handler;
import android.os.Looper;
import android.support.annotation.CallSuper;
import android.support.annotation.NonNull;
import android.text.TextUtils;

import com.neulion.android.upnpcast.util.CastUtils;
import com.neulion.android.upnpcast.util.ILogger;
import com.neulion.android.upnpcast.util.ILogger.DefaultLoggerImpl;

import org.fourthline.cling.controlpoint.SubscriptionCallback;
import org.fourthline.cling.model.gena.CancelReason;
import org.fourthline.cling.model.gena.GENASubscription;
import org.fourthline.cling.model.message.UpnpResponse;
import org.fourthline.cling.model.meta.Service;
import org.fourthline.cling.support.avtransport.lastchange.AVTransportLastChangeParser;
import org.fourthline.cling.support.avtransport.lastchange.AVTransportVariable;
import org.fourthline.cling.support.avtransport.lastchange.AVTransportVariable.RelativeTimePosition;
import org.fourthline.cling.support.lastchange.LastChange;
import org.fourthline.cling.support.lastchange.LastChangeParser;
import org.fourthline.cling.support.model.TransportState;
import org.fourthline.cling.support.renderingcontrol.lastchange.RenderingControlLastChangeParser;
import org.fourthline.cling.support.renderingcontrol.lastchange.RenderingControlVariable.Volume;

import java.util.Map;

/**
 * User: liuwei(wei.liu@neulion.com.com)
 * Date: 2018-07-04
 * Time: 15:47
 */
public abstract class BaseCastEventSubscription extends SubscriptionCallback
{
    protected Handler mHandler = new Handler(Looper.getMainLooper());

    protected ILogger mLogger = new DefaultLoggerImpl(getClass().getSimpleName() + "@" + Integer.toHexString(getClass().hashCode()));

    protected ICastControlListener mControlListener;

    protected EventCallbackListener mEventCallback;

    public interface EventCallbackListener
    {
        void established(GENASubscription subscription);

        void ended(GENASubscription subscription, CancelReason reason, UpnpResponse responseStatus);

        void failed(GENASubscription subscription, UpnpResponse responseStatus, Exception exception, String defaultMsg);
    }

    BaseCastEventSubscription(Service service, ICastControlListener listener, EventCallbackListener eventCallback)
    {
        super(service);

        mControlListener = listener;

        mEventCallback = eventCallback;
    }

    @Override
    @CallSuper
    protected void failed(GENASubscription subscription, UpnpResponse responseStatus, Exception exception, String defaultMsg)
    {
        mLogger.e(String.format("[failed] [%s][%s]", subscription, defaultMsg));

        mEventCallback.failed(subscription, responseStatus, exception, defaultMsg);
    }

    @Override
    @CallSuper
    protected void established(GENASubscription subscription)
    {
        mLogger.i(String.format("[established] [%s]", subscription));

        mEventCallback.established(subscription);
    }

    @Override
    @CallSuper
    protected void ended(GENASubscription subscription, CancelReason reason, UpnpResponse responseStatus)
    {
        mLogger.i(String.format("[ended] [%s][%s][%s]", subscription, reason, responseStatus));

        mEventCallback.ended(subscription, reason, responseStatus);
    }

    @Override
    protected void eventReceived(GENASubscription subscription)
    {
        mLogger.d(String.format("[eventReceived] [%s]", subscription));

        mLogger.d(String.format("\r[currentValues] [%s]", subscription.getCurrentValues().keySet()));

        String lastChange = parseLastChange(subscription);

        if (!TextUtils.isEmpty(lastChange))
        {
            LastChange action = null;

            try
            {
                action = new LastChange(getLastChangeParser(), lastChange);
            }
            catch (Exception e)
            {
                e.printStackTrace();

                mLogger.e(String.format("lastChanged={%s}", lastChange));

                mLogger.e(e.getMessage());
            }

            if (action != null)
            {
                processLastChange(action);
            }
        }
    }

    @CallSuper
    protected void processLastChange(@NonNull LastChange lastChange)
    {
        mLogger.d("\r" + lastChange.toString());
    }

    @Override
    protected void eventsMissed(GENASubscription subscription, int numberOfMissedEvents)
    {
        mLogger.d(String.format("[eventsMissed] [%s][%s]", subscription, numberOfMissedEvents));
    }

    protected abstract LastChangeParser getLastChangeParser();

    protected String parseLastChange(GENASubscription subscription)
    {
        Map currentValues = subscription.getCurrentValues();

        if (currentValues != null && currentValues.containsKey("LastChange"))
        {
            return currentValues.get("LastChange").toString();
        }

        return null;
    }

    void notifyCallback(Runnable runnable)
    {
        if (Thread.currentThread() != Looper.getMainLooper().getThread())
        {
            mHandler.post(runnable);
        }
        else
        {
            runnable.run();
        }
    }

    // -------------------------------------------------------------------------------------------
    // Control listener
    // -------------------------------------------------------------------------------------------
    public static class AvTransportSubscription extends BaseCastEventSubscription
    {
        private LastChangeParser mLastChangeParser;

        public AvTransportSubscription(Service service, ICastControlListener listener, EventCallbackListener eventCallback)
        {
            super(service, listener, eventCallback);

            mLastChangeParser = new AVTransportLastChangeParser();
        }

        @Override
        protected LastChangeParser getLastChangeParser()
        {
            return mLastChangeParser;
        }

        @Override
        protected void processLastChange(@NonNull LastChange lastChange)
        {
            super.processLastChange(lastChange);

            //Parse TransportState value.
            AVTransportVariable.TransportState transportState = lastChange.getEventedValue(0, AVTransportVariable.TransportState.class);

            if (transportState != null)
            {
                TransportState ts = transportState.getValue();

                if (ts == TransportState.PLAYING)
                {
                    notifyCallback(new Runnable()
                    {
                        @Override
                        public void run()
                        {
                            mControlListener.onStart();
                        }
                    });
                }
                else if (ts == TransportState.PAUSED_PLAYBACK)
                {
                    notifyCallback(new Runnable()
                    {
                        @Override
                        public void run()
                        {
                            mControlListener.onPause();
                        }
                    });
                }
                else if (ts == TransportState.STOPPED)
                {
                    notifyCallback(new Runnable()
                    {
                        @Override
                        public void run()
                        {
                            mControlListener.onStop();
                        }
                    });
                }

                return;
            }

            //RelativeTimePosition
            String position;

            RelativeTimePosition relativeTimePosition = lastChange.getEventedValue(0, RelativeTimePosition.class);

            if (relativeTimePosition != null)
            {
                position = lastChange.getEventedValue(0, RelativeTimePosition.class).getValue();

                final long intTime = CastUtils.getIntTime(position);

                notifyCallback(new Runnable()
                {
                    @Override
                    public void run()
                    {
                        mControlListener.onSeekTo(intTime);
                    }
                });
            }
        }
    }

    public static class RenderSubscription extends BaseCastEventSubscription
    {
        private LastChangeParser mLastChangeParser;

        public RenderSubscription(Service service, ICastControlListener listener, EventCallbackListener eventCallback)
        {
            super(service, listener, eventCallback);

            mLastChangeParser = new RenderingControlLastChangeParser();
        }

        @Override
        protected LastChangeParser getLastChangeParser()
        {
            return mLastChangeParser;
        }

        @Override
        protected void processLastChange(@NonNull LastChange lastChange)
        {
            super.processLastChange(lastChange);

            final Volume volume = lastChange.getEventedValue(0, Volume.class);

            if (volume != null)
            {
                notifyCallback(new Runnable()
                {
                    @Override
                    public void run()
                    {
                        mControlListener.onVolume(volume.getValue().getVolume());
                    }
                });
            }
        }
    }
}
