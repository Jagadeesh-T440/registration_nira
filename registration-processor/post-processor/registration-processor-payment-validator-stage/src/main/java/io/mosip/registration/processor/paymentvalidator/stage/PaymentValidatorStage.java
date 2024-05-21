package io.mosip.registration.processor.paymentvalidator.stage;

import java.util.HashMap;

import java.util.Objects;

import org.json.simple.JSONObject;

import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.core.env.Environment;
import org.springframework.http.MediaType;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.mosip.kernel.core.exception.ExceptionUtils;
import io.mosip.kernel.core.logger.spi.Logger;
import io.mosip.registration.processor.core.abstractverticle.MessageBusAddress;
import io.mosip.registration.processor.core.abstractverticle.MessageDTO;
import io.mosip.registration.processor.core.abstractverticle.MosipEventBus;
import io.mosip.registration.processor.core.abstractverticle.MosipRouter;
import io.mosip.registration.processor.core.abstractverticle.MosipVerticleAPIManager;
import io.mosip.registration.processor.core.code.RegistrationTransactionTypeCode;
import io.mosip.registration.processor.core.constant.ProviderStageName;
import io.mosip.registration.processor.core.http.ResponseWrapper;
import io.mosip.registration.processor.core.logger.RegProcessorLogger;
import io.mosip.registration.processor.packet.storage.utils.Utilities;
import io.mosip.registration.processor.paymentvalidator.constants.PrnStatusCode;
import io.mosip.registration.processor.paymentvalidator.constants.RegType;
import io.mosip.registration.processor.paymentvalidator.constants.TaxHeadCode;
import io.mosip.registration.processor.paymentvalidator.dto.ConsumePrnRequestDTO;
import io.mosip.registration.processor.paymentvalidator.dto.IsPrnRegInLogsRequestDTO;
import io.mosip.registration.processor.paymentvalidator.dto.PrnStatusResponseDTO;
import io.mosip.registration.processor.paymentvalidator.dto.PrnStatusResponseDataDTO;
import io.mosip.registration.processor.paymentvalidator.dto.PrnStatusRequestDTO;
import io.mosip.registration.processor.paymentvalidator.util.CustomizedRestApiClient;
import io.mosip.registration.processor.status.dto.InternalRegistrationStatusDto;
import io.mosip.registration.processor.status.dto.RegistrationStatusDto;
import io.mosip.registration.processor.status.service.RegistrationStatusService;

/**
 * Payment Validation Stage for verifying payment registration numbers
 * 
 * 
 * @author Ibrahim Nkambo
 */

@ComponentScan(basePackages = { "${mosip.auth.adapter.impl.basepackage}",
		"io.mosip.registration.processor.rest.client.config", "io.mosip.registration.processor.core.kernel.beans",
		"io.mosip.registration.processor.core.config", "io.mosip.registration.processor.packet.storage.config",
		"io.mosip.registration.processor.paymentvalidator.config",
		"io.mosip.registration.processor.paymentvalidator.service" })
public class PaymentValidatorStage extends MosipVerticleAPIManager {

	private static final String STAGE_PROPERTY_PREFIX = "mosip.regproc.paymentvalidator.";
	private static Logger regProcLogger = RegProcessorLogger.getLogger(PaymentValidatorStage.class);

	@Value("${gateway.payment.service.api.get-prn-status}")
	private String getPrnStatusApiUrl;

	@Value("${gateway.payment.service.api.check-if-prn-consumed}")
	private String checkPrnConsumptionApiUrl;

	@Value("${gateway.payment.service.api.consume-prn}")
	private String consumePrnApiUrl;

	@Value("${gateway.payment.service.api.check-logs}")
	private String checkLogsApiUrl;

	/** The mosip event bus. */
	private MosipEventBus mosipEventBus;

	/**
	 * After this time intervel, message should be considered as expired (In
	 * seconds).
	 */
	@Value("${mosip.regproc.paymentvalidator.message.expiry-time-limit}")
	private Long messageExpiryTimeLimit;

	private static final String DATETIME_PATTERN = "mosip.registration.processor.datetime.pattern";

	/** The cluster manager url. */
	@Value("${vertx.cluster.configuration}")
	private String clusterManagerUrl;

	/** worker pool size. */
	@Value("${worker.pool.size}")
	private Integer workerPoolSize;

	/** Mosip router for APIs */
	@Autowired
	MosipRouter router;

	@Autowired
	CustomizedRestApiClient restApiClient;

	private ObjectMapper objectMapper = new ObjectMapper();

	@Autowired
	private Utilities utilities;

	@Autowired
	private Environment env;

	/** The registration status service. */
	@Autowired
	RegistrationStatusService<String, InternalRegistrationStatusDto, RegistrationStatusDto> registrationStatusService;

	@SuppressWarnings("null")
	@Override
	public MessageDTO process(MessageDTO object) {

		object.setIsValid(Boolean.FALSE);
		object.setInternalError(Boolean.FALSE);
		object.setMessageBusAddress(MessageBusAddress.PAYMENT_VALIDATOR_BUS_IN);
		regProcLogger.info("In Registration Processor - Payment Validator - Entering payment validator stage");

		boolean isTransactionSuccessful = false;

		String regId = object.getRid();
		String regType = object.getReg_type();
		InternalRegistrationStatusDto registrationStatusDto = null;

		try {
			registrationStatusDto = registrationStatusService.getRegistrationStatus(regId, object.getReg_type(),
					object.getIteration(), object.getWorkflowInstanceId());
			registrationStatusDto
					.setLatestTransactionTypeCode(RegistrationTransactionTypeCode.PAYMENT_VALIDATION.toString());
			registrationStatusDto.setRegistrationStageName(getStageName());

			regProcLogger.info("In Registration Processor - Payment Validator - Extracting PRN from packet");
			String prnNum = utilities.getPacketManagerService().getField(regId, "PRN", object.getReg_type(),
					ProviderStageName.PAYMENT_VALIDATOR);

			/* Will change to NIN in new env version */
			regProcLogger.info("In Registration Processor - Payment Validator - Extracting NIN from packet");
			String uin = utilities.getPacketManagerService().getField(regId, "UIN", object.getReg_type(),
					ProviderStageName.PAYMENT_VALIDATOR);

			if (regType.equalsIgnoreCase(RegType.LOST_USECASE) || regType.equalsIgnoreCase(RegType.UPDATE_USECASE)) {

					PrnStatusResponseDTO prnStatusResponseMap = checkPrnStatus(prnNum);
					PrnStatusResponseDataDTO dataResponse = prnStatusResponseMap.getData();
					
					if (dataResponse != null) {
						try {
							/* will change to allow handles method using NIN */
							JSONObject uinJson = utilities.retrieveIdrepoJson(uin);
						
							if (Objects.isNull(uinJson)) {
								regProcLogger.error("In Registration Processor - Payment Validator - UIN/NIN doesn't exist.");
								/* Send notification to applicant here */
								object.setIsValid(Boolean.FALSE);
							} else {
								regProcLogger.info("In Registration Processor - Payment Validator - UIN/NIN check - passed");
								/* can we wait for payment if the PRN isn't paid yet? */
								if (!dataResponse.getStatusCode()
										.equalsIgnoreCase(PrnStatusCode.PRN_STATUS_RECEIVED_CREDITED.getStatusCode())) {
									regProcLogger.info("In Registration Processor - Payment Validator - PRN not paid.");
									/* Send notification to applicant here */
									object.setIsValid(Boolean.FALSE);
									/* how to route packet to try for more time for update of status from payment gateway service */
								} else {
									regProcLogger.info("In Registration Processor - Payment Validator - Payment status check - passed");
									if (!validateTaxHeadAndRegType(dataResponse, regType)) {
										regProcLogger.info("In Registration Processor - Payment Validator - PRN not valid for the usecase");
										object.setIsValid(Boolean.FALSE);
										/* Send notification to applicant here */
									} else {
										regProcLogger.info("In Registration Processor - Payment Validator - PRN valid for the usecase");
										
										if(checkTranscLogs(prnNum, regId)) {
											/* Check for re-processing of packet */
											if(!registrationStatusDto.getStatusCode().equals("PROCESSED")
												|| !registrationStatusDto.getStatusCode().equals("PROCESSING")) {
												
												object.setIsValid(Boolean.TRUE);
												regProcLogger.info(
														"In Registration Processor - Payment Validator - PRN consumption success. Send to next stage.");
											}
											else {
												
												regProcLogger.info(
														"In Registration Processor - Payment Validator - PRNs paid/used before. Reject packet.");
												/* Send notification to applicant here */
												object.setIsValid(Boolean.FALSE);
											}
										}else {
											
											regProcLogger.info(
													"In Registration Processor - Payment Validator - PRN consumption check - false");
											/* Add regId and PRN to consumption */ 
											ConsumePrnRequestDTO consumePrnRequestDTO = new ConsumePrnRequestDTO();
											consumePrnRequestDTO.setPrn(prnNum);
											consumePrnRequestDTO.setRegId(regId);

											regProcLogger.info(
													"In Registration Processor - Payment Validator - Proceeding to consume PRN");
											if (consumePrn(consumePrnRequestDTO)) {
												object.setIsValid(Boolean.TRUE);
												regProcLogger.info(
														"In Registration Processor - Payment Validator - PRN consumption success. Send to next stage.");
											} else {
												regProcLogger.error(
														"In Registration Processor - Payment Validator - PRN consumption failed. Send to reprocessing.");
												object.setIsValid(Boolean.FALSE);
											}	
										}
										
										
									}

								}

							}

						} catch (Exception e) {
							object.setIsValid(Boolean.FALSE);
							object.setInternalError(Boolean.TRUE);
							regProcLogger.error(
									"In Registration Processor - Payment Validator - Failed to check if NIN exists: "
											+ e.getMessage());
						}
					}
					else {
						object.setIsValid(Boolean.FALSE);
						regProcLogger.error("In Registration Processor - Payment Validator - Invalid PRN");
						/* Send notification to applicant here */
					}

			}
		} catch (Exception e) {
			object.setIsValid(Boolean.FALSE);
			object.setInternalError(Boolean.TRUE);
			regProcLogger.error("In Registration Processor - Payment Validator - Failed to access Payment service api: "
					+ e.getMessage() + ExceptionUtils.getStackTrace(e));
		}

		return object;
	}

	@Override
	public void deployVerticle() {
		mosipEventBus = this.getEventBus(this, clusterManagerUrl, workerPoolSize);
		this.consumeAndSend(mosipEventBus, MessageBusAddress.PAYMENT_VALIDATOR_BUS_IN,
				MessageBusAddress.PAYMENT_VALIDATOR_BUS_OUT, messageExpiryTimeLimit);
	}

	@Override
	protected String getPropertyPrefix() {
		return STAGE_PROPERTY_PREFIX;
	}

	@Override
	public void start() {
		router.setRoute(this.postUrl(getVertx(), MessageBusAddress.PAYMENT_VALIDATOR_BUS_IN,
				MessageBusAddress.PAYMENT_VALIDATOR_BUS_OUT));
		this.createServer(router.getRouter(), getPort());
	}
	/**
	 * This method calls an external API to check the transaction logs if PRN and RegId is present
	 * 
	 * 
	 * @param prn
	 * @param regId
	 * @return status
	 */
	@SuppressWarnings("unchecked")
	private boolean checkTranscLogs(String prn, String regId) {
		regProcLogger.info(
				"In Registration Processor - Payment Validator - Checking payment gateway service if PRN and Reg Id are present in transaction logs");

		boolean isPresentInLogs = false;

		IsPrnRegInLogsRequestDTO isPrnRegInLogsRequestDTO = new IsPrnRegInLogsRequestDTO();
		isPrnRegInLogsRequestDTO.setPrn(prn);
		isPrnRegInLogsRequestDTO.setRegId(regId);

		HashMap<String, Boolean> responseMap = null;
		ResponseWrapper<?> response = null;

		try {
			response = restApiClient.postApi(checkLogsApiUrl, MediaType.APPLICATION_JSON, isPrnRegInLogsRequestDTO,
					ResponseWrapper.class);
			
			if(response.getErrors()!=null) {
				isPresentInLogs = true;
			}
			else {
				responseMap = (HashMap<String, Boolean>) response.getResponse();
				if (responseMap != null && responseMap.get("presentInLogs")==true) {
					isPresentInLogs = true;
				}
			}	
		} catch (Exception e) {
			regProcLogger.error("Internal Error occured while contacting gateway service for PRN status. "
					+ ExceptionUtils.getStackTrace(e));
		}
		return isPresentInLogs;

	}

	/**
	 * This method calls an external API to consume a PRN
	 * 
	 * @param consumePrnRequestDTO
	 * @return consumption status
	 */
	@SuppressWarnings("unchecked")
	private boolean consumePrn(ConsumePrnRequestDTO consumePrnRequestDTO) {
		regProcLogger.info(
				"In Registration Processor - Payment Validator - Consuming of PRN and addition into transaction logs");
		HashMap<String, Boolean> responseMap = null;
		try {
			regProcLogger.info("Request {} :" + consumePrnRequestDTO.toString());
			ResponseWrapper<?> response = restApiClient.postApi(consumePrnApiUrl, MediaType.APPLICATION_JSON, consumePrnRequestDTO,
					ResponseWrapper.class);
			regProcLogger.info(
					"In Registration Processor - Payment Validator - response for consumeprn: " + response.toString());

			if (response != null && response.getResponse() != null) {
				responseMap = (HashMap<String, Boolean>) response.getResponse();
				
				return responseMap.get("consumedStatus");
			}

		} catch (Exception e) {
			regProcLogger.error("Internal Error occured contacting gateway service for PRN consumption. "
					+ ExceptionUtils.getStackTrace(e));
		}

		return false;

	}
	
	/**
	 * This method calls an external API to check for the status of a PRN 
	 * 
	 * 
	 * @param prn
	 * @return PrnStatusResponseDTO
	 */
	private PrnStatusResponseDTO checkPrnStatus(String prn) {
		regProcLogger.info(
				"In Registration Processor - Payment Validator - Checking payment gateway service for PRN status");

		PrnStatusRequestDTO prnStatusRequestDTO = new PrnStatusRequestDTO();
		prnStatusRequestDTO.setPRN(prn);
		PrnStatusResponseDTO response = null;

		try {
			response = restApiClient.postApi(getPrnStatusApiUrl, MediaType.APPLICATION_JSON, prnStatusRequestDTO,
					PrnStatusResponseDTO.class);
		} catch (Exception e) {
			regProcLogger.error("Internal Error occured while contacting gateway service for PRN status. "
					+ ExceptionUtils.getStackTrace(e));
		}
		return response;
	}
	
	/**
	 * This method validates the PRN taxhead against the registration type i.e. LOST, UPDATE
	 * 
	 * @param response
	 * @param regType
	 * @return status
	 */
	private boolean validateTaxHeadAndRegType(PrnStatusResponseDataDTO response, String regType) {
		
		if(regType.equalsIgnoreCase(response.getProcessFlow())) {
			if(response.getTaxHeadCode().equalsIgnoreCase(TaxHeadCode.TAX_HEAD_CHANGE.getTaxHeadCode())){
				if(response.getAmountPaid().equals(TaxHeadCode.TAX_HEAD_CHANGE.getAmountPaid())) {
					return true;
				}
			}
			else if(response.getTaxHeadCode().equalsIgnoreCase(TaxHeadCode.TAX_HEAD_CORRECTION_ERRORS.getTaxHeadCode())){
				if(response.getAmountPaid().equals(TaxHeadCode.TAX_HEAD_CORRECTION_ERRORS.getAmountPaid())) {
					return true;
				}
			}
			else if(response.getTaxHeadCode().equalsIgnoreCase(TaxHeadCode.TAX_HEAD_REPLACE.getTaxHeadCode())){
				if(response.getAmountPaid().equals(TaxHeadCode.TAX_HEAD_REPLACE.getAmountPaid())) {
					return true;
				}
			}
			else if(response.getTaxHeadCode().equalsIgnoreCase(TaxHeadCode.TAX_HEAD_REPLACE_DEFACED.getTaxHeadCode())){
				if(response.getAmountPaid().equals(TaxHeadCode.TAX_HEAD_REPLACE_DEFACED.getAmountPaid())) {
					return true;
				}
			}
			else {
				return false;
			}
		}
		
		return false;
	}
	
	/**
	 * This method sends notification to applicant based on successful and failed processing of payment check
	 */
	private void sendNotification() {
		
	}

}
