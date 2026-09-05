package com.example.printshop.config;

import com.example.printshop.security.AuthInterceptor;
import com.example.printshop.security.RateLimitInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.io.File;
import java.util.Arrays;

@Configuration
public class WebConfig implements WebMvcConfigurer {
    private final AuthInterceptor authInterceptor;
    private final RateLimitInterceptor rateLimitInterceptor;
    private final String uploadDirProperty;
    private final String allowedOrigins;

    public WebConfig(AuthInterceptor authInterceptor,
                     RateLimitInterceptor rateLimitInterceptor,
                     @Value("${printshop.file.upload-dir:}") String uploadDirProperty,
                     @Value("${printshop.security.allowed-origins}") String allowedOrigins) {
        this.authInterceptor = authInterceptor;
        this.rateLimitInterceptor = rateLimitInterceptor;
        this.uploadDirProperty = uploadDirProperty;
        this.allowedOrigins = allowedOrigins;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(rateLimitInterceptor)
                .addPathPatterns("/api/**")
                .order(0);
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/api/**")
                .order(1);
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOriginPatterns(Arrays.stream(allowedOrigins.split(","))
                        .map(String::trim)
                        .filter(origin -> !origin.isBlank())
                        .toArray(String[]::new))
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(false)
                .maxAge(3600);
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String uploadDir = resolveUploadDir();
        if (!uploadDir.endsWith("/")) {
            uploadDir += "/";
        }

        ensureDir(uploadDir + "avatars/");
        ensureDir(uploadDir + "stores/");

        registry.addResourceHandler("/api/public-files/avatars/**")
                .addResourceLocations("file:" + uploadDir + "avatars/");
        registry.addResourceHandler("/api/public-files/stores/**")
                .addResourceLocations("file:" + uploadDir + "stores/");
    }

    private String resolveUploadDir() {
        if (uploadDirProperty != null && !uploadDirProperty.isBlank()) {
            ensureDir(uploadDirProperty);
            return uploadDirProperty;
        }
        String productionDir = "/www/wwwroot/printshop/files/";
        File dir = new File(productionDir);
        if (dir.exists()) {
            return productionDir;
        }
        String localDir = System.getProperty("user.dir") + "/files/";
        ensureDir(localDir);
        return localDir;
    }

    private void ensureDir(String path) {
        File dir = new File(path);
        if (!dir.exists()) {
            dir.mkdirs();
        }
    }
}
