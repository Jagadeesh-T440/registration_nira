package io.mosip.registration.processor.status.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.ComponentScan.Filter;
import org.springframework.context.annotation.FilterType;

import io.mosip.registration.processor.core.abstractverticle.MosipEventBus;
import io.mosip.registration.processor.core.abstractverticle.MosipVerticleAPIManager;
import io.mosip.registration.processor.core.abstractverticle.MosipVerticleManager;
import io.mosip.registration.processor.core.abstractverticle.StageHealthCheckHandler;
import io.mosip.registration.processor.core.eventbus.KafkaMosipEventBus;
import io.mosip.registration.processor.core.eventbus.VertxMosipEventBus;
import io.mosip.registration.processor.rest.client.config.RestConfigBean;
import io.mosip.registration.processor.status.config.RegistrationStatusBeanConfig;
import io.mosip.registration.processor.status.config.RegistrationStatusServiceBeanConfig;

/**
 * The Registration Status API
 * 
 * @author Pranav Kumar
 *
 */
@SpringBootApplication
@ComponentScan(basePackages = { "io.mosip.registration.processor.status.*",
		"io.mosip.registration.processor.rest.client.*", "io.mosip.registration.processor.core.token.*",
		"io.mosip.registration.processor.core.config",
		"${mosip.auth.adapter.impl.basepackage}" },
		excludeFilters = @Filter(type = FilterType.ASSIGNABLE_TYPE, value = { RegistrationStatusServiceBeanConfig.class,
				RegistrationStatusBeanConfig.class, RestConfigBean.class,KafkaMosipEventBus.class,VertxMosipEventBus.class
				,StageHealthCheckHandler.class,MosipVerticleManager.class,MosipVerticleAPIManager.class,MosipEventBus.class}))
public class RegistrationStatusApiApplication
{
    public static void main( String[] args )
    {
        SpringApplication.run(RegistrationStatusApiApplication.class, args);
    }
}
