package io.mosip.registration.processor.citizenship.verification.constants;

public enum Relationship {
	FATHER("Father"),
	MOTHER("Mother"),
    GRAND_MOTHER_ON_FATHERS_SIDE("Grandmother on Father's side"),
    GRAND_FATHER_ON_FATHERS_SIDE("Grandfather on Father's side"),
    MATERNAL_UCLE_OR_AUNT("Maternal Uncle or Aunt"),
    PATERNAL_UCLE_OR_AUNT("Paternal Uncle or Aunt"),
	BROTHER_OR_SISTER("Brother or Sister");

    private final String relation;

    private Relationship(String relation) {
        this.relation = relation;
    }
    public String getRelationship() {
    	return relation;
    }
    
    public static Relationship fromString(String relation) {
        for (Relationship r : Relationship.values()) {
            if (r.getRelationship().equalsIgnoreCase(relation)) {
                return r;
            }
        }
        throw new IllegalArgumentException("No enum constant for relation: " + relation);
    }
}
