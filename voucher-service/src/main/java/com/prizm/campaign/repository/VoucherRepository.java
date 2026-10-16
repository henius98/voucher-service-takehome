package com.prizm.campaign.repository;

import com.prizm.campaign.model.Voucher;
import org.springframework.data.jpa.repository.JpaRepository;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Date;
import java.util.List;

public interface VoucherRepository extends JpaRepository<Voucher, Long> {

    Voucher findByCode(String code);

    List<Voucher> findByCampaignId(Long campaignId);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE Voucher v SET v.status = 'REDEEMED', v.redeemedBy = :userId, v.redeemedAt = :redeemedAt WHERE v.code = :code AND v.status = 'ACTIVE'")
    int claimVoucher(@Param("code") String code, @Param("userId") String userId, @Param("redeemedAt") Date redeemedAt);
}
