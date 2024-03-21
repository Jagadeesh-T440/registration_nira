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
@Table(name = "prn_consumed", schema="regprc")
public class PrnConsumedEntity implements Serializable{
	
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	@Id
    @Column(name = "prn_consumed_id")
	@GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long prnConsumedId;

    @Column(name = "prn_number")
    private String prnNumber;

    @Column(name = "reg_id")
    private String regId;
}