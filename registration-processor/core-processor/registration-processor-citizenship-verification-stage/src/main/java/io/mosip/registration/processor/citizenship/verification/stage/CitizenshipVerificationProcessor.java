package io.mosip.registration.processor.citizenship.verification.stage;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.io.IOUtils;


import java.io.ByteArrayInputStream;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.mosip.kernel.core.logger.spi.Logger;
import io.mosip.kernel.core.util.JsonUtils;
import io.mosip.kernel.core.util.exception.JsonProcessingException;
import io.mosip.registration.processor.citizenship.verification.constants.CitizenshipType;
import io.mosip.registration.processor.citizenship.verification.constants.Relationship;
import io.mosip.registration.processor.citizenship.verification.constants.StatusForNinandLivivngStatus;
import io.mosip.registration.processor.citizenship.verification.service.NinUsageService;
import io.mosip.registration.processor.citizenship.verification.util.NotificationUtility;
import io.mosip.registration.processor.core.abstractverticle.MessageBusAddress;
import io.mosip.registration.processor.core.abstractverticle.MessageDTO;
import io.mosip.registration.processor.core.constant.MappingJsonConstants;
import io.mosip.registration.processor.core.constant.ProviderStageName;
import io.mosip.registration.processor.core.exception.ApisResourceAccessException;
import io.mosip.registration.processor.core.exception.PacketManagerException;
import io.mosip.registration.processor.core.exception.util.PlatformErrorMessages;
import io.mosip.registration.processor.core.logger.RegProcessorLogger;

import io.mosip.registration.processor.core.util.RegistrationExceptionMapperUtil;
import io.mosip.registration.processor.packet.manager.decryptor.Decryptor;
import io.mosip.registration.processor.packet.storage.exception.IdRepoAppException;

import io.mosip.registration.processor.packet.storage.utils.Utilities;
import io.mosip.registration.processor.status.dto.InternalRegistrationStatusDto;
import io.mosip.registration.processor.status.dto.RegistrationAdditionalInfoDTO;
import io.mosip.registration.processor.status.dto.RegistrationStatusDto;

import io.mosip.registration.processor.status.entity.SyncRegistrationEntity;
import io.mosip.registration.processor.status.service.RegistrationStatusService;


import java.time.LocalDate;
import java.time.Period;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;


import org.json.simple.JSONObject;

@Service
public class CitizenshipVerificationProcessor {

	private static Logger regProcLogger = RegProcessorLogger.getLogger(CitizenshipVerificationProcessor.class);

	@Autowired
	private NinUsageService ninUsageService;

	@Autowired
	RegistrationStatusService<String, InternalRegistrationStatusDto, RegistrationStatusDto> registrationStatusService;

	@Autowired
	private Utilities utility;
	
	@Autowired
	RegistrationExceptionMapperUtil registrationStatusMapperUtil;
	
	@Autowired
	private NotificationUtility notificationutility;
	
	@Value("${mosip.notificationtype}")
	private String notificationTypes;

	
	@Autowired
	private Decryptor decryptor;

	
	private ObjectMapper objectMapper;
	
	
	
	@Value("${mosip.registration.processor.datetime.pattern}")
	private String dateformat;
	
	

	public MessageDTO process(MessageDTO object) {

		object.setMessageBusAddress(MessageBusAddress.CITIZENSHIP_VERIFICATION_BUS_IN);
		object.setIsValid(Boolean.FALSE);
		object.setInternalError(Boolean.FALSE);

		String registrationId = object.getRid();

		try {
			if (validatePacketCitizenship(registrationId, object)) {
				object.setIsValid(Boolean.TRUE);
				object.setInternalError(Boolean.FALSE);
				regProcLogger.info("In Registration Processor", "Citizenship Verification",
						"Reg Id: " + registrationId + " validation passes.");
			} else {
				object.setIsValid(Boolean.FALSE);
				object.setInternalError(Boolean.FALSE);
				regProcLogger.info("In Registration Processor", "Citizenship Verification", "Reg Id: " + registrationId
						+ " validation failed. --> packet goes to manual verification stage");
			}

		} catch (Exception e) {
			e.printStackTrace();
			object.setIsValid(Boolean.FALSE);
			object.setInternalError(Boolean.TRUE);
			regProcLogger.error("In Registration Processor", "Citizenship Verification",
					"Failed to validate citizenship for packet: " + e.getMessage());

		}
		return object;

	}

	private boolean validatePacketCitizenship(String registrationId, MessageDTO object) {
		boolean ifCitizenshipValid = false;
		
		objectMapper = new ObjectMapper();

		try {
			regProcLogger.info("Starting citizenship validation for registration ID: {}", registrationId);
			// Consolidate fields into a single list,
			List<String> fieldsToFetch = new ArrayList<>(List.of(MappingJsonConstants.APPLICANT_TRIBE,
					MappingJsonConstants.APPLICANT_CITIZENSHIPTYPE, MappingJsonConstants.APPLICANT_DATEOFBIRTH,
					MappingJsonConstants.APPLICANT_CLAN, MappingJsonConstants.APPLICANT_PLACE_OF_ORIGIN,
					MappingJsonConstants.FATHER_NIN, MappingJsonConstants.FATHER_TRIBE,
					MappingJsonConstants.FATHER_CLAN, MappingJsonConstants.FATHER_PLACE_OF_ORIGIN,
					MappingJsonConstants.FATHER_SURNAME, MappingJsonConstants.FATHER_GIVENNAME,
					MappingJsonConstants.FATHER_OTHERNAMES, MappingJsonConstants.MOTHER_NIN,
					MappingJsonConstants.MOTHER_TRIBE, MappingJsonConstants.MOTHER_CLAN,
					MappingJsonConstants.MOTHER_PLACE_OF_ORIGIN, MappingJsonConstants.MOTHER_SURNAME,
					MappingJsonConstants.MOTHER_GIVENNAME, MappingJsonConstants.MOTHER_OTHERNAMES,
					MappingJsonConstants.GUARDIAN_NIN, MappingJsonConstants.FATHER_LIVINGSTATUS,
					MappingJsonConstants.MOTHER_LIVINGSTATUS, MappingJsonConstants.GUARDIAN_RELATION_TO_APPLICANT

			));

			// Fetch all fields in a single call
			Map<String, String> applicantFields = utility.getPacketManagerService().getFields(registrationId,
					fieldsToFetch, object.getReg_type(), ProviderStageName.CITIZENSHIP_VERIFICATION);
			
			
			
			applicantFields.put(MappingJsonConstants.GUARDIAN_LIVING_STATUS, "Alive");
			

			regProcLogger.info("fields fetched {}: " + applicantFields.toString());
			// Retrieve the citizenshipType from the fetched fields
			String citizenshipType = null;
			String jsonCitizenshipTypes = applicantFields.get(MappingJsonConstants.APPLICANT_CITIZENSHIPTYPE);
			
			try {
				
				List<Map<String, String>> citizenshipTypes = objectMapper.readValue(jsonCitizenshipTypes, new TypeReference<List<Map<String, String>>>() {}); 
				 citizenshipType = citizenshipTypes.get(0).get("value");
			
			}
			catch (Exception e) {
				// TODO: handle exception
			}

			if (!CitizenshipType.BIRTH.getCitizenshipType().equalsIgnoreCase(citizenshipType)) {
				regProcLogger.info("Citizenship verification failed: Not Citizen By Birth");
				ifCitizenshipValid = false;
				// Log skipping validation
			} else {
				regProcLogger.info("Citizenship verification proceed: Citizen By Birth");
				// Add the age to the applicantFields map
				applicantFields.put(MappingJsonConstants.AGE, String.valueOf(utility.getApplicantAge(registrationId, object.getReg_type(),
						ProviderStageName.CITIZENSHIP_VERIFICATION)));

				// Proceed with validations based on the presence of parent NIN
				if (!checkIfAtLeastOneParentHasNIN(applicantFields)) {
					regProcLogger.info("Citizenship verification proceed: No parent has NIN");
					ifCitizenshipValid = handleValidationWithNoParentNinFound(applicantFields);
				} else {
					regProcLogger.info("Citizenship verification proceed: Atleast one parent has NIN");
					ifCitizenshipValid = handleValidationWithParentNinFound(applicantFields);
				}
			}
		} catch (ApisResourceAccessException | PacketManagerException | JsonProcessingException | IOException e) {
			object.setIsValid(Boolean.FALSE);
			object.setInternalError(Boolean.TRUE);
			regProcLogger.error("In Registration Processor", "Citizenship Verification",
					"Failed to validate citizenship for packet: "
							+ PlatformErrorMessages.PACKET_MANAGER_EXCEPTION.getMessage());
		}

		return ifCitizenshipValid;
	}

	private boolean checkIfAtLeastOneParentHasNIN(Map<String, String> fields) {
		String fatherNIN = fields.get("fatherNIN");
		String motherNIN = fields.get("motherNIN");
		return fatherNIN != null && !fatherNIN.isEmpty() || (motherNIN != null && !motherNIN.isEmpty());
	}

	private boolean handleValidationWithParentNinFound(Map<String, String> applicantFields) {
		regProcLogger.info("Citizenship verification proceed: Handling validation with parents NIN found");
		
	    DateTimeFormatter formatter = DateTimeFormatter.ofPattern(MappingJsonConstants.DATE_FORMAT);
	   //boolean isValid = false; // Start assuming the information is not valid

	    String fatherNIN = applicantFields.get(MappingJsonConstants.FATHER_NIN);
	    regProcLogger.info("Father's NIN: " + fatherNIN);
	    String motherNIN = applicantFields.get(MappingJsonConstants.MOTHER_NIN);

	    LocalDate applicantDob = parseDate(applicantFields.get(MappingJsonConstants.APPLICANT_DATEOFBIRTH), formatter);
	    regProcLogger.info("Parsed applicant date of birth from string '" + applicantDob + "' to LocalDate: " + applicantDob);
	    
	    if (applicantDob == null) {
	        regProcLogger.error("Invalid applicant date of birth.");
	        return false;
	    }
	    // Check if both parents' NINs are provided
	    if (fatherNIN != null) {
	        // Validate the father's NIN (if both parents' NINs are provided, this will still validate only the father's NIN)
	        return validateParentInfo(fatherNIN, "FATHER", applicantFields, applicantDob, formatter);
	    } else if (motherNIN != null) {
	        // If only the mother's NIN is provided
	        return validateParentInfo(motherNIN, "MOTHER", applicantFields, applicantDob, formatter);
	    }

	    // If neither parent's NIN is provided, log an error or handle accordingly
	    regProcLogger.error("Neither parent's NIN is provided.");
	    return false;
	}

	private boolean validateParentInfo(String parentNin, String parentType, Map<String, String> applicantFields, LocalDate applicantDob, DateTimeFormatter formatter) {
	    
		regProcLogger.info("Citizenship verification proceed: Validating parent");
		if (parentNin == null) {
	        return false; // No NIN provided for the parent
	    }

	    try {
	        if (ninUsageService.isNinUsedMorethanNtimes(parentNin, parentType)) {
	            regProcLogger.error(parentType + "'s NIN is used more than N times.");
	            return false;
	        }

	        JSONObject parentInfoJson = utility.retrieveIdrepoJson(parentNin);
	        regProcLogger.info("parentInfoJson {}: " + parentInfoJson);
	        
	        if (parentInfoJson == null) {
	            regProcLogger.error(parentType + "'s NIN not found in repo data.");
	            return false;
	        }

	        String livingStatusKey = (parentType.equals("FATHER") ? MappingJsonConstants.FATHER_LIVINGSTATUS : MappingJsonConstants.MOTHER_LIVINGSTATUS);
	        String jsonlivingStatus = applicantFields.getOrDefault(livingStatusKey, "UNKNOWN");
	        
	        String livingStatus = "UNKNOWN";
	        try {
	            if (!jsonlivingStatus.equals("UNKNOWN")) {
	                List<Map<String, String>> livingStatusList = objectMapper.readValue(jsonlivingStatus, new TypeReference<List<Map<String, String>>>() {});
	                livingStatus = livingStatusList.get(0).get("value");
	            }
	        } catch (Exception e) {
	            regProcLogger.error("Error parsing living status JSON", e);
	        }
	        
	        regProcLogger.info("Living status retrieved: " + livingStatus);
	        
	        String status = utility.retrieveIdrepoJsonStatus(parentNin);
	        regProcLogger.info("ID repo status retrieved: " + status);

	        boolean isValidStatus = checkStatus(livingStatus, status);
	        if (!isValidStatus) {
	            regProcLogger.error("Status check failed for " + parentType + ".");
	            return false;
	        }

	        String parentDobStr = (String) parentInfoJson.get(MappingJsonConstants.APPLICANT_DATEOFBIRTH);
	        LocalDate parentDob = parseDate(parentDobStr, formatter);
	        regProcLogger.info("Parsed parent date of birth from string '" + parentDobStr + "' to LocalDate: " + parentDob);
	        
	        if (parentDob == null || !checkApplicantAgeWithParentOrGuardian(applicantDob, parentDob, 15)) {
	            regProcLogger.error(parentType + "'s age difference with the applicant is less than 15 years.");
	            return false;
	        }


	        Map<String, String> person1Map = extractDemographics(parentType, parentInfoJson);
	        regProcLogger.info("Extracted demographics for {}: {}", parentType, person1Map);
	        
	        Map<String, String> person2Map = extractApplicantDemographics(applicantFields);
	        regProcLogger.info("Applicant Extracted demographics for {}: {}", parentType, person2Map);

	        return ValidateTribeAndClan(person1Map, person2Map);
	    } catch (Exception e) {
	        regProcLogger.error("Error processing " + parentType + "'s information: " + e.getMessage());
	        return false;
	    }
	}

	private LocalDate parseDate(String dateStr, DateTimeFormatter formatter) {
	    try {
	        return LocalDate.parse(dateStr, formatter);
	    } catch (DateTimeParseException e) {
	        return null;
	    }
	}
	
	

	private Map<String, String> extractDemographics(String parentType, JSONObject parentInfoJson) {
	    Map<String, String> person1Map = new HashMap<>();
	    person1Map.put(MappingJsonConstants.PERSON, parentType + " in NIRA System");
	    ObjectMapper objectMapper = new ObjectMapper();

	    // Helper method to extract and parse JSON values
	    extractAndPutValue(person1Map, MappingJsonConstants.TRIBE, parentInfoJson, MappingJsonConstants.PARENT_TRIBE, objectMapper);
	    extractAndPutValue(person1Map, MappingJsonConstants.CLAN, parentInfoJson, MappingJsonConstants.PARENT_CLAN, objectMapper);
	    extractAndPutValue(person1Map, MappingJsonConstants.PLACE_OF_ORIGIN, parentInfoJson, MappingJsonConstants.PARENT_PLACE_OF_ORIGIN, objectMapper);

	    return person1Map;
	}

	private void extractAndPutValue(Map<String, String> map, String key, JSONObject jsonObject, String jsonKey, ObjectMapper objectMapper) {
	    String jsonString = null;
	    try {
	        jsonString = jsonObject.get(jsonKey).toString();
	    } catch (Exception e) {
	        // Handle missing key or other exception
	    }
	    if (jsonString != null && !jsonString.isEmpty()) {
	        try {
	            List<Map<String, String>> list = objectMapper.readValue(jsonString, new TypeReference<List<Map<String, String>>>() {});
	            if (!list.isEmpty()) {
	                map.put(key, list.get(0).get("value"));
	            }
	        } catch (Exception e) {
	            // Handle exception
	        }
	    }
	}
	
	
	
	private Map<String, String> extractApplicantDemographics(Map<String, String> applicantFields) {
	    Map<String, String> person2Map = new HashMap<>();
	    person2Map.put(MappingJsonConstants.PERSON, "Applicant");
	    ObjectMapper objectMapper = new ObjectMapper();

	    // Helper method to extract and parse JSON values
	    extractAndPutValue(person2Map, MappingJsonConstants.TRIBE, applicantFields.get(MappingJsonConstants.APPLICANT_TRIBE), objectMapper);
	    extractAndPutValue(person2Map, MappingJsonConstants.CLAN, applicantFields.get(MappingJsonConstants.APPLICANT_CLAN), objectMapper);
	    extractAndPutValue(person2Map, MappingJsonConstants.PLACE_OF_ORIGIN, applicantFields.get(MappingJsonConstants.APPLICANT_PLACE_OF_ORIGIN), objectMapper);

	    return person2Map;
	}

	private void extractAndPutValue(Map<String, String> map, String key, String jsonString, ObjectMapper objectMapper) {
	    if (jsonString != null && !jsonString.isEmpty()) {
	        try {
	            List<Map<String, String>> list = objectMapper.readValue(jsonString, new TypeReference<List<Map<String, String>>>() {});
	            if (!list.isEmpty()) {
	                map.put(key, list.get(0).get("value"));
	            }
	        } catch (Exception e) {
	            // Handle exception
	        }
	    }
	}
	
	
	
	
	private boolean ValidateTribeAndClan(Map<String, String> person1, Map<String, String> person2) {
		Boolean isValid = false;
		
		if (person1.get(MappingJsonConstants.TRIBE).equalsIgnoreCase(person2.get(MappingJsonConstants.TRIBE))) {
			regProcLogger.info("Tribe validation passed for " + person1.get(MappingJsonConstants.PERSON) + " and " + person2.get(MappingJsonConstants.PERSON));
			
			if (person1.get(MappingJsonConstants.CLAN).equalsIgnoreCase(person2.get(MappingJsonConstants.CLAN))) {
				 regProcLogger.info("Clan validation passed for " + person1.get(MappingJsonConstants.PERSON) + " and " + person2.get(MappingJsonConstants.PERSON));
				 
				if (person1.get(MappingJsonConstants.PLACE_OF_ORIGIN)
						.equalsIgnoreCase(person2.get(MappingJsonConstants.PLACE_OF_ORIGIN))) {
					regProcLogger.info("Place of origin validation passed for " + person1.get(MappingJsonConstants.PERSON) + " and " + person2.get(MappingJsonConstants.PERSON));
					isValid = true;
				} else {
					regProcLogger.error("Mismatch in " + person1.get(MappingJsonConstants.PERSON) + ", "
							+ person2.get(MappingJsonConstants.PERSON) + "'s " + MappingJsonConstants.PLACE_OF_ORIGIN
							+ " information.");
				}
			} else {
				regProcLogger.error("Mismatch in " + person1.get(MappingJsonConstants.PERSON) + ", "
						+ person2.get(MappingJsonConstants.PERSON) + "'s " + MappingJsonConstants.CLAN
						+ " information.");
			}
		} else {
			regProcLogger.error("Mismatch in " + person1.get(MappingJsonConstants.PERSON) + ", "
					+ person2.get(MappingJsonConstants.PERSON) + "'s " + MappingJsonConstants.TRIBE + " information.");
		}

		return isValid;
	}
	
	
	
	
	private boolean ValidateguardianTribeAndClan(Map<String, String> guardian1, Map<String, String> guardian2) {
		Boolean isValid = false;
		if (guardian1.get(MappingJsonConstants.TRIBE).equalsIgnoreCase(guardian2.get(MappingJsonConstants.TRIBE))) {
			if (guardian1.get(MappingJsonConstants.CLAN).equalsIgnoreCase(guardian2.get(MappingJsonConstants.CLAN))) {
		 {
					isValid = true;
				
				}
			} else {
				regProcLogger.error("Mismatch in " + guardian1.get(MappingJsonConstants.PERSON) + ", "
						+ guardian2.get(MappingJsonConstants.PERSON) + "'s " + MappingJsonConstants.CLAN
						+ " information.");
			}
		} else {
			regProcLogger.error("Mismatch in " + guardian1.get(MappingJsonConstants.PERSON) + ", "
					+ guardian2.get(MappingJsonConstants.PERSON) + "'s " + MappingJsonConstants.TRIBE + " information.");
		}

		return isValid;
	}
	
	

	
	public boolean checkStatus(String livingStatus, String status) {
	    boolean isValid = false;
	    
	    try {
	    StatusForNinandLivivngStatus livingStatusEnum = StatusForNinandLivivngStatus.valueOf(livingStatus.toUpperCase());
	    StatusForNinandLivivngStatus uinstatusAsEnum = StatusForNinandLivivngStatus.valueOf(status.toUpperCase());

	    switch (livingStatusEnum) {
	        case ALIVE:
	            isValid = handleAliveStatus(uinstatusAsEnum);
	            break;
	        case DECEASED:
	            isValid = handleDeceasedStatus(uinstatusAsEnum);
	            break;
	        default:
	            regProcLogger.error("Unexpected living status: " + livingStatus);
	            break;
	    }
	    } catch (IllegalArgumentException e) {
	        regProcLogger.error("Invalid status provided: " + e.getMessage());
	        // Handle the case where an invalid status is provided.
	        isValid = false;
	    }

	    return isValid;
	}


	private boolean handleAliveStatus(StatusForNinandLivivngStatus uinstatusAsEnum) {
		// Logic for handling ALIVE status with respect to UIN status
	    if (StatusForNinandLivivngStatus.DEACTIVATED.equals(uinstatusAsEnum)) {
	        regProcLogger.error("Operation failed: Living status is alive but UIN status is deactivated.");
	        return false;
	    } else if (StatusForNinandLivivngStatus.ACTIVATED.equals(uinstatusAsEnum)) {
	        return true; // Valid scenario
	    } else {
	        regProcLogger.error("Unexpected UIN status for alive individual.");
	        return false;
	    }
	}

	private boolean handleDeceasedStatus(StatusForNinandLivivngStatus uinstatusAsEnum) {
	 // Logic for handling DECEASED status with respect to UIN status
	    if (StatusForNinandLivivngStatus.ACTIVATED.equals(uinstatusAsEnum)) {
	        sendNotification(null, null);
	        return true; // Assuming continuation despite the discrepancy
	    } else if (StatusForNinandLivivngStatus.DEACTIVATED.equals(uinstatusAsEnum)) {
	        return true; // Valid scenario
	    } else {
	        regProcLogger.error("Unexpected UIN status for deceased individual.");
	        return false;
	    }
	}

	private void sendNotification(SyncRegistrationEntity regEntity,
			  InternalRegistrationStatusDto registrationStatusDto) {
	    try {
	     
			
			String registrationId = registrationStatusDto.getRegistrationId();
			if (regEntity.getOptionalValues() != null);
			
			InputStream inputStream = new ByteArrayInputStream(regEntity.getOptionalValues());;
			InputStream decryptedInputStream = decryptor.decrypt(
						registrationId,
						utility.getRefId(registrationId, regEntity.getReferenceId()),
						inputStream );;
			String decryptedData = IOUtils.toString(decryptedInputStream, StandardCharsets.UTF_8);
			RegistrationAdditionalInfoDTO registrationAdditionalInfoDTO = (RegistrationAdditionalInfoDTO) JsonUtils
					.jsonStringToJavaObject(RegistrationAdditionalInfoDTO.class, decryptedData);
			String[] allNotificationTypes = notificationTypes.split("\\|");
			
			notificationutility.sendNotification(registrationAdditionalInfoDTO, registrationStatusDto, regEntity, allNotificationTypes);
	    } catch (Exception e) {
	        regProcLogger.error("Send notification failed for rid: " + e.getMessage());
	        
	    }
	}





	private boolean checkApplicantAgeWithParentOrGuardian(LocalDate applicantDob, LocalDate parentOrGuardianDob,
			int ageCondition) {
		return (Period.between(parentOrGuardianDob, applicantDob).getYears() >= ageCondition);
	}

	
	
	private boolean handleValidationWithNoParentNinFound(Map<String, String> applicantFields) {
		String guardianNin = applicantFields.get(MappingJsonConstants.GUARDIAN_NIN);
		String guardianRelationToApplicant = applicantFields.get(MappingJsonConstants.GUARDIAN_RELATION_TO_APPLICANT);
		boolean isValidGuardian = false; // Assume validation starts as false

		if (guardianNin != null) {
			try {
				if (ninUsageService.isNinUsedMorethanNtimes(MappingJsonConstants.GUARDIAN_NIN, "20")) { //TODO Mention what relation
					// Handle the case where the NIN usage is over the limit
					return false;
				}

				JSONObject guardianInfo = utility.retrieveIdrepoJson(MappingJsonConstants.GUARDIAN_NIN);
				//String status  = utility.retrieveIdrepoJsonStatus(MappingJsonConstants.GUARDIAN_NIN);
				
				if (guardianRelationToApplicant.equalsIgnoreCase(Relationship.GRAND_FATHER_ON_FATHERS_SIDE.getRelationship())
						|| Relationship.GRAND_MOTHER_ON_FATHERS_SIDE.getRelationship().equalsIgnoreCase(guardianRelationToApplicant)) {
					isValidGuardian = validateGrandparentRelationship(applicantFields, guardianInfo);

				} else if (guardianRelationToApplicant.equalsIgnoreCase(Relationship.BROTHER_OR_SISTER.getRelationship())) {
					isValidGuardian = validateSiblingRelationship(applicantFields, guardianInfo);

				} else if (guardianRelationToApplicant.equalsIgnoreCase(Relationship.MATERNAL_UCLE_OR_AUNT.getRelationship())
						|| Relationship.PATERNAL_UCLE_OR_AUNT.getRelationship().equalsIgnoreCase(guardianRelationToApplicant)) {
					isValidGuardian = validateUncleAuntRelationship(applicantFields, guardianInfo);
				}

				if (!isValidGuardian) {
					regProcLogger.error("Guardian information validation failed.");
				}
				return isValidGuardian;
			} catch (Exception e) {
				regProcLogger.error("Error during guardian information validation: " + e.getMessage());
				return false; // If an exception occurs, return false
			}
		} else {
			regProcLogger.info("Guardian NIN not provided, validation cannot proceed.");
			return false; // If no guardian NIN is provided, return false
		}
	}

	private boolean validateParentAndGrandparentInformation(Map<String, String> applicantFields,JSONObject guardianInfo) {
		
		//String guardianRelationToApplicant = applicantFields.get("guardianRelationToApplicant");
		boolean isValidGuardian = true; // Start with an assumption of validity.

		// Check specific guardian relationships 

			// Check if father's information is provided by the applicant
			String fatherClan = applicantFields.get("fatherClan");
			String fatherTribe = applicantFields.get("fatherTribe");
			String fatherNIN = applicantFields.get("UIN");
			boolean hasFatherInfo = !fatherNIN.isEmpty()
					|| (fatherClan != null && !fatherClan.isEmpty()) && (fatherTribe != null && !fatherTribe.isEmpty());

			// If father's information is provided, proceed with validation against the
			// guardian's (grandfather's/grandmother's) information
			if (hasFatherInfo) {
				String grandfatherClan = guardianInfo.get("fatherClan").toString();
				String grandfatherTribe = guardianInfo.get("fatherTribe").toString();

				// Validate father's clan
				if (!fatherClan.equalsIgnoreCase(grandfatherClan))
					if (isValidGuardian && !fatherTribe.equalsIgnoreCase(grandfatherTribe))
						if (isValidGuardian && !fatherTribe.equalsIgnoreCase(grandfatherTribe))
							if (isValidGuardian && !fatherTribe.equalsIgnoreCase(grandfatherTribe))
								isValidGuardian = true;
							else {
								regProcLogger.error("Mismatch in clan information between father and grandfather.");

							}

						// If clan matches, validate father's tribe
						else {
							regProcLogger.error("Mismatch in tribe information between father and grandfather.");

						}
			} else {
				// If father's information is not sufficiently provided
				regProcLogger.error("Insufficient father's information provided for validation.");
				 

			}
			return isValidGuardian;// Return the result of the validation
		}  
			



	
	
	
	
	
	private boolean validateGrandparentRelationship(Map<String, String> applicantFields, JSONObject guardianInfo) throws IdRepoAppException, ApisResourceAccessException {

		String livingStatus = applicantFields.get(MappingJsonConstants.GUARDIAN_LIVING_STATUS); // Retrieve from applicantFields
		String status = utility.retrieveIdrepoJsonStatus(MappingJsonConstants.GUARDIAN_NIN);
		

		boolean isValidStatus = checkStatus(livingStatus, status);

		// Based on the result, you might want to log an error or take some other action
		if (!isValidStatus) {
		    // Log an error or take other actions as needed
		    regProcLogger.error("Status check failed.");
		}

		boolean isValidGuardian = true; // Assume validation success initially

		String guardianDobStr = guardianInfo.get(MappingJsonConstants.APPLICANT_DATEOFBIRTH).toString(); // Retrieve the DOB string
		LocalDate guardianDob = LocalDate.parse(guardianDobStr);
		LocalDate applicantDob = LocalDate.parse(applicantFields.get(MappingJsonConstants.APPLICANT_DATEOFBIRTH));
		

		// First, check the age difference
		if (!checkApplicantAgeWithParentOrGuardian(applicantDob, guardianDob, 20)) {
			regProcLogger.error("Guardian (grandfather) is not at least 20 years older than the applicant.");
			isValidGuardian = false; // Fail validation if age condition is not met
		}

		// Proceed with checking guardian clan and tribe if age condition is met
		
		
		Map<String, String> guardian1Map = new HashMap<>();
		guardian1Map.put(MappingJsonConstants.PERSON, "Guardian in NIRA System");
		guardian1Map.put(MappingJsonConstants.TRIBE,guardianInfo.get(MappingJsonConstants.GUARDIAN_TRIBE)!= null ? guardianInfo.get(MappingJsonConstants.GUARDIAN_TRIBE).toString(): null);
		guardian1Map.put(MappingJsonConstants.CLAN,guardianInfo.get(MappingJsonConstants.GUARDIAN_CLAN)!= null ? guardianInfo.get(MappingJsonConstants.GUARDIAN_CLAN).toString(): null);
		
		
		Map<String, String> guardian2Map = new HashMap<>();
		guardian2Map.put(MappingJsonConstants.PERSON, "Guardian in Form");
		guardian2Map.put(MappingJsonConstants.TRIBE,applicantFields.get(MappingJsonConstants.GUARDIAN_TRIBE_FORM));
		guardian2Map.put(MappingJsonConstants.CLAN,applicantFields.get(MappingJsonConstants.GUARDIAN_CLAN_FORM));
		
		
		isValidStatus = ValidateguardianTribeAndClan(guardian1Map, guardian2Map);
		
		// Only proceed to additional validations if all previous checks have passed
		if (isValidGuardian) {
			isValidGuardian = validateParentAndGrandparentInformation(applicantFields, guardianInfo);
		}

		return isValidGuardian; // Return the validation result
	}

	private boolean validateSiblingRelationship(Map<String, String> applicantFields, JSONObject guardianInfo) throws IdRepoAppException, ApisResourceAccessException {
		// Assume you can obtain living status and UIN status similarly to previous
		// examples
		String livingStatus = applicantFields.get(MappingJsonConstants.GUARDIAN_LIVING_STATUS); // Retrieve from applicantFields
		String status = utility.retrieveIdrepoJsonStatus(MappingJsonConstants.GUARDIAN_NIN);

		boolean isValidStatus = checkStatus(livingStatus, status);

		// Based on the result, you might want to log an error or take some other action
		if (!isValidStatus) {
		    // Log an error or take other actions as needed
		    regProcLogger.error("Status check failed.");
		}

		boolean isValidGuardian = true; // Assume the guardian's information is valid initially.
	
		Map<String, String> guardian1Map = new HashMap<>();
		guardian1Map.put(MappingJsonConstants.PERSON, "Guardian in NIRA System");
		guardian1Map.put(MappingJsonConstants.TRIBE,guardianInfo.get(MappingJsonConstants.GUARDIAN_TRIBE)!= null ? guardianInfo.get(MappingJsonConstants.GUARDIAN_TRIBE).toString(): null );
		guardian1Map.put(MappingJsonConstants.CLAN,guardianInfo.get(MappingJsonConstants.GUARDIAN_CLAN)!= null ? guardianInfo.get(MappingJsonConstants.GUARDIAN_CLAN).toString(): null);
		
		
		Map<String, String> guardian2Map = new HashMap<>();
		guardian2Map.put(MappingJsonConstants.PERSON, "Guardian in Form");
		guardian2Map.put(MappingJsonConstants.TRIBE,applicantFields.get(MappingJsonConstants.GUARDIAN_TRIBE_FORM));
		guardian2Map.put(MappingJsonConstants.CLAN,applicantFields.get(MappingJsonConstants.GUARDIAN_CLAN_FORM));
		
		
		isValidStatus = ValidateguardianTribeAndClan(guardian1Map, guardian2Map);

		return isValidGuardian; // Return the overall validation result.
	}

	private boolean validateUncleAuntRelationship(Map<String, String> applicantFields, JSONObject guardianInfo) throws IdRepoAppException, ApisResourceAccessException {

		// Extract living status and UIN status from the provided data structures
		String livingStatus = applicantFields.get("livingStatus"); // Retrieve living status from applicantFields
		String status = utility.retrieveIdrepoJsonStatus(MappingJsonConstants.GUARDIAN_NIN);

		boolean isValidStatus = checkStatus(livingStatus, status);

		
		if (!isValidStatus) {
		    regProcLogger.error("Status check failed.");
		}

		boolean isValid = true; // Assume the guardian's information is valid initially.

		Map<String, String> guardian1Map = new HashMap<>();
		guardian1Map.put(MappingJsonConstants.PERSON, "Guardian in NIRA System");
		guardian1Map.put(MappingJsonConstants.TRIBE,guardianInfo.get(MappingJsonConstants.GUARDIAN_TRIBE)!= null ? guardianInfo.get(MappingJsonConstants.GUARDIAN_TRIBE).toString(): null);
		guardian1Map.put(MappingJsonConstants.CLAN,guardianInfo.get(MappingJsonConstants.GUARDIAN_CLAN)!= null ? guardianInfo.get(MappingJsonConstants.GUARDIAN_CLAN).toString(): null);
		
		
		Map<String, String> guardian2Map = new HashMap<>();
		guardian2Map.put(MappingJsonConstants.PERSON, "Guardian in Form");
		guardian2Map.put(MappingJsonConstants.TRIBE,applicantFields.get(MappingJsonConstants.GUARDIAN_TRIBE_FORM));
		guardian2Map.put(MappingJsonConstants.CLAN,applicantFields.get(MappingJsonConstants.GUARDIAN_CLAN_FORM));
		
		
		isValidStatus = ValidateguardianTribeAndClan(guardian1Map, guardian2Map);
		
		

		return isValid; // Return the validation result.
	}

}
