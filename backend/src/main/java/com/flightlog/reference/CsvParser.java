package com.flightlog.reference;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;

/** 最小的 RFC 4180 CSV 解析器：支持引号、转义引号、引号内换行、CRLF。跳过空行。 */
final class CsvParser {

  private CsvParser() {}

  static List<List<String>> parse(Reader source) {
    // 处理 "" 转义时需要往前看一个字符
    Reader reader = source.markSupported() ? source : new BufferedReader(source);
    List<List<String>> rows = new ArrayList<>();
    List<String> row = new ArrayList<>();
    StringBuilder field = new StringBuilder();
    boolean inQuotes = false;
    boolean rowHasContent = false;
    try {
      int c;
      while ((c = reader.read()) != -1) {
        char ch = (char) c;
        if (inQuotes) {
          if (ch == '"') {
            reader.mark(1);
            int next = reader.read();
            if (next == '"') {
              field.append('"');
            } else {
              inQuotes = false;
              if (next != -1) {
                reader.reset();
              }
            }
          } else {
            field.append(ch);
          }
          continue;
        }
        switch (ch) {
          case '"' -> {
            inQuotes = true;
            rowHasContent = true;
          }
          case ',' -> {
            row.add(field.toString());
            field.setLength(0);
            rowHasContent = true;
          }
          case '\n' -> {
            if (rowHasContent || !field.isEmpty()) {
              row.add(field.toString());
              rows.add(row);
            }
            row = new ArrayList<>();
            field.setLength(0);
            rowHasContent = false;
          }
          case '\r' -> {
            // CRLF：忽略 \r，换行由 \n 处理
          }
          default -> {
            field.append(ch);
            rowHasContent = true;
          }
        }
      }
    } catch (IOException ex) {
      throw new UncheckedIOException(ex);
    }
    if (rowHasContent || !field.isEmpty()) {
      row.add(field.toString());
      rows.add(row);
    }
    return rows;
  }
}
