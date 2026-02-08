/*
 * Copyright 2025 IKE Community
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.ike.example;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.*;

/**
 * Test suite for {@link Concept}.
 */
@DisplayName("Concept Tests")
class ConceptTest {
    
    @Test
    @DisplayName("Should create valid concept")
    void shouldCreateValidConcept() {
        Concept concept = new Concept(
            123456789L,
            "Diabetes mellitus (disorder)",
            "Diabetes mellitus",
            "A metabolic disorder characterized by high blood glucose levels"
        );
        
        assertThat(concept.conceptId()).isEqualTo(123456789L);
        assertThat(concept.fullySpecifiedName()).isEqualTo("Diabetes mellitus (disorder)");
        assertThat(concept.preferredTerm()).isEqualTo("Diabetes mellitus");
        assertThat(concept.definition()).contains("metabolic disorder");
    }
    
    @Test
    @DisplayName("Should reject invalid concept ID")
    void shouldRejectInvalidConceptId() {
        assertThatThrownBy(() -> 
            new Concept(-1L, "Name", "Term", "Definition")
        ).isInstanceOf(IllegalArgumentException.class)
         .hasMessageContaining("Concept ID must be positive");
    }
    
    @Test
    @DisplayName("Should reject null FSN")
    void shouldRejectNullFsn() {
        assertThatThrownBy(() -> 
            new Concept(123L, null, "Term", "Definition")
        ).isInstanceOf(IllegalArgumentException.class)
         .hasMessageContaining("Fully specified name required");
    }
    
    @ParameterizedTest
    @ValueSource(longs = {1L, 3L, 5L, 7L, 9L})
    @DisplayName("Should identify odd IDs as primitive")
    void shouldIdentifyOddIdsAsPrimitive(long id) {
        Concept concept = new Concept(id, "Name", "Term", "Definition");
        assertThat(concept.isPrimitive()).isTrue();
    }
    
    @ParameterizedTest
    @ValueSource(longs = {2L, 4L, 6L, 8L, 10L})
    @DisplayName("Should identify even IDs as fully defined")
    void shouldIdentifyEvenIdsAsFullyDefined(long id) {
        Concept concept = new Concept(id, "Name", "Term", "Definition");
        assertThat(concept.isPrimitive()).isFalse();
    }
    
    @Test
    @DisplayName("Should create clinical finding with semantic tag")
    void shouldCreateClinicalFinding() {
        Concept finding = Concept.clinicalFinding(
            12345L,
            "Hypertension",
            "High blood pressure",
            "Elevated arterial blood pressure"
        );
        
        assertThat(finding.fullySpecifiedName()).endsWith("(finding)");
        assertThat(finding.fullySpecifiedName()).isEqualTo("Hypertension (finding)");
    }
    
    @Test
    @DisplayName("Should not duplicate semantic tag")
    void shouldNotDuplicateSemanticTag() {
        Concept finding = Concept.clinicalFinding(
            12345L,
            "Hypertension (finding)",
            "High blood pressure",
            "Elevated arterial blood pressure"
        );
        
        assertThat(finding.fullySpecifiedName()).isEqualTo("Hypertension (finding)");
        assertThat(finding.fullySpecifiedName()).doesNotContain("(finding)(finding)");
    }
}
