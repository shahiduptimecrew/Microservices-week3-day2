package com.example.order.client;

import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.util.concurrent.TimeoutException;

/** Helpers to classify exceptions thrown by downstream calls. */
final class DownstreamErrors {

    private DownstreamErrors() {
    }

    /** True if the call failed because the downstream service was too slow. */
    static boolean isTimeout(Throwable t) {
        for (Throwable cur = t; cur != null; cur = cur.getCause()) {
            if (cur instanceof SocketTimeoutException
                    || cur instanceof HttpTimeoutException
                    || cur instanceof TimeoutException) {
                return true;
            }
        }
        return false;
    }

    /** Returns the first cause of the given type, or null. */
    static <T extends Throwable> T findCause(Throwable t, Class<T> type) {
        for (Throwable cur = t; cur != null; cur = cur.getCause()) {
            if (type.isInstance(cur)) {
                return type.cast(cur);
            }
        }
        return null;
    }
}
