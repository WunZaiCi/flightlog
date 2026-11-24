package com.flightlog.common;

import java.time.Duration;
import java.util.Arrays;
import org.springframework.boot.autoconfigure.web.WebProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 由后端托管前端静态文件，位置取 {@code spring.web.resources.static-locations}（默认 classpath:/static/）。
 *
 * <ul>
 *   <li>{@code /_next/static/**}：文件名带内容哈希，长期缓存（immutable）。
 *   <li>其他文件和页面：{@code no-cache}，每次用内容 ETag 重新验证，部署新版本后立刻生效。前端路由交给 {@link SpaResourceResolver}。
 * </ul>
 *
 * <p>Spring Boot 默认的 {@code /**} 静态映射已用 {@code spring.web.resources.add-mappings=false} 关闭。
 */
@Configuration
public class SpaWebConfig implements WebMvcConfigurer {

  private static final CacheControl IMMUTABLE =
      CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable();

  private final String[] staticLocations;

  public SpaWebConfig(WebProperties webProperties) {
    this.staticLocations = webProperties.getResources().getStaticLocations();
  }

  @Override
  public void addResourceHandlers(ResourceHandlerRegistry registry) {
    String[] nextStaticLocations =
        Arrays.stream(staticLocations)
            .map(location -> (location.endsWith("/") ? location : location + "/") + "_next/static/")
            .toArray(String[]::new);

    registry
        .addResourceHandler("/_next/static/**")
        .addResourceLocations(nextStaticLocations)
        .setCacheControl(IMMUTABLE)
        .setUseLastModified(false)
        .resourceChain(true);

    registry
        .addResourceHandler("/**")
        .addResourceLocations(staticLocations)
        .setCacheControl(CacheControl.noCache())
        .setUseLastModified(false)
        .setEtagGenerator(new ContentEtagGenerator())
        .resourceChain(true)
        .addResolver(new SpaResourceResolver());
  }
}
