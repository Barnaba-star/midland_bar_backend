package com.midland.bar.Utils.Responses;

public class ResponseBoolean<T>{
    private Boolean success;
    private String message;

    public ResponseBoolean(Boolean success){
        this.success = success;
    }

    public ResponseBoolean(String message){
        this.message = message;
    }

    public ResponseBoolean(Boolean success, String message){
        this.success = success;
        this.message =message;
    }
}
