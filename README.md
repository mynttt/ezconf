# ezconf

A simple hierarchical scoped group based key:value storage/configuration format.

[![Build Status](https://travis-ci.com/mynttt/ezconf.svg?branch=master)](https://travis-ci.com/mynttt/ezconf) 
[![codecov](https://codecov.io/gh/mynttt/ezconf/branch/master/graph/badge.svg)](https://codecov.io/gh/mynttt/ezconf)
[![Codacy Badge](https://api.codacy.com/project/badge/Grade/8cb79eae6f2c4cb2a203679556b6203d)](https://www.codacy.com/manual/mynttt/ezconf?utm_source=github.com&amp;utm_medium=referral&amp;utm_content=mynttt/ezconf&amp;utm_campaign=Badge_Grade)
[![GitHub issues](https://img.shields.io/github/issues/mynttt/ezconf)](https://github.com/mynttt/ezconf/issues) 
[![javadoc](https://javadoc.io/badge2/de.mynttt/ezconf/javadoc.svg)](https://javadoc.io/doc/de.mynttt/ezconf) 
[![GitHub license](https://img.shields.io/github/license/mynttt/ezconf)](https://github.com/mynttt/ezconf/blob/master/LICENSE)
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/de.mynttt/ezconf/badge.svg)](https://maven-badges.herokuapp.com/maven-central/de.mynttt/ezconf)
[![Download](https://api.bintray.com/packages/mynttt/oss/de.mynttt.ezconf/images/download.svg) ](https://bintray.com/mynttt/oss/de.mynttt.ezconf/_latestVersion)
[![security status](https://www.meterian.io/badge/gh/mynttt/ezconf/security)](https://www.meterian.io/report/gh/mynttt/ezconf)

## Syntax

An ezconf file follows the following syntax:

```
group1 {
  subgroup1 {
      key: value;
      key2: \:value\}escaped\;;
      key3: key
            is
            multiline;
      key4: "has literal \"";
  }
}

group2 {
  key: value;
}
```

Groups can be nested arbitrarily deep.

## Syntax and Constraints

- ### Groups:
  - Groups can be nested
  - Only groups can contain key:value pairs
  - Group identifier names must be unique in the given scope
  - Group identifier names can only contain letters and digits, whitespace is not permitted and will be skipped. (Example: `t est {}` will be parsed as `test`.)
  - Group identifiers cannot be multiline or blank

- ### Keys:
  - Keys can contain whitespace but will be trimmed (i.e. `  k ey  : value;` will be parsed as `k ey`)
  - Keys cannot be multiline or blank
  - Keys must be unique in a given scope
  - Null keys cannot exist and are invalid
  - Special symbols must be escaped with `\`, to escape the escape do this `\\` (value literal must not be escaped for keys)

- ### Values:
  - Values can contain whitespace and can be multiline but cannot be blank
  - Values will be trimmed at each line while parsing (i.e. ` value   \n   stop;` will be parsed as `value\nstop`)
  - Null values do not exist and are invalid
  - Special symbols must be escaped with `\`, to escape the escape do this `\\`
  - Literals allow to not escape special characters (exception: `"`)
    - Literals must have the first non-whitespace character start with `"`
    - An unescaped `"` will trigger an exception if the first non-whitespace character is not `"`
    - Within the literal `"` must still be escaped
    - A closing literal still requires the `;`
  
- Parser automatically escaped key / value pairs on dump

## Special symbols
```
{ -> begin group
} -> end group
: -> key value seperator
; -> end value
# -> comment, skip everything after for this line
" -> value literal
```

## API Example

A reference implementation is shipped with this release and JavaDoc is available for all publicly accessible code.

```java

import java.util.Optional;
import de.mynttt.ezconf.Configuration;
import de.mynttt.ezconf.ConfigurationBuilder;
import de.mynttt.ezconf.ConfigurationGroup;
import de.mynttt.ezconf.ConfigurationValidator;
import de.mynttt.ezconf.DefaultValidators;
import de.mynttt.ezconf.EzConf;
import de.mynttt.ezconf.ConfigurationValidator.ValidationContext;

public class Example {

    public static void main(String[] args) {
        Configuration conf = EzConf.defaultParser().parse("example { key: true; nested { key: value; }}");
        System.out.println("Check if group exists? : " + conf.groupExists("example"));
        
        ConfigurationGroup nested = conf.getGroup("example.nested");
        System.out.println("Check if value exists? : " + nested.keyExists("key"));
        System.out.println("Access values: " + nested.getValue("key") + " or " + conf.getValue("example.nested#key"));
        
        Optional<String> value = conf.findValue("example#key");
        
        for(var entry : nested)
            System.out.println(entry.getKey() + ": " + entry.getValue());
        
        Configuration built = ConfigurationBuilder.newDefaultInstance()
                .addRoot("root")
                    .addChild("child")
                    .put("key", "value")
                    .endChild()
                .endRoot()
                .build();
        
        System.out.println("Printing with literals used for escaping:");
        System.out.println(EzConf.defaultParser().dumpPretty(built));
        System.out.println("Printing without literals used for escaping:");
        System.out.println(EzConf.defaultParser(ParserFlags.DUMP_ESCAPE_INSTEAD_OF_LITERAL).dumpPretty(built));
        
        ValidationContext ctx = ConfigurationValidator.newInstance()
                .requireGroups("example", "example.nested")
                .valuesMatchInGroup(DefaultValidators.IS_BOOLEAN, "example")
                .build();
        
        System.out.println("Is valid? : " + ctx.validate(conf).isValid());
        
        ValidationContext ctx2 = ConfigurationValidator.newInstance()
                .valuesMatchRecursively(DefaultValidators.IS_BYTE, "example")
                .build();
        
        System.out.println("Is invalid? : " + !ctx2.validate(conf).isValid());
    }
    
}

```
