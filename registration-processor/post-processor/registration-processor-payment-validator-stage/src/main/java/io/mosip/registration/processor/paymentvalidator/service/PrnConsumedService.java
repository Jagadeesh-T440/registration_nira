package io.mosip.registration.processor.paymentvalidator.service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.itextpdf.text.pdf.PdfStructTreeController.returnType;

import io.mosip.registration.processor.core.logger.RegProcessorLogger;
import io.mosip.registration.processor.paymentvalidator.dto.ConsumePrnRequestDTO;
import io.mosip.registration.processor.paymentvalidator.dto.ConsumePrnResponseDTO;
import io.mosip.registration.processor.paymentvalidator.dto.ExceptionJSONInfoDTO;
import io.mosip.registration.processor.paymentvalidator.dto.MainMosipRequestWrapper;
import io.mosip.registration.processor.paymentvalidator.dto.MainMosipResponseDTO;
import io.mosip.registration.processor.paymentvalidator.dto.PrnConsumedBooleanDTO;
import io.mosip.registration.processor.paymentvalidator.dto.PrnFoundDTO;
import io.mosip.registration.processor.paymentvalidator.dto.PrnsConsumedListMetaDTO;
import io.mosip.registration.processor.paymentvalidator.dto.PrnsConsumedListViewDTO;
import io.mosip.registration.processor.paymentvalidator.entity.PrnConsumedEntity;
import io.mosip.registration.processor.paymentvalidator.repository.PrnConsumedRepository;
import io.mosip.kernel.core.exception.ExceptionUtils;
import io.mosip.kernel.core.logger.spi.Logger;

@Service
public class PrnConsumedService {

	/** The reg proc logger. */
	private static Logger regProcLogger = RegProcessorLogger.getLogger(PrnConsumedService.class);
	
	@Autowired
	private PrnConsumedRepository prnConsumedRepository;
	
	@Value("${mosip.all.version}")
	private double version;
    
    @Value("${mosip.utc-datetime-pattern}")
	private String mosipDateTimeFormat;
	
	public PrnsConsumedListMetaDTO findAllConsumedPrns(){
		regProcLogger.info("In Registration Processor", "Payment Validator Service", "Finding all ConsumedPrns");
		PrnsConsumedListMetaDTO prnsListMetaDTO = new PrnsConsumedListMetaDTO();
		try {
			
			List<PrnConsumedEntity> listPrns = prnConsumedRepository.findAll();
	
			if(!Objects.isNull(listPrns)) {
			
				List<PrnsConsumedListViewDTO> viewList = new ArrayList<>();

		    	for (PrnConsumedEntity prnConsumed : listPrns) {		
					PrnsConsumedListViewDTO viewDto = new PrnsConsumedListViewDTO();
					viewDto.setPrnConsumedId(prnConsumed.getPrnConsumedId());
					viewDto.setRegId(prnConsumed.getRegId());
					viewDto.setPrnConsumedNumber(prnConsumed.getPrnNumber());
					viewList.add(viewDto);
		    	}
		    	
		    	prnsListMetaDTO.setPrns(viewList);
				prnsListMetaDTO.setTotalRecords(Integer.toString(listPrns.size()));	
			}
			else {
				regProcLogger.error("No consumed prns {} found" );

			}
		} catch (Exception ex) {
			regProcLogger.error("Error occured while fetching consumed prns" + ExceptionUtils.getStackTrace(ex));
		}
		return prnsListMetaDTO;
	}

	public PrnFoundDTO findPrnByRegId(String regId) throws Exception{
		
		regProcLogger.info("In Registration Processor", "Payment Validator Service", "Finding consumed prn using regId");
		
		PrnFoundDTO prnFoundDTO = new PrnFoundDTO();
		
		try {
			PrnConsumedEntity consumedEntity = prnConsumedRepository.findPrnByRegId(regId);
			
			if(!Objects.isNull(consumedEntity)){
				String prnFound = consumedEntity.getPrnNumber();
				prnFoundDTO.setPrnNumber(prnFound);

			}
			else {
				regProcLogger.info("Record for Reg Id not found");
				
			}
			
		}
		catch (Exception e) {
			regProcLogger.error("Error occured while fetching prn record for Reg Id" + ExceptionUtils.getStackTrace(e));
		}
    	
        return prnFoundDTO;
		
	}

	public boolean checkIfPrnConsumed(String prnNumber) throws Exception{
		try {
			PrnConsumedEntity prnConsumedEntity = prnConsumedRepository.findByPrnNumber(prnNumber);
			if(Objects.isNull(prnConsumedEntity)){
				return false;
			}
			else {
				return true;
			}	
		}
		catch (Exception e) {
			regProcLogger.error("Error occured while checking if PRN has been consumed for service" + ExceptionUtils.getStackTrace(e));
		}
        return false;
	}

	public boolean consumePrnAsUsed(ConsumePrnRequestDTO requestDTO){
		try {
			
			if(!Objects.isNull(requestDTO)) {
				PrnConsumedEntity checkExConsumedEntity  = prnConsumedRepository.findByPrnNumber(requestDTO.getPrnNum());
				if(!Objects.isNull(checkExConsumedEntity)) {
					return false;
				}
				else {
					PrnConsumedEntity newConsumedEntity = new PrnConsumedEntity();
					newConsumedEntity.setPrnNumber(requestDTO.getPrnNum());
					newConsumedEntity.setRegId(requestDTO.getRegId());
					
					newConsumedEntity = prnConsumedRepository.save(newConsumedEntity);
					
					return true;
				}
			}
			else {
				regProcLogger.error("Bad request. Request {} missing");
				return false;
			}
	
		} catch (Exception e) {
			regProcLogger.error("PRN consumption failed." + ExceptionUtils.getStackTrace(e));
			return false;
		}
	}

}