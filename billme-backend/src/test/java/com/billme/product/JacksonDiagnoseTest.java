package com.billme.product;

import com.billme.product.dto.CreateProductRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.test.context.support.WithMockUser;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.mock.mockito.MockBean;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest
@AutoConfigureMockMvc
public class JacksonDiagnoseTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ProductService productService;

    @Test
    @WithMockUser(roles = "MERCHANT")
    public void testDeserialization() throws Exception {
        String json = "{\"name\": \"Red Pen\", \"price\": 10.00, \"barcode\": \"PEN1234\", \"gstRate\": 18}";
        
        System.out.println("TESTING JSON: " + json);
        
        when(productService.createProduct(any(), any())).thenReturn(new com.billme.product.dto.ProductResponse());

        mockMvc.perform(post("/api/merchant/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
                .andExpect(status().isOk());
        
        ArgumentCaptor<CreateProductRequest> captor = ArgumentCaptor.forClass(CreateProductRequest.class);
        verify(productService).createProduct(captor.capture(), anyString());
        
        System.out.println("DEBUG: Deserialized gstRate = " + captor.getValue().getGstRate());
    }
}
