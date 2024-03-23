package io.mosip.registration.processor.paymentvalidator.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import io.mosip.registration.processor.paymentvalidator.entity.PrnConsumedEntity;


public interface PrnConsumedRepository extends JpaRepository<PrnConsumedEntity, Long>{
	
	List <PrnConsumedEntity> findAll();
    
    /*@Query(name = "select prn_number from PrnConsumedEntity p where p.regId =?1", nativeQuery = true)
    String getPrnByRegId(String regId);*/
	
	PrnConsumedEntity findPrnByRegId(String regId);

	PrnConsumedEntity findByPrnNumber(String prnNumber);
    
    /*@Query(name = "select count(*) from PrnConsumedEntity p where p.prn_number =?1", nativeQuery = true)
    Integer checkIfPrnConsumed(String prnNumber);
    */

}