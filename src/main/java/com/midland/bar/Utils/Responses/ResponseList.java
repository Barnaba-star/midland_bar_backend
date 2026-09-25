package com.midland.bar.Utils.Responses;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;
@Getter
@Setter
@NoArgsConstructor
public class ResponseList<T>{
    private List<T> data;
    private List<String> errors;
    private ResponseStatus status;
    private String message;

    public ResponseList(List<T> data){
        this.data = data;
        this.status = ResponseStatus.SUCCESS;
    }

    public ResponseList(List<T> data, String message){
        this.message = message;
        this.data = data;
        this.status = ResponseStatus.WARNING;
    }

    public ResponseList(List<T> data, String message, List<String> errors){
        this.message = message;
        this.data = data;
        this.status = ResponseStatus.WARNING;
        this.errors = errors;
    }
    public ResponseList(List<T> data,  List<String> errors){

        this.data = data;
        this.status = ResponseStatus.WARNING;
        this.errors = errors;
    }

    public ResponseList(String message){
        this.message = message;
    }


}
