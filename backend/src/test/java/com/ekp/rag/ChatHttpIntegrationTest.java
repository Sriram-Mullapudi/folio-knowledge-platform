package com.ekp.rag;
import com.ekp.TestcontainersConfiguration;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.jdbc.core.JdbcTemplate;
import java.net.*;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import java.util.function.Consumer;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={"ekp.generation.enabled=true","ekp.generation.api-key=fake-test-only"})
@Import(TestcontainersConfiguration.class) @ActiveProfiles("test")
class ChatHttpIntegrationTest {
 @LocalServerPort int port;
 @Autowired JdbcTemplate jdbc;
 @Autowired ObjectMapper mapper;
 @MockBean ChatModelClient model;
 final HttpClient client=HttpClient.newHttpClient();
 @Test void streamsWithRealAuthenticationAndServletAsyncDispatch() throws Exception {
  String email="stream-"+UUID.randomUUID()+"@example.test";
  var registered=client.send(HttpRequest.newBuilder(URI.create("http://localhost:"+port+"/api/v1/auth/register"))
   .header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(Map.of("email",email,"password","test-password-123")))).build(),HttpResponse.BodyHandlers.ofString());
  assertThat(registered.statusCode()).isEqualTo(201);
  String token=mapper.readTree(registered.body()).get("accessToken").asText();
  UUID owner=jdbc.queryForObject("SELECT id FROM users WHERE email=?",UUID.class,email),doc=UUID.randomUUID();
  jdbc.update("INSERT INTO documents(id,original_filename,content_type,size_bytes,content_hash,storage_key,uploaded_by,status) VALUES (?,?,?,?,?,?,?,?)",doc,"leave.txt","text/plain",20,doc.toString(),doc.toString(),owner,"COMPLETED");
  jdbc.update("INSERT INTO document_chunks(document_id,chunk_index,content,char_count,token_estimate) VALUES (?,?,?,?,?)",doc,0,"Vacation allowance is twenty days.",34,9);
  doAnswer(inv->{Consumer<String> out=inv.getArgument(1);out.accept("Twenty days [1].");return null;}).when(model).stream(anyList(),any());
  var response=client.send(HttpRequest.newBuilder(URI.create("http://localhost:"+port+"/api/v1/chat"))
   .timeout(Duration.ofSeconds(15)).header("Authorization","Bearer "+token).header("Accept","text/event-stream, application/json")
   .header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString("{\"question\":\"vacation\"}")).build(),HttpResponse.BodyHandlers.ofString());
  assertThat(response.statusCode()).isEqualTo(200);
  assertThat(response.body()).contains("event:sources","Twenty days [1].","event:done").doesNotContain("Authentication required");
  assertThat(response.body().indexOf("event:sources")).isLessThan(response.body().indexOf("event:token"));
 }
}
