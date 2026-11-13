package com.midland.bar.Config.AttachConfig;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@lombok.RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final com.midland.bar.Bar.Live.LivePublishInterceptor livePublishInterceptor;

    @Value("${file.upload-dir}")
    private String uploadDir;

    @Override
    public void addInterceptors(org.springframework.web.servlet.config.annotation.InterceptorRegistry registry) {
        registry.addInterceptor(livePublishInterceptor).addPathPatterns("/bar/**");
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Profile pictures and logos were fetched in full on every screen
        // (194 KB each time). The name stays the same when a picture is
        // replaced, so the browser keeps it but asks first: unchanged, the
        // answer is an empty 304 (Last-Modified) instead of the whole file.
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations("file:" + uploadDir)
                .setCacheControl(org.springframework.http.CacheControl.noCache().cachePublic());
    }
}

