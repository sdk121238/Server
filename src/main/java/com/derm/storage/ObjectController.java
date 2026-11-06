package com.derm.storage;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;
import org.springframework.core.io.FileSystemResource;
import java.io.IOException;

@RestController
@RequestMapping("/objects")
public class ObjectController {
    private final UploadService uploads;
    /** 初始化本组件所需的依赖与配置。 */
    public ObjectController(UploadService uploads) { this.uploads=uploads; }
    /** 接收签名URL的原始图片字节，成功返回204。 */
    @PutMapping("/{id}") public ResponseEntity<Void> put(@PathVariable String id,@RequestParam String ticket,HttpServletRequest request) throws IOException {
        uploads.put(id,ticket,request.getContentType(),request.getInputStream());return ResponseEntity.noContent().header("Cache-Control","no-store").build();
    }
    /** 验证签名下载请求，以私有且不缓存的响应返回图片文件。 */
    @GetMapping("/{id}") public ResponseEntity<FileSystemResource> get(@PathVariable String id,@RequestParam String ticket) {
        var value=uploads.get(id,ticket);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(value.mimeType())).contentLength(value.size())
            .header("Cache-Control","private, no-store").header("Content-Disposition","attachment; filename=\""+id+"\"")
            .body(new FileSystemResource(value.path()));
    }
}
