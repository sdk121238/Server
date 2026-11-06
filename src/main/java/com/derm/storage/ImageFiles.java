package com.derm.storage;

import com.derm.api.*;
import com.derm.auth.Crypto;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import javax.imageio.*;
import javax.imageio.stream.ImageInputStream;

@Component
public class ImageFiles {
    public static final long MAX_BYTES=104857600;
    public static final long MAX_PIXELS=40_000_000;
    private final Path root;
    /** 初始化私有图片存储目录。 */
    public ImageFiles(@Value("${app.storage-root}")String root) throws IOException { this.root=Path.of(root).toAbsolutePath().normalize();Files.createDirectories(this.root); }
    /** 校验UUID后生成私有存储路径，避免任意路径访问。 */
    public Path path(String id,String suffix) { Contract.uuid(id);return root.resolve(id+suffix); }
    /** 流式接收图片到临时文件，限制实际字节数并清理失败文件。 */
    public Path receive(String upload,InputStream input,long expected) throws IOException {
        Path temporary=path(upload,"."+UUID.randomUUID()+".tmp");
        try(var output=Files.newOutputStream(temporary,StandardOpenOption.CREATE_NEW)) {
            byte[] buffer=new byte[16384];long size=0;int count;
            while((count=input.read(buffer))!=-1) {
                size+=count;if(size>MAX_BYTES||size>expected) throw new ApiException(413,"FILE_TOO_LARGE","上传字节超出声明大小");output.write(buffer,0,count);
            }
            if(size!=expected) throw ApiException.invalid("上传字节长度与声明不一致");
        } catch(Exception e) { Files.deleteIfExists(temporary);throw e; }
        return temporary;
    }
    /** 将接收完成的临时文件移入待确认位置。 */
    public void stage(Path temporary,String upload) throws IOException { Files.move(temporary,path(upload,".staging"),StandardCopyOption.REPLACE_EXISTING); }
    /** 校验文件长度、哈希、真实类型及尺寸，成功后确认为正式图片。 */
    public void confirm(String upload,String file,JsonNode metadata) {
        Path staged=path(upload,".staging"),ready=path(file,".image");
        Path source=Files.isRegularFile(staged)?staged:ready;
        if(!Files.isRegularFile(source)) throw new ApiException(409,"FILE_NOT_READY","图片字节尚未上传");
        try {
            if(Files.size(source)!=metadata.get("sizeBytes").asLong()) throw ApiException.invalid("文件长度校验失败");
            MessageDigest digest=MessageDigest.getInstance("SHA-256");
            try(var input=Files.newInputStream(source)) { byte[] buffer=new byte[16384];int n;while((n=input.read(buffer))!=-1) digest.update(buffer,0,n); }
            if(!HexFormat.of().formatHex(digest.digest()).equals(metadata.get("sha256").asText())) throw new ApiException(422,"CHECKSUM_MISMATCH","文件SHA-256校验失败");
            try(ImageInputStream input=ImageIO.createImageInputStream(source.toFile())) {
                Iterator<ImageReader> readers=ImageIO.getImageReaders(input);
                if(!readers.hasNext()) throw new ApiException(415,"UNSUPPORTED_MEDIA_TYPE","图片不能解码");
                ImageReader reader=readers.next();
                try {
                    reader.setInput(input,true,true);
                    String format=reader.getFormatName().toLowerCase(Locale.ROOT);
                    String mime=switch(format) { case "png"->"image/png";case "jpeg","jpg"->"image/jpeg";default->"unsupported"; };
                    if(!mime.equals(metadata.get("mimeType").asText())) throw new ApiException(415,"UNSUPPORTED_MEDIA_TYPE","实际图片类型与声明不一致");
                    int width=reader.getWidth(0),height=reader.getHeight(0);
                    if((long)width*height>MAX_PIXELS||width!=metadata.get("width").asInt()||height!=metadata.get("height").asInt()) throw ApiException.invalid("图片尺寸不匹配或超过解码上限");
                    if(reader.read(0)==null) throw ApiException.invalid("图片解码失败");
                } finally { reader.dispose(); }
            }
            if(!source.equals(ready)) Files.move(source,ready,StandardCopyOption.ATOMIC_MOVE);
        } catch(ApiException e) { throw e; } catch(Exception e) { throw ApiException.invalid("图片校验失败"); }
    }
}
