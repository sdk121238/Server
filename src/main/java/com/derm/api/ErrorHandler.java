package com.derm.api;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.slf4j.LoggerFactory;

@RestControllerAdvice
public class ErrorHandler {
    private final Json json;
    public ErrorHandler(Json json) { this.json=json; }
    public ApiResult error(ApiException e,String requestId) {
        var body=json.object().put("code",e.code).put("message",e.getMessage()).put("requestId",requestId);
        body.set("details",json.mapper.valueToTree(e.details));
        return new ApiResult(e.status,body,null);
    }
    @ExceptionHandler(ApiException.class) ResponseEntity<?> api(ApiException e,HttpServletRequest r) { return error(e,RequestIds.id(r)).response(); }
    @ExceptionHandler({HttpMessageNotReadableException.class,IllegalArgumentException.class})
    ResponseEntity<?> invalid(Exception e,HttpServletRequest r) { return error(new ApiException(400,"VALIDATION_ERROR","请求格式不合法"),RequestIds.id(r)).response(); }
    @ExceptionHandler({NoResourceFoundException.class,HttpRequestMethodNotSupportedException.class})
    ResponseEntity<?> missing(Exception e,HttpServletRequest r) { return error(ApiException.missing(),RequestIds.id(r)).response(); }
    @ExceptionHandler(Exception.class) ResponseEntity<?> internal(Exception e,HttpServletRequest r) {
        LoggerFactory.getLogger(ErrorHandler.class).error("Request {} failed: {}",RequestIds.id(r),e.getClass().getSimpleName());
        return error(new ApiException(500,"SERVER_ERROR","服务暂时不可用"),RequestIds.id(r)).response();
    }
}
