package io.mosip.registration.processor.paymentvalidator.constants;

public enum TaxHeadCode {
	
	TAX_HEAD_CASE_LOST("LOST", "Replacement of a Lost ID"),
	TAX_HEAD_CASE_UPDATE_NEW_CARD("UPDATE_NEW_CARD", "Change Of Information-New ID Required");
	
	final String regType;
	private final String taxHeadCode;


	private TaxHeadCode(String regType, String taxHeadCode) {
		this.regType = regType;
		this.taxHeadCode = taxHeadCode;
	}


	public String getRegType() {
		return regType;
	}

	public String getTaxHeadCode() {
		return taxHeadCode;
	}


}
