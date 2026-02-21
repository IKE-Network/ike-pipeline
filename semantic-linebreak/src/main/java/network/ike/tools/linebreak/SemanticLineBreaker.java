package network.ike.tools.linebreak;

import org.asciidoctor.Asciidoctor;
import org.asciidoctor.Options;
import org.asciidoctor.SafeMode;
import org.asciidoctor.ast.Block;
import org.asciidoctor.ast.Cursor;
import org.asciidoctor.ast.Document;
import org.asciidoctor.ast.StructuralNode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Reformats AsciiDoc files to use semantic linefeeds (one sentence per line).
 * <p>
 * Uses AsciidoctorJ to parse the document AST, identifying paragraph blocks that
 * contain prose. Only those blocks are reformatted; delimited blocks (listings,
 * diagrams, tables, passthroughs, etc.) are never touched.
 * <p>
 * Breaking rules:
 * <ul>
 *   <li>Sentence breaks: {@code . ? !} followed by a space and uppercase letter</li>
 *   <li>Clause breaks (optional): {@code , ;} followed by a space, when line exceeds threshold</li>
 * </ul>
 * <p>
 * Hard line breaks ({@code " +"} at end of line) are preserved.
 * Abbreviations (Dr., Mr., e.g., i.e., etc.) are recognized and not treated as sentence ends.
 */
public class SemanticLineBreaker {

    // ── Abbreviations that end with a period but do NOT end a sentence ──────────

    private static final Set<String> ABBREVIATIONS = Set.of(
            // Titles and honorifics
            "Dr", "Mr", "Mrs", "Ms", "Prof", "Sr", "Jr", "St", "Rev",
            // Military / government titles
            "Gen", "Gov", "Rep", "Sen", "Sgt", "Cpl", "Pvt",
            "Capt", "Lt", "Col", "Maj", "Cmdr", "Adm", "Pres",
            // Latin abbreviations
            "vs", "cf",
            // Multi-period Latin (matched as token before final period)
            "e.g", "i.e",
            // Reference prefixes
            "Fig", "No", "Vol", "Sec", "Ref", "Ed", "Ch", "Pt",
            // Common abbreviations
            "approx", "dept", "govt", "est",
            // Multi-period geographic / time
            "U.S", "U.K", "A.M", "P.M", "a.m", "p.m"
    );

    // ── Configuration ───────────────────────────────────────────────────────────

    private boolean clauseBreak = false;
    private int clauseBreakThreshold = 80;
    private boolean dryRun = false;
    private boolean verbose = false;

    // ── Entry point ─────────────────────────────────────────────────────────────

    public static void main(String[] args) throws IOException {
        var tool = new SemanticLineBreaker();
        tool.run(args);
    }

    private void run(String[] args) throws IOException {
        if (args.length < 1) {
            printUsage();
            System.exit(1);
        }

        String inputPath = null;
        String outputPath = null;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--output", "-o" -> outputPath = args[++i];
                case "--clause-break" -> clauseBreak = true;
                case "--clause-threshold" -> clauseBreakThreshold = Integer.parseInt(args[++i]);
                case "--dry-run", "-n" -> dryRun = true;
                case "--verbose", "-v" -> verbose = true;
                case "--help", "-h" -> { printUsage(); System.exit(0); }
                default -> {
                    if (args[i].startsWith("-")) {
                        System.err.println("Unknown option: " + args[i]);
                        System.exit(1);
                    }
                    inputPath = args[i];
                }
            }
        }

        if (inputPath == null) {
            System.err.println("No input file specified.");
            System.exit(1);
        }

        Path inPath = Path.of(inputPath);
        if (!Files.exists(inPath)) {
            System.err.println("File not found: " + inputPath);
            System.exit(1);
        }

        // Default: write to stdout if --dry-run, otherwise in-place
        if (outputPath == null && !dryRun) {
            outputPath = inputPath;
        }

        // ── Read source ─────────────────────────────────────────────────────
        List<String> sourceLines = Files.readAllLines(inPath);
        String source = String.join("\n", sourceLines);

        // ── Parse with AsciidoctorJ ─────────────────────────────────────────
        try (Asciidoctor asciidoctor = Asciidoctor.Factory.create()) {
            Options options = Options.builder()
                    .sourcemap(true)
                    .safe(SafeMode.SAFE)
                    .build();

            Document doc = asciidoctor.load(source, options);

            // ── Collect paragraph line ranges ───────────────────────────────
            List<int[]> paragraphRanges = new ArrayList<>();
            collectParagraphRanges(doc, paragraphRanges);

            // Sort by start line (should already be in order, but be safe)
            paragraphRanges.sort(Comparator.comparingInt(r -> r[0]));

            if (verbose) {
                System.err.printf("Found %d paragraph(s) to process.%n", paragraphRanges.size());
                for (int[] range : paragraphRanges) {
                    System.err.printf("  lines %d-%d%n", range[0] + 1, range[1] + 1);
                }
            }

            // ── Apply sentence breaks in reverse order ──────────────────────
            List<String> result = new ArrayList<>(sourceLines);
            int changes = 0;

            for (int i = paragraphRanges.size() - 1; i >= 0; i--) {
                int start = paragraphRanges.get(i)[0];
                int end = paragraphRanges.get(i)[1];

                // Extract and join the paragraph lines
                var paragraphLines = result.subList(start, end + 1);
                String joined = joinParagraph(paragraphLines);

                // Apply sentence breaking
                List<String> broken = breakSentences(joined);

                // Only replace if we actually changed something
                if (!broken.equals(new ArrayList<>(paragraphLines))) {
                    changes++;
                    if (verbose) {
                        System.err.printf("  Reformatting lines %d-%d (%d lines -> %d lines)%n",
                                start + 1, end + 1, end - start + 1, broken.size());
                    }
                    // Remove old lines, insert new
                    for (int j = end; j >= start; j--) {
                        result.remove(j);
                    }
                    result.addAll(start, broken);
                }
            }

            System.err.printf("Processed %d paragraph(s), reformatted %d.%n",
                    paragraphRanges.size(), changes);

            // ── Write output ────────────────────────────────────────────────
            String output = String.join("\n", result);
            // Preserve trailing newline if original had one
            if (source.endsWith("\n")) {
                output += "\n";
            }

            if (dryRun) {
                System.out.print(output);
            } else {
                Files.writeString(Path.of(outputPath), output);
                System.err.println("Wrote: " + outputPath);
            }
        }
    }

    // ── AST Walking ─────────────────────────────────────────────────────────────

    /**
     * Recursively walk the AST and collect [startLine, endLine] (0-based) for
     * every paragraph block. Skips paragraphs without source location (e.g.,
     * generated or from included files).
     */
    private void collectParagraphRanges(StructuralNode node, List<int[]> ranges) {
        if ("paragraph".equals(node.getContext())) {
            Cursor loc = node.getSourceLocation();
            if (loc != null && node instanceof Block block) {
                List<String> lines = block.getLines();
                if (lines != null && !lines.isEmpty()) {
                    int startLine = loc.getLineNumber() - 1; // convert to 0-based
                    int endLine = startLine + lines.size() - 1;
                    ranges.add(new int[]{startLine, endLine});
                }
            }
            // Paragraphs don't have child blocks, so no need to recurse further
            return;
        }

        // Recurse into child blocks (sections, lists, admonitions, etc.)
        List<StructuralNode> children = node.getBlocks();
        if (children != null) {
            for (StructuralNode child : children) {
                collectParagraphRanges(child, ranges);
            }
        }
    }

    // ── Paragraph Joining ───────────────────────────────────────────────────────

    /**
     * Join paragraph lines into a single string, respecting AsciiDoc hard line
     * breaks ({@code " +"} at end of line). Hard breaks are preserved as literal
     * {@code \n} boundaries that won't be reformatted.
     */
    private String joinParagraph(List<String> lines) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (i > 0) {
                sb.append(' ');
            }
            // Preserve hard line breaks: " +" at end of line
            if (line.endsWith(" +")) {
                sb.append(line, 0, line.length() - 2);
                sb.append(" +\n"); // keep as a forced break boundary
            } else {
                sb.append(line);
            }
        }
        return sb.toString();
    }

    // ── Sentence Breaking ───────────────────────────────────────────────────────

    /**
     * Break a joined paragraph into one-sentence-per-line.
     * <p>
     * If the paragraph contains hard line breaks ({@code \n} from " +" markers),
     * each segment is broken independently.
     */
    List<String> breakSentences(String text) {
        // Handle hard line breaks as independent segments
        if (text.contains(" +\n")) {
            List<String> result = new ArrayList<>();
            String[] segments = text.split(" \\+\n");
            for (int s = 0; s < segments.length; s++) {
                List<String> broken = breakSegment(segments[s].trim());
                if (s < segments.length - 1 && !broken.isEmpty()) {
                    // Re-attach the hard break marker to the last line of this segment
                    int last = broken.size() - 1;
                    broken.set(last, broken.get(last) + " +");
                }
                result.addAll(broken);
            }
            return result;
        }
        return breakSegment(text);
    }

    /**
     * Core breaking logic for a single segment of prose (no hard breaks within).
     */
    private List<String> breakSegment(String text) {
        if (text.isBlank()) {
            return List.of();
        }

        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();

        int len = text.length();
        int i = 0;

        while (i < len) {
            char c = text.charAt(i);
            current.append(c);

            // ── Sentence-ending punctuation: . ? ! ──────────────────────────
            if ((c == '.' || c == '?' || c == '!')
                    && i + 2 < len
                    && text.charAt(i + 1) == ' '
                    && Character.isUpperCase(text.charAt(i + 2))) {

                // For periods, check if this is an abbreviation
                if (c == '.' && isAbbreviation(current.toString())) {
                    i++;
                    continue;
                }

                // Also check: is the "." part of a URL or file path?
                // e.g., "file.adoc The" — period is part of filename
                // Simple heuristic: if char before period is not a letter, don't break
                // (catches "2.0 The" → don't break on version numbers)
                if (c == '.' && current.length() >= 2) {
                    char beforeDot = current.charAt(current.length() - 2);
                    if (Character.isDigit(beforeDot)) {
                        i++;
                        continue;
                    }
                }

                // Break here
                result.add(current.toString().trim());
                current.setLength(0);
                i += 2; // skip the space; next char is the uppercase letter
                continue;
            }

            // ── Closing quote after sentence punctuation: ." or ?" or !" ─────
            if ((c == '"' || c == '\u201d' || c == '\'')
                    && current.length() >= 2) {
                char beforeQuote = current.charAt(current.length() - 2);
                if ((beforeQuote == '.' || beforeQuote == '?' || beforeQuote == '!')
                        && i + 2 < len
                        && text.charAt(i + 1) == ' '
                        && Character.isUpperCase(text.charAt(i + 2))) {

                    result.add(current.toString().trim());
                    current.setLength(0);
                    i += 2;
                    continue;
                }
            }

            // ── Optional clause breaks: , ; (only when line is long) ────────
            if (clauseBreak
                    && (c == ',' || c == ';')
                    && i + 2 < len
                    && text.charAt(i + 1) == ' '
                    && current.length() > clauseBreakThreshold) {

                result.add(current.toString().trim());
                current.setLength(0);
                i += 2;
                continue;
            }

            i++;
        }

        // Flush remainder
        String remainder = current.toString().trim();
        if (!remainder.isEmpty()) {
            result.add(remainder);
        }

        return result;
    }

    // ── Abbreviation Detection ──────────────────────────────────────────────────

    /**
     * Check if the text ending with a period is an abbreviation.
     * Extracts the last word before the period and checks the known set.
     *
     * @param textEndingWithPeriod the accumulated text including the trailing period
     */
    private static boolean isAbbreviation(String textEndingWithPeriod) {
        // Strip trailing period
        String text = textEndingWithPeriod.substring(0, textEndingWithPeriod.length() - 1).trim();
        if (text.isEmpty()) return false;

        // Find the last word (token after last space)
        int lastSpace = text.lastIndexOf(' ');
        String lastWord = (lastSpace >= 0) ? text.substring(lastSpace + 1) : text;

        // Direct lookup
        if (ABBREVIATIONS.contains(lastWord)) return true;

        // Check for single uppercase letter (common initial: "J. Smith")
        if (lastWord.length() == 1 && Character.isUpperCase(lastWord.charAt(0))) return true;

        return false;
    }

    // ── Usage ───────────────────────────────────────────────────────────────────

    private void printUsage() {
        System.err.println("""
                Usage: semantic-linebreak [options] <file.adoc>

                Reformats AsciiDoc prose paragraphs to use one-sentence-per-line.
                Only paragraph blocks are modified; listings, diagrams, tables,
                and all other block types are preserved unchanged.

                Options:
                  -o, --output <file>     Write to file (default: in-place)
                  -n, --dry-run           Print result to stdout, don't modify files
                  -v, --verbose           Show which paragraphs are being reformatted
                  --clause-break          Also break on , and ; (for long lines)
                  --clause-threshold <n>  Min line length before clause break (default: 80)
                  -h, --help              Show this message

                Examples:
                  semantic-linebreak doc.adoc                     # in-place reformat
                  semantic-linebreak -n doc.adoc                  # preview to stdout
                  semantic-linebreak --clause-break doc.adoc      # also break at clauses
                  semantic-linebreak -o reformatted.adoc doc.adoc # write to new file
                """);
    }
}
