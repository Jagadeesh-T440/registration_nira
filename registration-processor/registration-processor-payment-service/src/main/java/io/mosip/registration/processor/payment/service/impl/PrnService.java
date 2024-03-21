package io.mosip.registration.processor.payment.service.impl;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import io.mosip.kernel.core.logger.spi.Logger;
import io.mosip.registration.processor.core.logger.RegProcessorLogger;
import io.mosip.registration.processor.payment.service.dto.ExceptionJSONInfoDTO;
import io.mosip.registration.processor.payment.service.dto.MainMosipResponseDTO;
import io.mosip.registration.processor.payment.service.dto.PrnPaymentStatusDTO;
import io.mosip.registration.processor.payment.service.dto.PrnsListMetaDTO;
import io.mosip.registration.processor.payment.service.dto.PrnsListViewDTO;
import io.mosip.registration.processor.payment.service.entity.PrnEntity;
import io.mosip.registration.processor.payment.service.repository.PrnRepository;

@Service
public class PrnService {
	
	/** The reg proc logger. */
	private static Logger regProcLogger = RegProcessorLogger.getLogger(PrnService.class);
	
	@Autowired
	private PrnRepository prnRepository;
	
	@Value("${mosip.all.version}")
	private double version;
    
    @Value("${mosip.utc-datetime-pattern}")
	private String mosipDateTimeFormat;
	
	public MainMosipResponseDTO<PrnsListMetaDTO> findAllPrns(){
		regProcLogger.info("In Registration Processor", "Payment Validator Service", "Finding all Prns");
		
		
		MainMosipResponseDTO<PrnsListMetaDTO> response = new MainMosipResponseDTO<PrnsListMetaDTO>();
		PrnsListMetaDTO prnsListMetaDTO = new PrnsListMetaDTO();
		response.setVersion(String.valueOf(version));
		response.setResponsetime(DateTimeFormatter.ofPattern(mosipDateTimeFormat).format(LocalDateTime.now()));
		
		List<ExceptionJSONInfoDTO> explist = new ArrayList<ExceptionJSONInfoDTO>();
		ExceptionJSONInfoDTO exception = new ExceptionJSONInfoDTO();
		
		try {
			
			List<PrnEntity> listPrns = prnRepository.findAll();
			
			if(!Objects.isNull(listPrns)) {
			
				List<PrnsListViewDTO> viewList = new ArrayList<>();

		    	for (PrnEntity paymentRegistrationNumber : listPrns) {		
					PrnsListViewDTO viewDto = new PrnsListViewDTO();
					viewDto.setPrnId(paymentRegistrationNumber.getPrnId());
					viewDto.setPrnStatusCode(paymentRegistrationNumber.getPrnStatusCode());
					viewDto.setPrnNumber(paymentRegistrationNumber.getPrnNumber());
					viewList.add(viewDto);
		    	}
		    	
		    	prnsListMetaDTO.setPrns(viewList);
				prnsListMetaDTO.setTotalRecords(Integer.toString(listPrns.size()));
				response.setResponse(prnsListMetaDTO);
				
			}
			else {
				//throw new Exception("Error while getting all prns");
				exception.setMessage("No PRNs found");
				//regProcLogger.error("Exception {}", exception);
				explist.add(exception);
				response.setErrors(explist);
			}
		} catch (Exception ex) {
			regProcLogger.error("Error occured while fetching prns");
			
			exception.setMessage(ex.getMessage());
			regProcLogger.error("Exception {}", exception);
			explist.add(exception);
			response.setErrors(explist);
		}
		return response;
	}

	public MainMosipResponseDTO<PrnPaymentStatusDTO> getPrnStatus(String prnNumber) throws Exception{
		regProcLogger.info("In Registration Processor", "Payment Validator Service", "Finding status of PRN");
    	PrnPaymentStatusDTO prnPaymentStatusDTO = new PrnPaymentStatusDTO();
    	MainMosipResponseDTO<PrnPaymentStatusDTO> response = new MainMosipResponseDTO<>();
		
		response.setVersion(String.valueOf(version));
		response.setResponsetime(DateTimeFormatter.ofPattern(mosipDateTimeFormat).format(LocalDateTime.now()));
		
		List<ExceptionJSONInfoDTO> explist = new ArrayList<ExceptionJSONInfoDTO>();
		ExceptionJSONInfoDTO exception = new ExceptionJSONInfoDTO();
		
		try {
			PrnEntity paymentRegistrationNumber = prnRepository.findByPrnNumber(prnNumber);
			
			if(!Objects.isNull(paymentRegistrationNumber)){
				prnPaymentStatusDTO.setPrnNumber(paymentRegistrationNumber.getPrnNumber());
				prnPaymentStatusDTO.setPrnStatusCode(paymentRegistrationNumber.getPrnStatusCode());
				prnPaymentStatusDTO.setPrnStatusDesc(paymentRegistrationNumber.getPrnStatusDesc());
				prnPaymentStatusDTO.setPrnTaxHead(paymentRegistrationNumber.getPrnTaxHead());
				prnPaymentStatusDTO.setPrnTaxPayerName(paymentRegistrationNumber.getTaxPayerName().toUpperCase());
				response.setResponse(prnPaymentStatusDTO);
			}
			else {
				regProcLogger.error("PRN not found","Error occured while fetching status of PRN from URA");

				exception.setMessage("PRN not found");
				explist.add(exception);
				response.setErrors(explist);
			
			}
		}
		catch (Exception e) {
			//throw new Exception("Error while finding PRN status", e);
			exception.setMessage("Error occurred while fetching PRN status" + e.getMessage());
			explist.add(exception);
			response.setErrors(explist);
		}
    	
        return response;
    }

}
