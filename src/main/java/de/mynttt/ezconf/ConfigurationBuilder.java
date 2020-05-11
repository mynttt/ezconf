package de.mynttt.ezconf;

import java.util.Objects;
import de.mynttt.ezconf.implementation.DefaultConfigurationFactory;

/**
 * The ConfigurationBuilder allows to build {@link Configuration} instances.<br>
 * It provides a fluent API with the {@link GroupBuilderContext}.
 * <p>Example:</p>
 * 
 * Java:
 * <pre>
 * Configuration c = newDefaultInstance()
 *       .addRoot("level1")
 *           .put("main", "exists")
 *           .put("main_here", "exists")
 *           .addChild("level2")
 *               .put("key2", "exists")
 *           .endChild()
 *           .addChild("level3")
 *               .put("key3", "exists")
 *           .endChild()
 *           .addChild("level4")
 *               .put("key", "value")
 *               .addChild("level4a")
 *                   .put("key4a", "exists\nis\nmultiline")
 *                   .addChild("level5a")
 *                       .put("key", "value")
 *                   .endChild()
 *               .endChild()
 *           .endChild()
 *       .endRoot()
 *       .build();
 * </pre>
 * 
 * Result (pretty print):
 * <pre>
 * level1 {
 *     main_here: exists;
 *     main: exists;
 * 
 *     level2 {
 *         key2: exists;
 *     }
 * 
 *     level3 {
 *         key3: exists;
 *     }
 * 
 *     level4 {
 *         key: value;
 * 
 *         level4a {
 *             key4a: exists
 *                    is
 *                    multiline;
 * 
 *             level5a {
 *                 key: value;
 *             }
 *         }
 *     }
 * }
 * </pre>
 * 
 * @author mynttt
 *
 */
public final class ConfigurationBuilder {
    private static final ConfigurationFactory DEFAULT = new DefaultConfigurationFactory();
    
    /**
     * Allows to add nested groups and key:value pairs.
     * 
     * @author mynttt
     *
     */
    public final static class GroupBuilderContext {
        private final Configuration configuration;
        private final ConfigurationFactory factory;
        private final ConfigurationBuilder builder;
        private final Stack<ConfigurationGroup> groups = new Stack<>();
        private final Stack<String> path = new Stack<>();
        
        private GroupBuilderContext(ConfigurationBuilder builder, ConfigurationFactory factory, Configuration configuration) {
            this.builder = builder;
            this.factory = factory;
            this.configuration = configuration;
        }
        
        /**
         * Put a key:value pair.
         * @param key to put
         * @param value to associate with key
         * @return {@link GroupBuilderContext} instance
         * @throws NullPointerException if key or value is null
         * @throws IllegalArgumentException if key or value are blank
         */
        public GroupBuilderContext put(String key, String value) {
            Objects.requireNonNull(key, "Key must be non-null.");
            Objects.requireNonNull(value, "Value for '" + key + "' must be non-null");
            String k = key.trim(), v = value.trim();
            if(k.isEmpty())
                throw new IllegalArgumentException("Key is blank for value: '"+v+"'. Blank keys are not permitted.");
            if(k.contains("\n"))
                throw new IllegalArgumentException("Key is not permitted to be multiline: '"+k+"'");
            if(v.isEmpty())
                throw new IllegalArgumentException("Value is blank for key: '"+k+"'. Blank values are not permitted.");
            groups.peek().addKeyValue(k, v);
            return this;
        }
        
        /**
         * Closes an open root group.
         * @return {@link ConfigurationBuilder} instance
         * @throws IllegalStateException if a child is currently open
         */
        public ConfigurationBuilder endRoot() {
            if(groups.size() != 1)
                throw new IllegalStateException("Cannot end root while still in child. Current path: '"+groups.peek().getPath()+"'");
            groups.pop();
            path.pop();
            return builder;
        }
        
        /**
         * Closes an open child group.
         * @return {@link GroupBuilderContext} instance
         * @throws IllegalStateException if no child is currently open
         */
        public GroupBuilderContext endChild() {
            if(groups.size() == 1)
                throw new IllegalStateException("Cannot end child while in root. Current path: '"+groups.peek().getPath()+"'");
            groups.pop();
            path.pop();
            return this;
        }
        
        /**
         * Adds a child group and reference the newly added child group
         * @param name of the child group
         * @return {@link GroupBuilderContext} instance
         * @throws IllegalArgumentException if the name is invalid (null, empty, contains whitespace, contains other characters than digits and letters)
         */
        public GroupBuilderContext addChild(String name) {
            validateName(name);
            path.push(name);
            ConfigurationGroup group = factory.newConfigurationGroup(String.join(".", path));
            configuration.addGroup(group.getPath(), group);
            groups.peek().addChild(group);
            groups.push(group);
            return this;
        }
    }
    
    private final ConfigurationFactory factory;
    private final Configuration configuration;
    private final GroupBuilderContext context;
    private boolean valid = true;
    
    private ConfigurationBuilder(ConfigurationFactory factory) {
        Objects.requireNonNull(factory, "factory must be non-null.");
        this.factory = factory;
        this.configuration = factory.newConfiguration();
        this.context =  new GroupBuilderContext(this, factory, configuration);
    }
    
    /**
     * Creates the default builder (using the {@link DefaultConfigurationFactory})
     * @return new default builder
     */
    public static ConfigurationBuilder newDefaultInstance() {
        return new ConfigurationBuilder(DEFAULT);
    }
    
    /**
     * Creates a builder using a custom implementation
     * @param factory to use for the builder
     * @return new custom builder
     */
    public static ConfigurationBuilder newInstance(ConfigurationFactory factory) {
        return new ConfigurationBuilder(factory);
    }
    
    /**
     * Adds a new root group and switches to the {@link GroupBuilderContext}
     * @param name of the root group
     * @return {@link GroupBuilderContext} instance
     * @throws IllegalArgumentException if the name is invalid (null, empty, contains whitespace, contains other characters than digits and letters)
     */
    public GroupBuilderContext addRoot(String name) {
        validateName(name);
        context.path.push(name);
        String path = String.join(".", context.path);
        ConfigurationGroup group = factory.newConfigurationGroup(path);
        context.groups.push(group);
        configuration.addGroup(path, group);
        return context;
    }
    
    /**
     * Commit the changes and close the builder.
     * @return the built configuration
     * @throws IllegalStateException if called more than once
     */
    public Configuration build() {
        if(!valid)
            throw new IllegalStateException("Builder has already been closed with 'build()'.");
        valid = false;
        return configuration;
    }
    
    private static void validateName(String name) {
        Objects.requireNonNull(name, "Group name must not be null.");
        if(name.trim().isEmpty())
            throw new IllegalArgumentException("Group name should not be empty.");
        for(int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if(Character.isWhitespace(c))
                throw new IllegalArgumentException("Whitespace is not allowed for group name.");
            if(!Character.isDigit(c) && !Character.isLetter(c))
                throw new IllegalArgumentException("Character in group name must be digit or letter. Encountered: '"+c+"' in '"+name+"'");
        }
    }
}
