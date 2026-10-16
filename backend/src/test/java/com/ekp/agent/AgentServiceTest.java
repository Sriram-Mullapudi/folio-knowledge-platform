package com.ekp.agent;
import com.ekp.agent.AgentRequest.Mode;
import com.ekp.document.*;
import com.ekp.rag.*;
import com.ekp.search.*;
import com.ekp.embedding.ChunkVectorRepository.SearchHit;
import com.ekp.security.AuthenticatedUser;
import com.ekp.common.error.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.data.domain.*;
import java.util.*;
import java.util.function.Consumer;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;
class AgentServiceTest {
    final SearchService search=mock(SearchService.class);
    final DocumentRepository docs=mock(DocumentRepository.class);
    final DocumentChunkRepository chunks=mock(DocumentChunkRepository.class);
    final ChatModelClient model=mock(ChatModelClient.class);
    final GenerationProperties props=mock(GenerationProperties.class);
    final JdbcTemplate jdbc=mock(JdbcTemplate.class);
    final AgentService service=new AgentService(search,docs,chunks,model,props,jdbc);
    final AuthenticatedUser user=new AuthenticatedUser(UUID.randomUUID(),"user@test", "USER");
    @Test void routesPredictablyAndRespectsExplicitMode() {
        assertThat(AgentService.route(new AgentRequest("Summarize this",null,null))).isEqualTo(Mode.SUMMARIZATION);
        assertThat(AgentService.route(new AgentRequest("Compare policies",null,null))).isEqualTo(Mode.RESEARCH);
        assertThat(AgentService.route(new AgentRequest("How many documents",null,null))).isEqualTo(Mode.METADATA);
        assertThat(AgentService.route(new AgentRequest("Find policy",null,null))).isEqualTo(Mode.RETRIEVAL);
        assertThat(AgentService.route(new AgentRequest("Summarize",Mode.RESEARCH,null))).isEqualTo(Mode.RESEARCH);
    }
    @Test void quantityQuestionsAboutContentDoNotRouteToInventory() {
        assertThat(AgentService.route(new AgentRequest("How many days of vacation?",null,null))).isEqualTo(Mode.RETRIEVAL);
    }
    @Test void rejectsFabricatedAndMissingCitations() {
        assertThatThrownBy(()->CitationValidator.validate("claim [99]",2)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(()->CitationValidator.validate("claim",2)).isInstanceOf(IllegalStateException.class);
        CitationValidator.validate("claim [1] and [2]",2);
    }
    @Test void capsResearchQueriesAndRemovesDuplicates() {
        assertThat(AgentService.researchQueries("alpha and beta; alpha? gamma; delta")).containsExactly("alpha","beta","gamma");
    }
    @Test void deniesForeignDocumentBeforeAnyTools() {
        UUID id=UUID.randomUUID(); when(docs.findByIdAndUploadedBy(id,user.id())).thenReturn(Optional.empty());
        assertThatThrownBy(()->service.run(new AgentRequest("summary",Mode.SUMMARIZATION,id),user)).isInstanceOf(ResourceNotFoundException.class);
        verifyNoInteractions(model,search,chunks,jdbc);
    }
    @Test void refusesGenerationWithoutProvider() {
        assertThatThrownBy(()->service.run(new AgentRequest("policy",Mode.RETRIEVAL,null),user)).isInstanceOf(GenerationDisabledException.class);
        verifyNoInteractions(model,search);
    }
    @Test void abstainsWithoutEvidenceAndNeverCallsModel() {
        when(props.isUsable()).thenReturn(true); when(props.maxContextChars()).thenReturn(1000);
        when(search.retrieve(any(),eq(user))).thenReturn(List.of());
        var result=service.run(new AgentRequest("policy",Mode.RETRIEVAL,null),user);
        assertThat(result.sources()).isEmpty(); assertThat(result.answer()).contains("No supporting");
        verifyNoInteractions(model);
    }
    @Test void summaryRequiresDocument() {
        when(props.isUsable()).thenReturn(true);
        assertThatThrownBy(()->service.run(new AgentRequest("summarize",Mode.SUMMARIZATION,null),user)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void researchIsolatesFailedStepsAndGeneratesWithCitations() {
        when(props.isUsable()).thenReturn(true); when(props.maxContextChars()).thenReturn(1000);
        UUID id=UUID.randomUUID(); Document doc=mock(Document.class); when(doc.getOriginalFilename()).thenReturn("policy.txt");
        when(docs.findByIdAndUploadedBy(id,user.id())).thenReturn(Optional.of(doc));
        when(search.retrieve(any(),eq(user))).thenThrow(new IllegalStateException("offline")).thenReturn(List.of(new SearchHit(UUID.randomUUID(),id,0,"Vacation is 20 days.",0,.02)));
        doAnswer(inv->{ Consumer<String> out=inv.getArgument(1); out.accept("Vacation is 20 days [1]."); return null; }).when(model).stream(anyList(),any());
        var result=service.run(new AgentRequest("vacation and benefits",Mode.RESEARCH,null),user);
        assertThat(result.sources()).hasSize(1); assertThat(result.answer()).contains("[1]");
        assertThat(result.trace()).anyMatch(s->s.contains("failed"));
        verify(search,times(2)).retrieve(any(),eq(user)); verify(model,times(1)).stream(anyList(),any());
    }
    @Test void missingCitationsReturnActionableFailureInsteadOfAnUnverifiedAnswer() {
        when(props.isUsable()).thenReturn(true); when(props.maxContextChars()).thenReturn(1000);
        UUID id=UUID.randomUUID(); Document doc=mock(Document.class); when(doc.getOriginalFilename()).thenReturn("policy.txt");
        when(docs.findByIdAndUploadedBy(id,user.id())).thenReturn(Optional.of(doc));
        when(search.retrieve(any(),eq(user))).thenReturn(List.of(new SearchHit(UUID.randomUUID(),id,0,"Vacation is 20 days.",0,.02)));
        doAnswer(inv->{ Consumer<String> out=inv.getArgument(1); out.accept("Vacation is 20 days."); return null; }).when(model).stream(anyList(),any());
        assertThatThrownBy(()->service.run(new AgentRequest("vacation",Mode.RETRIEVAL,null),user))
            .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
            .hasMessageContaining("Find passages");
    }
    @Test void metadataWorksWithoutModelAndNeverUsesQuestionAsSql() {
        when(jdbc.queryForMap(anyString(),eq(user.id()),isNull(),isNull(),isNull(),isNull())).thenReturn(Map.of("documents",2,"bytes",100,"ready",1));
        var result=service.run(new AgentRequest("DROP TABLE documents",Mode.METADATA,null),user);
        assertThat(result.answer()).contains("2 documents"); verifyNoInteractions(model,search);
        verify(jdbc).queryForMap(argThat(s->!s.contains("DROP TABLE")),eq(user.id()),isNull(),isNull(),isNull(),isNull());
    }
}
