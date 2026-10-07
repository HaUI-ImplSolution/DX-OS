// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.DX_Farm.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableAsync
public class AsyncConfig {

  private static final Logger log = LoggerFactory.getLogger(AsyncConfig.class);

  // Bounded executor for Mattermost notifications; a full queue drops the task instead of
  // rejecting it on the caller thread.
  @Bean(name = "mattermostNotifyExecutor")
  public ThreadPoolTaskExecutor mattermostNotifyExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(2);
    executor.setMaxPoolSize(2);
    executor.setQueueCapacity(100);
    executor.setThreadNamePrefix("mattermost-notify-");
    executor.setRejectedExecutionHandler(
        (task, pool) -> log.warn("Mattermost notification dropped: queue is full"));
    return executor;
  }
}
