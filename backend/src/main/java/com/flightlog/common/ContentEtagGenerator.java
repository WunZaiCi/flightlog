package com.flightlog.common;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import org.springframework.core.io.Resource;
import org.springframework.util.DigestUtils;

/**
 * 按文件内容生成 ETag。
 *
 * <p>打包进 jar 的文件时间戳是固定的（可复现构建），不能靠 Last-Modified 判断文件是否更新，所以改用内容哈希。 结果按「URL + 修改时间 +
 * 长度」缓存，避免每次请求都重新读文件。
 */
class ContentEtagGenerator implements Function<Resource, String> {

  private final Map<String, String> cache = new ConcurrentHashMap<>();

  @Override
  public String apply(Resource resource) {
    try {
      String key =
          resource.getURL() + "@" + resource.lastModified() + "@" + resource.contentLength();
      return cache.computeIfAbsent(key, k -> hash(resource));
    } catch (IOException | UncheckedIOException ex) {
      // 拿不到 ETag 时不做条件请求，照常返回完整内容
      return null;
    }
  }

  private static String hash(Resource resource) {
    try (InputStream in = resource.getInputStream()) {
      return "\"" + DigestUtils.md5DigestAsHex(in) + "\"";
    } catch (IOException ex) {
      throw new UncheckedIOException(ex);
    }
  }
}
