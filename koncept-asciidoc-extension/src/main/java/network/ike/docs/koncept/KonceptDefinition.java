package network.ike.docs.koncept;

/**
 * Immutable definition of a Koncept, including natural language definition,
 * description logic axiom, and optional terminology identifiers.
 *
 * @param identifier   CamelCase identifier used in markup (e.g., "HeartFailure")
 * @param label        Human-readable label (e.g., "Heart Failure")
 * @param definition   Natural language definition text
 * @param axiom        Description logic axiom string using Unicode DL symbols
 * @param sctid        Optional SNOMED CT concept identifier
 * @param iri          Optional OWL IRI for the concept
 */
public record KonceptDefinition(
        String identifier,
        String label,
        String definition,
        String axiom,
        String sctid,
        String iri
) {

    /**
     * Builder for constructing KonceptDefinition instances from parsed YAML
     * or programmatic sources.
     */
    public static class Builder {
        private String identifier;
        private String label;
        private String definition;
        private String axiom;
        private String sctid;
        private String iri;

        public Builder identifier(String identifier) {
            this.identifier = identifier;
            return this;
        }

        public Builder label(String label) {
            this.label = label;
            return this;
        }

        public Builder definition(String definition) {
            this.definition = definition;
            return this;
        }

        public Builder axiom(String axiom) {
            this.axiom = axiom;
            return this;
        }

        public Builder sctid(String sctid) {
            this.sctid = sctid;
            return this;
        }

        public Builder iri(String iri) {
            this.iri = iri;
            return this;
        }

        public KonceptDefinition build() {
            if (identifier == null || identifier.isBlank()) {
                throw new IllegalStateException("KonceptDefinition requires an identifier");
            }
            if (label == null) {
                // Default: split camelCase
                label = identifier.replaceAll("([a-z])([A-Z])", "$1 $2");
            }
            return new KonceptDefinition(identifier, label, definition, axiom, sctid, iri);
        }
    }

    public static Builder builder() {
        return new Builder();
    }
}
