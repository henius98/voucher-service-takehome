package com.prizm.campaign.repository;

import com.prizm.campaign.model.Campaign;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CampaignRepository extends JpaRepository<Campaign, Long> {

    List<Campaign> findByClientCode(String clientCode);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE Campaign c SET c.remainingStock = c.remainingStock - 1 WHERE c.id = :id AND c.remainingStock > 0")
    int decrementStock(@Param("id") Long id);
}
