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
 * Reformats AsciiDoc files to use semantic linefeeds.
 * <p>
 * Line breaks are placed at logical boundaries — sentences, clauses, asides,
 * introductions, and compound-sentence joints — producing source text that is
 * easier to diff, edit, and reason about.
 * <p>
 * Uses AsciidoctorJ to parse the document AST, identifying paragraph blocks that
 * contain prose. Only those blocks are reformatted; delimited blocks (listings,
 * diagrams, tables, passthroughs, etc.) are never touched.
 * <p>
 * Default breaking rules (in priority order):
 * <ol>
 *   <li>Sentence ends: {@code . ? !} followed by a space and uppercase letter</li>
 *   <li>Closing quote after sentence: {@code ." ?" !"} followed by space and uppercase</li>
 *   <li>Em-dash (Unicode {@code \u2014}) followed by space</li>
 *   <li>Em-dash (AsciiDoc {@code --}) surrounded by spaces</li>
 *   <li>Semicolon followed by space</li>
 *   <li>Colon followed by space (guarded against URLs, times, definition lists)</li>
 *   <li>Comma followed by coordinating conjunction ({@code and, but, or, yet, so, nor})</li>
 *   <li>Simple comma clause break (optional, threshold-gated)</li>
 * </ol>
 * <p>
 * Use {@code --sentences-only} to restrict breaking to sentence boundaries only
 * (rules 1-2 above).
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

    // ── Coordinating conjunctions for comma+conjunction breaks ─────────────────

    private static final List<String> CONJUNCTIONS =
            List.of("and ", "but ", "or ", "yet ", "so ", "nor ");

    // ── AsciiDoc inline macros that should start on their own line ────────────
    // These are block-level annotations embedded in paragraph text.  The tool
    // emits a line break *before* each one so they sit on a dedicated line,
    // and never breaks *inside* their bracket arguments.

    private static final List<String> OWN_LINE_MACROS =
            List.of("indexterm:[", "indexterm2:[", "footnote:[");

    // ── Configuration ───────────────────────────────────────────────────────────

    private boolean sentencesOnly = false;
    private boolean clauseBreak = true;
    private int clauseBreakThreshold = 0;
    private int maxLineLength = 64;
    private int minRemainder = 15;
    private int minLineLength = 10;
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
                case "--sentences-only" -> sentencesOnly = true;
                case "--clause-break" -> clauseBreak = true;
                case "--clause-threshold" -> clauseBreakThreshold = Integer.parseInt(args[++i]);
                case "--max-line-length" -> maxLineLength = Integer.parseInt(args[++i]);
                case "--min-remainder" -> minRemainder = Integer.parseInt(args[++i]);
                case "--min-line-length" -> minLineLength = Integer.parseInt(args[++i]);
                case "--no-wrap" -> maxLineLength = Integer.MAX_VALUE;
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
                List<String> broken = breakSemanticLines(joined);

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

    // ── Semantic Line Breaking ─────────────────────────────────────────────────

    /**
     * Break a joined paragraph into one-sentence-per-line.
     * <p>
     * If the paragraph contains hard line breaks ({@code \n} from " +" markers),
     * each segment is broken independently.
     */
    List<String> breakSemanticLines(String text) {
        // Handle hard line breaks as independent segments
        if (text.contains(" +\n")) {
            List<String> result = new ArrayList<>();
            String[] segments = text.split(" \\+\n");
            for (int s = 0; s < segments.length; s++) {
                List<String> broken = mergeShortLines(softWrap(breakSegment(segments[s].trim())));
                if (s < segments.length - 1 && !broken.isEmpty()) {
                    // Re-attach the hard break marker to the last line of this segment
                    int last = broken.size() - 1;
                    broken.set(last, broken.get(last) + " +");
                }
                result.addAll(broken);
            }
            return result;
        }
        return mergeShortLines(softWrap(breakSegment(text)));
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
        int bracketDepth = 0; // track nesting inside [...] (macro arguments)

        while (i < len) {
            char c = text.charAt(i);

            // ── Break before AsciiDoc macros that get their own line ─────────
            // indexterm:[], indexterm2:[], footnote:[], and shorthand index
            // entries (((…))) and ((…)).  Emit accumulated prose as one line,
            // then consume the entire macro (including brackets) into a new line.
            if (bracketDepth == 0) {
                String rest = text.substring(i);

                // Check for named macros: indexterm:[…], indexterm2:[…], footnote:[…]
                String matchedMacro = null;
                for (String macro : OWN_LINE_MACROS) {
                    if (rest.startsWith(macro)) {
                        matchedMacro = macro;
                        break;
                    }
                }

                if (matchedMacro != null) {
                    // Flush any accumulated prose before the macro
                    String prose = current.toString().trim();
                    if (!prose.isEmpty()) {
                        result.add(prose);
                        current.setLength(0);
                    }
                    // Consume the macro through its closing bracket
                    int depth = 0;
                    int j = i;
                    while (j < len) {
                        char mc = text.charAt(j);
                        current.append(mc);
                        if (mc == '[') depth++;
                        else if (mc == ']') {
                            depth--;
                            if (depth == 0) { j++; break; }
                        }
                        j++;
                    }
                    result.add(current.toString().trim());
                    current.setLength(0);
                    i = j;
                    // Skip trailing space after macro
                    if (i < len && text.charAt(i) == ' ') i++;
                    continue;
                }

                // Check for hidden shorthand index: (((…)))
                // Only triple-paren gets its own line — it's invisible in output.
                // Double-paren ((…)) is a visible inline term; leave it in prose.
                if (rest.startsWith("(((")) {
                    String prose = current.toString().trim();
                    if (!prose.isEmpty()) {
                        result.add(prose);
                        current.setLength(0);
                    }
                    String closer = ")))";
                    int closeIdx = rest.indexOf(closer, 3);
                    if (closeIdx >= 0) {
                        int endIdx = closeIdx + closer.length();
                        current.append(rest, 0, endIdx);
                        result.add(current.toString().trim());
                        current.setLength(0);
                        i += endIdx;
                        if (i < len && text.charAt(i) == ' ') i++;
                        continue;
                    }
                    // No closing found — fall through to normal processing
                }
            }

            current.append(c);

            // ── Track square bracket depth ───────────────────────────────────
            // Skip all breaking rules inside [...] — these contain AsciiDoc
            // macro arguments (indexterm, link, image, xref, etc.) where
            // commas and punctuation are syntax, not prose boundaries.
            if (c == '[') {
                bracketDepth++;
                i++;
                continue;
            }
            if (c == ']' && bracketDepth > 0) {
                bracketDepth--;
                i++;
                continue;
            }
            if (bracketDepth > 0) {
                i++;
                continue;
            }

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

            // ── Em-dash (Unicode \u2014) ──────────────────────────────────────
            if (!sentencesOnly
                    && c == '\u2014'
                    && i + 1 < len
                    && text.charAt(i + 1) == ' ') {

                result.add(current.toString().trim());
                current.setLength(0);
                i += 2; // skip past the space
                continue;
            }

            // ── Em-dash (AsciiDoc: space-dash-dash-space) ────────────────────
            if (!sentencesOnly
                    && c == '-'
                    && i + 2 < len
                    && text.charAt(i + 1) == '-'
                    && text.charAt(i + 2) == ' '
                    && current.length() >= 2
                    && current.charAt(current.length() - 2) == ' ') {

                current.append('-'); // append second dash
                result.add(current.toString().trim());
                current.setLength(0);
                i += 3; // skip past second dash + space
                continue;
            }

            // ── Semicolon ────────────────────────────────────────────────────
            if (!sentencesOnly
                    && c == ';'
                    && i + 2 < len
                    && text.charAt(i + 1) == ' ') {

                result.add(current.toString().trim());
                current.setLength(0);
                i += 2;
                continue;
            }

            // ── Colon (guarded) ──────────────────────────────────────────────
            if (!sentencesOnly
                    && c == ':'
                    && i + 2 < len
                    && text.charAt(i + 1) == ' '
                    && text.charAt(i + 2) != '/') { // not URL scheme (://)

                // Guard: preceded by digit → likely time (10:30)
                boolean isTime = current.length() >= 2
                        && Character.isDigit(current.charAt(current.length() - 2));

                // Guard: preceded by colon → AsciiDoc definition list (Term::)
                boolean isDefList = current.length() >= 2
                        && current.charAt(current.length() - 2) == ':';

                // Guard: preceded by URL scheme
                String accumulated = current.toString();
                boolean isUrl = accumulated.endsWith("http:")
                        || accumulated.endsWith("https:")
                        || accumulated.endsWith("ftp:")
                        || accumulated.endsWith("mailto:");

                if (!isTime && !isDefList && !isUrl) {
                    result.add(current.toString().trim());
                    current.setLength(0);
                    i += 2;
                    continue;
                }
            }

            // ── Comma + conjunction ──────────────────────────────────────────
            if (!sentencesOnly
                    && c == ','
                    && i + 2 < len
                    && text.charAt(i + 1) == ' ') {

                String rest = text.substring(i + 2);
                if (startsWithConjunction(rest)) {
                    result.add(current.toString().trim());
                    current.setLength(0);
                    i += 2; // conjunction starts the new line
                    continue;
                }
            }

            // ── Simple comma/semicolon clause breaks (threshold-gated) ───────
            if (clauseBreak
                    && c == ','
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

    // ── Soft Wrap ──────────────────────────────────────────────────────────────

    /**
     * Wrap lines that exceed {@code maxLineLength} at the last word boundary
     * before the limit.  This is a post-processing pass applied after semantic
     * breaks, ensuring that long single-clause sentences don't produce
     * excessively wide lines in source.
     * <p>
     * Guards:
     * <ul>
     *   <li>Never breaks inside square brackets (macro arguments).</li>
     *   <li>Skips a break if the remainder would be shorter than
     *       {@code minRemainder} characters (avoids orphan words).</li>
     * </ul>
     */
    private List<String> softWrap(List<String> lines) {
        if (maxLineLength == Integer.MAX_VALUE) {
            return lines;
        }
        List<String> wrapped = new ArrayList<>();
        for (String line : lines) {
            if (line.length() <= maxLineLength) {
                wrapped.add(line);
                continue;
            }
            String remaining = line;
            while (remaining.length() > maxLineLength) {
                int breakAt = findSoftBreak(remaining);
                if (breakAt < 0) {
                    break; // no valid break point, keep as-is
                }
                String remainder = remaining.substring(breakAt + 1);
                // Skip break if remainder is too short (orphan guard)
                if (remainder.length() < minRemainder) {
                    break;
                }
                wrapped.add(remaining.substring(0, breakAt));
                remaining = remainder;
            }
            if (!remaining.isEmpty()) {
                wrapped.add(remaining);
            }
        }
        return wrapped;
    }

    // ── Merge Short Lines ────────────────────────────────────────────────────────

    /**
     * Merge lines shorter than {@code minLineLength} with the following line.
     * This prevents orphan words or very short fragments (like "A" or "The")
     * from sitting alone on a line before a macro or long phrase.
     * <p>
     * Lines that are own-line macros (indexterm, footnote, (((…))) are never
     * merged — they are intentionally on their own line.
     */
    private List<String> mergeShortLines(List<String> lines) {
        if (lines.size() <= 1) {
            return lines;
        }
        List<String> merged = new ArrayList<>();
        int i = 0;
        while (i < lines.size()) {
            String line = lines.get(i);
            // Merge forward if this line is short, not the last line,
            // and neither this line nor the next is an own-line macro
            if (line.length() < minLineLength
                    && i + 1 < lines.size()
                    && !isOwnLineMacro(line)
                    && !isOwnLineMacro(lines.get(i + 1))) {
                merged.add(line + " " + lines.get(i + 1));
                i += 2;
            } else {
                merged.add(line);
                i++;
            }
        }
        return merged;
    }

    /**
     * Check whether a line is an own-line macro that should not be merged.
     */
    private static boolean isOwnLineMacro(String line) {
        for (String macro : OWN_LINE_MACROS) {
            if (line.startsWith(macro.substring(0, macro.length() - 1))) {
                return true;
            }
        }
        return line.startsWith("(((");
    }

    // ── Macros whose bracket content is structured syntax (no wrapping) ────────

    private static final List<String> NO_WRAP_MACROS =
            List.of("indexterm:[", "indexterm2:[");

    /**
     * Find the best soft-wrap break point in a line: the last space at or
     * before {@code maxLineLength} that is not inside a no-wrap bracket
     * region (e.g. {@code indexterm:[…]}).  Spaces inside {@code footnote:[…]}
     * are valid break points because footnote content is prose.
     * Returns -1 if no valid break point exists.
     */
    private int findSoftBreak(String line) {
        int noWrapDepth = 0;   // depth inside no-wrap macro brackets
        int bestBreak = -1;

        int limit = Math.min(line.length(), maxLineLength);
        for (int j = 0; j < limit; j++) {
            char ch = line.charAt(j);
            if (ch == '[' && isNoWrapMacroAt(line, j)) {
                noWrapDepth++;
            } else if (ch == '[') {
                // footnote or other prose brackets — wrappable, ignore
            } else if (ch == ']' && noWrapDepth > 0) {
                noWrapDepth--;
            }
            if (ch == ' ' && noWrapDepth == 0) {
                bestBreak = j;
            }
        }

        if (bestBreak > 0) {
            return bestBreak;
        }

        // No space found before limit — find first space after limit
        // that's outside no-wrap brackets
        for (int j = limit; j < line.length(); j++) {
            char ch = line.charAt(j);
            if (ch == '[' && isNoWrapMacroAt(line, j)) {
                noWrapDepth++;
            } else if (ch == ']' && noWrapDepth > 0) {
                noWrapDepth--;
            }
            if (ch == ' ' && noWrapDepth == 0) return j;
        }

        return -1;
    }

    /**
     * Check whether the {@code '['} at position {@code bracketPos} is the
     * opening bracket of a no-wrap macro (indexterm, indexterm2).
     */
    private static boolean isNoWrapMacroAt(String line, int bracketPos) {
        for (String macro : NO_WRAP_MACROS) {
            int start = bracketPos - (macro.length() - 1); // macro ends with '['
            if (start >= 0 && line.startsWith(macro, start)) {
                return true;
            }
        }
        return false;
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

    // ── Conjunction Detection ────────────────────────────────────────────────────

    /**
     * Check if the given text starts with a coordinating conjunction followed by
     * a space (e.g., "and ", "but ", "or ").
     */
    private static boolean startsWithConjunction(String text) {
        for (String conj : CONJUNCTIONS) {
            if (text.startsWith(conj)) return true;
        }
        return false;
    }

    // ── Usage ───────────────────────────────────────────────────────────────────

    private void printUsage() {
        System.err.println("""
                Usage: semantic-linebreak [options] <file.adoc>

                Reformats AsciiDoc prose paragraphs to use semantic linefeeds.
                Breaks lines at logical boundaries: sentences, em-dashes, semicolons,
                colons, and comma+conjunction joints. Only paragraph blocks are
                modified; listings, diagrams, tables, and all other block types are
                preserved unchanged.

                Options:
                  -o, --output <file>     Write to file (default: in-place)
                  -n, --dry-run           Print result to stdout, don't modify files
                  -v, --verbose           Show which paragraphs are being reformatted
                  --sentences-only        Break only at sentence boundaries (. ? !)
                  --clause-break          Also break on simple commas (default: on)
                  --clause-threshold <n>  Min line length before comma break (default: 0)
                  --max-line-length <n>   Soft-wrap long lines at word boundary (default: 64)
                  --min-remainder <n>     Skip wrap if remainder < n chars (default: 15)
                  --min-line-length <n>   Merge short lines with next line (default: 10)
                  --no-wrap               Disable soft wrapping
                  -h, --help              Show this message

                Examples:
                  semantic-linebreak doc.adoc                     # semantic linefeeds
                  semantic-linebreak -n doc.adoc                  # preview to stdout
                  semantic-linebreak --sentences-only doc.adoc    # sentences only
                  semantic-linebreak --max-line-length 100 doc.adoc  # wider soft wrap
                  semantic-linebreak -o reformatted.adoc doc.adoc # write to new file
                """);
    }
}
