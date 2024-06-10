package io.mosip.registration.processor.paymentvalidator.config;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.OAuth2RestTemplate;
import org.springframework.security.oauth2.client.resource.OAuth2ProtectedResourceDetails;
import org.springframework.web.client.RestTemplate;

import io.mosip.registration.processor.paymentvalidator.util.CustomizedRestApiClient;
import io.mosip.registration.processor.rest.client.utils.RestApiClient;

@Configuration
public class PaymentValidatorConfig {

	@Bean
	public RestTemplate restTemplate(RestTemplateBuilder builder) {
		return builder.build();
	}
	
	
	@Bean
	public CustomizedRestApiClient getCustomizedRestApiClient() {
		return new CustomizedRestApiClient();
	}

}
