package io.mosip.registration.processor.payment.service.controller;

import javax.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.mosip.registration.processor.payment.service.dto.ConsumePrnRequestDTO;
import io.mosip.registration.processor.payment.service.dto.ConsumePrnResponseDTO;
import io.mosip.registration.processor.payment.service.dto.MainMosipRequestWrapper;
import io.mosip.registration.processor.payment.service.dto.MainMosipResponseDTO;
import io.mosip.registration.processor.payment.service.dto.PrnConsumedBooleanDTO;
import io.mosip.registration.processor.payment.service.dto.PrnFoundDTO;
import io.mosip.registration.processor.payment.service.dto.PrnPaymentStatusDTO;
import io.mosip.registration.processor.payment.service.dto.PrnsConsumedListMetaDTO;
import io.mosip.registration.processor.payment.service.dto.PrnsListMetaDTO;
import io.mosip.registration.processor.payment.service.impl.PrnConsumedService;
import io.mosip.registration.processor.payment.service.impl.PrnService;
import io.swagger.v3.oas.annotations.Operation;

@RestController
public class PaymentServiceController {
	
	private final PrnService prnService;
	private final PrnConsumedService prnConsumedService;
	
	public PaymentServiceController(PrnService prnService, PrnConsumedService prnConsumedService) {
		this.prnService = prnService;
		this.prnConsumedService = prnConsumedService;
	}
	
	@GetMapping("/getAllPrns")
	@Operation(summary = "getAllPrns", description = "Fetch all prns", tags = "payment-service-controller")
	public ResponseEntity<MainMosipResponseDTO<PrnsListMetaDTO>> getAllPrns(){
		
		return ResponseEntity.status(HttpStatus.OK)
					.body(prnService.findAllPrns());
	}
	
	@GetMapping("/getPrnStatus/{prnNumber}")
	@Operation(summary = "getPrnStatus", description = "Fetch the status of a given preregistration Id", tags = "payment-service-controller")
	public ResponseEntity<MainMosipResponseDTO<PrnPaymentStatusDTO>> getPrnStatus(
			@PathVariable("prnNumber") String prnNumber) throws Exception{
		
		return ResponseEntity.status(HttpStatus.OK)
				.body(prnService.getPrnStatus(prnNumber));
		
	}
	
	
	@GetMapping("/getAllConsumedPrns")
	@Operation(summary = "getAllConsumedPrns", description = "Fetch all consumed prns", tags = "payment-service-controller")
	public ResponseEntity<MainMosipResponseDTO<PrnsConsumedListMetaDTO>> getAllConsumedPrns(){
		
		return ResponseEntity.status(HttpStatus.OK)
					.body(prnConsumedService.findAllConsumedPrns());
	}
	
	
	@GetMapping("/getPrnByRegId/{regId}")
	@Operation(summary = "getPrnByRegId", description = "Fetch consumed by regId", tags = "payment-service-controller")
	public ResponseEntity<MainMosipResponseDTO<PrnFoundDTO>> getPrnByRedId(
			@PathVariable("regId") String regId) throws Exception{
				return ResponseEntity.status(HttpStatus.OK).body(prnConsumedService.findPrnByRegId(regId));
		
	}
	
	@GetMapping("/checkIfPrnConsumed/{prnNumber}")
	@Operation(summary = "checkIfPrnConsumed", description = "Check if prn has been used before", tags = "payment-service-controller")
	public ResponseEntity<MainMosipResponseDTO<PrnConsumedBooleanDTO>> checkIfPrnConsumed(
		@PathVariable("prnNumber") String prnNumber) throws Exception{
			
		return ResponseEntity.status(HttpStatus.OK).body(prnConsumedService.checkIfPrnConsumed(prnNumber));
			
	}
	
	@PostMapping("/consumePrn")
	@Operation(summary = "consumePrn", description = "Consume a new paid and checked prn", tags = "payment-service-controller")
	public ResponseEntity<MainMosipResponseDTO<ConsumePrnResponseDTO>> consumePrn(
			@Valid @RequestBody MainMosipRequestWrapper<ConsumePrnRequestDTO> consumePrnRequestDTO){
		
		return ResponseEntity.status(HttpStatus.OK)
				.body(prnConsumedService.consumePrnAsUsed(consumePrnRequestDTO));
	}
	
	

}
