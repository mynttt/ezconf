package de.mynttt.ezconf;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * A ConfigurationGroup is a node that can store key:value pairs and have other {@link ConfigurationGroup} as children.<br>
 * A ConfigurationGroup is immutable and should only be manipulated by the parser.
 * 
 * @author mynttt
 *
 */
public abstract class ConfigurationGroup implements Iterable<Map.Entry<String, String>> {
    
    /**
     * Lookups a value associated with a key.
     * @param key to lookup.
     * @return either the value or null.
     */
    public abstract String getValue(String key);
    
    /**
     * Returns an {@link Optional} holding a value associated with a key.
     * @param key to lookup.
     * @return an optional holding the value.
     */
    public final Optional<String> findValue(String key) {
        return Optional.ofNullable(getValue(key));
    }
    
    /**
     * Check if a key exists.
     * @param key to check.
     * @return true if exists.
     */
    public abstract boolean keyExists(String key);
    
    /**
     * Get a read-only list of keys within this node.
     * @return all keys held by this node.
     */
    public abstract Set<String> getKeys();
    
    /**
     * Get a read-only collection of values within this node.
     * @return all values held by this node.
     */
    public abstract Collection<String> getValues();
    
    /**
     * Get a read-only list of children within this node.
     * @return all children held by this node.
     */
    public abstract List<ConfigurationGroup> getChildren();
    
    /**
     * Get the path of this node.
     * @return the current path of the node.
     */
    public abstract String getPath();
    
    /**
     * Only used by the parser to add key:value pairs to the node.
     * @param key to add.
     * @param value to add.
     * @throws IllegalArgumentException if key already exists in group.
     */
    protected abstract void addKeyValue(String key, String value);
    
    /**
     * Only used by the parser to add children to the node.
     * @param child to add.
     */
    protected abstract void addChild(ConfigurationGroup child);
}