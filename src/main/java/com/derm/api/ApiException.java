package com.derm.api;

import java.util.Map;

public class ApiException extends RuntimeException {
    public final int status;
    public final String code;
    public final Map<String,Object> details;
    /** 初始化本组件所需的依赖与配置。 */
    public ApiException(int status, String code, String message) { this(status,code,message,Map.of()); }
    /** 初始化本组件所需的依赖与配置。 */
    public ApiException(int status, String code, String message, Map<String,Object> details) {
        super(message); this.status=status; this.code=code; this.details=details;
    }
    /** 构造字段或业务校验失败的422异常。 */
    public static ApiException invalid(String message) { return new ApiException(422,"VALIDATION_ERROR",message); }
    /** 构造不泄露资源存在性的404异常。 */
    public static ApiException missing() { return new ApiException(404,"NOT_FOUND","资源不存在或不可访问"); }
}
