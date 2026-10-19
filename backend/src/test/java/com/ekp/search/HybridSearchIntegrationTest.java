package com.ekp.search;
import com.ekp.TestcontainersConfiguration;
import com.ekp.embedding.ChunkVectorRepository;
import com.ekp.security.AuthenticatedUser;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
@SpringBootTest @Import(TestcontainersConfiguration.class) @ActiveProfiles("test") @Transactional
class HybridSearchIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired SearchService search;
    @Autowired KeywordRepository keywords;
    @Autowired ChunkVectorRepository vectors;
    UUID owner() {
        UUID id=UUID.randomUUID();
        jdbc.update("INSERT INTO users(id,email,password_hash) VALUES (?,?,?)",id,id+"@test.local","test"); return id;
    }
    UUID document(UUID owner,String content,String status) {
        UUID doc=UUID.randomUUID();
        jdbc.update("INSERT INTO documents(id,original_filename,content_type,size_bytes,content_hash,storage_key,uploaded_by,status) VALUES (?,?,?,?,?,?,?,?)",doc,"policy.txt","text/plain",content.length(),doc.toString(),doc.toString(),owner,status);
        jdbc.update("INSERT INTO document_chunks(document_id,chunk_index,content,char_count,token_estimate) VALUES (?,?,?,?,?)",doc,0,content,content.length(),10); return doc;
    }
    @Test void keywordFallbackEnforcesOwnershipDocumentScopeAndReadyState() {
        UUID a=owner(), b=owner(); UUID own=document(a,"Vacation policy grants twenty days.","COMPLETED");
        UUID foreign=document(b,"Vacation policy grants forty days.","COMPLETED");
        document(a,"Vacation policy draft.","PROCESSING");
        var user=new AuthenticatedUser(a,"a@test","USER");
        assertThat(search.search(new SearchRequest("vacation",null,5),user)).extracting(SearchHitResponse::documentId).containsExactly(own);
        assertThat(search.search(new SearchRequest("vacation",foreign,5),user)).isEmpty();
        assertThat(search.search(new SearchRequest("the and",null,5),user)).isEmpty();
        assertThat(keywords.search("' OR 1=1 --",a,null,5)).isEmpty();
    }
    @Test void naturalQuestionsFallBackToMatchingTermsWithoutCrossingOwners() {
        UUID a=owner(),b=owner();
        UUID own=document(a,"Vacation: Team members receive 20 paid days of leave each year.","COMPLETED");
        UUID foreign=document(b,"Vacation days for members are 50.","COMPLETED");
        assertThat(keywords.search("How many vacation days do team members receive?",a,own,5))
            .extracting(ChunkVectorRepository.SearchHit::documentId).containsExactly(own);
        assertThat(keywords.search("How many vacation days?",a,foreign,5)).isEmpty();
        assertThat(keywords.search("spacecraft launch date",a,own,5)).isEmpty();
    }
    @Test void collectionFiltersApplyBeforeVectorRanking() {
        UUID a=owner(),b=owner(),collection=UUID.randomUUID();
        jdbc.update("INSERT INTO document_collections(id,owner_id,name) VALUES (?,?,?)",collection,a,"Scoped");
        UUID included=document(a,"vacation included","COMPLETED");
        document(a,"vacation outside","COMPLETED");
        jdbc.update("UPDATE documents SET collection_id=? WHERE id=?",collection,included);
        float[] v=new float[1536];v[0]=1;
        for(UUID id:jdbc.queryForList("SELECT id FROM document_chunks",UUID.class))vectors.saveEmbedding(id,v,"text-embedding-3-small");
        assertThat(vectors.search(v,a,null,10,collection)).extracting(ChunkVectorRepository.SearchHit::documentId).containsExactly(included);
        assertThat(vectors.search(v,b,null,10,collection)).isEmpty();
        assertThat(keywords.search("vacation days",a,null,10,collection)).extracting(ChunkVectorRepository.SearchHit::documentId).containsExactly(included);
    }
    @Test void vectorsAlsoEnforceOwnershipAndCompletedState() {
        UUID a=owner(),b=owner(); UUID own=document(a,"vacation","COMPLETED");
        UUID foreign=document(b,"secret","COMPLETED"); document(a,"draft","PROCESSING");
        float[] vector=new float[1536]; vector[0]=1;
        var ids=jdbc.queryForList("SELECT id FROM document_chunks", UUID.class);
        for(UUID id:ids) vectors.saveEmbedding(id,vector,"text-embedding-3-small");
        assertThat(vectors.search(vector,a,null,10)).extracting(ChunkVectorRepository.SearchHit::documentId).containsExactly(own);
        assertThat(vectors.search(vector,a,foreign,10)).isEmpty();
    }

    @Test void excludesDifferentModelsAndSchedulesThemForReindexing() {
        UUID a=owner(); UUID doc=document(a,"old model passage","COMPLETED");
        UUID chunk=jdbc.queryForObject("SELECT id FROM document_chunks WHERE document_id=?",UUID.class,doc);
        float[] v=new float[1536];v[0]=1;
        vectors.saveEmbedding(chunk,v,"old-model");
        assertThat(vectors.search(v,a,doc,5)).isEmpty();
        assertThat(vectors.findUnembedded(doc,8)).hasSize(1);
        assertThat(vectors.documentsWithUnembeddedChunks()).contains(doc);
    }
}
