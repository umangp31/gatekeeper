package com.gatekeeper.rbac;

import com.gatekeeper.common.ApiException;
import org.springframework.http.HttpStatus;

public class CyclicHierarchyException extends ApiException {

    public CyclicHierarchyException() {
        super(HttpStatus.CONFLICT, "This edge would introduce a cycle in the role hierarchy");
    }
}
