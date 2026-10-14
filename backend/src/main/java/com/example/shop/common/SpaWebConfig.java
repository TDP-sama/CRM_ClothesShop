package com.example.shop.common;

import java.io.IOException;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

/**
 * In production the built React app is packaged into {@code classpath:/static} (see the Dockerfile), so one server
 * serves both the SPA and the API on the same origin. Files that exist are served as-is; any other non-API path
 * (e.g. {@code /san-pham/ao-thun}) gets {@code index.html} so React Router can handle it. Controllers are matched
 * before this handler, so API routes are unaffected; unknown {@code /api/...} paths still 404.
 */
@Configuration
public class SpaWebConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/")
                .resourceChain(true)
                .addResolver(new PathResourceResolver() {
                    @Override
                    protected Resource getResource(String path, Resource location) throws IOException {
                        Resource requested = location.createRelative(path);
                        if (requested.exists() && requested.isReadable()) {
                            return requested;
                        }
                        if (path.startsWith("api/") || path.startsWith("v3/") || path.startsWith("swagger-ui")) {
                            return null;
                        }
                        Resource index = new ClassPathResource("/static/index.html");
                        return index.exists() ? index : null; // no SPA bundled (local dev uses Vite)
                    }
                });
    }
}
