package com.promoteur.app.service;

import com.promoteur.app.AbstractIntegrationTest;
import com.promoteur.app.attachment.AttachmentOwnerType;
import com.promoteur.app.attachment.AttachmentResponse;
import com.promoteur.app.attachment.AttachmentService;
import com.promoteur.app.expense.ExpenseCategoryRepository;
import com.promoteur.app.expense.ExpenseRequest;
import com.promoteur.app.expense.ExpenseResponse;
import com.promoteur.app.expense.ExpenseService;
import com.promoteur.app.project.ProjectRequest;
import com.promoteur.app.project.ProjectResponse;
import com.promoteur.app.project.ProjectService;
import com.promoteur.app.project.ProjectStatus;
import com.promoteur.app.shared.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Covers FE-05: a proof file is stored on the filesystem behind StorageService, linked to an
 * existing document, served back byte for byte, and gone from both row and disk once deleted.
 */
class AttachmentTest extends AbstractIntegrationTest {

    private static final byte[] PDF_BYTES = "%PDF-1.4 bordereau de virement".getBytes();

    private final AtomicInteger sequence = new AtomicInteger();

    @Autowired
    private AttachmentService attachmentService;
    @Autowired
    private ExpenseService expenseService;
    @Autowired
    private ProjectService projectService;
    @Autowired
    private ExpenseCategoryRepository expenseCategoryRepository;

    private ProjectResponse project;

    @BeforeAll
    void seedProject() {
        ProjectRequest request = new ProjectRequest();
        request.setCode("ATT-PRJ");
        request.setName("Projet pièces jointes");
        request.setStatus(ProjectStatus.IN_PROGRESS);
        this.project = this.projectService.create(request);
    }

    @Test
    @DisplayName("a PDF attached to an expense is stored, listed, and served back byte for byte")
    void aPdfAttachedToAnExpenseIsStoredListedAndServedBack() throws IOException {
        ExpenseResponse expense = this.createExpense();

        AttachmentResponse stored = this.attachmentService.upload(AttachmentOwnerType.EXPENSE, expense.id(),
                new MockMultipartFile("file", "virement.pdf", "application/pdf", PDF_BYTES));

        assertThat(stored.originalName()).isEqualTo("virement.pdf");
        assertThat(stored.contentType()).isEqualTo("application/pdf");
        assertThat(stored.sizeBytes()).isEqualTo(PDF_BYTES.length);
        assertThat(stored.uploadedBy()).isEqualTo("system");
        assertThat(this.attachmentService.findByOwner(AttachmentOwnerType.EXPENSE, expense.id()))
                .extracting(AttachmentResponse::id).containsExactly(stored.id());
        assertThat(this.attachmentService.content(stored.id()).getContentAsByteArray()).isEqualTo(PDF_BYTES);
    }

    @Test
    @DisplayName("the browser's path is stripped from the stored name")
    void theBrowsersPathIsStrippedFromTheStoredName() {
        ExpenseResponse expense = this.createExpense();

        AttachmentResponse stored = this.attachmentService.upload(AttachmentOwnerType.EXPENSE, expense.id(),
                new MockMultipartFile("file", "C:\\Users\\amine\\scan.png", "image/png", PDF_BYTES));

        assertThat(stored.originalName()).isEqualTo("scan.png");
    }

    @Test
    @DisplayName("a Word document is refused: only PDF, JPEG and PNG are accepted")
    void aWordDocumentIsRefused() {
        ExpenseResponse expense = this.createExpense();

        assertThatThrownBy(() -> this.attachmentService.upload(AttachmentOwnerType.EXPENSE, expense.id(),
                new MockMultipartFile("file", "contrat.docx",
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document", PDF_BYTES)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Formats acceptés : PDF, JPEG, PNG");
    }

    @Test
    @DisplayName("a file over 10 MB is refused")
    void aFileOverTenMegabytesIsRefused() {
        ExpenseResponse expense = this.createExpense();
        byte[] tooBig = new byte[(int) (10L * 1024 * 1024) + 1];

        assertThatThrownBy(() -> this.attachmentService.upload(AttachmentOwnerType.EXPENSE, expense.id(),
                new MockMultipartFile("file", "gros.pdf", "application/pdf", tooBig)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("10 Mo");
    }

    @Test
    @DisplayName("an empty upload is refused")
    void anEmptyUploadIsRefused() {
        ExpenseResponse expense = this.createExpense();

        assertThatThrownBy(() -> this.attachmentService.upload(AttachmentOwnerType.EXPENSE, expense.id(),
                new MockMultipartFile("file", "vide.pdf", "application/pdf", new byte[0])))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Aucun fichier");
    }

    @Test
    @DisplayName("a file cannot be attached to a document that does not exist")
    void aFileCannotBeAttachedToAMissingDocument() {
        assertThatThrownBy(() -> this.attachmentService.upload(AttachmentOwnerType.SUPPLIER_INVOICE, 999_999L,
                new MockMultipartFile("file", "facture.pdf", "application/pdf", PDF_BYTES)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("SUPPLIER_INVOICE");
    }

    @Test
    @DisplayName("deleting an attachment removes the row and the file")
    void deletingAnAttachmentRemovesTheRowAndTheFile() {
        ExpenseResponse expense = this.createExpense();
        AttachmentResponse stored = this.attachmentService.upload(AttachmentOwnerType.EXPENSE, expense.id(),
                new MockMultipartFile("file", "cheque.jpg", "image/jpeg", PDF_BYTES));

        this.attachmentService.delete(stored.id());

        assertThat(this.attachmentService.findByOwner(AttachmentOwnerType.EXPENSE, expense.id())).isEmpty();
        assertThatThrownBy(() -> this.attachmentService.content(stored.id()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    private ExpenseResponse createExpense() {
        ExpenseRequest request = new ExpenseRequest();
        request.setExpenseDate(LocalDate.of(2026, 9, 1));
        request.setDescription("Dépense pièce jointe " + this.sequence.incrementAndGet());
        request.setAmountHt(new BigDecimal("100.000"));
        request.setVatRate(new BigDecimal("0.1900"));
        request.setCategoryId(this.expenseCategoryRepository.findAll().get(0).getId());
        request.setProjectId(this.project.id());
        return this.expenseService.create(request);
    }
}
