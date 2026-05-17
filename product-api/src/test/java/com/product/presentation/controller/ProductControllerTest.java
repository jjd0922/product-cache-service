package com.product.presentation.controller;

import com.product.application.dto.result.ProductResult;
import com.product.application.port.in.ProductQueryUseCase;
import com.product.config.SecurityConfig;
import com.product.presentation.assembler.ProductResponseAssembler;
import com.product.presentation.common.advice.GlobalExceptionHandler;
import com.product.presentation.controller.ProductController;
import com.product.presentation.dto.response.ProductResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ProductController.class)
@Import({GlobalExceptionHandler.class, SecurityConfig.class})
class ProductControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ProductQueryUseCase productQueryUseCase;

    @MockBean
    private ProductResponseAssembler productResponseAssembler;

    @Test
    @DisplayName("상품 단건 조회 성공 시 200 과 success 응답을 반환한다")
    void getProduct_whenProductExists_thenReturnOk() throws Exception {
        ProductResult result = new ProductResult(
                1L,
                "상품A",
                new BigDecimal("10000"),
                new BigDecimal("9000"),
                10,
                false,
                "ON_SALE",
                Instant.parse("2026-03-20T00:00:00Z")
        );

        ProductResponse response = new ProductResponse(
                1L,
                "상품A",
                new BigDecimal("10000"),
                new BigDecimal("9000"),
                10,
                false,
                "ON_SALE",
                Instant.parse("2026-03-20T00:00:00Z")
        );

        when(productQueryUseCase.getProduct(1L)).thenReturn(Optional.of(result));
        when(productResponseAssembler.from(eq(result))).thenReturn(response);

        mockMvc.perform(get("/products/{productId}", 1L))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.name").value("상품A"))
                .andExpect(jsonPath("$.data.price").value(10000))
                .andExpect(jsonPath("$.data.salePrice").value(9000))
                .andExpect(jsonPath("$.data.stock").value(10))
                .andExpect(jsonPath("$.data.soldOut").value(false))
                .andExpect(jsonPath("$.data.displayStatus").value("ON_SALE"))
                .andExpect(jsonPath("$.path").value("/products/1"));

        verify(productQueryUseCase).getProduct(1L);
        verify(productResponseAssembler).from(result);
    }

    @Test
    @DisplayName("상품 단건 조회 결과가 없으면 404 와 failure 응답을 반환한다")
    void getProduct_whenProductDoesNotExist_thenReturnNotFound() throws Exception {
        when(productQueryUseCase.getProduct(999L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/products/{productId}", 999L))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("PRODUCT_404"))
                .andExpect(jsonPath("$.error.message").value("상품을 찾을 수 없습니다."))
                .andExpect(jsonPath("$.path").value("/products/999"));
    }

    @Test
    @DisplayName("상품 다건 조회 성공 시 200 과 success 응답을 반환한다")
    void getProducts_whenRequestIsValid_thenReturnOk() throws Exception {
        ProductResult result1 = new ProductResult(
                1L,
                "상품A",
                new BigDecimal("10000"),
                new BigDecimal("9000"),
                10,
                false,
                "ON_SALE",
                Instant.parse("2026-03-20T00:00:00Z")
        );
        ProductResult result2 = new ProductResult(
                2L,
                "상품B",
                new BigDecimal("20000"),
                null,
                0,
                true,
                "SOLD_OUT",
                Instant.parse("2026-03-20T00:00:01Z")
        );

        ProductResponse response1 = new ProductResponse(
                1L,
                "상품A",
                new BigDecimal("10000"),
                new BigDecimal("9000"),
                10,
                false,
                "ON_SALE",
                Instant.parse("2026-03-20T00:00:00Z")
        );
        ProductResponse response2 = new ProductResponse(
                2L,
                "상품B",
                new BigDecimal("20000"),
                null,
                0,
                true,
                "SOLD_OUT",
                Instant.parse("2026-03-20T00:00:01Z")
        );

        when(productQueryUseCase.getProducts(List.of(1L, 2L))).thenReturn(List.of(result1, result2));
        when(productResponseAssembler.from(result1)).thenReturn(response1);
        when(productResponseAssembler.from(result2)).thenReturn(response2);

        String requestBody = """
                {
                  "ids": [1, 2]
                }
                """;

        mockMvc.perform(post("/products/ids")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].id").value(1))
                .andExpect(jsonPath("$.data[0].name").value("상품A"))
                .andExpect(jsonPath("$.data[1].id").value(2))
                .andExpect(jsonPath("$.data[1].name").value("상품B"))
                .andExpect(jsonPath("$.data[1].soldOut").value(true))
                .andExpect(jsonPath("$.data[1].displayStatus").value("SOLD_OUT"))
                .andExpect(jsonPath("$.path").value("/products/ids"));
    }

    @Test
    @DisplayName("상품 다건 조회 시 request body 가 null 이면 빈 목록으로 조회한다")
    void getProducts_whenRequestBodyIsNull_thenUseEmptyList() throws Exception {
        when(productQueryUseCase.getProducts(List.of())).thenReturn(List.of());

        mockMvc.perform(post("/products/ids")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(0))
                .andExpect(jsonPath("$.path").value("/products/ids"));
    }

    @Test
    @DisplayName("상품 다건 조회 시 ids 가 null 이면 빈 목록으로 조회한다")
    void getProducts_whenIdsIsNull_thenUseEmptyList() throws Exception {
        when(productQueryUseCase.getProducts(List.of())).thenReturn(List.of());

        String requestBody = """
                {
                  "ids": null
                }
                """;

        mockMvc.perform(post("/products/ids")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(0))
                .andExpect(jsonPath("$.path").value("/products/ids"));
    }
}
