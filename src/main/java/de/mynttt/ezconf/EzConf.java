package de.mynttt.ezconf;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import de.mynttt.ezconf.implementation.DefaultConfigurationFactory;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * The EZConf parser.
 * <p>
 * Syntax:<p>
 * <code>GROUP_IDENTIFIER</code>: <p>
 *  - Declared groups can have key:value pairs and child groups.<br>
 *  - <code>GROUP_IDENTIFIER</code> can be nested.<br>
 *  - There can't be two <code>GROUP_IDENTIFIER</code> with the same name in the same scope.<br>
 *  - <code>GROUP_IDENTIFIER</code> can only consist out of digits and letters.
 *  - <code>GROUP_IDENTIFIER</code> cannot be multiline and whitespace will be skipped. (.i.e. T OM {} =&gt; TOM)
 * <p>
 * <code>KEY</code>: <p>
 *  - Can contain whitespace, will be trimmed.<br>
 *  - Can't be multiline. <br>
 *  - Can't be blank.<br>
 *  - Must be unique for the given scope.<br>
 * <p>
 * <code>VALUE</code>: <p>
 * - Can contain whitespace, can be multiline.<br>
 * - Will be trimmed at every line so '  t  ' will be parsed as 't'.<br>
 * - Can't be blank.<br>
 * - Can have literals: i.e <code>value: "this is a literal! this \" must be escaped! I must end with ; after I am closed";</code><br>
 * - To use a literal the first non-whitespace character must be the literal character<br>
 * - If literals are not being used they must be escaped as \".<br>
 * - Literals can also be multiline<br>
 * <p>
 * Special characters: <code>:, ;, {, }, #</code> must be escaped with <code>\</code> when not in a literal.<br>
 * When using a literal only <code>"</code> must be escaped!.<br>
 * <code>\</code> must also be escaped.<br>
 * <code>#</code> is the comment character.
 * 
 * <p>Null values don't exist.</p>
 * 
 * <pre>
 * GROUP_IDENTIFIER {
 *     KEY: VALUE;
 *     KEY: VALUE;
 *     ...
 *     
 *     #I'm a comment
 *     
 *     KEY: \{\}\#\:\;\\
 *          escaped chars and multiline value;
 *     
 *     KEY: "A literal! \"
 *           Can also be multiline!";
 *     
 *     GROUP_IDENTIFIER {
 *         ....
 *     }
 * }
 * 
 * GROUP_IDENTIFIER {
 *     ...
 * }
 * 
 * 
 * </pre>
 * 
 * @author mynttt
 *
 */
public final class EzConf {
    private static final String INDENT = "    ";
    private static final char BEGIN_GROUP = '{', 
                        END_GROUP = '}',
                        KV_SEPERATOR = ':',
                        ESCAPE = '\\',
                        COMMENT = '#',
                        V_END = ';',
                        LITERAL = '"';
    
    private static final Map<Character, String> SHOULD_ESCAPE_KEY = Map.of(
            BEGIN_GROUP, "\\" + BEGIN_GROUP,
            END_GROUP, "\\" + END_GROUP,
            KV_SEPERATOR, "\\" + KV_SEPERATOR,
            ESCAPE, "\\" + ESCAPE,
            COMMENT, "\\" + COMMENT,
            V_END, "\\" + V_END
            );
    
    private static final Map<Character, String> SHOULD_ESCAPE_VALUE = new HashMap<>(SHOULD_ESCAPE_KEY);
    
    static {
        SHOULD_ESCAPE_VALUE.put(LITERAL, "\\" + LITERAL);
    }
    
    private static final EscapeHandler 
            ESC_LITERAL = (sb, buf, s) -> sb.append(LITERAL)
                                            .append(escaped(Map.of('"', "\\\""), s, buf))
                                            .append(LITERAL),
            ESC_NO_LITERAL = (sb, buf, s) -> sb.append(escaped(SHOULD_ESCAPE_VALUE, s, buf));
    
    private static final EzConf DEFAULT = new EzConf(new DefaultConfigurationFactory());
    
    private final ConfigurationFactory factory;
    private final EnumSet<ParserFlags> flags = EnumSet.noneOf(ParserFlags.class);
    private final EscapeHandler handler;
    
    private enum State {
        IN_GROUP, IN_VALUE, IN_LITERAL, UNDEFINED;
    }
    
    /**
     * Flags allow to alter the parsing/dumping behavior.
     * 
     * @author mynttt
     *
     */
    public enum ParserFlags {
        
        /**
         * Force the dumper to not use literals.<br>
         */
        DUMP_ESCAPE_INSTEAD_OF_LITERAL;
    }
                        
    private EzConf(ConfigurationFactory factory, ParserFlags...flags) {
        this.factory = factory;
        if(flags != null)
            this.flags.addAll(Arrays.stream(flags).peek(Objects::requireNonNull).collect(Collectors.toSet()));
        this.handler = this.flags.contains(ParserFlags.DUMP_ESCAPE_INSTEAD_OF_LITERAL) ? ESC_NO_LITERAL : ESC_LITERAL;
    }
    
    /**
     * Default parser.
     * @return a parser with the {@link DefaultConfigurationFactory} factory.
     */
    public static EzConf defaultParser() {
        return DEFAULT;
    }
    
    /**
     * Default parser with specified flags.
     * @param flags flags to set with the parser.
     * @return a parser with the specific flags set.
     */
    public static EzConf defaultParser(ParserFlags... flags) {
        return new EzConf(DEFAULT.factory, flags);
    }
    
    /**
     * Create a parser with own implementation.
     * @param factory to create {@link Configuration} and {@link ConfigurationGroup}
     * @param flags to set with the parser.
     * @return parser with custom factory.
     */
    public static EzConf parser(ConfigurationFactory factory, ParserFlags... flags) {
        Objects.requireNonNull(factory, "factory must not be null");
        return new EzConf(factory, flags);
    }
    
    /**
     * Parses a string.
     * @param input to parse.
     * @return parsed {@link Configuration}
     * @throws IllegalStateException on syntax error.
     * @throws IllegalArgumentException on invariant violation.
     */
    public Configuration parse(String input) {
        Objects.requireNonNull(input, "input must not be null");
        return parseInternal(new InputStreamReader(new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8)), StandardCharsets.UTF_8));
    }
    
    /**
     * Parses a resource from a path with UTF_8 encoding.
     * @param input to parse.
     * @return parsed {@link Configuration}
     * @throws IOException if reading fails.
     * @throws IllegalStateException on syntax error.
     * @throws IllegalArgumentException on invariant violation.
     */
    public Configuration parse(Path input) throws IOException {
        return parse(input, StandardCharsets.UTF_8);
    }
    
    /**
     * Parses a resource from a path with a custom charset.
     * @param input to parse.
     * @param charset charset to use.
     * @return parsed {@link Configuration}
     * @throws IOException if reading fails.
     * @throws IllegalStateException on syntax error.
     * @throws IllegalArgumentException on invariant violation.
     */
    public Configuration parse(Path input, Charset charset) throws IOException {
        Objects.requireNonNull(input, "input must not be null");
        Objects.requireNonNull(charset, "charset must not be null");
        return parseInternal(new InputStreamReader(Files.newInputStream(input), charset));
    }
    
    /**
     * Parses an InputStream with UTF_8 encoding.
     * @param stream to parse.
     * @return parsed {@link Configuration}
     * @throws IllegalStateException on syntax error.
     * @throws IllegalArgumentException on invariant violation.
     */
    public Configuration parse(InputStream stream) {
        return parse(stream, StandardCharsets.UTF_8);
    }
    
    /**
     * Parses an InputStream with a custom charset.
     * @param stream to parse.
     * @param charset charset to use.
     * @return parsed {@link Configuration}
     * @throws IllegalStateException on syntax error.
     * @throws IllegalArgumentException on invariant violation.
     */
    public Configuration parse(InputStream stream, Charset charset) {
        Objects.requireNonNull(stream, "stream must not be null");
        Objects.requireNonNull(charset, "charset must not be null");
        return parseInternal(new InputStreamReader(stream, charset));
    }
    
    @SuppressFBWarnings("REC_CATCH_EXCEPTION")
    private Configuration parseInternal(InputStreamReader in) {
        State state = State.UNDEFINED;
        String lineBuffer;
        StringBuilder buffer = new StringBuilder(1000);
        
        Configuration conf = factory.newConfiguration();
        Stack<ConfigurationGroup> groups = new Stack<>();
        Stack<String> paths = new Stack<String>();
        
        String currentKey = null;

        int line = 0;
        int newLineLiteralCounter = -1;
        
        try(BufferedReader r = new BufferedReader(in)) {
            while((lineBuffer = r.readLine()) != null) {
                lineBuffer = lineBuffer.trim();
                
                // Carry over newlines in certain states
                if(state == State.IN_VALUE || state == State.UNDEFINED || state == State.IN_LITERAL) {
                    buffer.append('\n');
                    newLineLiteralCounter++;
                }
                
                for(int i = 0; i < lineBuffer.length(); i++) {
                    char c = lineBuffer.charAt(i);
                    buffer.append(c);
                    
                    // Break on comment
                    if(c == COMMENT && state != State.IN_LITERAL) {
                        buffer.deleteCharAt(buffer.length()-1);
                    //Backtrack on whitespace
                        String tmp = buffer.toString();
                        for(int j = tmp.length()-1; j>=0;  j--) {
                            if(!Character.isWhitespace(tmp.charAt(j)))
                                break;
                            buffer.deleteCharAt(j);
                        }
                        break;
                    }
                    
                    switch(state) {
                    case IN_GROUP:
                        boolean _break = false;
                        
                        switch(c) {
                            case ESCAPE:
                                buffer.deleteCharAt(buffer.length()-1);
                                buffer.append(lineBuffer.charAt(++i));
                                _break = true;
                                break;
                        
                            case KV_SEPERATOR:
                                buffer.deleteCharAt(buffer.length()-1);
                                currentKey = buffer.toString().trim();
                                if(currentKey.isEmpty())
                                    throw new IllegalStateException(error("Keys are not permitted to be blank.", line, i));
                                buffer.setLength(0);
                                state = State.IN_VALUE;
                                _break = true;
                                break;
                        
                            case BEGIN_GROUP:
                                buffer.deleteCharAt(buffer.length()-1);
                                String path = buffer.toString().replaceAll("\\s+","");
                                for(int k = 0; k < path.length(); k++) {
                                    char ch = path.charAt(k);
                                    if(!(Character.isDigit(ch) || Character.isLetter(ch)))
                                        throw new IllegalStateException(error("BEGIN_GROUP identifer must be DIGIT or NUMBER: '" + buffer.toString() + "'", line, i));
                                }
                                buffer.setLength(0);
                                paths.push(path);
                                ConfigurationGroup g = factory.newConfigurationGroup(createPath(paths));
                                groups.peek().addChild(g);
                                groups.push(g);
                                _break = true;
                                break;
                        
                            case END_GROUP:
                                buffer.setLength(0);
                                paths.pop();
                                conf.addGroup(groups.peek().getPath(), groups.pop());
                                state = groups.isEmpty() ? State.UNDEFINED : State.IN_GROUP;
                                _break = true;
                                break;
                                
                            default:
                                break;
                        }
                        
                        if(_break)
                            break;
                        
                        // Detect multiline
                        if(i == lineBuffer.length()-1)
                            throw new IllegalStateException(error("BEGIN_GROUP or KEY cannot be multiline: '" + buffer.toString() + "'", line, i));
                        
                        break;
                    case IN_VALUE:
                        
                        switch(c) {
                            case ESCAPE:
                                buffer.deleteCharAt(buffer.length()-1);
                                buffer.append(lineBuffer.charAt(++i));
                                _break = true;
                                break;
                            case V_END:
                                buffer.deleteCharAt(buffer.length()-1);
                                String currentValue = buffer.toString().trim();
                                if(currentValue.isBlank())
                                    throw new IllegalStateException(error("Values are not permitted to be blank.", line, i));
                                buffer.setLength(0);
                                groups.peek().addKeyValue(currentKey, currentValue);
                                state = State.IN_GROUP;
                                break;
                            case LITERAL:
                                String candidate = buffer.toString();
                                candidate = candidate.substring(0, candidate.length()-1);
                                if(!candidate.isBlank())
                                    throw new IllegalStateException(error("Cannot transition to LITERAL with non-literal value already started.", line, i));
                                buffer.deleteCharAt(buffer.length()-1);
                                state = State.IN_LITERAL;
                                break;
                            default:
                        }
                        
                        break;
                    case IN_LITERAL:
                        
                        if(c != LITERAL)
                            break;

                        if(i > 0 && lineBuffer.charAt(i-1) == ESCAPE) {
                            buffer.setCharAt(buffer.length()-2, c);
                            buffer.setLength(buffer.length()-1);
                        } else {
                            buffer.deleteCharAt(buffer.length()-1);
                            String currentValue = buffer.toString().trim();
                            if(currentValue.isBlank())
                                throw new IllegalStateException(error("Values are not permitted to be blank.", line, i));
                            if(newLineLiteralCounter > 0)
                                currentValue = currentValue.lines().map(String::trim).collect(Collectors.joining("\n"));
                            buffer.setLength(0);
                            groups.peek().addKeyValue(currentKey, currentValue);
                            if(i >= lineBuffer.length()-1 || !(lineBuffer.charAt(i+1) == V_END))
                                throw new IllegalStateException(error("Closing literal must be followed by V_END! (Unescaped literal in value?)", line, i));
                            i++;
                            state = State.IN_GROUP;
                            newLineLiteralCounter = -1;
                        }
                        
                        break;
                    case UNDEFINED:
                        
                        // Begin group parsing
                        if(c == BEGIN_GROUP) {
                            buffer.deleteCharAt(buffer.length()-1);
                            String path = buffer.toString().trim();
                            if(path.isEmpty())
                                throw new IllegalStateException(error("Group identifier are not permitted to be blank.", line, i));
                            buffer.setLength(0);
                            groups.push(factory.newConfigurationGroup(path));
                            paths.push(path);
                            state = State.IN_GROUP;
                            break;
                        }
                        
                        // Skip whitespace
                        if(Character.isWhitespace(c)) {
                            buffer.deleteCharAt(buffer.length()-1);
                            continue;
                        }
                        
                        // End group in UNDEFINED
                        if(c == END_GROUP)
                            throw new IllegalStateException(error("Cannot END_GROUP when in UNDEFINED state.", line, i));
                        
                        // Invalid character
                        if(!(Character.isDigit(c) || Character.isLetter(c)))
                            throw new IllegalStateException(error("BEGIN_GROUP identifer must be DIGIT or NUMBER: '" + buffer.toString() + "'", line, i));
                        
                        //Detect newlines
                        if(i == lineBuffer.length()-1)
                            throw new IllegalStateException(error("BEGIN_GROUP cannot be multiline: '" + buffer.toString() + "'", line, i));
                        break;
                    default:
                        throw new AssertionError("invalid parser state: " + state);
                    }
                }
                line++;
            }
        } catch (Exception e) {
            throw rethrow(e);
        }
        
        if(state == State.IN_LITERAL)
            throw new IllegalStateException("Invalid EZConf! String literal has not been closed. Document end reached in state IN_LITERAL should be UNDEFINED.");
        
        if(state != State.UNDEFINED)
            throw new IllegalStateException("Invalid EZConf! Document end reached in state '" + state + "' should be UNDEFINED.");
        
        return conf;
    }
    
    private String createPath(Stack<String> paths) {
        return String.join(".", paths);
    }

    private static String error(String msg, int line, int posInLine) {
        return String.format("PARSE_ERROR: Line=%d, PosInLine=%d, Msg=%s", line, posInLine, msg);
    }
    
    /**
     * Dumps a {@link Configuration} in compressed form with UTF-8 encoding.
     * @param destination to dump the files.
     * @param configuration to dump.
     * @throws IOException if writing fails.
     */
    public void dump(Path destination, Configuration configuration) throws IOException {
        Objects.requireNonNull(destination, "destination must not be null");
        Files.write(destination, dumpInternal(configuration, false).getBytes(StandardCharsets.UTF_8));
    }
    
    /**
     * Dumps a {@link Configuration} in human readable form with UTF-8 encoding.
     * @param destination to dump the files.
     * @param configuration to dump.
     * @throws IOException if writing fails.
     */
    public void dumpPretty(Path destination, Configuration configuration) throws IOException {
        Files.write(destination, dumpInternal(configuration, true).getBytes(StandardCharsets.UTF_8));
    }
    
    /**
     * Creates a compressed representation of the {@link Configuration}.
     * @param configuration to dump.
     * @return clear text serialized form.
     */
    public String dump(Configuration configuration) {
        return dumpInternal(configuration, false);
    }
    
    /**
     * Creates a human readable representation of the {@link Configuration}.
     * @param configuration to dump.
     * @return clear text serialized form.
     */
    public String dumpPretty(Configuration configuration) {
        return dumpInternal(configuration, true);
    }
    
    private String dumpInternal(Configuration configuration, boolean pretty) {
        Objects.requireNonNull(configuration, "configuration must not be null");
        StringBuilder sb = new StringBuilder(500);
        if(pretty) {
            dumpRecursivePretty(configuration.getRootGroups(), 0, 0, sb, new StringBuilder(100));
        } else {
            dumpRecursive(configuration.getRootGroups(), 0, sb, new StringBuilder(100));
        }
        return sb.toString().trim();
    }
    
    private static final String escaped(Map<Character, String> mapping, String toEscape, StringBuilder escapeBuffer) {
        for(int i = 0; i < toEscape.length(); i++) {
            char c = toEscape.charAt(i);
            String esc = mapping.get(c);
            escapeBuffer.append(esc == null ? c : esc);
        }
        String s = escapeBuffer.toString();
        escapeBuffer.setLength(0);
        return s;
    }
    
    private void dumpRecursive(List<ConfigurationGroup> groups, int substIdx, StringBuilder sb, StringBuilder escape) {
        for(ConfigurationGroup g : groups) {
            sb.append(g.getPath().substring(substIdx));
            sb.append("{");
            for(Map.Entry<String, String> e : g) {
                sb.append(escaped(SHOULD_ESCAPE_KEY, e.getKey(), escape))
                .append(":");
                handler.escape(sb, escape, e.getValue());
                sb.append(";");
            }
            List<ConfigurationGroup> children = g.getChildren();
            if(!children.isEmpty()) {
                dumpRecursive(children, g.getPath().length()+1, sb, escape);
            }
            sb.append("}");
        }
    }

    private void dumpRecursivePretty(List<ConfigurationGroup> groups, int substIdx, int indent, StringBuilder sb, StringBuilder escaped) {
        String indentStr = INDENT.repeat(indent);
        String indentStrInternal = INDENT.repeat(indent+1);
        for(ConfigurationGroup g : groups) {
            sb.append("\n");
            sb.append(indentStr).append(g.getPath().substring(substIdx));
            sb.append(" {").append(!g.getKeys().isEmpty() ? "\n" : "");
            for(Map.Entry<String, String> e : g) {
                String value;
                String key = escaped(SHOULD_ESCAPE_KEY, e.getKey(), escaped);
                if(e.getValue().contains("\n")) {
                    // Pretty print multiline
                    String indentMultiline = " ".repeat(indentStrInternal.length() + key.length() + 2);
                    value = e.getValue().replaceAll("\r\n", "\n");
                    value = value.replaceAll("\n", "\n"+indentMultiline);
                } else {
                    // Normal print
                    value = e.getValue();
                }
                sb.append(indentStrInternal)
                .append(key)
                .append(": ");
                handler.escape(sb, escaped, value);
                sb.append(";")
                .append("\n");
            }
            List<ConfigurationGroup> children = g.getChildren();
            if(!children.isEmpty()) {
                dumpRecursivePretty(children, g.getPath().length()+1, indent+1, sb, escaped);
            }
            sb.append(indentStr).append("}\n");
        }
    }
    
    @SuppressWarnings("unchecked")
    private static <T extends Throwable> RuntimeException rethrow(Throwable t) throws T {
        throw (T) t;
    }
}

@FunctionalInterface
interface EscapeHandler {
    void escape(StringBuilder out, StringBuilder buf, String in);
}