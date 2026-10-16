package com.prizm.campaign.service;

import com.prizm.campaign.dto.RedeemResponse;
import com.prizm.campaign.model.Campaign;
import com.prizm.campaign.model.Redemption;
import com.prizm.campaign.model.Voucher;
import com.prizm.campaign.repository.CampaignRepository;
import com.prizm.campaign.repository.RedemptionRepository;
import com.prizm.campaign.repository.VoucherRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;

import java.util.Date;

@Service
public class VoucherService {

    private static final Logger log = LoggerFactory.getLogger(VoucherService.class);

    @Autowired
    private VoucherRepository voucherRepository;

    @Autowired
    private CampaignRepository campaignRepository;

    @Autowired
    private RedemptionRepository redemptionRepository;

    @Autowired
    private AuditClient auditClient;

    @Value("${campaign.default-per-user-limit:2}")
    private int defaultPerUserLimit;

    @Transactional
    public RedeemResponse redeem(String code, String userId) {

        Voucher voucher = voucherRepository.findByCode(code);
        if (voucher == null) {
            return RedeemResponse.fail("Voucher not found");
        }

        if ("REDEEMED".equals(voucher.getStatus())) {
            return RedeemResponse.fail("Voucher already redeemed");
        }

        if ("VOID".equals(voucher.getStatus())) {
            return RedeemResponse.fail("Voucher is void");
        }

        Campaign campaign = campaignRepository.findByIdForUpdate(voucher.getCampaignId()).orElse(null);
        if (campaign == null) {
            return RedeemResponse.fail("Campaign not found");
        }

        if (!campaign.isActive()) {
            return RedeemResponse.fail("Campaign is not active");
        }

        if (campaign.getRemainingStock() <= 0) {
            return RedeemResponse.fail("Campaign out of stock");
        }

        int perUserLimit = campaign.getPerUserLimit() != null ? campaign.getPerUserLimit() : defaultPerUserLimit;
        if (redemptionRepository.countByCampaignIdAndUserId(campaign.getId(), userId) >= perUserLimit) {
            return RedeemResponse.fail("User redemption limit reached");
        }

        int claimed = voucherRepository.claimVoucher(code, userId, new Date());
        if (claimed == 0) {
            return RedeemResponse.fail("Voucher already redeemed");
        }

        int updated = campaignRepository.decrementStock(campaign.getId());
        if (updated == 0) {
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            return RedeemResponse.fail("Campaign out of stock");
        }

        Redemption redemption = new Redemption(
                voucher.getId(), campaign.getId(), userId, new Date());
        redemptionRepository.save(redemption);

        try {
            auditClient.recordRedemption(campaign.getClientCode(), voucher.getCode(), userId);
        } catch (Exception e) {
            log.error("audit call failed for voucher {}", voucher.getCode(), e);
        }

        int remainingStock = campaignRepository.findById(campaign.getId()).map(Campaign::getRemainingStock).orElse(0);
        return RedeemResponse.ok(voucher.getCode(), remainingStock);
    }

    public RedeemResponse voidVoucher(String code) {
        Voucher voucher = voucherRepository.findByCode(code);
        if (voucher == null) {
            return RedeemResponse.fail("Voucher not found");
        }
        voucher.setStatus("VOID");
        voucherRepository.save(voucher);
        return RedeemResponse.ok(voucher.getCode(), null);
    }
}
