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
    public ObjectController(UploadService uploads) { this.uploads=uploads; }
    @PutMapping("/{id}") public ResponseEntity<Void> put(@PathVariable String id,@RequestParam String ticket,HttpServletRequest request) throws IOException {
        uploads.put(id,ticket,request.getContentType(),request.getInputStream());return ResponseEntity.noContent().header("Cache-Control","no-store").build();
    }
    @GetMapping("/{id}") public ResponseEntity<FileSystemResource> get(@PathVariable String id,@RequestParam String ticket) {
        var value=uploads.get(id,ticket);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(value.mimeType())).contentLength(value.size())
            .header("Cache-Control","private, no-store").header("Content-Disposition","attachment; filename=\""+id+"\"")
            .body(new FileSystemResource(value.path()));
    }
}
