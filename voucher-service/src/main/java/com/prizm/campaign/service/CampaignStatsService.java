package com.prizm.campaign.service;

import com.prizm.campaign.dto.CampaignStats;
import com.prizm.campaign.model.Campaign;
import com.prizm.campaign.model.Voucher;
import com.prizm.campaign.repository.CampaignRepository;
import com.prizm.campaign.repository.RedemptionRepository;
import com.prizm.campaign.repository.VoucherRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;

@Service
public class CampaignStatsService {

    private final CampaignRepository campaignRepository;
    private final VoucherRepository voucherRepository;
    private final RedemptionRepository redemptionRepository;

    public CampaignStatsService(CampaignRepository campaignRepository,
                                VoucherRepository voucherRepository,
                                RedemptionRepository redemptionRepository) {
        this.campaignRepository = campaignRepository;
        this.voucherRepository = voucherRepository;
        this.redemptionRepository = redemptionRepository;
    }

    public CampaignStats getStats(Long campaignId) {
        Campaign campaign = campaignRepository.findById(campaignId).orElse(null);
        if (campaign == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Campaign not found");
        }

        List<Voucher> vouchers = voucherRepository.findByCampaignId(campaignId);

        int redeemed = 0;
        int active = 0;

        for (Voucher v : vouchers) {
            if (!redemptionRepository.findByVoucherId(v.getId()).isEmpty()) {
                redeemed++;
            }
            if ("ACTIVE".equals(v.getStatus())) {
                active++;
            }
        }

        CampaignStats stats = new CampaignStats();
        stats.setCampaignId(campaign.getId());
        stats.setName(campaign.getName());
        stats.setTotalStock(campaign.getTotalStock());
        stats.setRemainingStock(campaign.getRemainingStock());
        stats.setRedeemedCount(redeemed);
        stats.setActiveVoucherCount(active);
        return stats;
    }

    public List<CampaignStats> getStatsForClient(String clientCode) {
        List<CampaignStats> out = new ArrayList<>();
        for (Campaign c : campaignRepository.findByClientCode(clientCode)) {
            out.add(getStats(c.getId()));
        }
        return out;
    }
}
