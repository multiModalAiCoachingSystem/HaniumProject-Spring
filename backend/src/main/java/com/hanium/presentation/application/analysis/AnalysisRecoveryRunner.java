package com.hanium.presentation.application.analysis;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class AnalysisRecoveryRunner {
    private static final Logger log = LoggerFactory.getLogger(AnalysisRecoveryRunner.class);

    private final AnalysisRecoveryService recoveryService;

    public AnalysisRecoveryRunner(AnalysisRecoveryService recoveryService) {
        this.recoveryService = recoveryService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void recoverAfterStartup() {
        try {
            recoveryService.recoverOnStartup();
        } catch (RuntimeException exception) {
            log.warn("Backend recovery startup failed. reason=RECOVERY_STARTUP_ERROR");
        }
    }
}
