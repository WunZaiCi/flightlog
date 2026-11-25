package com.flightlog.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.flightlog.SqliteTestSupport;
import com.flightlog.auth.SessionClient;
import jakarta.servlet.http.Cookie;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/** AI 解析：ChatModel 用 mock，测试中绝不调用真实的 Gemini。 */
@SpringBootTest
@AutoConfigureMockMvc
class AiParseApiTest extends SqliteTestSupport {

  @DynamicPropertySource
  static void gemini(DynamicPropertyRegistry registry) {
    registry.add("flightlog.gemini.api-key", () -> "test-key");
  }

  @MockitoBean ChatModel chatModel;

  private final MockMvcTester mvc;
  private SessionClient user;

  AiParseApiTest(@Autowired MockMvcTester mvc) {
    this.mvc = mvc;
  }

  @BeforeEach
  void setUp() {
    when(chatModel.getOptions()).thenReturn(ChatOptions.builder().build());
    user = new SessionClient(mvc);
    user.register("ai" + System.nanoTime() + "@example.com", "correct-horse-1", null);
  }

  private void modelAnswers(String json) {
    when(chatModel.call(any(Prompt.class)))
        .thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage(json)))));
  }

  private MvcTestResult parseText(String text) {
    return user.postJson("/api/ai/parse", "{\"text\":\"" + text + "\"}");
  }

  @Test
  void roundTripBecomesTwoNormalizedCandidates() {
    modelAnswers(
        """
        {"flights":[
          {"flightNumber":"nh 242","date":"2026-11-03","departureAirport":"fuk","arrivalAirport":"hnd",
           "aircraftType":"b789","registration":"ja891a","notes":null},
          {"flightNumber":"NH241","date":"2026-11-06","departureAirport":"HND","arrivalAirport":"FUK",
           "aircraftType":null,"registration":null,"notes":"返程"}
        ]}
        """);

    MvcTestResult result = parseText("11月3日坐 NH242 从福冈到羽田，6号 NH241 回来");

    assertThat(result).hasStatusOk();
    var json = assertThat(result).bodyJson();
    json.extractingPath("$.flights.length()").isEqualTo(2);
    json.extractingPath("$.flights[0].flightNumber").isEqualTo("NH242");
    json.extractingPath("$.flights[0].flightDate").isEqualTo("2026-11-03");
    json.extractingPath("$.flights[0].depIata").isEqualTo("FUK");
    json.extractingPath("$.flights[0].arrIata").isEqualTo("HND");
    json.extractingPath("$.flights[0].aircraftType").isEqualTo("B789");
    json.extractingPath("$.flights[0].registration").isEqualTo("JA891A");
    json.extractingPath("$.flights[0].warnings").isEqualTo(List.of());
    json.extractingPath("$.flights[1].notes").isEqualTo("返程");
  }

  @Test
  void promptTellsTheModelTodayAndPassesTheUserText() {
    modelAnswers("{\"flights\":[]}");
    parseText("NH242 福冈到羽田");

    ArgumentCaptor<Prompt> prompt = ArgumentCaptor.forClass(Prompt.class);
    verify(chatModel).call(prompt.capture());
    String all = prompt.getValue().getContents();
    assertThat(all).contains(LocalDate.now().toString()).contains("NH242 福冈到羽田");
  }

  @Test
  void questionableFieldsComeWithWarnings() {
    modelAnswers(
        """
        {"flights":[{"flightNumber":"NH242","date":null,"departureAirport":"ZZZ",
          "arrivalAirport":"HND","aircraftType":null,"registration":null,"notes":null}]}
        """);

    MvcTestResult result = parseText("NH242");
    assertThat(result)
        .bodyJson()
        .extractingPath("$.flights[0].warnings")
        .isEqualTo(List.of("没有识别出日期", "机场 ZZZ 不在机场表里"));
    assertThat(result).bodyJson().extractingPath("$.flights[0].flightDate").isNull();
  }

  @Test
  void aircraftSubtypeFromModelIsConvertedOrFlagged() {
    modelAnswers(
        """
        {"flights":[
          {"flightNumber":"NH1","date":"2026-11-03","departureAirport":"HND","arrivalAirport":"FUK",
           "aircraftType":"A320-212","registration":null,"notes":null},
          {"flightNumber":"NH2","date":"2026-11-04","departureAirport":"FUK","arrivalAirport":"HND",
           "aircraftType":"Boeing 777","registration":null,"notes":null}
        ]}
        """);
    MvcTestResult result = parseText("x");
    assertThat(result).bodyJson().extractingPath("$.flights[0].aircraftType").isEqualTo("A320");
    assertThat(result).bodyJson().extractingPath("$.flights[1].aircraftType").isNull();
    assertThat(result).bodyJson().extractingPath("$.flights[1].aircraftSearch").isEqualTo("777");
    assertThat(result)
        .bodyJson()
        .extractingPath("$.flights[1].warnings")
        .isEqualTo(java.util.List.of("机型「Boeing 777」只有系列，请选择具体型号"));
  }

  @Test
  void bareFamilyCodesAreNotSavedAsUnrecognizedTypes() {
    modelAnswers(
        """
        {"flights":[
          {"flightNumber":"NH1","date":"2026-11-03","departureAirport":"HND","arrivalAirport":"FUK",
           "aircraftType":"777","registration":null,"notes":null},
          {"flightNumber":"NH2","date":"2026-11-04","departureAirport":"FUK","arrivalAirport":"HND",
           "aircraftType":"B787","registration":null,"notes":null},
          {"flightNumber":"NH3","date":"2026-11-05","departureAirport":"HND","arrivalAirport":"KIX",
           "aircraftType":"X9Z","registration":null,"notes":null}
        ]}
        """);
    MvcTestResult result = parseText("x");
    assertThat(result).bodyJson().extractingPath("$.flights[0].aircraftType").isNull();
    assertThat(result).bodyJson().extractingPath("$.flights[0].aircraftSearch").isEqualTo("777");
    assertThat(result).bodyJson().extractingPath("$.flights[1].aircraftSearch").isEqualTo("787");
    // 真正不认识的代码照常保留，作为未识别机型
    assertThat(result).bodyJson().extractingPath("$.flights[2].aircraftType").isEqualTo("X9Z");
  }

  @Test
  void nothingFoundIsFriendly() {
    modelAnswers("{\"flights\":[]}");
    MvcTestResult result = parseText("今天天气不错");
    assertThat(result).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
    assertThat(result).bodyJson().extractingPath("$.detail").isEqualTo("没有从内容里识别出航班，请手动填写");
  }

  @Test
  void quotaExhaustedIsFriendlyAndNotRetried() {
    when(chatModel.call(any(Prompt.class)))
        .thenThrow(new RuntimeException("429 RESOURCE_EXHAUSTED: quota exceeded"));
    MvcTestResult result = parseText("NH242");
    assertThat(result).hasStatus(HttpStatus.TOO_MANY_REQUESTS);
    assertThat(result).bodyJson().extractingPath("$.detail").isEqualTo("今天的免费额度用完了，请手动填写或稍后再试");
    verify(chatModel).call(any(Prompt.class));
  }

  @Test
  void otherModelErrorIsBadGateway() {
    when(chatModel.call(any(Prompt.class))).thenThrow(new RuntimeException("boom"));
    MvcTestResult result = parseText("NH242");
    assertThat(result).hasStatus(HttpStatus.BAD_GATEWAY);
    assertThat(result).bodyJson().extractingPath("$.detail").isEqualTo("AI 解析暂时不可用，请手动填写或稍后再试");
  }

  @Test
  void unparseableModelOutputIsBadGateway() {
    modelAnswers("这不是 JSON");
    assertThat(parseText("NH242")).hasStatus(HttpStatus.BAD_GATEWAY);
  }

  @Test
  void emptyOrTooLongTextIsRejected() {
    assertThat(parseText(" ")).hasStatus(HttpStatus.BAD_REQUEST);
    assertThat(parseText("x".repeat(5001))).hasStatus(HttpStatus.BAD_REQUEST);
  }

  @Test
  void imageIsSentToTheModelAsMedia() {
    modelAnswers(
        """
        {"flights":[{"flightNumber":"JL305","date":"2026-10-01","departureAirport":"HND",
          "arrivalAirport":"KIX","aircraftType":null,"registration":null,"notes":null}]}
        """);
    byte[] png = {(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10};

    MvcTestResult result = upload(new MockMultipartFile("image", "pass.png", "image/png", png));

    assertThat(result).hasStatusOk();
    assertThat(result).bodyJson().extractingPath("$.flights[0].flightNumber").isEqualTo("JL305");
    ArgumentCaptor<Prompt> prompt = ArgumentCaptor.forClass(Prompt.class);
    verify(chatModel).call(prompt.capture());
    UserMessage message = prompt.getValue().getUserMessage();
    assertThat(message.getMedia()).hasSize(1);
    assertThat(message.getMedia().getFirst().getMimeType().toString()).isEqualTo("image/png");
  }

  @Test
  void nonImageUploadIsRejected() {
    MvcTestResult result =
        upload(new MockMultipartFile("image", "a.pdf", "application/pdf", new byte[] {1, 2, 3}));
    assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
    assertThat(result).bodyJson().extractingPath("$.detail").isEqualTo("只支持图片（JPG、PNG、WebP、HEIC）");
  }

  @Test
  void requiresLogin() {
    assertThat(new SessionClient(mvc).primeCsrf().postJson("/api/ai/parse", "{\"text\":\"NH242\"}"))
        .hasStatus(HttpStatus.UNAUTHORIZED);
  }

  private MvcTestResult upload(MockMultipartFile file) {
    var request = mvc.post().uri("/api/ai/parse").multipart().file(file);
    request.header("X-XSRF-TOKEN", user.cookie(SessionClient.CSRF_COOKIE));
    request.cookie(
        new Cookie(SessionClient.CSRF_COOKIE, user.cookie(SessionClient.CSRF_COOKIE)),
        new Cookie(SessionClient.SESSION_COOKIE, user.cookie(SessionClient.SESSION_COOKIE)));
    return request.exchange();
  }
}
