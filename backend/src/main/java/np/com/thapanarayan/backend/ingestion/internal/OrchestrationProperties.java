package np.com.thapanarayan.backend.ingestion.internal;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Scheduling tunables for the daily pipeline (§10.11).
 *
 * @param scheduleEnabled whether the @Scheduled NPT trigger fires (opt-in, so dev/test runs stay quiet)
 * @param cron            Spring cron for the daily trigger, evaluated in {@code Asia/Kathmandu}
 * @param recentJobs      how many recent ingestion jobs the pipeline-status endpoint returns
 */
@ConfigurationProperties(prefix = "nepse.orchestration")
record OrchestrationProperties(
        Boolean scheduleEnabled,
        String cron,
        Integer recentJobs) {

    OrchestrationProperties {
        if (scheduleEnabled == null) {
            scheduleEnabled = false;
        }
        if (cron == null || cron.isBlank()) {
            cron = "0 1 15 * * *"; // 15:01 NPT, after the NEPSE close
        }
        if (recentJobs == null || recentJobs < 1) {
            recentJobs = 10;
        }
    }
}
