package com.cug.geotext.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cug.geotext.entity.GeologicalDictionary;
import com.cug.geotext.entity.GeologicalEntity;
import com.cug.geotext.mapper.GeologicalDictionaryMapper;
import com.cug.geotext.mapper.GeologicalEntityMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

class DictionaryServiceTest {
    @Test
    void distinguishesStandardNamesAliasesAndUnmatchedTerms() {
        GeologicalDictionaryMapper dictionaryMapper=mock(GeologicalDictionaryMapper.class);
        GeologicalEntityMapper entityMapper=mock(GeologicalEntityMapper.class);
        GeologicalDictionary term=new GeologicalDictionary(); term.setId(5L); term.setTermType("LITHOLOGY"); term.setStandardName("灰岩"); term.setAliases("石灰岩|碳酸钙岩"); term.setEnabled(true);
        when(dictionaryMapper.selectList(any())).thenReturn(List.of(term));
        GeologicalEntity exact=entity(1L,"灰岩"), alias=entity(2L,"石灰岩"), missing=entity(3L,"白云岩");
        GeologicalEntity coordinate=entity(4L,"东经 114.9300°");coordinate.setEntityType("COORDINATE");

        int matched=new DictionaryService(dictionaryMapper,entityMapper).normalize(List.of(exact,alias,missing,coordinate));

        assertThat(matched).isEqualTo(3);
        assertThat(exact.getNormalizationStatus()).isEqualTo("EXACT");
        assertThat(alias.getNormalizationStatus()).isEqualTo("ALIAS");
        assertThat(alias.getStandardName()).isEqualTo("灰岩");
        assertThat(missing.getNormalizationStatus()).isEqualTo("UNMATCHED");
        assertThat(coordinate.getNormalizationStatus()).isEqualTo("EXACT");
        assertThat(coordinate.getStandardName()).isEqualTo("东经114.9300°");
        verify(entityMapper).updateById(alias);
    }
    private GeologicalEntity entity(long id,String name){GeologicalEntity e=new GeologicalEntity();e.setId(id);e.setEntityName(name);e.setEntityType("LITHOLOGY");return e;}
}
