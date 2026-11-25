package com.flightlog.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 认证接口的请求和响应。 */
public final class AuthDtos {

  private AuthDtos() {}

  public record RegisterRequest(
      @NotBlank(message = "请填写邮箱") @Email(message = "邮箱格式不正确") @Size(max = 254, message = "邮箱太长")
          String email,
      @NotBlank(message = "请填写密码") @Size(min = 8, max = 64, message = "密码长度为 8 到 64 个字符")
          String password,
      @Size(max = 50, message = "昵称最多 50 个字符") String displayName) {

    public RegisterRequest {
      email = Emails.normalize(email);
      displayName = displayName == null ? null : displayName.strip();
    }
  }

  public record LoginRequest(
      @NotBlank(message = "请填写邮箱") @Size(max = 254, message = "邮箱太长") String email,
      @NotBlank(message = "请填写密码") @Size(max = 64, message = "密码太长") String password) {

    public LoginRequest {
      email = Emails.normalize(email);
    }
  }

  public record ChangePasswordRequest(
      @NotBlank(message = "请填写当前密码") @Size(max = 64, message = "密码太长") String currentPassword,
      @NotBlank(message = "请填写新密码") @Size(min = 8, max = 64, message = "密码长度为 8 到 64 个字符")
          String newPassword) {}

  public record UserResponse(long id, String email, String displayName) {

    static UserResponse of(UserAccount user) {
      return new UserResponse(user.id(), user.email(), user.displayName());
    }
  }
}
