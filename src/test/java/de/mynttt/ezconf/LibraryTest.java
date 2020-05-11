package de.mynttt.ezconf;

import static de.mynttt.ezconf.ConfigurationBuilder.newDefaultInstance;
import static de.mynttt.ezconf.EzConf.defaultParser;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import de.mynttt.ezconf.EzConf.ParserFlags;
import de.mynttt.ezconf.implementation.DefaultConfigurationFactory;

class LibraryTest {
    
    private static void assertContains(String sub, String full) {
        if(sub == null && full == null)
            return;
        if(full == null ^ sub == null)
            fail("AssertContains failed (sub or full null): sub=<"+sub+"> | full=<"+full+">");
        if(!full.contains(sub))
            fail("AssertContains failed (sub should be in full): Substring: <"+sub+"> | Full: <"+full+">");
    }
    
    private static void assertThrowsContains(Class<? extends Throwable> ex, Executable x, String contains) {
        var exc = assertThrows(ex, x);
        assertContains(contains, exc.getMessage());
    }

    private static Configuration p(String s) {
        return defaultParser().parse(s);
    }
    
    private static void illg(Executable x, String contains) {
        var ex = assertThrows(IllegalStateException.class, x);
        assertContains(contains, ex.getMessage());
    }
    
    @AfterAll static void tearDown() throws IOException {
        Files.deleteIfExists(Paths.get("_tpd1"));
        Files.deleteIfExists(Paths.get("_tpd2"));
    }
    
    @Test void passGroupLegalCharacter() {
        assertTrue(p("test12345ABC {}").groupExists("test12345ABC"));
    }
    
    @Test void failGroupIllegalCharacter() {
        illg(() -> p("test_?!-{"), "BEGIN_GROUP identifer must be DIGIT or NUMBER:");
    }
    
    @Test void failGroupIllegalClose() {
        illg(() -> p("test}"), "Cannot END_GROUP when in UNDEFINED state.");
    }
    
    @Test void failGroupSameIdentifier() {
        assertThrowsContains(IllegalArgumentException.class, () -> p("test {} test{}"), "Path already exists.");
    }
    
    @Test void failGroupMultilineIdentifier() {
        illg(() -> p("te\nst{}"), "BEGIN_GROUP cannot be multiline:");
    }
    
    @Test public void parseFlat() {
        Configuration c = defaultParser().parse(LibraryTest.class.getResourceAsStream("/FlatConfiguration.ez"));
        assertTrue(c.groupExists("all"));
        assertTrue(c.getGroup("all").keyExists("key"));
        assertEquals(c.getGroup("all").getValue("key"), "value");
        assertTrue(c.groupExists("31"));
        assertTrue(c.getGroup("31").keyExists("4"));
        assertEquals(c.getGroup("31").getValue("4"), "5");
        assertTrue(c.groupExists("22"));
        assertTrue(c.getGroup("22").keyExists("{"));
        assertEquals(c.getGroup("22").getValue("{"), "???");
    }
    
    @Test public void parseMultilineValue() {
        Configuration c = defaultParser().parse(LibraryTest.class.getResourceAsStream("/MultilineKV.ez"));
        assertEquals("value\nis\na;\nmultiline\nvalue", c.getGroup("all").getValue("key"));
    }
    
    @Test public void failOnMultilineKey() {
       illg(() -> p("test { ke\ny: value; }"), "BEGIN_GROUP or KEY cannot be multiline:");
    }
    
    @Test public void failIncompleteGroup() {
        illg(() -> p("test {"), "Invalid EZConf! Document end reached in state 'IN_GROUP' should be UNDEFINED.");
    }
    
    @Test public void failIncompleteKeyValue() {
        illg(() -> p("test { test"), "BEGIN_GROUP or KEY cannot be multiline");
    }
    
    @Test public void failIncompleteKeyValue2() {
        illg(() -> p("test { test: test"), " Document end reached in state 'IN_VALUE' should be UNDEFINED.");
    }
    
    @Test public void failIncompleteKeyValue3() {
        illg(() -> p("test { test: test; "), "Document end reached in state 'IN_GROUP' should be UNDEFINED.");
    }
    
    @Test public void failOnNestedGroup()  {
        illg(() -> p("test { tes.t { t: a; }}"), "BEGIN_GROUP identifer must be DIGIT or NUMBER:");
    }
    
    @Test public void shouldEscapeValueProberly() {
        assertEquals("#{}:;\\",  p("test { key: \\#\\{\\}\\:\\;\\\\;}").getValue("test#key"));
    }
    
    @Test public void shouldEscapeKeyProperly() {
        assertTrue(p("test { \\#\\{\\}\\:\\;\\\\: value;}").getGroup("test").keyExists("#{}:;\\"));
    }
    
    @Test public void shouldFailOnEscapedNestedGroupIdentifier() {
        illg(() -> p("test{ \\#\\{\\}\\:\\;\\\\{ key: value;}}"), "BEGIN_GROUP identifer must be DIGIT or NUMBER:");
    }
    
    @Test public void shouldFailOnEscapedGroupIdentifier() {
        illg(() -> p("\\#\\{\\}\\:\\;\\\\{ { key: value;}}"), "BEGIN_GROUP identifer must be DIGIT or NUMBER");
    }
    
    @Test public void parseKeyValue() {
        assertEquals("test", p("test { test: test; }").getGroup("test").getValue("test"));
    }
    
    @Test public void findValue() {
        assertTrue(p("test { test: test; }").getGroup("test").findValue("test").isPresent());
    }
    
    @Test public void findGroup() {
        assertTrue(p("tty { abc { 139d {}}}").findGroup("tty.abc.139d").isPresent());
    }
    
    @Test public void parseNested() {
        Configuration c = defaultParser().parse(LibraryTest.class.getResourceAsStream("/Nested.ez"));
        assertTrue(c.groupExists("level1"));
        assertTrue(c.groupExists("level1.level2"));
        assertTrue(c.groupExists("level1.level3"));
        assertTrue(c.groupExists("level1.level4"));
        assertTrue(c.groupExists("level1.level4.level4a"));
        assertEquals("exists", c.getGroup("level1").getValue("main"));
        assertEquals("exists", c.getGroup("level1").getValue("main_here"));
        assertEquals("exists", c.getGroup("level1.level2").getValue("key2"));
        assertEquals("exists", c.getGroup("level1.level3").getValue("key3"));
        assertEquals("exists\nis\nmultiline", c.getGroup("level1.level4.level4a").getValue("key4a"));
    }
    
    @Test public void testPrettyNoLiteral() throws IOException, URISyntaxException {
        var p = EzConf.defaultParser(ParserFlags.DUMP_ESCAPE_INSTEAD_OF_LITERAL);
        Configuration c = defaultParser().parse(Paths.get(LibraryTest.class.getResource("/Nested.ez").toURI()));
        assertEquals(Files.readString(Paths.get(LibraryTest.class.getResource("/pretty.txt").toURI()), StandardCharsets.UTF_8), p.dumpPretty(c));
        Configuration c2 = defaultParser().parse(LibraryTest.class.getResourceAsStream("/pretty.txt"));
        assertEquals(c, c2);
        assertEquals(c2, c);
    }
    
    @Test public void testNormalNoLiteral() throws IOException, URISyntaxException {
        var p = EzConf.defaultParser(ParserFlags.DUMP_ESCAPE_INSTEAD_OF_LITERAL);
        Configuration c = defaultParser().parse(LibraryTest.class.getResourceAsStream("/Nested.ez"));
        assertEquals(Files.readString(Paths.get(LibraryTest.class.getResource("/normal.txt").toURI()), StandardCharsets.UTF_8), p.dump(c));
        Configuration c2 = defaultParser().parse(LibraryTest.class.getResourceAsStream("/normal.txt"));
        assertEquals(c, c2);
        assertEquals(c2, c);
    }
    
    @Test public void testPathDumpNoLiteral() throws IOException {
        var p = EzConf.defaultParser(ParserFlags.DUMP_ESCAPE_INSTEAD_OF_LITERAL);
        Configuration c = defaultParser().parse(LibraryTest.class.getResourceAsStream("/Nested.ez"));
        String u1 = "_tpd1";
        String u2 = "_tpd2";
        p.dump(Paths.get(u1), c);
        p.dumpPretty(Paths.get(u2), c);
        Configuration c2 = defaultParser().parse(Paths.get(u1));
        Configuration c3 = defaultParser().parse(Paths.get(u2));
        assertEquals(c3, c2);
        assertEquals(c2, c3);
    }
    
    @Test public void validKeys() {
        Configuration c = EzConf.parser(new DefaultConfigurationFactory()).parse("t1{ t2{ key:1; a:b\nmultiline; c:2; d:3;}}");
        assertTrue(List.of("1", "2", "3", "b\nmultiline").containsAll(c.getGroup("t1.t2").getValues()));
    }
    
    @Test public void validValues() {
        Configuration c = EzConf.parser(new DefaultConfigurationFactory()).parse("t1{ t2{ key:value; a:b;}}");
        assertEquals(Set.of("key", "a"), c.getGroup("t1.t2").getKeys());
    }
    
    @Test public void equalsHashcodeReferenceImpl() {
        Configuration c1 = EzConf.parser(new DefaultConfigurationFactory()).parse("t1{ t2{ key:value; a:b;}}");
        Configuration c2 = EzConf.parser(new DefaultConfigurationFactory()).parse("t1{ t2{ key:value; a:b;}}");
        Configuration c3 = EzConf.parser(new DefaultConfigurationFactory()).parse("t1{ }");
        assertFalse(c1.getGroup("t1").equals(null));
        assertFalse(c1.getGroup("t1").equals(new Object()));
        assertTrue(c1.getGroup("t1").equals(c2.getGroup("t1")));
        assertTrue(c2.getGroup("t1").equals(c1.getGroup("t1")));
        assertFalse(c3.getGroup("t1").equals(c2.getGroup("t1")));
        assertTrue(c1.equals(c1));
        assertTrue(c1.getGroup("t1").equals(c1.getGroup("t1")));
        assertTrue(c1.equals(c2));
        assertTrue(c2.equals(c1));
        assertFalse(c1.equals(c3));
        assertEquals(c1.hashCode(), c2.hashCode());
        assertNotEquals(c1.hashCode(), c3.hashCode());
        assertFalse(c1.equals(null));
        assertFalse(c1.equals(new Object()));
    }
    
    @Test public void childDuplicateNested() {
        assertThrowsContains(IllegalArgumentException.class, () -> p("test { a {} b { c{} c{} }}"), "Path already exists.");
    }
    
    @Test public void failNestedMultilineKey() {
        illg(() -> p("test { test { t\nn: der; }}"), "BEGIN_GROUP or KEY cannot be multiline");
    }
    
    @Test public void spaceStaysInKey() {
        assertTrue(p("test { test { a  b: test; }}").getGroup("test.test").keyExists("a  b"));
    }
    
    @Test public void skipNestedSpaceInGroup() {
        assertTrue(p("test { te st { ab: test; }}").groupExists("test.test"));
    }
    
    @Test public void failDuplicateNestedGroup() {
        assertThrowsContains(IllegalArgumentException.class, () -> p("test { a{} b{} a{}}"), "Path already exists.");
    }
    
    @Test public void parsesChildren() {
        assertEquals(3, p("test{ a {}b{}c{}}").getGroup("test").getChildren().size());
    }
    
    @Test public void failMalformedQuery1() {
        assertThrowsContains(IllegalArgumentException.class, () -> p("test{}").findValue("#test"), "Queries cannot start with"); 
    }
    
    @Test public void failMalformedQuery2() {
        assertThrowsContains(IllegalArgumentException.class, () -> p("test{}").findValue("test"), "Queries must be in this format:");
    }
    
    @Test public void duplicateKey() {
        assertThrowsContains(IllegalArgumentException.class, () -> p("test{a:k; a:k;}"), "is already present.");
    }
    
    @Test public void failQueryNoGroup() {
        assertThrowsContains(NoSuchElementException.class, () -> p("test{}").findValue("tes#val"), "does not exist.");
    }
    
    @Test public void successQuery() {
        assertEquals("success", p("test { val: success; }").getValue("test#val"));
    }
    
    @Test public void successQuery2() {
        assertTrue(p("test { val: success; }").findValue("test#val").isPresent());
    }
    
    @Test public void failQuery() {
        assertEquals(null, p("test { val: success; }").getValue("test#vall"));
    }
    
    @Test public void shouldBuildEmptyConfig() {
        assertTrue(newDefaultInstance().build().getGroupCount() == 0);
    }
    
    @Test public void shouldBuildDoubleRootConfig() {
        Configuration c = newDefaultInstance()
                .addRoot("root1")
                    .put("key", "value")
                .endRoot()
                .addRoot("root2")
                    .put("value", "###key")
                .endRoot()
                .build();
        
        assertTrue(c.getRootGroupCount() == 2);
        assertTrue(c.getGroupCount() == 2);
        assertTrue(c.groupExists("root1"));
        assertTrue(c.groupExists("root2"));
        assertTrue("value".equals(c.getValue("root1#key")));
        assertTrue("###key".equals(c.getValue("root2#value")));
    }
    
    @Test public void shouldMatchNested() {
        Configuration c = defaultParser().parse(LibraryTest.class.getResourceAsStream("/Nested.ez"));
        Configuration c1 = newDefaultInstance()
                .addRoot("level1")
                    .put("main", "exists")
                    .put("main_here", "exists")
                    .addChild("level2")
                        .put("key2", "exists")
                    .endChild()
                    .addChild("level3")
                        .put("key3", "exists")
                    .endChild()
                    .addChild("level4")
                        .put("key", "value")
                        .addChild("level4a")
                            .put("key4a", "exists\nis\nmultiline")
                            .addChild("level5a")
                                .put("key", "value")
                            .endChild()
                        .endChild()
                    .endChild()
                .endRoot().build();
        assertEquals(c, c1);
        assertEquals(c1, c);
    }
    
    @Test public void shouldFailNullKey() {
        assertThrowsContains(NullPointerException.class, () -> newDefaultInstance().addRoot("t").put(null, ""), 
                "Key must be non-null.");
    }
    
    @Test public void shouldFailNullValue() {
        assertThrowsContains(NullPointerException.class, () -> newDefaultInstance().addRoot("t").put("t", null), 
                "must be non-null");
    }
    
    @Test public void shouldFailDuplicateKey() {
        assertThrowsContains(IllegalArgumentException.class, () -> newDefaultInstance().addRoot("t").put("t", "x").put("t", "y"), 
                "is already present.");
    }
    
    @Test public void shouldFailInvalidGroupName1() {
        assertThrowsContains(IllegalArgumentException.class, () -> newDefaultInstance().addRoot(""), 
                "Group name should not be empty.");
    }
    
    @Test public void shouldFailInvalidGroupName2() {
        assertThrowsContains(IllegalArgumentException.class, () -> newDefaultInstance().addRoot("te.st_-"), 
                "Character in group name must be digit or letter.");
    }
    
    @Test public void shouldFailInvalidGroupName3() {
        assertThrowsContains(IllegalArgumentException.class, () -> newDefaultInstance().addRoot(" tes t "), 
                "Whitespace is not allowed for group name.");
    }
    
    @Test public void shouldFailDuplicateGroupRoot() {
        assertThrowsContains(IllegalArgumentException.class, () -> newDefaultInstance().addRoot("t").endRoot().addRoot("t"),
                "Path already exists.");
    }
    
    @Test public void shouldFailDuplicateGroupRootNested() {
        assertThrowsContains(IllegalArgumentException.class, () -> newDefaultInstance()
                .addRoot("t")
                .endRoot()
                .addRoot("d")
                .endRoot()
                .addRoot("tp")
                    .addChild("ttd")
                    .endChild()
                    .addChild("ttd"), "Path already exists.");
    }
    
    @Test public void shouldFailIllegalEndCallRoot() {
        illg(() -> newDefaultInstance().addRoot("t").addChild("t").endRoot(), "Cannot end root while still in child. Current path");
    }
    
    @Test public void shouldFailIllegalEndCallChild() {
        illg(() -> newDefaultInstance().addRoot("t").endChild(), "Cannot end child while in root.");
    }
    
    @Test public void dumpShouldHandleEscapedCharactersNoLiteral() {
        var p = EzConf.defaultParser(ParserFlags.DUMP_ESCAPE_INSTEAD_OF_LITERAL);
        var c1 = defaultParser().parse(p.dump(newDefaultInstance()
            .addRoot("test")
            .put("\\{};.\":#", "\\{};.\":#")
            .endRoot()
            .build()));
        assertEquals("\\{};.\":#", c1.getValue("test#\\{};.\":#"));
    }
    
    @Test public void prettyDumpShouldHandleEscapedCharactersNoLiteral() {
        var p = EzConf.defaultParser(ParserFlags.DUMP_ESCAPE_INSTEAD_OF_LITERAL);
        var c1 = defaultParser().parse(p.dumpPretty(newDefaultInstance()
                .addRoot("test")
                .put("\\{};.\":#", "\\{};.\":#")
                .endRoot()
                .build()));
            assertEquals("\\{};.\":#", c1.getValue("test#\\{};.\":#"));
    }
    
    @Test public void dumpShouldHandleEscapedCharactersLiteral() {
        var p = EzConf.defaultParser();
        var c1 = defaultParser().parse(p.dump(newDefaultInstance()
            .addRoot("test")
            .put("\\{};.\":#", "\\{};.\":#")
            .endRoot()
            .build()));
        assertEquals("\\{};.\":#", c1.getValue("test#\\{};.\":#"));
    }
    
    @Test public void prettyDumpShouldHandleEscapedCharactersLiteral() {
        var p = EzConf.defaultParser();
        var c1 = defaultParser().parse(p.dumpPretty(newDefaultInstance()
                .addRoot("test")
                .put("\\{};.\":#", "\\{};.\":#")
                .endRoot()
                .build()));
            assertEquals("\\{};.\":#", c1.getValue("test#\\{};.\":#"));
    }
    
    @Test public void doubleCloseBuilder() {
        var b = newDefaultInstance();
        b.build();
        illg(() -> b.build(), "Builder has already been closed with 'build()'."); 
    }
    
    @Test public void validateWithoutErrors() {
        var c1 = defaultParser().parse("test{} test2{} test3{test4{}}");
        var ctx = ConfigurationValidator.newInstance()
                .requireGroups("test", "test2", "test3", "test3.test4")
                .build();
        assertTrue(ctx.validate(c1).isValid());
    }
    
    @Test public void failRequireGroups() {
        assertThrowsContains(IllegalArgumentException.class, () -> ConfigurationValidator.newInstance().requireGroups(), 
                "groups must contain group identifiers");
    }
    
    @Test public void groupDoesNotExist() {
        var c1 = defaultParser().parse("test{} test2{t1{}}");
        var ctx = ConfigurationValidator.newInstance().requireGroups("test", "test2.t1", "test2.t3")
                .build();
        var res = ctx.validate(c1);
        assertEquals(1, res.getIssues().size());
        assertFalse(res.isValid());
    }
    
    @Test public void matchRecursively() {
        var c1 = defaultParser().parse("test{ k: true;} test2{} test3 { 1: false; 2: false; tse { 22: true; 34: false; 5 { x: true; } 8 {x: false; 99{22: false;}}}}");
        var ctx = ConfigurationValidator.newInstance()
                .valuesMatchRecursively(DefaultValidators.IS_BOOLEAN, "test", "test2", "test3", "test3.tse")
                .build();
        var res = ctx.validate(c1);
        assertTrue(res.isValid());
    }
    
    @Test public void matchRecursivelyFail() {
        var c1 = defaultParser().parse("test{ k: true;} test2{} test3 { 1: falsee; 2: false; tse { 22: true; 34: false; 5 { x: true; } 8 {x: false; 99{22: false;}}}}");
        var ctx = ConfigurationValidator.newInstance()
                .valuesMatchRecursively(DefaultValidators.IS_BOOLEAN, "test", "test2", "test3", "test3.tse")
                .build();
        var res = ctx.validate(c1);
        assertFalse(res.isValid());
        assertEquals(1, res.getIssues().size());
    }
    
    @Test public void matchRecursivelyFailNoGroup() {
        var c1 = defaultParser().parse("test{ k: true;} test232{} test3 { 1: false; 2: false; tse { 22: true; 34: false; 5 { x: true; } 8 {x: false; 99{22: false;}}}}");
        var ctx = ConfigurationValidator.newInstance()
                .valuesMatchRecursively(DefaultValidators.IS_BOOLEAN, "test", "test2", "test3", "test3.tse")
                .build();
        var res = ctx.validate(c1);
        assertFalse(res.isValid());
        assertEquals(1, res.getIssues().size());
    }
    
    @Test public void failRecursRequireGroups() {
        assertThrowsContains(IllegalArgumentException.class, () -> ConfigurationValidator.newInstance().valuesMatchRecursively(DefaultValidators.IS_BYTE), 
                "groups must contain group identifiers");
    }
    
    @Test public void valuesMatchInGroup() {
        var c1 = defaultParser().parse("test { k: 1; b: c; }");
        var ctx = ConfigurationValidator.newInstance().valuesMatchInGroup(DefaultValidators.IS_BYTE, "test", "k").build();
        var res = ctx.validate(c1);
        assertTrue(res.isValid());
    }
    
    @Test public void valuesMatchInGroupFail() {
        var c1 = defaultParser().parse("test { k: 1; b: c; }");
        var ctx = ConfigurationValidator.newInstance().valuesMatchInGroup(DefaultValidators.IS_BYTE, "test").build();
        var res = ctx.validate(c1);
        assertFalse(res.isValid());
        assertEquals(1, res.getIssues().size());
    }
    
    @Test public void valuesMatchInGroupFail2() {
        var c1 = defaultParser().parse("test { k: 1; b: c; }");
        var ctx = ConfigurationValidator.newInstance().valuesMatchInGroup(DefaultValidators.IS_BYTE, "testt").build();
        var res = ctx.validate(c1);
        assertFalse(res.isValid());
        assertEquals(1, res.getIssues().size());
    }
    
    @Test public void valuesMatchInGroupPassNoSpecificKey() {
        var c1 = defaultParser().parse("test { k: 1; b: 127; }");
        var ctx = ConfigurationValidator.newInstance().valuesMatchInGroup(DefaultValidators.IS_BYTE, "test").build();
        var res = ctx.validate(c1);
        assertTrue(res.isValid());
    }
    
    @Test public void valuesMatchInGroupFailNoGroup() {
        var c1 = defaultParser().parse("test { k: 1; b: 127; }");
        var ctx = ConfigurationValidator.newInstance().valuesMatchInGroup(DefaultValidators.IS_BYTE, "test.k", "k", "b").build();
        var res = ctx.validate(c1);
        assertFalse(res.isValid());
        assertEquals(1, res.getIssues().size());
    }
    
    @Test public void valuesMatchInGroupFailNoValue() {
        var c1 = defaultParser().parse("test { k: 1; b: 127; }");
        var ctx = ConfigurationValidator.newInstance().valuesMatchInGroup(DefaultValidators.IS_BYTE, "test", "c").build();
        var res = ctx.validate(c1);
        assertFalse(res.isValid());
        assertEquals(1, res.getIssues().size());
    }
    
    @Test public void valuesMatchInGroupFailInvalid() {
        var c1 = defaultParser().parse("test { k: 1; b: 255; }");
        var ctx = ConfigurationValidator.newInstance().valuesMatchInGroup(DefaultValidators.IS_BYTE, "test", "b").build();
        var res = ctx.validate(c1);
        assertFalse(res.isValid());
        assertEquals(1, res.getIssues().size());
    }
    
    @Test public void failMatchGroupRequireKeys() {
        assertThrowsContains(IllegalArgumentException.class, () -> ConfigurationValidator.newInstance().valuesMatchInGroup(DefaultValidators.IS_BYTE, "t", new String[0]), 
                "keys must contain key identifiers");
    }
    
    @Test public void requireKeys() {
        var c1 = defaultParser().parse("t{a:b;c:d;}");
        var ctx = ConfigurationValidator.newInstance().requireKeys("t", "a", "c").build();
        assertTrue(ctx.validate(c1).isValid());
    }
    
    @Test public void requireKeysFail() {
        var c1 = defaultParser().parse("t{a:b;c:d;}");
        var ctx = ConfigurationValidator.newInstance().requireKeys("t", "a", "cb").build();
        var res = ctx.validate(c1);
        assertFalse(res.isValid());
        assertEquals(1, res.getIssues().size());
    }
    
    @Test public void requireKeysFailNoGroup() {
        var c1 = defaultParser().parse("t{a:b;c:d;}");
        var ctx = ConfigurationValidator.newInstance().requireKeys("tt", "a", "cb").build();
        var res = ctx.validate(c1);
        assertFalse(res.isValid());
        assertEquals(1, res.getIssues().size());
    }
    
    @Test public void failRequireKeys() {
        assertThrowsContains(IllegalArgumentException.class, () -> ConfigurationValidator.newInstance().requireKeys("t", new String[0]),
                "keys must contain key identifiers");
    }
    
    @Test public void testValidators() {
        assertNotNull(DefaultValidators.IS_BOOLEAN.validate(null));
        assertNotNull(DefaultValidators.IS_BOOLEAN.validate("truee"));
        assertNull(DefaultValidators.IS_BOOLEAN.validate("True"));
        assertNull(DefaultValidators.IS_BOOLEAN.validate("fAlsE"));
        assertNull(DefaultValidators.IS_INT.validate("-1"));
        assertNotNull(DefaultValidators.IS_INT.validate("abc"));
        assertNull(DefaultValidators.IS_SHORT.validate("16000"));
        assertNotNull(DefaultValidators.IS_SHORT.validate("102949294"));
        assertNull(DefaultValidators.IS_LONG.validate(Long.toString(Long.MAX_VALUE)));
        assertNotNull(DefaultValidators.IS_LONG.validate(".awfawf"));
        assertNull(DefaultValidators.IS_BYTE.validate("-1"));
        assertNotNull(DefaultValidators.IS_BYTE.validate("255"));
        assertNull(DefaultValidators.IS_DOUBLE.validate("2.12488124"));
        assertNotNull(DefaultValidators.IS_DOUBLE.validate("awfawf"));
        assertNull(DefaultValidators.IS_FLOAT.validate("2.12488124"));
        assertNotNull(DefaultValidators.IS_FLOAT.validate("awfawfawfg23"));
        assertNull(DefaultValidators.patternValidatorOf(Pattern.compile("[A-Za-z]*")).validate("abcEHDBafj"));
        assertNotNull(DefaultValidators.patternValidatorOf(Pattern.compile("[A-Za-z]*")).validate("abcE2HDBafj"));
        assertNull(DefaultValidators.IS_MAP.validate("test\nis\na\nmap\n"));
        assertNotNull(DefaultValidators.IS_MAP.validate("test\nis\na\nmap\nnot"));
        assertNotNull(DefaultValidators.IS_MAP.validate(null));
    }
    
    @Test public void shouldFailEmptyGroupName() {
        illg(() -> defaultParser().parse("   {}"), "Group identifier are not permitted to be blank.");
    }
    
    @Test public void shouldFailEmptyKeyName() {
        illg(() -> defaultParser().parse("t{ : value;}"), "Keys are not permitted to be blank.");
    }
    
    @Test public void shouldFailEmptyValue() {
        illg(() -> defaultParser().parse("T{ k:    ;}"), "Values are not permitted to be blank.");
    }
    
    @Test public void shouldFailEmptyValueMultiline() {
        illg(() -> defaultParser().parse("T{ k: \n\n  \n;}"), "Values are not permitted to be blank.");
    }
    
    @Test public void shouldFailEmptyKeyBuilder() {
        assertThrowsContains(IllegalArgumentException.class, () -> newDefaultInstance().addRoot("t").put("            ", "v"),
                "Blank keys are not permitted");
    }
    
    @Test public void shouldFailMultilineKeyBuilder() {
        assertThrowsContains(IllegalArgumentException.class, () -> newDefaultInstance().addRoot("t").put(" invalid \n key  ", "v"),
                "Key is not permitted to be multiline:");
    }
    
    @Test public void shouldFailEmptyValueBuilder() {
        assertThrowsContains(IllegalArgumentException.class, () -> newDefaultInstance().addRoot("t").put("     v       ", "        "),
                "Blank values are not permitted.");
    }
    
    @Test public void shouldFailEmptyValueMultilineBuilder() {
        assertThrowsContains(IllegalArgumentException.class, () -> newDefaultInstance().addRoot("t").put("     v       ", "    \n\n\n\n    "),
                "Blank values are not permitted.");
    }
    
    @Test public void stackTest() {
        Stack<Integer> s = new Stack<>();
        assertTrue(s.isEmpty());
        s.push(1);
        assertEquals(1, s.size());
        assertEquals(1, s.peek());
        assertEquals(1, s.size());
        assertFalse(s.isEmpty());
        assertEquals(1, s.pop());
        assertEquals(0, s.size());
        assertTrue(s.isEmpty());
        assertThrowsContains(NoSuchElementException.class, () -> s.peek(), "stack is empty");
        assertThrowsContains(NoSuchElementException.class, () -> s.pop(), "stack is empty");
        s.push(1);
        s.push(2);
        s.push(3);
        assertThrowsContains(UnsupportedOperationException.class, () -> s.iterator().remove(), "remove");
        int sum = 0;
        for(var it = s.iterator(); it.hasNext(); sum += it.next()) {}
        assertEquals(6, sum);
    }
    
    @Test public void parseLiterals() {
        var c = defaultParser().parse("literals { literal: \"this is a literal!\\\" []{}#\\:;\"; }");
        assertEquals("this is a literal!\" []{}#\\:;", c.getValue("literals#literal"));
    }
    
    @Test public void unescapedLiteralFailMustBeClosed() {
        illg(() -> defaultParser().parse("literals { l: \"literal; }"), "String literal has not been closed. Document end reached in state IN_LITERAL should be UNDEFINED.");
    }
    
    @Test public void unescapedLiteralFailMustBeClosed2() {
        illg(() -> defaultParser().parse("literals { l: \"  ;}"), "String literal has not been closed. Document end reached in state IN_LITERAL should be UNDEFINED.");
    }
    
    @Test public void unescapedLiteralFailMustBeClosed3() {
        illg(() -> defaultParser().parse("literals { l: \"test"), "String literal has not been closed. Document end reached in state IN_LITERAL should be UNDEFINED.");
    }
    
    @Test public void unescapedLiteralFailUnescapedLiteral() {
        illg(() -> defaultParser().parse("l{l:\"literal\" done; }"), "Closing literal must be followed by V_END!");
    }
    
    @Test public void emptyLiteralFail() {
        illg(() -> defaultParser().parse("l{l: \"\"}"), "Values are not permitted to be blank.");
    }
    
    @Test public void literalEscapeParsed() {
        var c = defaultParser().parse("l{l: \\\"test; }");
        assertEquals("\"test", c.getValue("l#l"));
    }
    
    @Test public void unescapedLiteralInStandardValue() {
        illg(() -> defaultParser().parse("l{l: test\"; }"), "Cannot transition to LITERAL with non-literal value already started.");
    }
    
    @Test public void literalMultlineSuccess() {
        var c = defaultParser().parse("l{l: \"l\n  is  \n   multi line!\\\"           \n      \n\n\n  \n   \";}");
        assertEquals("l\nis\nmulti line!\"", c.getValue("l#l"));
    }
    
    @Test public void literalsNoEscape() {
        var c = defaultParser().parse("L{l: \"{}#;:\\\\\"\";}");
        assertEquals("{}#;:\\\"", c.getValue("L#l"));
    }
    
    @Test public void keyWhitespaceIsContained() {
        var c = defaultParser().parse("g { k ey: value; }");
        assertTrue(c.findValue("g#k ey") != null);
    }
}