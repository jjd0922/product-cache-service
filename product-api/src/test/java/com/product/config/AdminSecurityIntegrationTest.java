package com.product.config;

import com.product.application.dto.command.ProductCacheRebuildCommand;
import com.product.application.dto.result.RebuildJobResult;
import com.product.application.port.in.ProductCacheAdminUseCase;
import com.product.presentation.assembler.ProductCacheAdminRequestAssembler;
import com.product.presentation.assembler.ProductCacheAdminResponseAssembler;
import com.product.presentation.controller.ProductCacheAdminController;
import com.product.presentation.dto.request.RebuildRequest;
import com.product.presentation.dto.response.RebuildStartedResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ProductCacheAdminController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = {
        "product.admin.username=admin",
        "product.admin.password=secret"
})
class AdminSecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ProductCacheAdminUseCase productCacheAdminUseCase;

    @MockBean
    private ProductCacheAdminRequestAssembler productCacheAdminRequestAssembler;

    @MockBean
    private ProductCacheAdminResponseAssembler productCacheAdminResponseAssembler;

    @Test
    void adminApi_returns401WhenUnauthenticated() throws Exception {
        mockMvc.perform(post("/admin/cache/products/rebuild")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("AUTHENTICATION_REQUIRED"))
                .andExpect(jsonPath("$.path").value("/admin/cache/products/rebuild"));

        verifyNoInteractions(
                productCacheAdminUseCase,
                productCacheAdminRequestAssembler,
                productCacheAdminResponseAssembler
        );
    }

    @Test
    void adminApi_returns403WhenAuthenticatedUserDoesNotHaveAdminRole() throws Exception {
        mockMvc.perform(post("/admin/cache/products/rebuild")
                        .with(user("operator").roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("ACCESS_DENIED"))
                .andExpect(jsonPath("$.path").value("/admin/cache/products/rebuild"));

        verifyNoInteractions(
                productCacheAdminUseCase,
                productCacheAdminRequestAssembler,
                productCacheAdminResponseAssembler
        );
    }

    @Test
    void adminApi_acceptsRequestWhenAdminBasicAuthIsValid() throws Exception {
        UUID jobId = UUID.randomUUID();
        ProductCacheRebuildCommand command = new ProductCacheRebuildCommand(List.of(1L));
        RebuildJobResult result = new RebuildJobResult(
                jobId,
                "RUNNING",
                1L,
                0L,
                0,
                true,
                "rebuild started",
                null,
                "IDS(1)",
                LocalDateTime.of(2026, 5, 17, 0, 0),
                null
        );
        RebuildStartedResponse response = new RebuildStartedResponse(
                jobId,
                "RUNNING",
                1L,
                0L,
                0,
                true,
                "rebuild started",
                "IDS(1)",
                LocalDateTime.of(2026, 5, 17, 0, 0)
        );

        when(productCacheAdminRequestAssembler.from(any(RebuildRequest.class))).thenReturn(command);
        when(productCacheAdminUseCase.rebuild(command)).thenReturn(result);
        when(productCacheAdminResponseAssembler.toStartedResponse(result)).thenReturn(response);

        mockMvc.perform(post("/admin/cache/products/rebuild")
                        .with(httpBasic("admin", "secret"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productIds\":[1]}"))
                .andExpect(status().isAccepted())
                .andExpect(header().string("Location", "/admin/cache/products/jobs/" + jobId))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.jobId").value(jobId.toString()));
    }

    @Test
    void adminApi_doesNotEchoBasicCredentialWhenAuthenticationFails() throws Exception {
        mockMvc.perform(post("/admin/cache/products/rebuild")
                        .with(httpBasic("admin", "wrong-secret"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().string(not(containsString("wrong-secret"))))
                .andExpect(content().string(not(containsString("Authorization"))));
    }
}
