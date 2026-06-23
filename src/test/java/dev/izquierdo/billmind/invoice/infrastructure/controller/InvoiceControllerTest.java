package dev.izquierdo.billmind.invoice.infrastructure.controller;

import dev.izquierdo.billmind._shared.application.command.CommandBus;
import dev.izquierdo.billmind._shared.application.query.QueryBus;
import dev.izquierdo.billmind._shared.domain.exceptions.ValidationErrorsException;
import dev.izquierdo.billmind._shared.domain.port.ExternalAuthPort;
import dev.izquierdo.billmind._shared.infrastructure.auth.AdminRoutesService;
import dev.izquierdo.billmind._shared.infrastructure.session.PublicRoutesService;
import dev.izquierdo.billmind._shared.infrastructure.session.SessionContext;
import dev.izquierdo.billmind._shared.infrastructure.session.SessionService;
import dev.izquierdo.billmind.invoice.domain.exceptions.InvoiceNotFoundException;
import dev.izquierdo.billmind.invoice.domain.exceptions.NotASupplyInvoiceException;
import dev.izquierdo.billmind.invoice.domain.exceptions.UnsupportedSupplyTypeException;
import dev.izquierdo.billmind.invoice.domain.model.Invoice;
import dev.izquierdo.billmind._shared.domain.model.SupplyDomain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import dev.izquierdo.billmind._shared.infrastructure.config.SecurityConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.web.multipart.MaxUploadSizeExceededException;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(InvoiceController.class)
@Import(SecurityConfig.class)
class InvoiceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CommandBus commandBus;

    @MockitoBean
    private QueryBus queryBus;

    @MockitoBean
    private SessionService sessionService;

    @MockitoBean
    private SessionContext sessionContext;

    @MockitoBean
    private PublicRoutesService publicRoutesService;

    // JwtAuthFilter is a Filter, so @WebMvcTest instantiates it; its collaborators are not part
    // of the web slice and must be supplied as mocks.
    @MockitoBean
    private ExternalAuthPort externalAuthPort;

    @MockitoBean
    private AdminRoutesService adminRoutesService;

    private static final UUID SESSION_ID = UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567890");

    @BeforeEach
    void setUp() {
        when(sessionContext.getSessionId()).thenReturn(SESSION_ID);
    }

    // --- Upload ---

    @Test
    void uploadInvoice_withValidFile_returns201AndInvoiceId() throws Exception {
        byte[] pdfContent = "%PDF-1.4 fake invoice content".getBytes();
        MockMultipartFile file = new MockMultipartFile("file", "factura.pdf", "application/pdf", pdfContent);

        Invoice invoice = Invoice.builder(UUID.randomUUID(), "factura.pdf")
                .sessionId(SESSION_ID)
                .supplyType(SupplyDomain.ELECTRICITY)
                .build();
        when(queryBus.dispatch(any())).thenReturn(invoice);

        mockMvc.perform(multipart("/api/v1/invoices")
                        .file(file)
                        .header("X-Session-Id", SESSION_ID.toString()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("Factura subida y procesada correctamente."))
                .andExpect(jsonPath("$.data.fileName").value("factura.pdf"))
                .andExpect(jsonPath("$.data.invoiceId").isNotEmpty());

        verify(commandBus).dispatch(any());
    }

    @Test
    void uploadInvoice_withoutFile_returns400() throws Exception {
        mockMvc.perform(multipart("/api/v1/invoices")
                        .header("X-Session-Id", SESSION_ID.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("El campo 'file' es obligatorio"));
    }

    @Test
    void uploadInvoice_withEmptyFile_returns400() throws Exception {
        MockMultipartFile emptyFile = new MockMultipartFile("file", "vacio.pdf", "application/pdf", new byte[0]);

        mockMvc.perform(multipart("/api/v1/invoices")
                        .file(emptyFile)
                        .header("X-Session-Id", SESSION_ID.toString()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void uploadInvoice_withInvalidMimeType_returns400() throws Exception {
        MockMultipartFile invalidFile = new MockMultipartFile(
                "file", "malicious.pdf", "application/pdf", "not a pdf content".getBytes());

        mockMvc.perform(multipart("/api/v1/invoices")
                        .file(invalidFile)
                        .header("X-Session-Id", SESSION_ID.toString()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void uploadInvoice_withNonSupplyInvoice_returns422() throws Exception {
        doThrow(new NotASupplyInvoiceException()).when(commandBus).dispatch(any());

        mockMvc.perform(multipart("/api/v1/invoices")
                        .file(validPdf("contrato.pdf"))
                        .header("X-Session-Id", SESSION_ID.toString()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value(
                        "El archivo no parece ser una factura de suministro del hogar (electricidad, gas, agua o telecomunicaciones)"
                ));
    }

    @Test
    void uploadInvoice_withUnsupportedSupplyType_returns422() throws Exception {
        doThrow(new UnsupportedSupplyTypeException(SupplyDomain.GAS)).when(commandBus).dispatch(any());

        mockMvc.perform(multipart("/api/v1/invoices")
                        .file(validPdf("factura-gas.pdf"))
                        .header("X-Session-Id", SESSION_ID.toString()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value(
                        "El tipo de suministro 'GAS' no está soportado todavía. Por el momento solo se aceptan facturas de electricidad."
                ));
    }

    @Test
    void uploadInvoice_whenFileTooLarge_returns400WithClearMessage() throws Exception {
        doThrow(new MaxUploadSizeExceededException(5L * 1024 * 1024)).when(commandBus).dispatch(any());

        mockMvc.perform(multipart("/api/v1/invoices")
                        .file(validPdf("factura.pdf"))
                        .header("X-Session-Id", SESSION_ID.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("El archivo supera el tamaño máximo permitido (5 MB)"));
    }

    @Test
    void uploadInvoice_whenNullPointerException_returns500() throws Exception {
        doThrow(new NullPointerException("unexpected null")).when(commandBus).dispatch(any());

        mockMvc.perform(multipart("/api/v1/invoices")
                        .file(validPdf("factura.pdf"))
                        .header("X-Session-Id", SESSION_ID.toString()))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("Se ha producido un error interno en el servidor"));
    }

    @Test
    void uploadInvoice_whenUnexpectedException_returns500() throws Exception {
        doThrow(new RuntimeException("db connection lost")).when(commandBus).dispatch(any());

        mockMvc.perform(multipart("/api/v1/invoices")
                        .file(validPdf("factura.pdf"))
                        .header("X-Session-Id", SESSION_ID.toString()))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("Se ha producido un error interno en el servidor"));
    }

    @Test
    void uploadInvoice_whenValidationErrorsException_returns400WithErrors() throws Exception {
        Map<String, Map<String, String>> errors = Map.of(
                "file", Map.of("size", "El archivo supera el tamaño máximo permitido")
        );
        doThrow(new ValidationErrorsException(errors)).when(commandBus).dispatch(any());

        mockMvc.perform(multipart("/api/v1/invoices")
                        .file(validPdf("factura.pdf"))
                        .header("X-Session-Id", SESSION_ID.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.file.size").exists());
    }

    // --- Session filter ---

    @Test
    void uploadInvoice_withMissingSessionHeader_returns400() throws Exception {
        mockMvc.perform(multipart("/api/v1/invoices")
                        .file(validPdf("factura.pdf")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("La cabecera X-Session-Id es obligatoria"));
    }

    @Test
    void uploadInvoice_withInvalidSessionHeader_returns400() throws Exception {
        mockMvc.perform(multipart("/api/v1/invoices")
                        .file(validPdf("factura.pdf"))
                        .header("X-Session-Id", "not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("La cabecera X-Session-Id debe ser un UUID válido"));
    }

    // --- GET /api/v1/invoices/{id} ---

    @Test
    void getInvoice_withValidIdAndMatchingSession_returns200() throws Exception {
        UUID invoiceId = UUID.randomUUID();
        Invoice invoice = Invoice.builder(invoiceId, "factura.pdf")
                .sessionId(SESSION_ID)
                .supplyType(SupplyDomain.ELECTRICITY)
                .provider("IBERDROLA")
                .build();
        when(queryBus.dispatch(any())).thenReturn(invoice);

        mockMvc.perform(get("/api/v1/invoices/" + invoiceId)
                        .header("X-Session-Id", SESSION_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(invoiceId.toString()))
                .andExpect(jsonPath("$.data.fileName").value("factura.pdf"))
                .andExpect(jsonPath("$.data.supplyType").value("ELECTRICITY"));
    }

    @Test
    void getInvoice_withUnknownId_returns404() throws Exception {
        when(queryBus.dispatch(any())).thenThrow(new InvoiceNotFoundException());

        mockMvc.perform(get("/api/v1/invoices/" + UUID.randomUUID())
                        .header("X-Session-Id", SESSION_ID.toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Factura no encontrada"));
    }

    // --- GET /api/v1/invoices ---

    @Test
    void getSessionInvoices_withValidSession_returns200WithList() throws Exception {
        UUID invoiceId = UUID.randomUUID();
        Invoice invoice = Invoice.builder(invoiceId, "factura.pdf")
                .sessionId(SESSION_ID)
                .supplyType(SupplyDomain.GAS)
                .provider("NATURGY")
                .build();
        when(queryBus.dispatch(any())).thenReturn(List.of(invoice));

        mockMvc.perform(get("/api/v1/invoices")
                        .header("X-Session-Id", SESSION_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].id").value(invoiceId.toString()))
                .andExpect(jsonPath("$.data[0].provider").value("NATURGY"));
    }

    @Test
    void getSessionInvoices_withNoInvoices_returns200WithEmptyList() throws Exception {
        when(queryBus.dispatch(any())).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/invoices")
                        .header("X-Session-Id", SESSION_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data").isEmpty());
    }

    private MockMultipartFile validPdf(String filename) {
        return new MockMultipartFile("file", filename, "application/pdf",
                "%PDF-1.4 fake content".getBytes());
    }
}