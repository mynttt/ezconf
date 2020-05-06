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
import de.mynttt.ezconf.implementation.DefaultConfigurationFactory;

class LibraryTest {

    private static Configuration p(String s) {
        return defaultParser().parse(s);
    }
    
    private static void illg(Executable x) {
        assertThrows(IllegalStateException.class, x);
    }
    
    @AfterAll static void tearDown() throws IOException {
        Files.deleteIfExists(Paths.get("_tpd1"));
        Files.deleteIfExists(Paths.get("_tpd2"));
    }
    
    @Test void passGroupLegalCharacter() {
        assertTrue(p("test12345ABC {}").groupExists("test12345ABC"));
    }
    
    @Test void failGroupIllegalCharacter() {
        illg(() -> p("test_?!-{"));
    }
    
    @Test void failGroupIllegalClose() {
        illg(() -> p("test}"));
    }
    
    @Test void failGroupSameIdentifier() {
        assertThrows(IllegalArgumentException.class, () -> p("test {} test{}"));
    }
    
    @Test void failGroupMultilineIdentifier() {
        illg(() -> p("te\nst{}"));
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
       illg(() -> p("test { ke\ny: value; }"));
    }
    
    @Test public void failIncompleteGroup() {
        illg(() -> p("test {"));
    }
    
    @Test public void failIncompleteKeyValue() {
        illg(() -> p("test { test"));
    }
    
    @Test public void failIncompleteKeyValue2() {
        illg(() -> p("test { test: test"));
    }
    
    @Test public void failIncompleteKeyValue3() {
        illg(() -> p("test { test: test; "));
    }
    
    @Test public void failOnNestedGroup()  {
        illg(() -> p("test { tes.t { t: a; }}"));
    }
    
    @Test public void shouldEscapeValueProberly() {
        assertEquals("#{}:;\\",  p("test { key: \\#\\{\\}\\:\\;\\\\;}").getValue("test#key"));
    }
    
    @Test public void shouldEscapeKeyProperly() {
        assertTrue(p("test { \\#\\{\\}\\:\\;\\\\: value;}").getGroup("test").keyExists("#{}:;\\"));
    }
    
    @Test public void shouldFailOnEscapedNestedGroupIdentifier() {
        illg(() -> p("test{ \\#\\{\\}\\:\\;\\\\{ key: value;}}"));
    }
    
    @Test public void shouldFailOnEscapedGroupIdentifier() {
        illg(() -> p("\\#\\{\\}\\:\\;\\\\{ { key: value;}}"));
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
    
    @Test public void testPretty() throws IOException, URISyntaxException {
        Configuration c = defaultParser().parse(Paths.get(LibraryTest.class.getResource("/Nested.ez").toURI()));
        assertEquals(Files.readString(Paths.get(LibraryTest.class.getResource("/pretty.txt").toURI()), StandardCharsets.UTF_8), EzConf.dumpPretty(c));
        Configuration c2 = defaultParser().parse(LibraryTest.class.getResourceAsStream("/pretty.txt"));
        assertEquals(c, c2);
        assertEquals(c2, c);
    }
    
    @Test public void testNormal() throws IOException, URISyntaxException {
        Configuration c = defaultParser().parse(LibraryTest.class.getResourceAsStream("/Nested.ez"));
        assertEquals(Files.readString(Paths.get(LibraryTest.class.getResource("/normal.txt").toURI()), StandardCharsets.UTF_8), EzConf.dump(c));
        Configuration c2 = defaultParser().parse(LibraryTest.class.getResourceAsStream("/normal.txt"));
        assertEquals(c, c2);
        assertEquals(c2, c);
    }
    
    @Test public void testPathDump() throws IOException {
        Configuration c = defaultParser().parse(LibraryTest.class.getResourceAsStream("/Nested.ez"));
        String u1 = "_tpd1";
        String u2 = "_tpd2";
        EzConf.dump(Paths.get(u1), c);
        EzConf.dumpPretty(Paths.get(u2), c);
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
        assertThrows(IllegalArgumentException.class, () -> p("test { a {} b { c{} c{} }}"));
    }
    
    @Test public void failNestedMultilineKey() {
        illg(() -> p("test { test { t\nn: der; }}"));
    }
    
    @Test public void spaceStaysInKey() {
        assertTrue(p("test { test { a  b: test; }}").getGroup("test.test").keyExists("a  b"));
    }
    
    @Test public void skipNestedSpaceInGroup() {
        assertTrue(p("test { te st { ab: test; }}").groupExists("test.test"));
    }
    
    @Test public void failDuplicateNestedGroup() {
        assertThrows(IllegalArgumentException.class, () -> p("test { a{} b{} a{}}"));
    }
    
    @Test public void parsesChildren() {
        assertEquals(3, p("test{ a {}b{}c{}}").getGroup("test").getChildren().size());
    }
    
    @Test public void failMalformedQuery1() {
        assertThrows(IllegalArgumentException.class, () -> p("test{}").findValue("#test")); 
    }
    
    @Test public void failMalformedQuery2() {
        assertThrows(IllegalArgumentException.class, () -> p("test{}").findValue("test"));
    }
    
    @Test public void duplicateKey() {
        assertThrows(IllegalArgumentException.class, () -> p("test{a:k; a:k;}"));
    }
    
    @Test public void failQueryNoGroup() {
        assertThrows(NoSuchElementException.class, () -> p("test{}").findValue("tes#val"));
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
        assertThrows(NullPointerException.class, () -> newDefaultInstance().addRoot("t").put(null, ""));
    }
    
    @Test public void shouldFailNullValue() {
        assertThrows(NullPointerException.class, () -> newDefaultInstance().addRoot("t").put("t", null));
    }
    
    @Test public void shouldFailDuplicateKey() {
        assertThrows(IllegalArgumentException.class, () -> newDefaultInstance().addRoot("t").put("t", "").put("t", ""));
    }
    
    @Test public void shouldFailInvalidGroupName1() {
        assertThrows(IllegalArgumentException.class, () -> newDefaultInstance().addRoot(""));
    }
    
    @Test public void shouldFailInvalidGroupName2() {
        assertThrows(IllegalArgumentException.class, () -> newDefaultInstance().addRoot("te.st_-"));
    }
    
    @Test public void shouldFailInvalidGroupName3() {
        assertThrows(IllegalArgumentException.class, () -> newDefaultInstance().addRoot(" tes t "));
    }
    
    @Test public void shouldFailDuplicateGroupRoot() {
        assertThrows(IllegalArgumentException.class, () -> newDefaultInstance().addRoot("t").endRoot().addRoot("t"));
    }
    
    @Test public void shouldFailDuplicateGroupRootNested() {
        assertThrows(IllegalArgumentException.class, () -> newDefaultInstance()
                .addRoot("t")
                .endRoot()
                .addRoot("d")
                .endRoot()
                .addRoot("tp")
                    .addChild("ttd")
                    .endChild()
                    .addChild("ttd"));
    }
    
    @Test public void shouldFailIllegalEndCallRoot() {
        illg(() -> newDefaultInstance().addRoot("t").addChild("t").endRoot());
    }
    
    @Test public void shouldFailIllegalEndCallChild() {
        illg(() -> newDefaultInstance().addRoot("t").endChild());
    }
    
    @Test public void dumpShouldHandleEscapedCharacters() {
        var c1 = defaultParser().parse(EzConf.dump(newDefaultInstance()
            .addRoot("test")
            .put("\\{};.:#", "\\{};.:#")
            .endRoot()
            .build()));
        assertEquals("\\{};.:#", c1.getValue("test#\\{};.:#"));
    }
    
    @Test public void prettyDumpShouldHandleEscapedCharacters() {
        var c1 = defaultParser().parse(EzConf.dumpPretty(newDefaultInstance()
                .addRoot("test")
                .put("\\{};.:#", "\\{};.:#")
                .endRoot()
                .build()));
            assertEquals("\\{};.:#", c1.getValue("test#\\{};.:#"));
    }
    
    @Test public void doubleCloseBuilder() {
        var b = newDefaultInstance();
        b.build();
        illg(() -> b.build()); 
    }
    
    @Test public void validateWithoutErrors() {
        var c1 = defaultParser().parse("test{} test2{} test3{test4{}}");
        var ctx = ConfigurationValidator.newInstance()
                .requireGroups("test", "test2", "test3", "test3.test4")
                .build();
        assertTrue(ctx.validate(c1).isValid());
    }
    
    @Test public void failRequireGroups() {
        assertThrows(IllegalArgumentException.class, () -> ConfigurationValidator.newInstance().requireGroups());
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
        assertThrows(IllegalArgumentException.class, () -> ConfigurationValidator.newInstance().valuesMatchRecursively(DefaultValidators.IS_BYTE));
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
        assertThrows(IllegalArgumentException.class, () -> ConfigurationValidator.newInstance().valuesMatchInGroup(DefaultValidators.IS_BYTE, "t", new String[0]));
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
        assertThrows(IllegalArgumentException.class, () -> ConfigurationValidator.newInstance().requireKeys("t", new String[0]));
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
        illg(() -> defaultParser().parse("   {}"));
    }
    
    @Test public void shouldFailEmptyKeyName() {
        illg(() -> defaultParser().parse("t{ : value;}"));
    }
    
    @Test public void shouldFailEmptyValue() {
        illg(() -> defaultParser().parse("T{ k:    ;}"));
    }
    
    @Test public void shouldFailEmptyValueMultiline() {
        illg(() -> defaultParser().parse("T{ k: \n\n  \n;}"));
    }
    
    @Test public void shouldFailEmptyKeyBuilder() {
        assertThrows(IllegalArgumentException.class, () -> newDefaultInstance().addRoot("t").put("            ", "v"));
    }
    
    @Test public void shouldFailMultilineKeyBuilder() {
        assertThrows(IllegalArgumentException.class, () -> newDefaultInstance().addRoot("t").put(" invalid \n key  ", "v"));
    }
    
    @Test public void shouldFailEmptyValueBuilder() {
        assertThrows(IllegalArgumentException.class, () -> newDefaultInstance().addRoot("t").put("     v       ", "        "));
    }
    
    @Test public void shouldFailEmptyValueMultilineBuilder() {
        assertThrows(IllegalArgumentException.class, () -> newDefaultInstance().addRoot("t").put("     v       ", "    \n\n\n\n    "));
    }
}