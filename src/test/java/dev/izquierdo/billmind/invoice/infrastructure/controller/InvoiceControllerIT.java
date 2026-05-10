package dev.izquierdo.billmind.invoice.infrastructure.controller;

import dev.izquierdo.billmind._shared.application.command.CommandBus;
import dev.izquierdo.billmind.invoice.domain.exceptions.NotASupplyInvoiceException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(InvoiceController.class)
class InvoiceControllerIT {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CommandBus commandBus;

    @Test
    void uploadInvoice_withValidFile_returns200AndInvoiceId() throws Exception {
        byte[] pdfContent = "%PDF-1.4 fake invoice content".getBytes();
        MockMultipartFile file = new MockMultipartFile(
                "file", "factura.pdf", "application/pdf", pdfContent
        );

        mockMvc.perform(multipart("/api/v1/invoices/upload").file(file))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("Invoice uploaded and vectorized successfully."))
                .andExpect(jsonPath("$.data.fileName").value("factura.pdf"))
                .andExpect(jsonPath("$.data.invoiceId").isNotEmpty());

        verify(commandBus).dispatch(any());
    }

    @Test
    void uploadInvoice_withoutFile_returns400() throws Exception {
        mockMvc.perform(multipart("/api/v1/invoices/upload"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("El campo 'file' es obligatorio"));
    }

    @Test
    void uploadInvoice_withEmptyFile_returns400() throws Exception {
        MockMultipartFile emptyFile = new MockMultipartFile(
                "file", "vacio.pdf", "application/pdf", new byte[0]
        );

        mockMvc.perform(multipart("/api/v1/invoices/upload").file(emptyFile))
                .andExpect(status().isBadRequest());
    }

    @Test
    void uploadInvoice_withInvalidMimeType_returns400() throws Exception {
        MockMultipartFile invalidFile = new MockMultipartFile(
                "file", "malicious.pdf", "application/pdf", "not a pdf content".getBytes()
        );

        mockMvc.perform(multipart("/api/v1/invoices/upload").file(invalidFile))
                .andExpect(status().isBadRequest());
    }

    @Test
    void uploadInvoice_withNonSupplyInvoice_returns422() throws Exception {
        doThrow(new NotASupplyInvoiceException()).when(commandBus).dispatch(any());

        byte[] pdfContent = "%PDF-1.4 fake non-supply content".getBytes();
        MockMultipartFile file = new MockMultipartFile(
                "file", "contrato.pdf", "application/pdf", pdfContent
        );

        mockMvc.perform(multipart("/api/v1/invoices/upload").file(file))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value(
                        "El archivo no parece ser una factura de suministro del hogar (electricidad, gas, agua o telecomunicaciones)"
                ));
    }
}
