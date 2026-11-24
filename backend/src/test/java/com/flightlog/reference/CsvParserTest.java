package com.flightlog.reference;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.StringReader;
import java.util.List;
import org.junit.jupiter.api.Test;

class CsvParserTest {

  private static List<List<String>> parse(String csv) {
    return CsvParser.parse(new StringReader(csv));
  }

  @Test
  void parsesPlainAndQuotedFields() {
    assertThat(parse("a,b,c\n1,\"x, y\",3\n"))
        .containsExactly(List.of("a", "b", "c"), List.of("1", "x, y", "3"));
  }

  @Test
  void handlesEscapedQuotesAndNewlinesInsideQuotes() {
    assertThat(parse("\"say \"\"hi\"\"\",\"line1\nline2\"\n"))
        .containsExactly(List.of("say \"hi\"", "line1\nline2"));
  }

  @Test
  void keepsEmptyFieldsAndHandlesCrlf() {
    assertThat(parse("a,,c\r\n,,\r\n")).containsExactly(List.of("a", "", "c"), List.of("", "", ""));
  }

  @Test
  void lastLineWithoutNewline() {
    assertThat(parse("a,b\n1,2")).containsExactly(List.of("a", "b"), List.of("1", "2"));
  }

  @Test
  void skipsBlankLines() {
    assertThat(parse("a\n\nb\n")).containsExactly(List.of("a"), List.of("b"));
  }
}
