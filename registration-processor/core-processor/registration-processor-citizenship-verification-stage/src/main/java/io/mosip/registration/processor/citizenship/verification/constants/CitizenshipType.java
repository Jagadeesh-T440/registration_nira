package io.mosip.registration.processor.citizenship.verification.constants;

public enum CitizenshipType {
	BIRTH_INDEGINOUS("By Birth -Indigenous Community", "C"),
	BIRTH_NON_INDEGINOUS("By Birth -Non Indigenous Community", "C"),
	NATURALISATION("By Naturalisation", "N"),
	REGISTRATION("By Registration", "R");
	
	final String citizenshipType;
	final String ninCode;

	private CitizenshipType(String citizenshipType, String ninCode) {
		this.citizenshipType = citizenshipType;
		this.ninCode = ninCode;
	}
	
	public String getCitizenshipType() {
		return citizenshipType;
	}
	
	public String getNinCode() {
		return ninCode;
	}
}
