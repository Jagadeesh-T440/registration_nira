package io.mosip.registration.processor.paymentvalidator.stage;

import java.net.URI;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.apache.commons.beanutils.converters.BooleanConverter;
import org.apache.commons.net.util.Base64;
import org.hibernate.engine.query.spi.sql.NativeSQLQueryCollectionReturn;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;

import io.mosip.kernel.core.exception.ExceptionUtils;
import io.mosip.kernel.core.http.RequestWrapper;
import io.mosip.kernel.core.logger.spi.Logger;
import io.mosip.kernel.core.util.DateUtils;
import io.mosip.registration.processor.core.abstractverticle.MessageBusAddress;
import io.mosip.registration.processor.core.abstractverticle.MessageDTO;
import io.mosip.registration.processor.core.abstractverticle.MosipEventBus;
import io.mosip.registration.processor.core.abstractverticle.MosipRouter;
import io.mosip.registration.processor.core.abstractverticle.MosipVerticleAPIManager;
import io.mosip.registration.processor.core.constant.ProviderStageName;
import io.mosip.registration.processor.core.http.ResponseWrapper;
import io.mosip.registration.processor.core.logger.RegProcessorLogger;
import io.mosip.registration.processor.core.spi.restclient.RegistrationProcessorRestClientService;
import io.mosip.registration.processor.packet.storage.utils.Utilities;
import io.mosip.registration.processor.paymentvalidator.constants.PrnStatusCode;
import io.mosip.registration.processor.paymentvalidator.constants.TaxHeadCode;
import io.mosip.registration.processor.paymentvalidator.dto.ConsumePrnRequestDTO;
import io.mosip.registration.processor.paymentvalidator.dto.MainMosipResponseDTO;
import io.mosip.registration.processor.paymentvalidator.dto.PrnConsumedBooleanDTO;
import io.mosip.registration.processor.paymentvalidator.service.PrnConsumedService;
import io.mosip.registration.processor.paymentvalidator.util.CustomizedRestApiClient;

@ComponentScan(basePackages = { "${mosip.auth.adapter.impl.basepackage}",
		"io.mosip.registration.processor.rest.client.config", "io.mosip.registration.processor.core.kernel.beans",
		"io.mosip.registration.processor.core.config", "io.mosip.registration.processor.packet.storage.config",
		"io.mosip.registration.processor.paymentvalidator.config"})
public class PaymentValidatorStage extends MosipVerticleAPIManager {

	private static final String STAGE_PROPERTY_PREFIX = "mosip.regproc.paymentvalidator.";
	private static Logger regProcLogger = RegProcessorLogger.getLogger(PaymentValidatorStage.class);
	
	@Value("${gateway.payment.service.api.get-prn-status}")
	private String getPrnStatusApiUrl;
	
	@Value("${gateway.payment.service.api.check-if-prn-consumed}")
	private String getCheckPrnConsumptionApiUrl;
	
	@Value("${gateway.payment.service.api.consume-prn}")
	private String consumePrnApiUrl;

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
	PrnConsumedService prnConsumedService;

	@Autowired
	CustomizedRestApiClient restApiClient;

	private ObjectMapper objectMapper = new ObjectMapper();

	@Autowired
	private Utilities utilities;
	
	@Autowired
	private Environment env;

	@Override
	public MessageDTO process(MessageDTO object) {
		
		object.setIsValid(Boolean.FALSE);
		object.setInternalError(Boolean.FALSE);
		object.setMessageBusAddress(MessageBusAddress.PAYMENT_VALIDATOR_BUS_IN);
		regProcLogger.info("In Registration Processor", "Payment Validator", "Entering payment validator stage");

		String regId = object.getRid();
		//String prnNum = "1122334455";

		try {
			
			String prnNum = utilities.getPacketManagerService().getField(regId,"PRN",object.getReg_type(), ProviderStageName.PAYMENT_VALIDATOR);
			regProcLogger.info("Extracting PRN from packet");
			
			String uinString = utilities.getPacketManagerService().getField(regId, "NIN", object.getReg_type(), ProviderStageName.PAYMENT_VALIDATOR);
			regProcLogger.info("Extracting NIN from packet");
			
			String url = getPrnStatusApiUrl + "/{prn}";

			Map<String, String> params = new HashMap<String, String>();
			params.put("prn", prnNum);

			URI uri = UriComponentsBuilder.fromUriString(url).buildAndExpand(params).toUri();
			regProcLogger.info("reponse body: "+ uri);
			
			ResponseWrapper<Object> response = restApiClient.getApi(uri, ResponseWrapper.class);
			
			@SuppressWarnings("unchecked")
			HashMap<String, String> jsonStatusResponsemap = (HashMap<String, String>) response.getResponse();
			
			regProcLogger.info("reponse body: "+ jsonStatusResponsemap);
			try {
				String prnReturnedStatusCode = jsonStatusResponsemap.get("prnStatusCode");
				String prnReturnedTaxHeadCode = jsonStatusResponsemap.get("prnTaxHead");
				String prnTaxPayerName = jsonStatusResponsemap.get("prnTaxPayerName");

				String objectApplicantIdDataString = utilities.retrieveIdrepoJson(uinString).toString();
				
				if(Objects.isNull(objectApplicantIdDataString)) {
					regProcLogger.error("In Registration Processor", "Payment Validator", "UIN: " + uinString
							+ " doesn't exist.");
					object.setIsValid(Boolean.FALSE);
				}
				else {
					JsonNode nodeApplicantIdData = objectMapper.readTree(objectApplicantIdDataString);
					
					regProcLogger.info("UIN object " + nodeApplicantIdData.toString());
					
					// change names array node to cater for surname, given names and other names
					ArrayNode nodeSurnameArrayNode = (ArrayNode) nodeApplicantIdData.path("surname");
					ArrayNode nodeGivenNamesArrayNode = (ArrayNode) nodeApplicantIdData.path("givenName");
					
					
					// can concat names after extraction from identity data json
					String fullNameString = nodeSurnameArrayNode.path(0).path("value").asText()+ " " + 
							nodeGivenNamesArrayNode.path(0).path("value").asText();

					if (prnTaxPayerName.equalsIgnoreCase(fullNameString)) {

						if (!prnReturnedTaxHeadCode.equals(TaxHeadCode.TAX_HEAD_CASE_LOST.getTaxHeadCode())
								&& !prnReturnedTaxHeadCode.equals(TaxHeadCode.TAX_HEAD_CASE_UPDATE_NEW_CARD.getTaxHeadCode())) {

							regProcLogger.info("In Registration Processor", "Payment Validator",
									"PRN: " + prnNum + " not valid for the usecase");
							object.setIsValid(Boolean.FALSE);
						} else {
							if (!prnReturnedStatusCode.equals(PrnStatusCode.PRN_STATUS_RECEIVED_CREDITED.getStatusCode())) {
								regProcLogger.info("In Registration Processor", "Payment Validator",
										"PRN: " + prnNum + " not paid. Reject application.");
								object.setIsValid(Boolean.FALSE);
							} else {
								if (checkIfPrnWasUsedBefore(prnNum)) {
									regProcLogger.info("In Registration Processor", "Payment Validator",
											"PRNs paid/used before. Reject a: " + prnNum + " wpplication.");
									object.setIsValid(Boolean.FALSE);
								} else {
									
									regProcLogger.info("PRN hasn't been used before so continue to saving" );
									// Confirm payment and add prn to consumption
									if (consumePrn(prnNum, regId)) {
										object.setIsValid(Boolean.TRUE);
										regProcLogger.info("In Registration Processor", "Payment Validator",
												"PRN: " + prnNum + " consumption success. Send to next stage.");
									} else {
										regProcLogger.error("In Registration Processor", "Payment Validator",
												"PRN: " + prnNum + " consumption failed. Send to reprocessing.");
										object.setIsValid(Boolean.FALSE);
									}
								}
							}

						}

					} else {
						regProcLogger.info("In Registration Processor", "Payment Validator", "PRN: " + prnNum
								+ ". Tax payer name is different from names on UIN. Sending to manual verification stage.");
						object.setIsValid(Boolean.FALSE);
					}
				}
				
				


			} catch (Exception e) {
				object.setIsValid(Boolean.FALSE);
				object.setInternalError(Boolean.TRUE);
				regProcLogger.error("In Registration Processor", "Payment Validator",
						"Failed to convert extract response from api: " + e.getMessage() + ExceptionUtils.getStackTrace(e));
			}

		} catch (Exception e) {
			object.setIsValid(Boolean.FALSE);
			object.setInternalError(Boolean.TRUE);
			regProcLogger.error("In Registration Processor", "Payment Validator",
					"Failed to access Payment service api: " + e.getMessage() + ExceptionUtils.getStackTrace(e));
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

	private boolean checkIfPrnWasUsedBefore(String prn) throws Exception {
		return prnConsumedService.checkIfPrnConsumed(prn);
	}

	private boolean consumePrn(String prn, String regId) throws Exception {

		ConsumePrnRequestDTO consumePrnRequestDTO = new ConsumePrnRequestDTO();
		consumePrnRequestDTO.setPrnNum(prn);
		consumePrnRequestDTO.setRegId(regId);

		return prnConsumedService.consumePrnAsUsed(consumePrnRequestDTO);
				
	}

}
