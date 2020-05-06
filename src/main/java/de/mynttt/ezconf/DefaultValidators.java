package de.mynttt.ezconf;

import java.util.Map;
import java.util.regex.Pattern;
import de.mynttt.ezconf.ConfigurationValidator.Validate;

/**
 * Default validators that can be used by the {@link ConfigurationValidator}.
 * 
 * @author mynttt
 *
 */
public final class DefaultValidators {
    /**
     * Byte.class validator
     */
    public static final Validate<Byte> IS_BYTE = v -> tryNumberValidation(Byte.class, v, () -> Byte.parseByte(v));
    
    /**
     * Short.class validator
     */
    
    public static final Validate<Short> IS_SHORT = v -> tryNumberValidation(Short.class, v, () -> Short.parseShort(v));
    
    /**
     * Integer.class validator
     */
    public static final Validate<Integer> IS_INT = v -> tryNumberValidation(Integer.class, v, () -> Integer.parseInt(v));
    
    /**
     * Long.class validator
     */
    public static final Validate<Long> IS_LONG = v -> tryNumberValidation(Long.class, v, () -> Long.parseLong(v));
    
    /**
     * Double.class validator
     */
    public static final Validate<Double> IS_DOUBLE = v -> tryNumberValidation(Double.class, v, () -> Double.parseDouble(v));
    
    /**
     * Float.class validator
     */
    public static final Validate<Double> IS_FLOAT = v -> tryNumberValidation(Float.class, v, () -> Float.parseFloat(v));
    
    /**
     * Boolean.class validator
     */
    public static final Validate<Boolean> IS_BOOLEAN = v -> {
        if(v == null)
            return "Boolean value is null";
        String s = v.toLowerCase();
        if("true".equals(s) || "false".equals(s)) return null;
        return "Invalid boolean: '"+v+"' must be (true|false)";
    };
    
    /**
     * Map validator<br>
     * A map is a multiline value where key:value pairs are separated by a newline.<br>
     * <p>For example this JSON map: <code>{"a": "b", "c": "d"}</code> would become:</p>
     * <pre>
     * map: a
     *      b
     *      c
     *      d;
     * </pre>
     */
    public static final Validate<Map<String, String>> IS_MAP = v -> {
        if(v == null)
            return "Map input value is null";
        if(v.lines().count() % 2 != 0)
            return "Map requires an even number of lines in a multi-line value.";
        return null;
    };
    
    private DefaultValidators() {};
    
    /**
     * A pattern validator validates a {@link Pattern}
     * @param pattern to validate for
     * @return validator instance of pattern
     */
    public static Validate<String> patternValidatorOf(Pattern pattern) {
        return s -> {
            if(!pattern.matcher(s).matches())
                return "Input: '"+s+"' does not match pattern: " + pattern.pattern();
            return null;
        };
    }
    
    private static String tryNumberValidation(Class<?> clazz, String value, Runnable r) {
        try {
            r.run();
            return null;
        } catch(NumberFormatException e) {
            return "Invalid type for " + clazz + ":'" + value +"'";
        }
    }
}
