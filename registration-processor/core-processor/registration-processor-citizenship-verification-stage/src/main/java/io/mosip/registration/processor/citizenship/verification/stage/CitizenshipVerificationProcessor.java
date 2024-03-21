package io.mosip.registration.processor.citizenship.verification.stage;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.apache.camel.processor.aggregate.AggregationStrategyBeanInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.mosip.kernel.core.logger.spi.Logger;
import io.mosip.kernel.core.util.exception.JsonProcessingException;
import io.mosip.registration.processor.citizenship.verification.constants.CitizenshipType;
import io.mosip.registration.processor.core.abstractverticle.MessageBusAddress;
import io.mosip.registration.processor.core.abstractverticle.MessageDTO;
import io.mosip.registration.processor.core.constant.MappingJsonConstants;
import io.mosip.registration.processor.core.constant.ProviderStageName;
import io.mosip.registration.processor.core.exception.ApisResourceAccessException;
import io.mosip.registration.processor.core.exception.PacketManagerException;
import io.mosip.registration.processor.core.exception.util.PlatformErrorMessages;
import io.mosip.registration.processor.core.logger.RegProcessorLogger;
import io.mosip.registration.processor.core.spi.restclient.RegistrationProcessorRestClientService;
import io.mosip.registration.processor.packet.storage.utils.Utilities;

@Service
public class CitizenshipVerificationProcessor {
	
	private static Logger regProcLogger = RegProcessorLogger.getLogger(CitizenshipVerificationProcessor.class);
	
	private static final String USER = "MOSIP_SYSTEM";

	public static final String GLOBAL_CONFIG_TRUE_VALUE = "Y";
	
	private ObjectMapper objectMapper = new ObjectMapper();

	@Autowired
	private Utilities utilities;
	
	@Autowired
	private Environment env;
	
	@Autowired
	private RegistrationProcessorRestClientService<Object> restClientService;
	
	public MessageDTO process(MessageDTO object) {
		
		object.setMessageBusAddress(MessageBusAddress.CITIZENSHIP_VERIFICATION_BUS_IN);
		object.setIsValid(Boolean.FALSE);
		object.setInternalError(Boolean.TRUE);
		
		String regId = object.getRid();
		
		try {
			if(validatePacketCitizenship(regId, object)) {
				object.setIsValid(Boolean.TRUE);
				object.setInternalError(Boolean.FALSE);
				regProcLogger.info("In Registration Processor", "Citizenship Verification",
						"Reg Id: " + regId + " validation passes.");
			}
			else {
				object.setIsValid(Boolean.FALSE);
				object.setInternalError(Boolean.FALSE);
				regProcLogger.info("In Registration Processor", "Citizenship Verification",
						"Reg Id: " + regId + " validation failed. --> packet goes to manual verification stage");
			}
			
		}
		catch(Exception e) {
			e.printStackTrace();
			object.setIsValid(Boolean.FALSE);
			object.setInternalError(Boolean.TRUE);
			regProcLogger.error("In Registration Processor", "Citizenship Verification",
					"Failed to validate citizenship for packet: " + e.getMessage());
			
		}
		return object;
		
	}
	
	private boolean validatePacketCitizenship(String regId, MessageDTO object) {
		boolean ifCitizenshipValid = false;
		
		try {
			String citizenshipType = utilities.getPacketManagerService().getField(regId, "applicantCitizenshipType", object.getReg_type(), ProviderStageName.PAYMENT_VALIDATOR);
		
			if (!CitizenshipType.BIRTH_INDEGINOUS.getCitizenshipType().equalsIgnoreCase(citizenshipType) || 
					!CitizenshipType.BIRTH_NON_INDEGINOUS.getCitizenshipType().equalsIgnoreCase(citizenshipType)) {
                
				ifCitizenshipValid = false;
				// If citizenship type is not "citizen by birth", do not perform validations - packet goes to manual verification
                regProcLogger.info("citizenship validation stage skipped for registrationId {} because citizenship type is not citizenbybirth", regId);
            }
			else {
				
				Map<String, String> applicantFields = utilities.getPacketManagerService().getFields(regId, List.of(
	                    MappingJsonConstants.DOB,
	                    MappingJsonConstants.AGE,
	                    MappingJsonConstants.APPLICANT_TRIBE,
	                    MappingJsonConstants.APPLICANT_CLAN,
	                    MappingJsonConstants.APPLICANT_PLACE_OF_ORIGIN,
	                    MappingJsonConstants.FATHER_NIN,
	                    MappingJsonConstants.FATHER_TRIBE,
	                    MappingJsonConstants.FATHER_CLAN,
	                    MappingJsonConstants.FATHER_PLACE_OF_ORIGIN,
	                    MappingJsonConstants.FATHER_SURNAME,
	                    MappingJsonConstants.FATHER_GIVENNAME,
	                    MappingJsonConstants.FATHER_OTHERNAMES,
	                    MappingJsonConstants.MOTHER_NIN,
	                    MappingJsonConstants.MOTHER_TRIBE,
	                    MappingJsonConstants.MOTHER_CLAN,
	                    MappingJsonConstants.MOTHER_PLACE_OF_ORIGIN,
	                    MappingJsonConstants.MOTHER_SURNAME,
	                    MappingJsonConstants.MOTHER_GIVENNAME,
	                    MappingJsonConstants.MOTHER_OTHERNAMES
	            ), object.getReg_type(), ProviderStageName.CITIZENSHIP_VERIFICATION);
				
				if (!checkIfAtLeastOneParentHasNIN(applicantFields)) {
	                //handleValidationWithNoParentNinFound(regId);
	            }
				else {
					if(handleValidationWithParentNinFound(applicantFields)) {
						ifCitizenshipValid = true;
					}
					else {
						ifCitizenshipValid = false;
					}
				}
				
			}
			
		
		} catch (ApisResourceAccessException | PacketManagerException | JsonProcessingException | IOException e) {
			//e.printStackTrace();
			object.setIsValid(Boolean.FALSE);
			object.setInternalError(Boolean.TRUE);
			regProcLogger.error("In Registration Processor", "Citizenship Verification",
					"Failed to validate citizenship for packet: " + PlatformErrorMessages.PACKET_MANAGER_EXCEPTION.getMessage());
		}
			
		return ifCitizenshipValid;
		
	}
	
	private boolean checkIfAtLeastOneParentHasNIN(Map<String, String> fields) {
        String fatherNIN = fields.get("fatherNIN");
        String motherNIN = fields.get("motherNIN");
        return fatherNIN != null && !fatherNIN.isEmpty() || (motherNIN != null && !motherNIN.isEmpty());
    }
	
	private boolean handleValidationWithParentNinFound(Map<String, String> fields) {
		
		//utilities.getPacketManagerService().
		
		return false;
		
	}

}
