package HaUI.ImplSolution.DX_Farm.config;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// Single source of "today" for business rules (isolation dates, harvest checks), independent of
// the container time zone.
@Configuration
public class TimeConfig {

  public static final String BUSINESS_ZONE_ID = "Asia/Ho_Chi_Minh";
  public static final ZoneId BUSINESS_ZONE = ZoneId.of(BUSINESS_ZONE_ID);

  @Bean
  public Clock clock() {
    return Clock.system(BUSINESS_ZONE);
  }
}
