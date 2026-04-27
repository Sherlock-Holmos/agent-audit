package com.audit.data.service.orchestration;

import com.audit.data.service.IRectificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
/**
 * 定时扫描整改提醒规则并生成提醒通知。
 */
public class RectificationReminderScheduler {

    private static final Logger LOGGER = LoggerFactory.getLogger(RectificationReminderScheduler.class);
    private static final String OWNER_SCOPE = "rectification_global";

    private final IRectificationService rectificationService;

    public RectificationReminderScheduler(IRectificationService rectificationService) {
        this.rectificationService = rectificationService;
    }

    @Scheduled(cron = "0 0 * * * ?")
    public void runReminderScan() {
        try {
            int count = rectificationService.runReminderScan(OWNER_SCOPE);
            if (count > 0) {
                LOGGER.info("Rectification reminder scan completed, notifications sent: {}", count);
            }
        } catch (Exception ex) {
            LOGGER.warn("Rectification reminder scan failed", ex);
        }
    }
}
