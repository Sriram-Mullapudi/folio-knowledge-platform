package com.ekp.document;

import com.ekp.TestcontainersConfiguration;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.MinIOContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Exercises the upload path end to end against real PostgreSQL and real MinIO:
 * a valid upload is stored (201), an identical re-upload is rejected as a
 * duplicate (409), an unsupported type is rejected (400), and an
 * unauthenticated upload is rejected (401).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@Testcontainers
class DocumentUploadIntegrationTest {

    @Container
    static final MinIOContainer MINIO =
            new MinIOContainer(DockerImageName.parse(System.getProperty("ekp.test.minio-image", "minio/minio@sha256:14cea493d9a34af32f524e538b8346cf79f3321eff8e708c1e2960462bd8936e")).asCompatibleSubstituteFor("minio/minio"));

    @DynamicPropertySource
    static void storageProps(DynamicPropertyRegistry registry) {
        registry.add("ekp.storage.s3.endpoint", MINIO::getS3URL);
        registry.add("ekp.storage.s3.access-key", MINIO::getUserName);
        registry.add("ekp.storage.s3.secret-key", MINIO::getPassword);
        registry.add("ekp.storage.s3.bucket", () -> "ekp-test");
        registry.add("ekp.storage.s3.region", () -> "us-east-1");
        registry.add("ekp.storage.s3.path-style-access", () -> true);
        registry.add("ekp.storage.s3.auto-create-bucket", () -> true);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void reprocessingReplacesSectionsWithoutDuplicateIndexes() throws Exception {
        String token=registerAndGetToken("reprocess-sections@test.local");
        String text="Cedar Health | Engineer | 2024–Present\n"+"Built scheduling tools. ".repeat(100)
                +"\nGeneral skills\nRust and Terraform.";
        var file=new MockMultipartFile("file","sections.txt","text/plain",text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        String response=mockMvc.perform(multipart("/api/v1/documents").file(file).header("Authorization","Bearer "+token))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String id=objectMapper.readTree(response).get("id").asText();
        awaitReady(id,token);
        String first=mockMvc.perform(get("/api/v1/documents/"+id+"/chunks").header("Authorization","Bearer "+token))
                .andReturn().getResponse().getContentAsString();
        int count=objectMapper.readTree(first).get("content").size();
        org.assertj.core.api.Assertions.assertThat(count).isGreaterThan(2);
        mockMvc.perform(post("/api/v1/documents/"+id+"/reprocess").header("Authorization","Bearer "+token)).andExpect(status().isOk());
        awaitReady(id,token);
        mockMvc.perform(get("/api/v1/documents/"+id+"/chunks").header("Authorization","Bearer "+token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements",is(count)));
    }

    private void awaitReady(String id,String token) throws Exception {
        for(int i=0;i<200;i++) {
            String json=mockMvc.perform(get("/api/v1/documents/"+id).header("Authorization","Bearer "+token))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            String state=objectMapper.readTree(json).get("status").asText();
            org.assertj.core.api.Assertions.assertThat(state).isNotEqualTo("FAILED");
            if(state.equals("COMPLETED"))return;
            Thread.sleep(50);
        }
        throw new AssertionError("Processing did not finish");
    }

    @Test
    void uploadThenDuplicateThenList() throws Exception {
        String token = registerAndGetToken("doc-owner@test.local");
        MockMultipartFile file = new MockMultipartFile(
                "file", "notes.txt", "text/plain",
                "hello enterprise knowledge platform".getBytes());

        mockMvc.perform(multipart("/api/v1/documents").file(file)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.filename", is("notes.txt")))
                .andExpect(jsonPath("$.contentType", is("text/plain")))
                .andExpect(jsonPath("$.status", is(notNullValue())));

        // Identical bytes for the same owner -> duplicate.
        mockMvc.perform(multipart("/api/v1/documents").file(file)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict());

        mockMvc.perform(get("/api/v1/documents").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements", is(1)));
    }

    @Test
    void unsupportedTypeIsRejected() throws Exception {
        String token = registerAndGetToken("bad-type@test.local");
        MockMultipartFile file = new MockMultipartFile(
                "file", "malware.exe", "application/octet-stream", "MZ\u0000\u0000".getBytes());

        mockMvc.perform(multipart("/api/v1/documents").file(file)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unauthenticatedUploadIsRejected() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "notes.txt", "text/plain", "anon".getBytes());
        mockMvc.perform(multipart("/api/v1/documents").file(file))
                .andExpect(status().isUnauthorized());
    }


    @Test
    void anotherUserCannotReadDownloadDeleteOrReprocessDocuments() throws Exception {
        String owner = registerAndGetToken("isolation-owner@test.local");
        String other = registerAndGetToken("isolation-other@test.local");
        MockMultipartFile file = new MockMultipartFile("file", "private.txt", "text/plain", "owner-only evidence".getBytes());
        String body = mockMvc.perform(multipart("/api/v1/documents").file(file)
                .header("Authorization", "Bearer " + owner)).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String id = objectMapper.readTree(body).get("id").asText();
        for (String suffix : new String[]{"", "/content", "/chunks"}) {
            mockMvc.perform(get("/api/v1/documents/" + id + suffix)
                    .header("Authorization", "Bearer " + other)).andExpect(status().isNotFound());
        }
        mockMvc.perform(post("/api/v1/documents/" + id + "/reprocess")
                .header("Authorization", "Bearer " + other)).andExpect(status().isNotFound());
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/v1/documents/" + id)
                .header("Authorization", "Bearer " + other)).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/documents").header("Authorization", "Bearer " + other))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements", is(0)));
        mockMvc.perform(get("/api/v1/documents/" + id + "/content")
                .header("Authorization", "Bearer " + owner)).andExpect(status().isOk());
    }

    private String registerAndGetToken(String email) throws Exception {
        String json = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"password123\"}".formatted(email)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(json).get("accessToken").asText();
    }
}
