package com.flightlog.common;

import java.io.IOException;
import java.util.List;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.resource.PathResourceResolver;

/**
 * 托管 Next.js 静态导出的解析规则。
 *
 * <ol>
 *   <li>{@code /api/**} 不参与回退，找不到就是 404。
 *   <li>文件存在就直接返回（JS、CSS、图片等）。
 *   <li>带扩展名但不存在的文件返回 404，避免把缺失的资源当成页面。
 *   <li>依次尝试 {@code path.html}、{@code path/index.html}，最后回退到 {@code index.html}。
 * </ol>
 */
class SpaResourceResolver extends PathResourceResolver {

  private static final String INDEX = "index.html";

  @Override
  protected Resource getResource(String resourcePath, Resource location) throws IOException {
    String path = stripSlashes(resourcePath);
    if (path.equals("api") || path.startsWith("api/")) {
      return null;
    }
    if (path.isEmpty()) {
      return super.getResource(INDEX, location);
    }

    Resource direct = super.getResource(path, location);
    if (direct != null) {
      return direct;
    }
    if (hasExtension(path)) {
      return null;
    }

    for (String candidate : List.of(path + ".html", path + "/" + INDEX)) {
      Resource resource = super.getResource(candidate, location);
      if (resource != null) {
        return resource;
      }
    }
    return super.getResource(INDEX, location);
  }

  private static String stripSlashes(String path) {
    int start = 0;
    int end = path.length();
    while (start < end && path.charAt(start) == '/') {
      start++;
    }
    while (end > start && path.charAt(end - 1) == '/') {
      end--;
    }
    return path.substring(start, end);
  }

  private static boolean hasExtension(String path) {
    String lastSegment = path.substring(path.lastIndexOf('/') + 1);
    return lastSegment.lastIndexOf('.') > 0;
  }
}
