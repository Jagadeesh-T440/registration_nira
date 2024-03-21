package io.mosip.registration.processor.payment.service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = { 
		"${mosip.auth.adapter.impl.basepackage}", "io.mosip.registration.processor.payment.service",
		"io.mosip.registration.processor.rest.client.config",
		"io.mosip.registration.processor.core.config","io.mosip.registration.processor.status.config"})
public class PaymentServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(PaymentServiceApplication.class, args);
	}

}
