package com.nilm.monitoring.notification;

public interface WebPushClient {

    PushResult send(PushSubscription subscription, byte[] payload);

    record PushResult(boolean accepted, boolean subscriptionGone, int statusCode, String error) {
        public static PushResult accepted(int statusCode) {
            return new PushResult(true, false, statusCode, null);
        }

        public static PushResult rejected(int statusCode, String error) {
            return new PushResult(false, statusCode == 404 || statusCode == 410, statusCode, error);
        }
    }
}
