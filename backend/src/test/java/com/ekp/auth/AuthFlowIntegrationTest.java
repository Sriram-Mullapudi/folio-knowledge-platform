package com.ekp.auth;

import com.ekp.TestcontainersConfiguration;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Exercises the full Phase 1 contract against a real pgvector PostgreSQL:
 * registration, login, the /me endpoint, and RBAC (USER forbidden from admin
 * routes, ADMIN allowed). This is the test that proves the security wiring.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class AuthFlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired private org.springframework.jdbc.core.JdbcTemplate jdbc;

    @Test
    void collectionsArePrivateScopeSearchAndKeepFilesWhenRemoved() throws Exception {
        String email="collection-"+java.util.UUID.randomUUID()+"@test.local";
        String token=registerAndGetAccessToken(email,"password123");
        String other=registerAndGetAccessToken("collection-other-"+java.util.UUID.randomUUID()+"@test.local","password123");
        java.util.UUID owner=jdbc.queryForObject("SELECT id FROM users WHERE email=?",java.util.UUID.class,email);
        java.util.UUID doc=java.util.UUID.randomUUID(),outside=java.util.UUID.randomUUID();
        for(java.util.UUID id:java.util.List.of(doc,outside)) {
            jdbc.update("INSERT INTO documents(id,original_filename,content_type,size_bytes,content_hash,storage_key,uploaded_by,status) VALUES (?,?,?,?,?,?,?,?)",id,"policy.txt","text/plain",20,id.toString(),id.toString(),owner,"COMPLETED");
            jdbc.update("INSERT INTO document_chunks(document_id,chunk_index,content,char_count,token_estimate) VALUES (?,0,'Vacation policy',15,3)",id);
        }
        String created=mockMvc.perform(post("/api/v1/collections").header("Authorization","Bearer "+token).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Research\"}"))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String collection=objectMapper.readTree(created).get("id").asText();
        String assignment=objectMapper.writeValueAsString(java.util.Map.of("collectionId",collection));
        mockMvc.perform(post("/api/v1/collections/documents/"+doc).header("Authorization","Bearer "+other).contentType(MediaType.APPLICATION_JSON).content(assignment)).andExpect(status().isNotFound());
        mockMvc.perform(post("/api/v1/collections/documents/"+doc).header("Authorization","Bearer "+token).contentType(MediaType.APPLICATION_JSON).content(assignment)).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/collections").header("Authorization","Bearer "+other)).andExpect(jsonPath("$.length()",is(0)));
        mockMvc.perform(get("/api/v1/documents?collectionId="+collection).header("Authorization","Bearer "+token)).andExpect(jsonPath("$.totalElements",is(1))).andExpect(jsonPath("$.content[0].collectionId",is(collection)));
        mockMvc.perform(post("/api/v1/search").header("Authorization","Bearer "+token).contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(java.util.Map.of("query","vacation","collectionId",collection))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.length()",is(1))).andExpect(jsonPath("$[0].documentId",is(doc.toString())));
        mockMvc.perform(post("/api/v1/collections/"+collection).header("Authorization","Bearer "+other).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Stolen\"}")).andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/v1/collections/"+collection).header("Authorization","Bearer "+other)).andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/v1/collections/"+collection).header("Authorization","Bearer "+token)).andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/documents/"+doc).header("Authorization","Bearer "+token)).andExpect(status().isOk()).andExpect(jsonPath("$.collectionId").isEmpty());
        mockMvc.perform(get("/api/v1/collections")).andExpect(status().isUnauthorized());
    }

    @Test
    void savedConversationsPersistAndStayPrivate() throws Exception {
        String owner=registerAndGetAccessToken("saved-"+java.util.UUID.randomUUID()+"@test.local","password123");
        String other=registerAndGetAccessToken("saved-other-"+java.util.UUID.randomUUID()+"@test.local","password123");
        String body="""
            {"title":"Vacation research","turns":[{"id":"turn-1","question":"vacation","answer":"20 days","sources":[],"kind":"SEARCH"}],"mode":"SEARCH","scope":""}
            """;
        String created=mockMvc.perform(post("/api/v1/conversations").header("Authorization","Bearer "+owner).contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String id=objectMapper.readTree(created).get("id").asText();
        mockMvc.perform(get("/api/v1/conversations/"+id).header("Authorization","Bearer "+owner))
            .andExpect(status().isOk()).andExpect(jsonPath("$.turns[0].answer",is("20 days")));
        mockMvc.perform(get("/api/v1/conversations").header("Authorization","Bearer "+owner))
            .andExpect(status().isOk()).andExpect(jsonPath("$[0].title",is("Vacation research")));
        mockMvc.perform(get("/api/v1/conversations/"+id).header("Authorization","Bearer "+other)).andExpect(status().isNotFound());
        mockMvc.perform(post("/api/v1/conversations/"+id).header("Authorization","Bearer "+other).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/conversations").header("Authorization","Bearer "+other)).andExpect(jsonPath("$.length()",is(0)));
        mockMvc.perform(post("/api/v1/conversations/"+id).header("Authorization","Bearer "+owner).contentType(MediaType.APPLICATION_JSON).content(body.replace("20 days","Updated evidence"))).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/conversations/"+id).header("Authorization","Bearer "+owner)).andExpect(jsonPath("$.turns[0].answer",is("Updated evidence")));
        mockMvc.perform(post("/api/v1/conversations").header("Authorization","Bearer "+owner).contentType(MediaType.APPLICATION_JSON).content(body.replace("SEARCH","INVALID"))).andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/conversations")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/conversations").contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isUnauthorized());
    }

    @Test
    void registrationAuditCommitsAfterUserAndFailedLoginSurvivesRollback() throws Exception {
        String email="audit-"+java.util.UUID.randomUUID()+"@test.local";
        registerAndGetAccessToken(email,"password123");
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject(
            "SELECT count(*) FROM audit_log a JOIN users u ON a.actor_id=u.id WHERE u.email=? AND a.action='REGISTER'",Integer.class,email)).isEqualTo(1);
        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(java.util.Map.of("email",email,"password","wrong-password"))))
            .andExpect(status().isUnauthorized());
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject(
            "SELECT count(*) FROM audit_log WHERE action='LOGIN_FAILED' AND metadata->>'email'=?",Integer.class,email)).isEqualTo(1);
    }

    @Test
    void workspaceAndMetadataAreOwnerScopedAndNewEndpointsRequireLogin() throws Exception {
        String email="workspace-"+java.util.UUID.randomUUID()+"@test.local";
        String token=registerAndGetAccessToken(email,"password123");
        String other=registerAndGetAccessToken("other-"+java.util.UUID.randomUUID()+"@test.local","password123");
        java.util.UUID owner=jdbc.queryForObject("SELECT id FROM users WHERE email=?",java.util.UUID.class,email);
        java.util.UUID doc=java.util.UUID.randomUUID();
        jdbc.update("INSERT INTO documents(id,original_filename,content_type,size_bytes,content_hash,storage_key,uploaded_by,status) VALUES (?,?,?,?,?,?,?,?)",doc,"test.txt","text/plain",12,doc.toString(),doc.toString(),owner,"COMPLETED");
        mockMvc.perform(get("/api/v1/workspace/summary").header("Authorization","Bearer "+token))
            .andExpect(status().isOk()).andExpect(jsonPath("$.documents",is(1))).andExpect(jsonPath("$.searchMode",is("keyword")));
        mockMvc.perform(get("/api/v1/workspace/summary").header("Authorization","Bearer "+other))
            .andExpect(status().isOk()).andExpect(jsonPath("$.documents",is(0)));
        mockMvc.perform(post("/api/v1/agent").header("Authorization","Bearer "+token).contentType(MediaType.APPLICATION_JSON)
            .content("{\"question\":\"How many documents?\",\"mode\":\"METADATA\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.answer",org.hamcrest.Matchers.containsString("1 documents")));
        mockMvc.perform(get("/api/v1/workspace/summary")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/agent").contentType(MediaType.APPLICATION_JSON).content("{\"question\":\"hi\"}"))
            .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/search").contentType(MediaType.APPLICATION_JSON).content("{\"query\":\"hi\"}"))
            .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/agent").header("Authorization","Bearer "+token).contentType(MediaType.APPLICATION_JSON)
            .content("{\"question\":\"hi\",\"mode\":\"INVALID\"}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void registerThenAccessProtectedAndAdminRoutes() throws Exception {
        // 1. Register a normal user -> 201 with role USER.
        String userAccess = registerAndGetAccessToken("user1@test.local", "password123");

        // 2. The user can read its own profile.
        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + userAccess))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email", is("user1@test.local")))
                .andExpect(jsonPath("$.role", is("USER")));

        // 3. The user is forbidden from admin routes (RBAC: 403, not 500).
        mockMvc.perform(get("/api/v1/admin/users")
                        .header("Authorization", "Bearer " + userAccess))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status", is(403)));

        // 4. The seeded admin can log in and reach admin routes.
        String adminAccess = loginAndGetAccessToken("admin@test.local", "admin-test-password-123");
        mockMvc.perform(get("/api/v1/admin/users")
                        .header("Authorization", "Bearer " + adminAccess))
                .andExpect(status().isOk());
    }

    @Test
    void unauthenticatedRequestToProtectedRouteReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status", is(401)));
    }

    @Test
    void duplicateRegistrationReturns409() throws Exception {
        registerAndGetAccessToken("dupe@test.local", "password123");
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("dupe@test.local", "password123")))
                .andExpect(status().isConflict());
    }

    @Test
    void disabledChatReturnsUsefulJsonInsteadOfAuthenticationError() throws Exception {
        String token=registerAndGetAccessToken("chat-off-"+java.util.UUID.randomUUID()+"@test.local","password123");
        mockMvc.perform(post("/api/v1/chat").header("Authorization","Bearer "+token)
            .contentType(MediaType.APPLICATION_JSON).accept(MediaType.TEXT_EVENT_STREAM)
            .content("{\"question\":\"What is in my document?\"}"))
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.message",org.hamcrest.Matchers.containsString("generation is not configured")));
    }

    private String registerAndGetAccessToken(String email, String password) throws Exception {
        String json = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(email, password)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return readToken(json);
    }

    private String loginAndGetAccessToken(String email, String password) throws Exception {
        String json = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(email, password)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return readToken(json);
    }

    private String readToken(String json) throws Exception {
        JsonNode node = objectMapper.readTree(json);
        return node.get("accessToken").asText();
    }

    private String body(String email, String password) {
        return "{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password);
    }
}
