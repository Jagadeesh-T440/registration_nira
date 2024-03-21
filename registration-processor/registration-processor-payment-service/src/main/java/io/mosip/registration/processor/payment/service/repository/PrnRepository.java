package io.mosip.registration.processor.payment.service.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import io.mosip.registration.processor.payment.service.entity.PrnEntity;

public interface PrnRepository extends JpaRepository<PrnEntity, Long>{
	
	/*@Query(name = "select * from PrnEntity p", nativeQuery = true)
    List <PrnEntity> getAllPrns();*/
	
	List <PrnEntity> findAll();
    
    /*@Query(name = "select prn_status from PrnEntity p where p.prn_number =?1", nativeQuery = true)
    String getStatusByPrnNumber(String prnNumber);*/
    
    String findPrnStatusByPrnNumber(String prnNumber);
    
    PrnEntity findByPrnNumber(String prnNumber);
    
    /*@Query(name = "select * from PrnEntity p where p.prn_status =?1", nativeQuery = true)
    List <PrnEntity> getAllByPrnStatus(String prnStatus);*/
    
    List<PrnEntity> findPrnEntitiesByPrnStatusCode(String prnStatusCode);
    
    /*@Query(value = "select * from PrnEntity p where p.prn_number = ?1", nativeQuery = true)
    PrnEntity getPrnById(String prnNumber);*/
    

}
