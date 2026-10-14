package com.civicconnect.common.ratelimit;

import com.civicconnect.common.error.ApiException;
import com.civicconnect.common.error.ErrorCode;

/** 429 Too Many Requests. retryAfterSeconds is sent back in the Retry-After header. */
public class RateLimitedException extends ApiException {

    private final long retryAfterSeconds;

    public RateLimitedException(String detail, long retryAfterSeconds) {
        super(ErrorCode.TOO_MANY_REQUESTS, detail);
        this.retryAfterSeconds = Math.max(1, retryAfterSeconds);
    }

    public long getRetryAfterSeconds() { return retryAfterSeconds; }
}
