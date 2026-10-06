package com.forge.controlplane.build;

public enum BuildStatus {

    QUEUED,
    RUNNING,
    SUCCESS,
    FAILED,
    TIMEOUT,
    CANCELLED
}