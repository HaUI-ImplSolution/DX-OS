package HaUI.ImplSolution.DX_Farm.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

// CORS được cấu hình ở security.SecurityConfig (chạy trước trong filter chain khi có
// Spring Security) - WebConfig chỉ còn lo serve file ảnh đã upload qua HTTP.
@Configuration
public class WebConfig implements WebMvcConfigurer {

  private final String uploadDir;

  public WebConfig(@Value("${app.upload-dir:./uploads}") String uploadDir) {
    this.uploadDir = uploadDir;
  }

  @Override
  public void addResourceHandlers(ResourceHandlerRegistry registry) {
    String location = uploadDir.endsWith("/") ? uploadDir : uploadDir + "/";
    registry.addResourceHandler("/uploads/**").addResourceLocations("file:" + location);
  }
}
