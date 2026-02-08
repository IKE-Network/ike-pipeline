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

/**
 * Example terminology concept demonstrating SNOMED CT-like structures.
 * 
 * <p>This class represents a simple clinical concept with:
 * <ul>
 *   <li>Unique identifier (SCTID-like)</li>
 *   <li>Fully specified name</li>
 *   <li>Preferred term</li>
 *   <li>Definition</li>
 * </ul>
 * 
 * @author IKE Community
 * @version 1.0.0
 */
public record Concept(
    long conceptId,
    String fullySpecifiedName,
    String preferredTerm,
    String definition
) {
    
    /**
     * Creates a concept with validation.
     * 
     * @param conceptId unique identifier
     * @param fullySpecifiedName the FSN
     * @param preferredTerm the PT
     * @param definition clinical definition
     * @throws IllegalArgumentException if any field is invalid
     */
    public Concept {
        if (conceptId <= 0) {
            throw new IllegalArgumentException("Concept ID must be positive");
        }
        if (fullySpecifiedName == null || fullySpecifiedName.isBlank()) {
            throw new IllegalArgumentException("Fully specified name required");
        }
        if (preferredTerm == null || preferredTerm.isBlank()) {
            throw new IllegalArgumentException("Preferred term required");
        }
        if (definition == null || definition.isBlank()) {
            throw new IllegalArgumentException("Definition required");
        }
    }
    
    /**
     * Determines if this concept is a primitive (non-fully-defined) concept.
     * In a real SNOMED implementation, this would check the definition status.
     * 
     * @return true if primitive
     */
    public boolean isPrimitive() {
        // Simplified: odd IDs are primitive
        return conceptId % 2 != 0;
    }
    
    /**
     * Factory method for creating a clinical finding concept.
     * 
     * @param conceptId unique identifier
     * @param fsn fully specified name (should include semantic tag)
     * @param pt preferred term
     * @param definition clinical definition
     * @return new Concept instance
     */
    public static Concept clinicalFinding(long conceptId, String fsn, String pt, String definition) {
        if (!fsn.contains("(finding)")) {
            fsn = fsn + " (finding)";
        }
        return new Concept(conceptId, fsn, pt, definition);
    }
}
