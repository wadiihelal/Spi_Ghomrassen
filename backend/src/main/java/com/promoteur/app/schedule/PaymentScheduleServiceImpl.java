package com.promoteur.app.schedule;

import com.promoteur.app.advance.ClientAdvanceRepository;
import com.promoteur.app.audit.AuditLogService;
import com.promoteur.app.purchase.ClientPurchase;
import com.promoteur.app.purchase.ClientPurchaseRepository;
import com.promoteur.app.shared.ListFilter;
import com.promoteur.app.shared.MessageService;
import com.promoteur.app.shared.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

@Service
@Transactional
@RequiredArgsConstructor
public class PaymentScheduleServiceImpl implements PaymentScheduleService {

    private static final int MONEY_SCALE = 3;
    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);

    private final PaymentInstallmentRepository installmentRepository;
    private final ClientPurchaseRepository clientPurchaseRepository;
    private final ClientAdvanceRepository clientAdvanceRepository;
    private final AuditLogService auditLogService;
    private final MessageService messageService;

    @Override
    @Transactional(readOnly = true)
    public PaymentScheduleResponse findByPurchase(final Long purchaseId) {
        final ClientPurchase purchase = this.purchase(purchaseId);
        return this.describe(purchase, this.installmentRepository.findByPurchaseIdOrderBySequenceNoAsc(purchaseId));
    }

    @Override
    public PaymentScheduleResponse replace(final Long purchaseId, final PaymentScheduleRequest request) {
        final ClientPurchase purchase = this.purchase(purchaseId);
        this.requireScheduleMatchesContract(purchase, request.getLines().stream()
                .map(InstallmentLineRequest::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add));

        this.installmentRepository.deleteByPurchaseId(purchaseId);
        this.installmentRepository.flush();

        final List<PaymentInstallment> saved = new ArrayList<>();
        int sequence = 1;
        for (final InstallmentLineRequest line : request.getLines()) {
            saved.add(this.installmentRepository.save(this.build(purchase, sequence++, line.getLabel(),
                    line.getDueDate(), line.getAmount(), line.getNotes())));
        }

        this.auditLogService.create("PURCHASE", purchaseId, "SCHEDULE",
                this.messageService.get("audit.schedule.replaced", purchase.getReference(), saved.size()));
        return this.describe(purchase, saved);
    }

    @Override
    public PaymentScheduleResponse generate(final Long purchaseId, final ScheduleTemplateRequest request) {
        final ClientPurchase purchase = this.purchase(purchaseId);
        final BigDecimal total = this.normalize(purchase.getTotalAmount());

        final BigDecimal percentageTotal = request.getLines().stream()
                .map(ScheduleTemplateRequest.TemplateLine::getPercentage)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (percentageTotal.compareTo(ONE_HUNDRED) != 0) {
            throw new IllegalArgumentException(
                    this.messageService.get("validation.schedule.percentagesMustSumTo100", percentageTotal));
        }

        this.installmentRepository.deleteByPurchaseId(purchaseId);
        this.installmentRepository.flush();

        final List<PaymentInstallment> saved = new ArrayList<>();
        BigDecimal allocated = BigDecimal.ZERO;
        final int lineCount = request.getLines().size();

        for (int index = 0; index < lineCount; index++) {
            final ScheduleTemplateRequest.TemplateLine line = request.getLines().get(index);
            final boolean isLast = index == lineCount - 1;
            // The last line takes whatever rounding left behind, so the plan matches the contract.
            final BigDecimal amount = isLast
                    ? total.subtract(allocated)
                    : total.multiply(line.getPercentage())
                    .divide(ONE_HUNDRED, MONEY_SCALE, RoundingMode.HALF_UP);
            allocated = allocated.add(amount);

            final String label = line.getLabel() == null || line.getLabel().isBlank()
                    ? this.defaultLabel(index, lineCount)
                    : line.getLabel();
            final LocalDate dueDate = request.getFirstDueDate()
                    .plusMonths((long) index * request.getIntervalMonths());

            saved.add(this.installmentRepository.save(
                    this.build(purchase, index + 1, label, dueDate, amount, null)));
        }

        this.auditLogService.create("PURCHASE", purchaseId, "SCHEDULE",
                this.messageService.get("audit.schedule.generated", purchase.getReference(), saved.size()));
        return this.describe(purchase, saved);
    }

    @Override
    public void clear(final Long purchaseId) {
        final ClientPurchase purchase = this.purchase(purchaseId);
        this.installmentRepository.deleteByPurchaseId(purchaseId);
        this.auditLogService.create("PURCHASE", purchaseId, "SCHEDULE",
                this.messageService.get("audit.schedule.cleared", purchase.getReference()));
    }

    @Override
    @Transactional(readOnly = true)
    public List<PaymentInstallmentResponse> search(final ListFilter filter, final InstallmentStatus status) {
        final List<PaymentInstallment> installments = this.installmentRepository.findForSchedule(
                filter.projectId(), filter.clientId(), filter.dateFrom(), filter.dateTo());

        // Settlement is per contract, so the lines are grouped before being described.
        final List<PaymentInstallmentResponse> described = new ArrayList<>();
        for (final Long purchaseId : installments.stream()
                .map(installment -> installment.getPurchase().getId())
                .distinct()
                .toList()) {
            final List<PaymentInstallment> ofPurchase = installments.stream()
                    .filter(installment -> installment.getPurchase().getId().equals(purchaseId))
                    .sorted((a, b) -> Integer.compare(a.getSequenceNo(), b.getSequenceNo()))
                    .toList();
            // A filtered view can hide earlier lines, so settlement is computed from the whole plan.
            described.addAll(this.describe(ofPurchase.get(0).getPurchase(),
                            this.installmentRepository.findByPurchaseIdOrderBySequenceNoAsc(purchaseId))
                    .installments().stream()
                    .filter(line -> ofPurchase.stream().anyMatch(kept -> kept.getId().equals(line.id())))
                    .toList());
        }

        return described.stream()
                .filter(line -> status == null || line.status() == status)
                .sorted((a, b) -> a.dueDate().compareTo(b.dueDate()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public InstallmentSummaryResponse summary(final Long projectId, final LocalDate reference) {
        final LocalDate day = reference == null ? LocalDate.now() : reference;
        final LocalDate monthStart = day.withDayOfMonth(1);
        final LocalDate monthEnd = day.withDayOfMonth(day.lengthOfMonth());

        final List<PaymentInstallmentResponse> all = this.search(
                new ListFilter(projectId, null, null, null, null, null, null, null, null, null), null);

        long overdueCount = 0;
        BigDecimal overdueAmount = BigDecimal.ZERO;
        long dueThisMonthCount = 0;
        BigDecimal dueThisMonthAmount = BigDecimal.ZERO;
        BigDecimal scheduled = BigDecimal.ZERO;
        BigDecimal collected = BigDecimal.ZERO;

        for (final PaymentInstallmentResponse line : all) {
            scheduled = scheduled.add(line.amount());
            collected = collected.add(line.settledAmount());

            if (line.status() == InstallmentStatus.OVERDUE) {
                overdueCount++;
                overdueAmount = overdueAmount.add(line.remainingAmount());
            }
            final boolean inMonth = !line.dueDate().isBefore(monthStart) && !line.dueDate().isAfter(monthEnd);
            if (inMonth && line.remainingAmount().signum() > 0) {
                dueThisMonthCount++;
                dueThisMonthAmount = dueThisMonthAmount.add(line.remainingAmount());
            }
        }

        return new InstallmentSummaryResponse(overdueCount, overdueAmount, dueThisMonthCount,
                dueThisMonthAmount, scheduled, collected);
    }

    /**
     * Spreads what the client has paid across the plan in sequence order, then reads each line's
     * state from its due date and its share.
     *
     * <p>This waterfall is the whole design: nothing links a payment to an instalment, so the
     * plan and the cash cannot drift apart. Paying the contract down always settles the oldest
     * outstanding line first, which is how a promoter reads a ledger.</p>
     */
    private PaymentScheduleResponse describe(final ClientPurchase purchase,
                                             final List<PaymentInstallment> installments) {
        final BigDecimal contractAmount = this.normalize(purchase.getTotalAmount());
        final BigDecimal collected = this.collectedOn(purchase);
        final LocalDate today = LocalDate.now();

        BigDecimal unallocated = collected;
        BigDecimal scheduled = BigDecimal.ZERO;
        BigDecimal overdue = BigDecimal.ZERO;
        final List<PaymentInstallmentResponse> lines = new ArrayList<>(installments.size());

        for (final PaymentInstallment installment : installments) {
            final BigDecimal amount = this.normalize(installment.getAmount());
            final BigDecimal settled = unallocated.min(amount).max(BigDecimal.ZERO);
            unallocated = unallocated.subtract(settled);
            final BigDecimal remaining = amount.subtract(settled);

            final boolean pastDue = installment.getDueDate().isBefore(today);
            final InstallmentStatus status;
            if (remaining.signum() <= 0) {
                status = InstallmentStatus.PAID;
            } else if (pastDue) {
                status = InstallmentStatus.OVERDUE;
            } else if (settled.signum() > 0) {
                status = InstallmentStatus.PARTIALLY_PAID;
            } else {
                status = InstallmentStatus.UPCOMING;
            }

            if (status == InstallmentStatus.OVERDUE) {
                overdue = overdue.add(remaining);
            }
            scheduled = scheduled.add(amount);

            lines.add(new PaymentInstallmentResponse(
                    installment.getId(),
                    purchase.getId(),
                    purchase.getReference(),
                    purchase.getClient() == null ? null : purchase.getClient().getId(),
                    purchase.getClient() == null ? null : purchase.getClient().getFullName(),
                    purchase.getProject() == null ? null : purchase.getProject().getId(),
                    purchase.getProject() == null ? null : purchase.getProject().getName(),
                    purchase.getApartment() == null ? null : purchase.getApartment().getId(),
                    purchase.getApartment() == null ? null : purchase.getApartment().getApartmentNumber(),
                    installment.getSequenceNo(),
                    installment.getLabel(),
                    installment.getDueDate(),
                    amount,
                    settled,
                    remaining,
                    status,
                    status == InstallmentStatus.OVERDUE
                            ? ChronoUnit.DAYS.between(installment.getDueDate(), today)
                            : 0L,
                    installment.getNotes()));
        }

        return new PaymentScheduleResponse(purchase.getId(), purchase.getReference(), contractAmount,
                scheduled, contractAmount.subtract(scheduled), collected, overdue, lines);
    }

    /**
     * Everything received on the contract: the direct payment plus every advance on its apartment.
     */
    private BigDecimal collectedOn(final ClientPurchase purchase) {
        final BigDecimal direct = this.normalize(purchase.getPaidAmount());
        if (purchase.getApartment() == null) {
            return direct;
        }
        return direct.add(this.clientAdvanceRepository
                .sumAmountByApartmentIds(List.of(purchase.getApartment().getId())).stream()
                .map(total -> this.normalize(total.totalAmount()))
                .findFirst()
                .orElse(BigDecimal.ZERO));
    }

    /**
     * A plan that does not add up to the contract is not a plan.
     */
    private void requireScheduleMatchesContract(final ClientPurchase purchase, final BigDecimal scheduled) {
        final BigDecimal total = this.normalize(purchase.getTotalAmount());
        if (scheduled.compareTo(total) != 0) {
            throw new IllegalArgumentException(this.messageService.get(
                    "validation.schedule.mustMatchContract", scheduled, total));
        }
    }

    private PaymentInstallment build(final ClientPurchase purchase, final int sequenceNo, final String label,
                                     final LocalDate dueDate, final BigDecimal amount, final String notes) {
        final PaymentInstallment installment = new PaymentInstallment();
        installment.setPurchase(purchase);
        installment.setSequenceNo(sequenceNo);
        installment.setLabel(label);
        installment.setDueDate(dueDate);
        installment.setAmount(amount.setScale(MONEY_SCALE, RoundingMode.HALF_UP));
        installment.setNotes(notes);
        return installment;
    }

    /**
     * Names the usual off-plan steps when the caller sends none.
     */
    private String defaultLabel(final int index, final int lineCount) {
        if (index == 0) {
            return this.messageService.get("schedule.label.reservation");
        }
        if (index == lineCount - 1) {
            return this.messageService.get("schedule.label.handover");
        }
        return this.messageService.get("schedule.label.tranche", index);
    }

    private ClientPurchase purchase(final Long purchaseId) {
        return this.clientPurchaseRepository.findById(purchaseId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        this.messageService.get("error.notFound.purchase", String.valueOf(purchaseId))));
    }

    private BigDecimal normalize(final BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }
}
