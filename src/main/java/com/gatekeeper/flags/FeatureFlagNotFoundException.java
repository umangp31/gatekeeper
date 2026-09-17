package com.gatekeeper.flags;

import com.gatekeeper.common.ApiException;
import org.springframework.http.HttpStatus;

public class FeatureFlagNotFoundException extends ApiException {

    public FeatureFlagNotFoundException() {
        super(HttpStatus.NOT_FOUND, "Feature flag not found");
    }
}
