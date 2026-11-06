package com.derm.api;
import java.util.Map;
public final class Preconditions {
    /** 禁止实例化仅提供静态版本校验方法的工具类。 */
    private Preconditions() {}
    /** 校验If-Match是否等于当前资源版本，区分缺失与版本冲突。 */
    public static void check(String match,long revision,String id) {
        if(match==null) throw new ApiException(428,"PRECONDITION_REQUIRED","请提供If-Match");
        if(!match.matches("\"[1-9][0-9]*\"")) throw new ApiException(400,"VALIDATION_ERROR","If-Match格式不合法");
        if(!match.equals("\""+revision+"\"")) throw new ApiException(412,"REVISION_CONFLICT","记录已变更，请先拉取最新版本",Map.of("resourceId",id,"currentRevision",revision));
    }
}
