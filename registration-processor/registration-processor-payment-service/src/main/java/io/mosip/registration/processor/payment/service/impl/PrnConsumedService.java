package io.mosip.registration.processor.payment.service.impl;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import io.mosip.registration.processor.core.logger.RegProcessorLogger;
import io.mosip.registration.processor.payment.service.dto.ConsumePrnRequestDTO;
import io.mosip.registration.processor.payment.service.dto.ConsumePrnResponseDTO;
import io.mosip.registration.processor.payment.service.dto.ExceptionJSONInfoDTO;
import io.mosip.registration.processor.payment.service.dto.MainMosipRequestWrapper;
import io.mosip.registration.processor.payment.service.dto.MainMosipResponseDTO;
import io.mosip.registration.processor.payment.service.dto.PrnConsumedBooleanDTO;
import io.mosip.registration.processor.payment.service.dto.PrnFoundDTO;
import io.mosip.registration.processor.payment.service.dto.PrnsConsumedListMetaDTO;
import io.mosip.registration.processor.payment.service.dto.PrnsConsumedListViewDTO;
import io.mosip.registration.processor.payment.service.entity.PrnConsumedEntity;
import io.mosip.registration.processor.payment.service.repository.PrnConsumedRepository;
import javassist.expr.NewArray;
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
	
	public MainMosipResponseDTO<PrnsConsumedListMetaDTO> findAllConsumedPrns(){
		regProcLogger.info("In Registration Processor", "Payment Validator Service", "Finding all ConsumedPrns");
		
		
		MainMosipResponseDTO<PrnsConsumedListMetaDTO> response = new MainMosipResponseDTO<PrnsConsumedListMetaDTO>();
		PrnsConsumedListMetaDTO prnsListMetaDTO = new PrnsConsumedListMetaDTO();
		response.setVersion(String.valueOf(version));
		response.setResponsetime(DateTimeFormatter.ofPattern(mosipDateTimeFormat).format(LocalDateTime.now()));
		
		List<ExceptionJSONInfoDTO> explist = new ArrayList<ExceptionJSONInfoDTO>();
		ExceptionJSONInfoDTO exception = new ExceptionJSONInfoDTO();
		
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
				response.setResponse(prnsListMetaDTO);
				
			}
			else {
				//throw new Exception("Error while getting all consumed prns");
				exception.setMessage("Error occurred while getting all consumed prns");
				//regProcLogger.error("Exception {}", exception);
				explist.add(exception);
				response.setErrors(explist);
			}
		} catch (Exception ex) {
			regProcLogger.error("Error occured while fetching consumed prns" + ex.getMessage());
			
			exception.setMessage(ex.getMessage());
			regProcLogger.error("Exception {}", exception);
			explist.add(exception);
			response.setErrors(explist);
		}
		return response;
	}

	public MainMosipResponseDTO<PrnFoundDTO> findPrnByRegId(String regId) throws Exception{
		
		regProcLogger.info("In Registration Processor", "Payment Validator Service", "Finding consumed prn using regId");
		
		
		PrnFoundDTO prnFoundDTO = new PrnFoundDTO();
		MainMosipResponseDTO<PrnFoundDTO> response = new MainMosipResponseDTO<>();
		
		response.setVersion(String.valueOf(version));
		response.setResponsetime(DateTimeFormatter.ofPattern(mosipDateTimeFormat).format(LocalDateTime.now()));
		
		List<ExceptionJSONInfoDTO> explist = new ArrayList<ExceptionJSONInfoDTO>();
		ExceptionJSONInfoDTO exception = new ExceptionJSONInfoDTO();
		
		try {
			//PaymentRegistrationNumberConsumed paymentRegistrationNumberConsumed = prnConsumedRepository.findPrnByRegId(regId);
			PrnConsumedEntity consumedEntity = prnConsumedRepository.findPrnByRegId(regId);
			
			if(!Objects.isNull(consumedEntity)){
				String prnFound = consumedEntity.getPrnNumber();
				prnFoundDTO.setPrnNumber(prnFound);
				response.setResponse(prnFoundDTO);
			}
			else {
				regProcLogger.error("Record for Reg Id not found","Error occured while fetching consumed PRN");
				
				
				exception.setMessage("Record for Reg Id not found");
				explist.add(exception);
				response.setErrors(explist);
			}
			
		}
		catch (Exception e) {
			//throw new Exception("Error while finding PRN", e);
			exception.setMessage("Error occured while fetching prn record for Reg Id" + e.getMessage());
			explist.add(exception);
			response.setErrors(explist);
		}
    	
        return response;
		
	}

	public MainMosipResponseDTO<PrnConsumedBooleanDTO> checkIfPrnConsumed(String prnNumber) throws Exception{
		
		PrnConsumedBooleanDTO prnConsumedBooleanDTO = new PrnConsumedBooleanDTO();
		MainMosipResponseDTO<PrnConsumedBooleanDTO> response = new MainMosipResponseDTO<>();
		
		response.setVersion(String.valueOf(version));
		response.setResponsetime(DateTimeFormatter.ofPattern(mosipDateTimeFormat).format(LocalDateTime.now()));
		
		
	
		try {
			PrnConsumedEntity prnConsumedEntity = prnConsumedRepository.findByPrnNumber(prnNumber);
			
			if(Objects.isNull(prnConsumedEntity)){
				prnConsumedBooleanDTO.setPrnAlreadyUsed(Boolean.FALSE);
				response.setResponse(prnConsumedBooleanDTO);
			}
			else {
				prnConsumedBooleanDTO.setPrnAlreadyUsed(Boolean.TRUE);
				response.setResponse(prnConsumedBooleanDTO);
			}
			
		}
		catch (Exception e) {
			List<ExceptionJSONInfoDTO> explist = new ArrayList<ExceptionJSONInfoDTO>();
			ExceptionJSONInfoDTO exception = new ExceptionJSONInfoDTO();
			exception.setMessage("Error occured while checking if PRN has been consumed for service");
			explist.add(exception);
			response.setErrors(explist);
		}
    	
        return response;
		
	}

	public MainMosipResponseDTO<ConsumePrnResponseDTO> consumePrnAsUsed(MainMosipRequestWrapper<ConsumePrnRequestDTO> requestDTO){
		
		MainMosipResponseDTO<ConsumePrnResponseDTO> response = new MainMosipResponseDTO<>();
		
		response.setVersion(String.valueOf(version));
		response.setResponsetime(DateTimeFormatter.ofPattern(mosipDateTimeFormat).format(LocalDateTime.now()));
		
		List<ExceptionJSONInfoDTO> explist = new ArrayList<ExceptionJSONInfoDTO>();
		ExceptionJSONInfoDTO exception = new ExceptionJSONInfoDTO();
		
		try {
			
			if(!Objects.isNull(requestDTO)) {
				PrnConsumedEntity checkExConsumedEntity  = prnConsumedRepository.findByPrnNumber(requestDTO.getRequest().getPrnNum());
				if(!Objects.isNull(checkExConsumedEntity)) {
					
					exception.setMessage("PRN already consumed.");
					explist.add(exception);
					response.setErrors(explist);
				}
				else {
					PrnConsumedEntity newConsumedEntity = new PrnConsumedEntity();
					newConsumedEntity.setPrnNumber(requestDTO.getRequest().getPrnNum());
					newConsumedEntity.setRegId(requestDTO.getRequest().getRegId());
					
					newConsumedEntity = prnConsumedRepository.save(newConsumedEntity);
					
					ConsumePrnResponseDTO responseDTO = new ConsumePrnResponseDTO();
					responseDTO.setPrnNum(newConsumedEntity.getPrnNumber());
					responseDTO.setRegId(newConsumedEntity.getRegId());
					responseDTO.setConsumedStatus(Boolean.TRUE);
					
					response.setResponse(responseDTO);
				}
			}
			else {
				response.setResponse(null);
				exception.setMessage("Bad request");
				explist.add(exception);
				response.setErrors(explist);
			}

			
		} catch (Exception e) {
			exception.setMessage("PRN consumption failed" + e.getMessage());
			explist.add(exception);
			response.setErrors(explist);

		}
		
		return response;
		
	}

}