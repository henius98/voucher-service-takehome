package com.prizm.campaign.service;

import com.prizm.campaign.dto.CampaignStats;
import com.prizm.campaign.model.Campaign;
import com.prizm.campaign.model.Redemption;
import com.prizm.campaign.model.Voucher;
import com.prizm.campaign.repository.CampaignRepository;
import com.prizm.campaign.repository.RedemptionRepository;
import com.prizm.campaign.repository.VoucherRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
class CampaignStatsIntegrationTest {

    @Autowired
    private CampaignStatsService campaignStatsService;

    @Autowired
    private CampaignRepository campaignRepository;

    @Autowired
    private VoucherRepository voucherRepository;

    @Autowired
    private RedemptionRepository redemptionRepository;

    @Test
    void getStatsComputesAggregateCountDirectlyFromDatabase() {
        Long campaignId = 888L;
        Campaign campaign = new Campaign();
        campaign.setId(campaignId);
        campaign.setName("Integration Campaign");
        campaign.setClientCode("INTEG");
        campaign.setTotalStock(10);
        campaign.setRemainingStock(8);
        campaign.setActive(true);
        campaignRepository.save(campaign);

        Voucher v1 = new Voucher();
        v1.setId(8881L);
        v1.setCampaignId(campaignId);
        v1.setCode("INT-0001");
        v1.setStatus("REDEEMED");
        voucherRepository.save(v1);

        Voucher v2 = new Voucher();
        v2.setId(8882L);
        v2.setCampaignId(campaignId);
        v2.setCode("INT-0002");
        v2.setStatus("ACTIVE");
        voucherRepository.save(v2);

        redemptionRepository.save(new Redemption(v1.getId(), campaignId, "user-int", new Date()));

        assertEquals(1L, redemptionRepository.countByCampaignId(campaignId));

        CampaignStats stats = campaignStatsService.getStats(campaignId);
        assertEquals(1, stats.getRedeemedCount());
        assertEquals(1, stats.getActiveVoucherCount());
        assertEquals(10, stats.getTotalStock());
        assertEquals(8, stats.getRemainingStock());
    }
}
