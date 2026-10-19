package com.ekp.rag;
import com.ekp.audit.AuditService;
import com.ekp.document.Document;
import com.ekp.document.DocumentRepository;
import com.ekp.embedding.ChunkVectorRepository.SearchHit;
import com.ekp.rag.dto.RagRequest;
import com.ekp.search.SearchService;
import com.ekp.security.AuthenticatedUser;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.function.Consumer;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;
class RagServiceTest {
    @Test void followUpExcludesGenericSkillPassagesOutsideTheOriginalTopic() {
        UUID doc=UUID.randomUUID();
        var role=new SearchHit(UUID.randomUUID(),doc,0,"At Cedar Health: Java and PostgreSQL.",0,.02);
        var skills=new SearchHit(UUID.randomUUID(),doc,3,"General skills: Rust and Kubernetes.",0,.02);
        when(props.maxContextChars()).thenReturn(2000);
        when(docs.findAllById(any())).thenReturn(List.of());
        when(search.retrieve(any(),eq(user))).thenAnswer(inv -> {
            com.ekp.search.SearchRequest r=inv.getArgument(0);
            return r.query().equals("What did the candidate build at Cedar Health?") ? List.of(role) : List.of(role,skills);
        });
        var history=List.of(new RagRequest.PreviousTurn("What did the candidate build at Cedar Health?", "Used Kubernetes and Rust [1]"));
        AnswerSink sink=mock(AnswerSink.class);
        service.answer(new RagRequest("Which technologies did he use there?",doc,null,null,history),user,sink);
        verify(sink).onSources(argThat(s -> s.size()==1 && s.get(0).snippet().contains("Cedar Health")));
        verify(model).stream(argThat(messages -> !messages.get(1).content().contains("Kubernetes")
                && messages.get(0).content().contains("general skills do not prove")),any());
    }

    @Test void namedTopicSwitchDoesNotReuseThePreviousEmployersRetrieval() {
        when(props.maxContextChars()).thenReturn(1000);
        when(search.retrieve(any(),eq(user))).thenReturn(List.of());
        var history=List.of(new RagRequest.PreviousTurn("What did he build at Cedar Health?", "A portal."));
        service.answer(new RagRequest("What did he use at Birch Payments?",null,null,null,history),user,mock(AnswerSink.class));
        verify(search,times(1)).retrieve(argThat(r -> r.query().equals("What did he use at Birch Payments?")),eq(user));
    }
    @Test void asksForAReferenceWhenFirstQuestionHasNoMatchingEvidence() {
        when(props.maxContextChars()).thenReturn(1000);
        when(search.retrieve(any(),eq(user))).thenReturn(List.of());
        for (String question : List.of("When does it launch?", "What changed?")) {
            AnswerSink sink=mock(AnswerSink.class);
            service.answer(new RagRequest(question,null,null),user,sink);
            verify(sink).onToken(contains("Which project or document do you mean?"));
            verify(sink).onComplete();
        }
        verifyNoInteractions(model);
    }

    @Test void firstQuestionCanUseEvidenceFromASelectedDocument() {
        when(props.maxContextChars()).thenReturn(1000);
        when(docs.findAllById(any())).thenReturn(List.of());
        UUID id=UUID.randomUUID();
        when(search.retrieve(any(),eq(user))).thenReturn(List.of(new SearchHit(UUID.randomUUID(),id,0,"Atlas launches in November",0,.02)));
        AnswerSink sink=mock(AnswerSink.class);
        service.answer(new RagRequest("When does it launch?",id,null),user,sink);
        verify(model).stream(anyList(),any());
        verify(sink,never()).onToken(contains("Which project"));
    }
    @Test void followUpRetrievesFreshEvidenceWithinTheSelectedScope() {
        UUID documentId=UUID.randomUUID(), collectionId=UUID.randomUUID();
        when(search.retrieve(any(),eq(user))).thenReturn(List.of());
        when(props.maxContextChars()).thenReturn(1000);
        var history=List.of(new RagRequest.PreviousTurn("Who owns Atlas?", "An unsupported old claim [9]"));
        AnswerSink sink=mock(AnswerSink.class);
        service.answer(new RagRequest("When does it launch?",documentId,5,collectionId,history),user,sink);
        verify(search).retrieve(argThat(r -> r.query().contains("Atlas")
                && r.query().contains("When does it launch?") && !r.query().contains("unsupported")
                && documentId.equals(r.documentId()) && collectionId.equals(r.collectionId())),eq(user));
        verifyNoInteractions(model);
        verify(sink).onToken(contains("couldn't find"));
    }

    @Test void historyIsBoundedAndNestedFieldsAreValidated() {
        try (var factory=jakarta.validation.Validation.buildDefaultValidatorFactory()) {
            var validator=factory.getValidator();
            var turn=new RagRequest.PreviousTurn("Who owns Atlas?", "Mira Chen");
            assertThat(validator.validate(new RagRequest("When?",null,null,null,List.of(turn)))).isEmpty();
            assertThat(validator.validate(new RagRequest("When?",null,null,null,Collections.nCopies(4,turn)))).isNotEmpty();
            assertThat(validator.validate(new RagRequest("When?",null,null,null,List.of(new RagRequest.PreviousTurn("", "x"))))).isNotEmpty();
            assertThat(validator.validate(new RagRequest("When?",null,null,null,List.of(new RagRequest.PreviousTurn("x", "a".repeat(1001)))))).isNotEmpty();
        }
    }
    final SearchService search=mock(SearchService.class);
    final DocumentRepository docs=mock(DocumentRepository.class);
    final ChatModelClient model=mock(ChatModelClient.class);
    final GenerationProperties props=mock(GenerationProperties.class);
    final AuditService audit=mock(AuditService.class);
    final RagService service=new RagService(search,docs,model,props,audit);
    final AuthenticatedUser user=new AuthenticatedUser(UUID.randomUUID(),"test@test","USER");
    @Test void abstainsWithoutCallingGenerationWhenNoEvidenceExists() {
        when(props.maxContextChars()).thenReturn(1000);
        when(search.retrieve(any(),eq(user))).thenReturn(List.of());
        AnswerSink sink=mock(AnswerSink.class);
        service.answer(new RagRequest("unknown",null,null),user,sink);
        verify(sink).onSources(List.of()); verify(sink).onToken(contains("couldn't find"));
        verify(sink).onComplete(); verifyNoInteractions(model); verify(sink,never()).onError(any());
    }
    @Test void streamsSourcesBeforeTokensUsingSharedRetrieval() {
        UUID docId=UUID.randomUUID(); Document doc=mock(Document.class);
        when(doc.getId()).thenReturn(docId); when(doc.getOriginalFilename()).thenReturn("policy.txt");
        when(docs.findAllById(any())).thenReturn(List.of(doc));
        when(props.maxContextChars()).thenReturn(1000);
        when(search.retrieve(any(),eq(user))).thenReturn(List.of(new SearchHit(UUID.randomUUID(),docId,0,"Twenty days of leave",0,.02)));
        doAnswer(inv->{ Consumer<String> out=inv.getArgument(1); out.accept("Twenty days [1]"); return null; }).when(model).stream(anyList(),any());
        AnswerSink sink=mock(AnswerSink.class);
        service.answer(new RagRequest("leave",null,null),user,sink);
        var order=inOrder(sink); order.verify(sink).onSources(argThat(s->s.size()==1 && s.get(0).number()==1));
        order.verify(sink).onToken("Twenty days [1]"); order.verify(sink).onComplete();
    }
    @Test void providerFailureHasOneErrorAndNoCompletion() {
        when(search.retrieve(any(),eq(user))).thenThrow(new IllegalStateException("retrieval unavailable"));
        AnswerSink sink=mock(AnswerSink.class); service.answer(new RagRequest("q",null,null),user,sink);
        verify(sink,times(1)).onError(any()); verify(sink,never()).onComplete();
    }
    @Test void generationAvailabilityDoesNotRequireEmbeddingProvider() {
        when(props.isUsable()).thenReturn(true); service.ensureAvailable(); verifyNoInteractions(search);
    }
}
