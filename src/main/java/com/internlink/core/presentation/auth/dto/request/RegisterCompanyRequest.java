package com.internlink.core.presentation.auth.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RegisterCompanyRequest {

    @NotBlank(message = "Tên doanh nghiệp không được để trống")
    @Size(max = 255, message = "Tên doanh nghiệp không quá 255 ký tự")
    private String companyName;

    @NotBlank(message = "Mã số thuế không được để trống")
    @Size(max = 50, message = "Mã số thuế không quá 50 ký tự")
    private String taxCode;

    @Size(max = 150)
    private String industry;
    @Size(max = 255)
    @jakarta.validation.constraints.Pattern(regexp = "^$|https?://[^\\s]+", message = "Website cần dùng http hoặc https")
    private String website;

    @NotNull(message = "Địa chỉ doanh nghiệp không được để trống")
    @jakarta.validation.Valid
    private CompanyRegistrationAddress address;

    @NotBlank(message = "Họ tên người đại diện không được để trống")
    @Size(max = 150)
    private String fullName;

    @NotBlank(message = "Email không được để trống")
    @Email(message = "Email không đúng định dạng")
    @Size(max = 150)
    private String email;

    @NotBlank(message = "Số điện thoại không được để trống")
    @Size(max = 20)
    @jakarta.validation.constraints.Pattern(regexp = "\\+?[0-9 ()-]{8,20}", message = "Số điện thoại không hợp lệ")
    private String phoneNumber;

    @NotBlank(message = "Mật khẩu không được để trống")
    @Size(min = 8, max = 72, message = "Mật khẩu phải từ 8 đến 72 ký tự")
    private String password;
}
