package com.midland.bar.Utils.Responses;

public enum ResponseStatus {
    // Upper-case to match the frontend's ResponseStatus enum
    // (Response.ts) — Jackson serializes these as-is, and the
    // frontend compares against 'SUCCESS'/'ERROR'/'WARNING' exactly.
    SUCCESS, ERROR, WARNING
}
