package io.mosip.registration.processor.verification.validators;

import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;
import io.mosip.registration.processor.core.logger.RegProcessorLogger;
import io.mosip.kernel.core.logger.spi.Logger;
/**
 * All the configuration validations will be done in this class
 */
@Component
public class ManualVerificationAppConfigurationsValidator {

    private static final Logger logger = RegProcessorLogger.getLogger(ManualVerificationAppConfigurationsValidator.class);

    /**
     * This configuration will be used by reprocessor stage to reprocess the events
     */
    @Value("${registration.processor.reprocess.elapse.time}")
    private long reprocessorElapseTime;

    @Value("${registration.processor.queue.verification.request.messageTTL}")
	private int mvRequestMessageTTL;

    @Value("${registration.processor.verification.reprocess.buffer.time:900}")
    private long reprocessBufferTime;

    /**
     * The below method will be called by spring once the context is initialized or refreshed
     * @param event Context refreshed event
     */
    @EventListener
    public void validateConfigurations(ContextRefreshedEvent event) {
        logger.info("ContextRefreshedEvent received");
        validateReprocessElapseTimeConfig();
    }

    private void validateReprocessElapseTimeConfig() {
        long allowedReprocessTime = mvRequestMessageTTL + reprocessBufferTime;
        if(reprocessorElapseTime <= allowedReprocessTime) {
            logger.warn("registration.processor.reprocess.elapse.time config {} is invalid," +
                " it should should be greater than the queue expiry with an" +
                " additional buffer {}", reprocessorElapseTime, allowedReprocessTime);
        }
    }
}
