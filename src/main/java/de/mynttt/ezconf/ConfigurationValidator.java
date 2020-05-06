package de.mynttt.ezconf;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import static java.util.function.Predicate.not;

/**
 * The ConfigurationValidator allows to validate parsed {@link Configuration} instances by building {@link ValidationContext} instances.
 * 
 * @author mynttt
 *
 */
public class ConfigurationValidator {
    private final List<ValidationRule> rules = new ArrayList<>();
            
    /**
     * Allows to define a custom validation function for a given type.
     * 
     * @author mynttt
     *
     * @param <T> type of validated type
     */
    public interface Validate<T> {
        
        /**
         * Validate a given type given the string representation
         * @param value to validate
         * @return null if valid or an error message
         */
        String validate(String value);
    }
    
    /**
     * Allows to define a custom validation rule.
     * 
     * @author mynttt
     * 
     */
    public interface ValidationRule {
        
        /**
         * Validates a given {@link Configuration}
         * @param configuration to validate
         * @return list of found violations
         */
        List<String> validate(Configuration configuration);
    }

    /**
     * Result of {@link ValidationContext#validate(Configuration)}
     * 
     * @author mynttt
     *
     */
    public static final class ValidationResult {
        private final List<String> issues = new ArrayList<String>();
        
        private ValidationResult() {}
        
        /**
         * Check if validation succeeded
         * @return true if valid
         */
        public boolean isValid() {
            return issues.isEmpty();
        }
        
        /**
         * Get list of issues
         * @return read only list of issues
         */
        public List<String> getIssues() {
            return Collections.unmodifiableList(issues);
        }
    }
    
    /**
     * A validation context contains several {@link ValidationRule} instances and can be used for validation.
     * 
     * @author mynttt
     *
     */
    public static final class ValidationContext {
        private final List<ValidationRule> rules;
        
        private ValidationContext(List<ValidationRule> rules) {
            this.rules = rules;
        }
        
        /**
         * Validate a configuration.
         * @param configuration to validate
         * @return validation result
         */
        public ValidationResult validate(Configuration configuration) {
            Objects.requireNonNull(configuration, "configuration must be non-null");
            ValidationResult res = new ValidationResult();
            rules.forEach(r -> res.issues.addAll(Objects.requireNonNull(r.validate(configuration), "!! validate() should not return null !!")));
            return res;
        }
    }
    
    private ConfigurationValidator() {}
    
    /**
     * Create a new builder instance
     * @return new builder instance
     */
    public static ConfigurationValidator newInstance() {
        return new ConfigurationValidator();
    }

    /**
     * Add a custom {@link ValidationRule}.
     * @param rule to add
     * @return this builder
     */
    public ConfigurationValidator customRule(ValidationRule rule) {
        Objects.requireNonNull(rule, "rule must not be null");
        rules.add(rule);
        return this;
    }
    
    /**
     * Check if the supplied set of keys and the validator type match with the mapped values within a group
     * @param validator to perform the check with
     * @param group to perform the check on
     * @param keys to include in the check
     * @return this builder
     * @throws NullPointerException if any supplied value is null
     * @throws IllegalArgumentException if the key varargs length is 0
     */
    public ConfigurationValidator valuesMatchInGroup(Validate<?> validator, String group, String...keys) {
        Objects.requireNonNull(validator, "validator must not be null");
        Objects.requireNonNull(group, "group must not be null");
        Objects.requireNonNull(keys, "keys must not be null");
        if(keys.length == 0) throw new IllegalArgumentException("keys must contain key identifiers");
        Arrays.stream(keys).forEach(Objects::requireNonNull);
        rules.add(conf -> {
            var l = new ArrayList<String>();
            var g = conf.getGroup(group);
            if(g == null) {
                l.add("Group: '"+group+"' does not exist.");
                return l;
            }
            Arrays.stream(keys).forEach(k -> {
                var v = g.getValue(k);
                if(v == null) {
                    l.add(group + "#" + k + ": does not exist.");
                    return;
                }
                var s = validator.validate(v);
                if(s != null)
                    l.add(String.format("Invalid @ %s: %s -> %s", g.getPath(), k, s));
            });
            return l;
        });
        return this;
    }

    /**
     * Check if all values in the group match the type of the given validator
     * @param validator to perform the check with
     * @param group to perform the check on
     * @return this builder
     * @throws NullPointerException if any supplied value is null
     */
    public ConfigurationValidator valuesMatchInGroup(Validate<?> validator, String group) {
        Objects.requireNonNull(validator, "validator must not be null");
        Objects.requireNonNull(group, "group must not be null");
        rules.add(conf -> {
            var l = new ArrayList<String>();
            var g = conf.getGroup(group);
            if(g == null) {
                l.add("Group: '"+group+"' does not exist.");
                return l;
            }
            g.forEach(e -> {
                String s = validator.validate(e.getValue());
                if(s != null) l.add(String.format("Invalid @ %s: %s -> %s", g.getPath(), e.getKey(), s));
            });
            return l;
        });
        return this;
    }

    /**
     * Check if all values of the groups and given subgroups match the type of the given validator
     * @param validator to perform the check with
     * @param groups to include in this check
     * @return this builder
     * @throws NullPointerException if any supplied value is null
     * @throws IllegalArgumentException if the groups varargs length is 0
     */
    public ConfigurationValidator valuesMatchRecursively(Validate<?> validator, String...groups) {
        Objects.requireNonNull(validator, "validator must not be null");
        Objects.requireNonNull(groups, "groups must not be null");
        if(groups.length == 0) throw new IllegalArgumentException("groups must contain group identifiers");
        Arrays.stream(groups).forEach(Objects::requireNonNull);
        rules.add(conf -> {
           var l = new ArrayList<String>();
           Arrays.stream(groups).forEach(group -> {
               var g = conf.getGroup(group);
               if(g == null) {
                   l.add("Group: '"+group+"' does not exist.");
                   return;
               }
               validateRecurs(validator, g, l);
           });
           return l;
        });
        return this;
    }
    
    private void validateRecurs(Validate<?> validator, ConfigurationGroup g, List<String> res) {
        g.forEach(e -> {
            String s = validator.validate(e.getValue());
            if(s != null) res.add(String.format("Invalid @ %s: %s -> %s", g.getPath(), e.getKey(), s));
        });
        g.getChildren().forEach(c -> validateRecurs(validator, c, res));
    }

    /**
     * Check if all given groups exist in the configuration
     * @param groups to check the existence for
     * @return this builder
     * @throws NullPointerException if any supplied value is null
     * @throws IllegalArgumentException if the groups varargs length is 0
     */
    public ConfigurationValidator requireGroups(String...groups) {
        Objects.requireNonNull(groups, "groups must not be null");
        if(groups.length == 0) throw new IllegalArgumentException("groups must contain group identifiers");
        Arrays.stream(groups).forEach(Objects::requireNonNull);
        rules.add(conf -> Arrays.stream(groups)
                .filter(not(conf::groupExists))
                .map(s -> "Group does not exist: '"+s+"'")
                .collect(Collectors.toList()));
        return this;
    }

    /**
     * Check if all given keys exist in the configuration for a group
     * @param group to target with the check
     * @param keys to check existence for
     * @return this builder
     * @throws NullPointerException if any supplied value is null
     * @throws IllegalArgumentException if the keys varargs length is 0
     */
    public ConfigurationValidator requireKeys(String group, String...keys) {
        Objects.requireNonNull(group, "group must not be null");
        Objects.requireNonNull(keys, "keys must not be null");
        if(keys.length == 0) throw new IllegalArgumentException("keys must contain key identifiers");
        Arrays.stream(keys).forEach(Objects::requireNonNull);
        rules.add(conf -> {
            var l = new ArrayList<String>(1);
            var g = conf.getGroup(group);
            if(g == null) {
                l.add("Group: '"+group+"' does not exist.");
                return l;
            }
            return Arrays.stream(keys)
                    .filter(not(g::keyExists))
                    .map(s -> "Key '"+s+"' does not exist in group '"+group+"'")
                    .collect(Collectors.toList());
        });
        return this;
    }
    
    /**
     * Build a {@link ValidationContext} with the current rules.<br>
     * It is possible to modify the builder afterwards, all previously created context instances will not be affected.
     * @return a context with the currently added rules
     */
    public ValidationContext build() {
        return new ValidationContext(new ArrayList<>(rules));
    }
}