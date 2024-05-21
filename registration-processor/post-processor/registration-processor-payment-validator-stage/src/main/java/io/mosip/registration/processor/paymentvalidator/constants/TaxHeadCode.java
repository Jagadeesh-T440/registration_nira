package io.mosip.registration.processor.paymentvalidator.constants;

/**
 * 
 * 
 * @author Ibrahim Nkambo
 */

public enum TaxHeadCode {
	
	TAX_HEAD_REPLACE("RLID01", "Replacement of a Lost ID", "50000"),
	TAX_HEAD_CHANGE("CI001", "Change Of Information-New ID Required", "50000"),
	TAX_HEAD_CORRECTION_ERRORS("CEID01", "Correction of Errors-New ID Required", "50000"),
	TAX_HEAD_REPLACE_DEFACED("RDID01", "Replacement of a Defaced or Damaged ID", "50000");
	
	final String taxHeadCode;
	private final String taxHeadDesc;
	private final String amountPaid;


	private TaxHeadCode(String taxHeadCode, String taxHeadDesc, String amountPaid) {
		this.taxHeadCode = taxHeadCode;
		this.taxHeadDesc = taxHeadDesc;
		this.amountPaid = amountPaid;
	}


	public String getTaxHeadDesc() {
		return taxHeadDesc;
	}

	public String getTaxHeadCode() {
		return taxHeadCode;
	}
	
	public String getAmountPaid() {
		return amountPaid;
	}


}
