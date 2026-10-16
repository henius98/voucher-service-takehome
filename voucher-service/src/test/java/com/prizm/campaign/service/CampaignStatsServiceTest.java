package com.prizm.campaign.service;

import com.prizm.campaign.dto.CampaignStats;
import com.prizm.campaign.model.Campaign;
import com.prizm.campaign.model.Voucher;
import com.prizm.campaign.repository.CampaignRepository;
import com.prizm.campaign.repository.RedemptionRepository;
import com.prizm.campaign.repository.VoucherRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CampaignStatsServiceTest {

    @Mock
    private CampaignRepository campaignRepository;

    @Mock
    private VoucherRepository voucherRepository;

    @Mock
    private RedemptionRepository redemptionRepository;

    @InjectMocks
    private CampaignStatsService campaignStatsService;

    @Test
    void getStatsUsesCountByCampaignIdAndAvoidsPerVoucherRedemptionQueries() {
        Long campaignId = 1L;
        Campaign campaign = new Campaign();
        campaign.setId(campaignId);
        campaign.setName("Test Campaign");
        campaign.setTotalStock(100);
        campaign.setRemainingStock(80);

        Voucher v1 = new Voucher();
        v1.setId(10L);
        v1.setCampaignId(campaignId);
        v1.setStatus("ACTIVE");

        Voucher v2 = new Voucher();
        v2.setId(11L);
        v2.setCampaignId(campaignId);
        v2.setStatus("REDEEMED");

        when(campaignRepository.findById(campaignId)).thenReturn(Optional.of(campaign));
        when(voucherRepository.findByCampaignId(campaignId)).thenReturn(List.of(v1, v2));
        when(redemptionRepository.countByCampaignId(campaignId)).thenReturn(15L);

        CampaignStats stats = campaignStatsService.getStats(campaignId);

        assertEquals(campaignId, stats.getCampaignId());
        assertEquals("Test Campaign", stats.getName());
        assertEquals(100, stats.getTotalStock());
        assertEquals(80, stats.getRemainingStock());
        assertEquals(15, stats.getRedeemedCount());
        assertEquals(1, stats.getActiveVoucherCount());

        verify(redemptionRepository, times(1)).countByCampaignId(campaignId);
        verify(redemptionRepository, never()).findByVoucherId(any());
    }
}
