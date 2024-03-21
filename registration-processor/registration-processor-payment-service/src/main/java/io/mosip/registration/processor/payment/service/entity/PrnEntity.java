package io.mosip.registration.processor.payment.service.entity;

import java.io.Serializable;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "prn", schema="regprc")
public class PrnEntity implements Serializable{
	
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	@Id
    @Column(name = "prn_id")
	@GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long prnId;


    @Column(name = "prn_number")
    private String prnNumber;

    @Column(name = "prn_status_code")
    private String prnStatusCode;
    
    @Column(name = "prn_status_desc")
    private String prnStatusDesc;
    
    @Column(name = "tax_head")
    private String prnTaxHead;
    
    @Column(name = "tax_payer_name")
    private String taxPayerName; 
    
    

}

