package com.flightlog.ai;

import com.flightlog.ai.AiDtos.ParseResponse;
import com.flightlog.ai.AiDtos.ParseTextRequest;
import jakarta.validation.Valid;
import java.io.IOException;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** AI 解析录入：粘贴的文字（JSON）或登机牌、电子客票截图（multipart）。结果只用于预填表单。 */
@RestController
@RequestMapping("/api/ai")
public class AiController {

  private final AiParseService aiParseService;

  public AiController(AiParseService aiParseService) {
    this.aiParseService = aiParseService;
  }

  @PostMapping(path = "/parse", consumes = MediaType.APPLICATION_JSON_VALUE)
  public ParseResponse parseText(@Valid @RequestBody ParseTextRequest body) {
    return aiParseService.parseText(body.text());
  }

  /** 图片只在内存里用于这一次解析，不保存。 */
  @PostMapping(path = "/parse", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ParseResponse parseImage(@RequestPart("image") MultipartFile image) throws IOException {
    return aiParseService.parseImage(image.getBytes(), image.getContentType());
  }
}
