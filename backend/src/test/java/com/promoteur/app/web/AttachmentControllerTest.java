package com.promoteur.app.web;

import com.promoteur.app.attachment.AttachmentController;
import com.promoteur.app.attachment.AttachmentOwnerType;
import com.promoteur.app.attachment.AttachmentResponse;
import com.promoteur.app.attachment.AttachmentService;
import com.promoteur.app.config.MessageSourceConfig;
import com.promoteur.app.exception.GlobalExceptionHandler;
import com.promoteur.app.shared.MessageServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Covers the two things only this controller does (FE-05): accept a multipart upload, and stream
 * bytes back with headers a browser can act on.
 *
 * <p>The download path is the one place in the API where a French filename crosses an HTTP
 * header. {@code Reçu février 2026.pdf} cannot go into {@code Content-Disposition} as raw bytes:
 * it has to be RFC 5987 encoded, or the accented characters reach the user's Downloads folder
 * mangled. The controller asks for that encoding explicitly; nothing checked that it arrives.</p>
 */
@WebMvcTest(controllers = AttachmentController.class)
@ActiveProfiles("test")
@Import({GlobalExceptionHandler.class, MessageServiceImpl.class, MessageSourceConfig.class})
class AttachmentControllerTest {

    private static final byte[] PDF_BYTES = "%PDF-1.4 bordereau de virement".getBytes();
    private static final String ACCENTED_NAME = "Reçu février 2026.pdf";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AttachmentService attachmentService;

    @Test
    @DisplayName("a valid pdf upload answers 201 with the stored metadata")
    void aValidPdfUploadAnswers201() throws Exception {
        given(this.attachmentService.upload(eq(AttachmentOwnerType.EXPENSE), eq(7L), any()))
                .willReturn(this.metadata());

        this.mockMvc.perform(multipart("/api/attachments")
                        .file(new MockMultipartFile("file", "bordereau.pdf",
                                MediaType.APPLICATION_PDF_VALUE, PDF_BYTES))
                        .param("ownerType", "EXPENSE")
                        .param("ownerId", "7"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(11))
                .andExpect(jsonPath("$.originalName").value(ACCENTED_NAME))
                .andExpect(jsonPath("$.sizeBytes").value(PDF_BYTES.length));
    }

    @Test
    @DisplayName("an unknown owner type answers 400 rather than 500")
    void anUnknownOwnerTypeAnswers400RatherThan500() throws Exception {
        this.mockMvc.perform(multipart("/api/attachments")
                        .file(new MockMultipartFile("file", "bordereau.pdf",
                                MediaType.APPLICATION_PDF_VALUE, PDF_BYTES))
                        .param("ownerType", "BOGUS")
                        .param("ownerId", "7"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value(containsString("ownerType")));
    }

    @Test
    @DisplayName("an upload without the file part answers 400 naming the missing part")
    void anUploadWithoutTheFilePartAnswers400() throws Exception {
        // Answered 500 before this work package: MissingServletRequestPartException fell through
        // to the generic branch, so a client that forgot the part got no usable reason.
        this.mockMvc.perform(multipart("/api/attachments")
                        .param("ownerType", "EXPENSE")
                        .param("ownerId", "7"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(containsString("file")));
    }

    @Test
    @DisplayName("the stored file is served with the content type and length of its metadata")
    void theStoredFileIsServedWithItsContentTypeAndLength() throws Exception {
        given(this.attachmentService.findById(11L)).willReturn(this.metadata());
        given(this.attachmentService.content(11L)).willReturn(new ByteArrayResource(PDF_BYTES));

        this.mockMvc.perform(get("/api/attachments/11"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", MediaType.APPLICATION_PDF_VALUE))
                .andExpect(header().longValue("Content-Length", PDF_BYTES.length))
                .andExpect(content().bytes(PDF_BYTES));
    }

    @Test
    @DisplayName("an accented filename is encoded so the browser saves it unmangled")
    void anAccentedFilenameIsEncodedForTheBrowser() throws Exception {
        given(this.attachmentService.findById(11L)).willReturn(this.metadata());
        given(this.attachmentService.content(11L)).willReturn(new ByteArrayResource(PDF_BYTES));

        final String disposition = this.mockMvc.perform(get("/api/attachments/11"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getHeader("Content-Disposition");

        // RFC 5987: the accented name travels percent-encoded in filename*, with a plain ASCII
        // filename beside it for older clients. « ç » is C3 A7 in UTF-8, « é » is C3 A9.
        assertThat(disposition)
                .startsWith("inline;")
                .contains("filename*=UTF-8''")
                .contains("Re%C3%A7u%20f%C3%A9vrier%202026.pdf");
    }

    @Test
    @DisplayName("a deletion answers 204 with no body")
    void aDeletionAnswers204WithNoBody() throws Exception {
        this.mockMvc.perform(delete("/api/attachments/11"))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));
    }

    private AttachmentResponse metadata() {
        return new AttachmentResponse(11L, ACCENTED_NAME, MediaType.APPLICATION_PDF_VALUE,
                (long) PDF_BYTES.length, "system", LocalDateTime.of(2026, 2, 14, 9, 30),
                AttachmentOwnerType.EXPENSE, 7L);
    }
}
