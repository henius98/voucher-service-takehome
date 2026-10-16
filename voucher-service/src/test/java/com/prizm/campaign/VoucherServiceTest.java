package com.prizm.campaign;

import com.prizm.campaign.dto.RedeemResponse;
import com.prizm.campaign.model.Campaign;
import com.prizm.campaign.model.Voucher;
import com.prizm.campaign.repository.CampaignRepository;
import com.prizm.campaign.repository.VoucherRepository;
import com.prizm.campaign.service.VoucherService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
class VoucherServiceTest {

    @Autowired
    private VoucherService voucherService;

    @Autowired
    private VoucherRepository voucherRepository;

    @Autowired
    private CampaignRepository campaignRepository;

    @Test
    void redeemActiveVoucherSucceeds() {
        RedeemResponse res = voucherService.redeem("RAYA-0001", "user-1");
        assertEquals("OK", res.getResult());
    }

    @Test
    void redeemAlreadyRedeemedVoucherFails() {
        RedeemResponse res = voucherService.redeem("RAYA-0004", "user-2");
        assertEquals("FAILED", res.getResult());
    }

    @Test
    void redeemUnknownCodeFails() {
        RedeemResponse res = voucherService.redeem("NOPE-9999", "user-3");
        assertEquals("FAILED", res.getResult());
    }

    @Test
    void redeemFailsWhenUserReachesPerUserLimit() {
        // user-99 already has 2 redemptions on campaign 1 in seed data (default limit is 2)
        RedeemResponse res = voucherService.redeem("RAYA-0002", "user-99");
        assertEquals("FAILED", res.getResult());
        assertEquals("User redemption limit reached", res.getMessage());
        assertEquals("ACTIVE", voucherRepository.findByCode("RAYA-0002").getStatus());
    }

    @Test
    void redeemAllowsUserInDifferentCampaign() {
        // user-99 is at limit for campaign 1, but has 0 redemptions in campaign 2
        RedeemResponse res = voucherService.redeem("MRDK-0001", "user-99");
        assertEquals("OK", res.getResult());
    }

    @Test
    void userCanRedeemUpToLimitThenFailsOnSubsequentAttempt() {
        RedeemResponse first = voucherService.redeem("RAYA-0003", "user-limit-test");
        assertEquals("OK", first.getResult());

        RedeemResponse second = voucherService.redeem("RAYA-0006", "user-limit-test");
        assertEquals("OK", second.getResult());

        Voucher extraVoucher = new Voucher();
        extraVoucher.setId(100L);
        extraVoucher.setCampaignId(1L);
        extraVoucher.setCode("RAYA-0007");
        extraVoucher.setStatus("ACTIVE");
        voucherRepository.save(extraVoucher);

        RedeemResponse third = voucherService.redeem("RAYA-0007", "user-limit-test");
        assertEquals("FAILED", third.getResult());
        assertEquals("User redemption limit reached", third.getMessage());
        assertEquals("ACTIVE", voucherRepository.findByCode("RAYA-0007").getStatus());
    }

    @Test
    void customPerUserLimitIsRespected() {
        Campaign campaign = new Campaign();
        campaign.setId(99L);
        campaign.setName("Custom Limit Campaign");
        campaign.setClientCode("TEST");
        campaign.setTotalStock(10);
        campaign.setRemainingStock(10);
        campaign.setActive(true);
        campaign.setPerUserLimit(1);
        campaignRepository.save(campaign);

        Voucher v1 = new Voucher();
        v1.setId(991L);
        v1.setCampaignId(99L);
        v1.setCode("CUST-0001");
        v1.setStatus("ACTIVE");
        voucherRepository.save(v1);

        Voucher v2 = new Voucher();
        v2.setId(992L);
        v2.setCampaignId(99L);
        v2.setCode("CUST-0002");
        v2.setStatus("ACTIVE");
        voucherRepository.save(v2);

        RedeemResponse first = voucherService.redeem("CUST-0001", "user-custom");
        assertEquals("OK", first.getResult());

        RedeemResponse second = voucherService.redeem("CUST-0002", "user-custom");
        assertEquals("FAILED", second.getResult());
        assertEquals("User redemption limit reached", second.getMessage());
    }

    @Test
    void nullPerUserLimitFallsBackToDefaultConfigLimit() {
        Campaign campaign = new Campaign();
        campaign.setId(98L);
        campaign.setName("Null Limit Campaign");
        campaign.setClientCode("TEST");
        campaign.setTotalStock(10);
        campaign.setRemainingStock(10);
        campaign.setActive(true);
        campaign.setPerUserLimit(null);
        campaignRepository.save(campaign);

        Voucher v1 = new Voucher();
        v1.setId(981L);
        v1.setCampaignId(98L);
        v1.setCode("NULL-0001");
        v1.setStatus("ACTIVE");
        voucherRepository.save(v1);

        Voucher v2 = new Voucher();
        v2.setId(982L);
        v2.setCampaignId(98L);
        v2.setCode("NULL-0002");
        v2.setStatus("ACTIVE");
        voucherRepository.save(v2);

        Voucher v3 = new Voucher();
        v3.setId(983L);
        v3.setCampaignId(98L);
        v3.setCode("NULL-0003");
        v3.setStatus("ACTIVE");
        voucherRepository.save(v3);

        assertEquals("OK", voucherService.redeem("NULL-0001", "user-null-limit").getResult());
        assertEquals("OK", voucherService.redeem("NULL-0002", "user-null-limit").getResult());
        RedeemResponse third = voucherService.redeem("NULL-0003", "user-null-limit");
        assertEquals("FAILED", third.getResult());
        assertEquals("User redemption limit reached", third.getMessage());
    }

    @Test
    void concurrentRedeemSameVoucherAllowsOnlyOneSuccess() throws Exception {
        Voucher concurrentVoucher = new Voucher();
        concurrentVoucher.setId(200L);
        concurrentVoucher.setCampaignId(1L);
        concurrentVoucher.setCode("CONC-0001");
        concurrentVoucher.setStatus("ACTIVE");
        voucherRepository.save(concurrentVoucher);

        int threads = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch readyLatch = new CountDownLatch(threads);
        CountDownLatch startLatch = new CountDownLatch(1);
        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger failCount = new AtomicInteger();

        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            final String uid = "user-conc-" + i;
            futures.add(executor.submit(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await();
                    RedeemResponse res = voucherService.redeem("CONC-0001", uid);
                    if ("OK".equals(res.getResult())) {
                        successCount.incrementAndGet();
                    } else {
                        failCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    failCount.incrementAndGet();
                }
            }));
        }

        readyLatch.await();
        startLatch.countDown();
        for (Future<?> f : futures) {
            f.get();
        }
        executor.shutdown();

        assertEquals(1, successCount.get());
        assertEquals(threads - 1, failCount.get());
        assertEquals("REDEEMED", voucherRepository.findByCode("CONC-0001").getStatus());
    }
}
