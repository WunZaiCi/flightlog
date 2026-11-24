package com.flightlog.common;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * 验证静态托管、前端路由回退和缓存头。
 *
 * <p>使用 src/test/resources/test-static/ 下的假页面，不受真实前端构建产物（frontend/out 复制进的 static/）影响。
 */
@SpringBootTest(properties = "spring.web.resources.static-locations=classpath:/test-static/")
@AutoConfigureMockMvc
class SpaFallbackTest {

  private final MockMvcTester mvc;

  SpaFallbackTest(@Autowired MockMvcTester mvc) {
    this.mvc = mvc;
  }

  @Test
  void rootForwardsToIndex() {
    // "/" 由 Spring Boot 的欢迎页映射转发到 index.html（MockMvc 不执行转发，只校验目标）
    assertThat(mvc.get().uri("/")).hasStatusOk().hasForwardedUrl("index.html");
  }

  @Test
  void indexHtmlIsServed() {
    assertThat(mvc.get().uri("/index.html")).hasStatusOk().bodyText().contains("INDEX");
  }

  @Test
  void routeServesMatchingHtmlFile() {
    assertThat(mvc.get().uri("/flights")).hasStatusOk().bodyText().contains("FLIGHTS");
  }

  @Test
  void nestedRouteServesMatchingHtmlFile() {
    assertThat(mvc.get().uri("/flights/new")).hasStatusOk().bodyText().contains("NEW_FLIGHT");
  }

  @Test
  void unknownRouteFallsBackToIndex() {
    assertThat(mvc.get().uri("/__spa_fallback_probe__"))
        .hasStatusOk()
        .hasContentTypeCompatibleWith(MediaType.TEXT_HTML)
        .bodyText()
        .contains("INDEX");
  }

  @Test
  void staticAssetIsServed() {
    assertThat(mvc.get().uri("/_next/static/app.js")).hasStatusOk().bodyText().contains("APP_JS");
  }

  @Test
  void missingStaticAssetReturns404InsteadOfIndex() {
    assertThat(mvc.get().uri("/_next/static/missing.js")).hasStatus(HttpStatus.NOT_FOUND);
    assertThat(mvc.get().uri("/missing.js")).hasStatus(HttpStatus.NOT_FOUND);
  }

  @Test
  void unknownApiPathReturnsProblemDetailInChinese() {
    assertThat(mvc.get().uri("/api/nope"))
        .hasStatus(HttpStatus.NOT_FOUND)
        .hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
        .bodyJson()
        .extractingPath("$.detail")
        .isEqualTo("接口不存在");
  }

  @Test
  void apiRootIsNotFallback() {
    assertThat(mvc.get().uri("/api")).hasStatus(HttpStatus.NOT_FOUND);
  }

  @Test
  void htmlIsRevalidatedByContentEtagNotLastModified() {
    MvcTestResult first = mvc.get().uri("/flights").exchange();
    assertThat(first)
        .hasStatusOk()
        .headers()
        .doesNotContainHeader(HttpHeaders.LAST_MODIFIED)
        .hasValue(HttpHeaders.CACHE_CONTROL, "no-cache");

    String etag = first.getResponse().getHeader(HttpHeaders.ETAG);
    assertThat(etag).isNotBlank();
    assertThat(mvc.get().uri("/flights").header(HttpHeaders.IF_NONE_MATCH, etag))
        .hasStatus(HttpStatus.NOT_MODIFIED);
  }

  @Test
  void fallbackPageIsNotCachedLongTerm() {
    assertThat(mvc.get().uri("/__spa_fallback_probe__"))
        .headers()
        .hasValue(HttpHeaders.CACHE_CONTROL, "no-cache");
  }

  @Test
  void hashedNextAssetsAreCachedImmutably() {
    assertThat(mvc.get().uri("/_next/static/app.js"))
        .hasStatusOk()
        .headers()
        .hasValue(HttpHeaders.CACHE_CONTROL, "max-age=31536000, public, immutable");
  }
}
