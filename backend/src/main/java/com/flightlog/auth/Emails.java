package com.flightlog.auth;

import java.util.Locale;

final class Emails {

  private Emails() {}

  /** 邮箱统一去掉首尾空格、转小写后存储和比较。 */
  static String normalize(String email) {
    return email == null ? null : email.strip().toLowerCase(Locale.ROOT);
  }
}
