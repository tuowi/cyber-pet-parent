package com.heima.shop.controller;

import com.heima.common.domain.Result;
import com.heima.common.util.UserContextHolder;
import com.heima.shop.domain.po.Product;
import com.heima.shop.properties.IconPathProperties;
import com.heima.shop.service.IProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Tag(name = "商品图标接口")
@RestController
@RequestMapping("/shop/upload")
@RequiredArgsConstructor
public class ProductIconController {

    /**
     * 最大文件大小：2MB
     */
    private static final long MAX_FILE_SIZE = 2 * 1024 * 1024;

    /**
     * 允许的 MIME 类型
     */
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/jpeg", "image/png", "image/gif", "image/bmp"
    );

    private final IconPathProperties iconPathProperties;

    private final String SHOP_PATH = "/avatars/shop/";

    private final IProductService productService;

    @Operation(summary = "头像上传")
    @PostMapping("/icon")
    public Result<String> upload(@RequestParam("productId") Long productId,
                                 @RequestParam("file") MultipartFile file) {
        // 校验用户是否管理员
        if (!UserContextHolder.isAdmin()){
            return Result.fail("该用户不是管理员");
        }
        // 校验商品是否存在
        Product product = productService.getById(productId);
        if (product==null){
            return Result.fail("该商品不存在");
        }

        // 1. 校验文件非空
        if (file == null || file.isEmpty()) {
            log.error("上传文件为空");
            return Result.fail("请选择要上传的文件");
        }

        // 2. 校验文件大小（服务端二次校验，application.yaml 也有限制）
        if (file.getSize() > MAX_FILE_SIZE) {
            log.error("文件过大：{} bytes", file.getSize());
            return Result.fail("文件大小不能超过 2MB");
        }

        log.info("开始上传头像：size={} bytes, contentType={}", file.getSize(), file.getContentType());

        // 3. 校验 MIME 类型（防伪造扩展名）
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType)) {
            log.error("不支持的 Content-Type：{}", contentType);
            return Result.fail("仅支持 jpg、jpeg、png、gif、bmp 格式的图片");
        }

        try {
            // 4. 校验文件魔数（magic bytes），防内容伪造
            if (!isValidImageFile(file)) {
                log.error("文件魔数校验失败，可能为伪造文件");
                return Result.fail("文件格式不合法");
            }

            // 5. 获取配置路径
            String avatarPath = iconPathProperties.getProductPath();
            if (avatarPath == null || avatarPath.isBlank()) {
                log.error("头像保存路径未配置，请检查 application.yml 中的 cyber.avatar.auth-path 配置");
                return Result.fail("文件保存路径未配置");
            }

            // 6. 规范化路径
            Path uploadPath = Paths.get(avatarPath).normalize();
            log.info("文件保存路径：{}", uploadPath.toAbsolutePath());

            // 7. 创建目录（如果不存在）
            if (!Files.exists(uploadPath)) {
                Files.createDirectories(uploadPath);
                log.info("创建目录：{}", uploadPath);
            }

            // 8. 生成唯一文件名
            String originalFilename = file.getOriginalFilename();
            String suffix = "";
            if (originalFilename != null && originalFilename.contains(".")) {
                suffix = originalFilename.substring(originalFilename.lastIndexOf("."));
                String allowedExt = suffix.toLowerCase();
                if (!allowedExt.matches("\\.(jpg|jpeg|png|gif|bmp)$")) {
                    log.error("不支持的文件格式：{}", suffix);
                    return Result.fail("仅支持 jpg、jpeg、png、gif、bmp 格式的图片");
                }
            }

            String fileName = UUID.randomUUID() + suffix;
            Path filePath = uploadPath.resolve(fileName);

            // 9. 保存文件到磁盘
            file.transferTo(filePath.toFile());
            log.info("文件保存成功：{}", filePath.toAbsolutePath());

            // 10. 保存头像路径到数据库
            productService.lambdaUpdate()
                    .set(Product::getIcon,fileName)
                    .set(Product::getUpdateTime, LocalDateTime.now())
                    .eq(Product::getId,productId)
                    .update();

            // 11. 返回访问地址
            String url = SHOP_PATH + fileName;
            return Result.ok(url);

        } catch (IOException e) {
            log.error("文件写入磁盘失败", e);
            return Result.fail("文件上传失败，请稍后重试");
        }
    }

    /**
     * 校验文件魔数（magic bytes），确保文件内容与扩展名匹配。
     * 仅凭扩展名和 Content-Type 可以被伪造，魔数是文件头的固有特征。
     */
    private boolean isValidImageFile(MultipartFile file) throws IOException {
        byte[] header = new byte[8];
        try (InputStream is = file.getInputStream()) {
            int read = is.read(header);
            if (read < 4) {
                return false;
            }
        }

        // JPEG: FF D8 FF
        if ((header[0] & 0xFF) == 0xFF && (header[1] & 0xFF) == 0xD8 && (header[2] & 0xFF) == 0xFF) {
            return true;
        }
        // PNG: 89 50 4E 47 0D 0A 1A 0A
        if (header[0] == (byte) 0x89 && header[1] == 0x50 && header[2] == 0x4E && header[3] == 0x47) {
            return true;
        }
        // GIF: 47 49 46 38 (GIF8)
        if (header[0] == 0x47 && header[1] == 0x49 && header[2] == 0x46 && header[3] == 0x38) {
            return true;
        }
        // BMP: 42 4D (BM)
        if (header[0] == 0x42 && header[1] == 0x4D) {
            return true;
        }

        return false;
    }
}
