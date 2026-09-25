package com.midland.bar.Utils.Responses;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;


@NoArgsConstructor
@Getter
@Setter
public class Response<T> {
    private T data;
    private String message;
    private ResponseStatus status;

    public Response(T data){
        this.data = data;
        status = ResponseStatus.SUCCESS;
    }

    public  Response(String message){
        this.message = message;
        this.status = ResponseStatus.WARNING;
    }

    public Response(T data, String message){
        this.data = data;
        this.message = message;
        this.status = ResponseStatus.SUCCESS;
    }

    public Response(T data, String message, ResponseStatus status){
        this.data = data;
        this.message = message;
        this.status = status;
    }
}
