package com.derm.auth;
/** 已认证访问者的账号与会话标识；实际角色从数据库实时读取。 */
public record Actor(String userId,String sessionId) {}
