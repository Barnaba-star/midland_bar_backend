package com.midland.bar.Monitoring.Dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** What the browser sends when something blew up on its side. */
@Getter
@Setter
@NoArgsConstructor
public class ClientErrorDTO {
    private String level;
    private String message;
    private String exceptionType;
    private String stackTrace;
    /** Router URL the user was on, or the failed request's URL. */
    private String path;
    private String httpMethod;
    /** Body the browser was sending when the request failed. */
    private String payload;
}
