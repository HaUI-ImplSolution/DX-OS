package HaUI.ImplSolution.DX_Farm.config;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// Single source of "today" for business rules (isolation dates, harvest checks), independent of
// the container time zone.
@Configuration
public class TimeConfig {

  public static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

  @Bean
  public Clock clock() {
    return Clock.system(BUSINESS_ZONE);
  }
}
