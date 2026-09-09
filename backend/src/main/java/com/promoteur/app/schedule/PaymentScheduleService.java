package com.promoteur.app.schedule;

import com.promoteur.app.shared.ListFilter;

import java.time.LocalDate;
import java.util.List;

/**
 * Payment schedules for sale contracts (UX-03).
 *
 * <p>Selling off plan means staged payments. The schedule is the promoter's claim on the buyer;
 * the advances are what actually arrived. This service holds the plan and works out, at read
 * time, how far the money received covers it.</p>
 */
public interface PaymentScheduleService {

    /**
     * The schedule of one contract, with each line's settled and remaining amounts.
     */
    PaymentScheduleResponse findByPurchase(Long purchaseId);

    /**
     * Replaces a contract's schedule with the given lines.
     *
     * @throws IllegalArgumentException when the lines do not add up to the contract total
     */
    PaymentScheduleResponse replace(Long purchaseId, PaymentScheduleRequest request);

    /**
     * Builds a schedule from percentages of the contract total, spaced by a fixed interval. The
     * last line takes the rounding remainder so the plan sums exactly.
     *
     * @throws IllegalArgumentException when the percentages do not add up to 100
     */
    PaymentScheduleResponse generate(Long purchaseId, ScheduleTemplateRequest request);

    /**
     * Removes a contract's schedule. The payments already received are untouched.
     */
    void clear(Long purchaseId);

    /**
     * Instalments across contracts, for the schedule screen.
     *
     * @param filter project and client narrowing; dates apply to the due date
     * @param status keep only this state, or {@code null} for all
     */
    List<PaymentInstallmentResponse> search(ListFilter filter, InstallmentStatus status);

    /**
     * What is late and what falls due in the month containing {@code reference}.
     */
    InstallmentSummaryResponse summary(Long projectId, LocalDate reference);
}
