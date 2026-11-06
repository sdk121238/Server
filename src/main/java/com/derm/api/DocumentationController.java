package com.derm.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Publishes the implemented contract rather than inferring operations from the catch-all router. */
@Controller
public class DocumentationController {
    private final Contract contract;
    private final Json json;

    /** 初始化本组件所需的依赖与配置。 */
    public DocumentationController(Contract contract, Json json) {
        this.contract = contract;
        this.json = json;
    }

    /** 将文档入口重定向到本地Swagger调试页面。 */
    @GetMapping({"/swagger-ui.html", "/swagger-ui", "/swagger-ui/"})
    public String page() { return "redirect:/swagger-ui/index.html"; }

    /** 导出当前已实现的接口定义，并补充本机地址和测试示例。 */
    @GetMapping(value = "/v3/api-docs", produces = "application/json")
    @ResponseBody
    public ResponseEntity<JsonNode> documentation(HttpServletRequest request) {
        ObjectNode document = (ObjectNode) contract.documentationCopy();
        ObjectNode paths = (ObjectNode) document.get("paths");
        List<String> emptyPaths = new ArrayList<>();
        paths.fields().forEachRemaining(path -> {
            ObjectNode operations = (ObjectNode) path.getValue();
            List<String> disabled = new ArrayList<>();
            operations.fields().forEachRemaining(op -> {
                if (!"P0".equals(op.getValue().path("x-priority").asText())) disabled.add(op.getKey());
            });
            operations.remove(disabled);
            if (operations.isEmpty()) emptyPaths.add(path.getKey());
        });
        paths.remove(emptyPaths);
        ObjectNode info = (ObjectNode) document.get("info");
        info.put("title", "皮肤病随访后端 · 患者与医生接口调试");
        info.put("description", "当前36个业务接口，另附2个图片传输操作。患者仅访问本账号数据；HOSPITAL_STAFF医生可查看全库，GET /patients?scope=mine筛选主治名下患者。医生不可修改其他账号数据。主治医师可空，先查询GET /doctors选择。年龄由出生信息计算，不能提交age。个人离线同步仅本人拥有数据。先登录，再将accessToken填入Authorize。修改带ETag与If-Match，新业务使用新Idempotency-Key。机构成员、审核、短信、分片与服务器算法尚未启用。Execute访问当前数据库，请使用虚构数据。");
        document.set("servers", json.array().add(json.object().put("url", request.getContextPath() + "/api/v1").put("description", "当前后端")));
        addExamples(document);
        addObjectOperations(paths, request.getContextPath());
        return ResponseEntity.ok().header("Cache-Control", "no-store").body(document);
    }

    /** 生成虚构且互相关联的UUID，为常用请求填充调试示例。 */
    private void addExamples(ObjectNode document) {
        String patient = UUID.randomUUID().toString();
        String lesion = UUID.randomUUID().toString();
        String capture = UUID.randomUUID().toString();
        String file = UUID.randomUUID().toString();
        var ids = Map.of("patientId", patient, "lesionId", lesion, "captureId", capture);
        document.path("paths").forEach(path -> path.forEach(operation -> {
            for (JsonNode parameter : operation.path("parameters")) {
                ObjectNode input = (ObjectNode) parameter;
                String name = input.path("name").asText();
                if (ids.containsKey(name)) input.put("example", ids.get(name));
                if (name.equals("Idempotency-Key")) input.put("example", UUID.randomUUID().toString());
                if (name.equals("If-Match")) input.put("example", "\"1\"");
            }
        }));
        ObjectNode schemas = (ObjectNode) document.path("components").path("schemas");
        example(schemas, "Login", json.object().put("account", "local-owner").put("password", "请填写本机API登录密码"));
        example(schemas, "PatientCreate", json.object().put("id", patient).put("name", "Swagger调试患者").put("birthYearMonth", "1990-01").put("phoneNumber","+8613800000000").put("gender","UNKNOWN").putNull("attendingDoctorId"));
        example(schemas, "LesionCreate", json.object().put("id", lesion).put("patientId", patient).put("bodySite", "左手背").put("note", "Swagger调试用虚构数据"));
        example(schemas, "UploadCreate", json.object().put("patientId", patient).put("sizeBytes", 83)
            .put("sha256", "0aedc9bece813993a4241852d4826e275f95d7cd3877a25e052922f2b84e3c0c")
            .put("mimeType", "image/png").put("width", 20).put("height", 20).put("strategy", "SINGLE"));
        example(schemas, "UploadComplete", json.object());
        ObjectNode captureExample = json.object().put("id", capture).put("lesionId", lesion).put("imageFileId", file)
            .put("capturedAt", System.currentTimeMillis()).put("deviceModel", "Swagger-Debug").put("source", "GALLERY")
            .put("assetKind", "CAPTURE_IMAGE").put("referenceOnly", false).putNull("calibration").putNull("analysis").putNull("annotation");
        captureExample.set("metadata", json.object());
        example(schemas, "CaptureCreate", captureExample);
        example(schemas, "ProfilePatch", json.object().put("displayName", "Swagger调试用户"));
        example(schemas, "PatientPatch", json.object().put("name", "Swagger调试患者（已修改）"));
        example(schemas, "LesionPatch", json.object().put("note", "调试备注已修改"));
        example(schemas, "CapturePatch", json.object().put("qualityNote", "Swagger调试完成"));
    }

    /** 为指定Schema写入示例值。 */
    private void example(ObjectNode schemas, String name, JsonNode value) {
        ((ObjectNode) schemas.get(name)).set("example", value);
    }

    /** 补充使用签名票据的图片二进制上传和下载接口。 */
    private void addObjectOperations(ObjectNode paths, String contextPath) {
        ObjectNode objects = json.object();
        for (String method : List.of("put", "get")) {
            boolean upload = method.equals("put");
            ObjectNode operation = json.object().put("operationId", upload ? "put_signed_image" : "get_signed_image")
                .put("summary", upload ? "上传原始图片（签名URL的二进制PUT）" : "下载原始图片（签名URL的GET）")
                .put("description", "从上传会话返回的 uploadUrl 或下载接口返回的 url 中，复制 /objects/ 后、? 前的UUID到 objectId，将 ticket= 后的完整值复制到 ticket。上传选择与声明一致的 Content-Type 和原始图片文件；PUT成功后仍须调用 complete。这里不填写完整URL，也不需要Bearer。")
                .put("x-documentation-only", true);
            operation.set("tags", json.array().add("图片传输"));
            operation.set("security", json.array());
            operation.set("servers", json.array().add(json.object().put("url", contextPath + "/")));
            var parameters = json.array();
            parameters.add(parameter("objectId", "path", "签名URL中的对象UUID：上传时为上传会话ID，下载时为文件ID", true));
            parameters.add(parameter("ticket", "query", "签名URL中ticket=后面的完整值", false));
            operation.set("parameters", parameters);
            if (upload) {
                ObjectNode media = json.object();
                for (String type : List.of("image/png", "image/jpeg")) {
                    ObjectNode entry = json.object();
                    entry.set("schema", json.object().put("type", "string").put("format", "binary"));
                    media.set(type, entry);
                }
                ObjectNode body = json.object().put("required", true);
                body.set("content", media);
                operation.set("requestBody", body);
            }
            ObjectNode responses = json.object();
            ObjectNode success = json.object().put("description", upload ? "图片字节已接收；继续调用complete" : "图片二进制");
            if (!upload) {
                ObjectNode media = json.object();
                ObjectNode entry = json.object();
                entry.set("schema", json.object().put("type", "string").put("format", "binary"));
                media.set("image/png", entry);
                media.set("image/jpeg", entry.deepCopy());
                success.set("content", media);
            }
            responses.set(upload ? "204" : "200", success);
            responses.set("default", json.object().put("description", "签名过期403，上传状态冲突409，文件类型错误415等"));
            operation.set("responses", responses);
            objects.set(method, operation);
        }
        paths.set("/objects/{objectId}", objects);
    }

    /** 生成图片路径标识或签名票据的参数定义。 */
    private ObjectNode parameter(String name, String in, String description, boolean uuid) {
        ObjectNode parameter = json.object().put("name", name).put("in", in).put("required", true).put("description", description);
        ObjectNode schema = json.object().put("type", "string");
        if (uuid) schema.put("format", "uuid");
        parameter.set("schema", schema);
        return parameter;
    }
}
