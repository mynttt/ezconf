package de.mynttt.ezconf;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;

/**
 * A configuration is returned by the {@link EzConf} after successfully parsing an EZConf file.<br>
 * It allows to query for groups.<br>
 * <p>
 * A group is referenced by its name, nested groups are referenced by joining their names with a dot.<p>
 * Example:
 * <br>
 * 
 * <pre>
 * root {
 *     nested {}
 * }
 * 
 * secondRoot {
 *    key: value;
 * }
 * </pre>
 * Query for groups via {@link #getGroup(String)}:
 * <hr>
 * Query root: <code>root</code><br>
 * Query secondRoot: <code>secondRoot</code><br>
 * Query nested: <code>root.nested</code><br>
 * <hr>
 * Query for key:value pair in secondRoot via {@link #getValue(String)}: <code>secondRoot#key</code>
 * 
 * @author mynttt
 *
 */
public abstract class Configuration implements Iterable<ConfigurationGroup> {
    
    /**
     * Returns a reference to the group.
     * @param path to query for within this configuration.
     * @return either the reference or null if not found
     */
    public abstract ConfigurationGroup getGroup(String path);
    
    /**
     * Returns an optional of a reference to a group.
     * @param path to query for within this configuration.
     * @return an {@link Optional} of the group reference.
     */
    public final Optional<ConfigurationGroup> findGroup(String path) {
        return Optional.ofNullable(getGroup(path));
    }
    
    /**
     * Queries a value by using the path combined with the key.
     * @param query to query for within this configuration.
     * @return either the value or null if not found within the group.
     * @throws IllegalArgumentException if the query is malformed.
     * @throws NoSuchElementException if the referenced group does not exist.
     */
    public final String getValue(String query) {
        Objects.requireNonNull(query, "path must not be null");
        int idx;
        if(query.startsWith("#"))
            throw new IllegalArgumentException("Invalid query: '"+query+"'. Queries cannot start with '#'");
        for(idx = 0; idx < query.length(); idx++) {
            if(query.charAt(idx) == '#')
                break;
        }
        if(idx == query.length())
            throw new IllegalArgumentException("Invalid query: '"+query+"'. Queries must be in this format: '<group>(.<subgroup>)*#key'");
        ConfigurationGroup g = getGroup(query.substring(0, idx));
        if(g == null)
            throw new NoSuchElementException("Group: '"+query.substring(0, idx)+"' does not exist.");
        return g.getValue(query.substring(idx+1));
    }
    
    /**
     * Gets a read-only list of root level groups for traversal.
     * @return list of root level groups.
     */
    public abstract List<ConfigurationGroup> getRootGroups();
    
    /**
     * Get the count of available groups.
     * @return amount of parsed groups (including children).
     */
    public abstract int getGroupCount();
    
    /**
     * Get the count of available root groups.
     * @return amount of parsed groups (including children).
     */
    public abstract int getRootGroupCount();
    
    /**
     * Queries an optional of a value by using the path combined with the key.
     * @param path to query for within this configuration.
     * @return an {@link Optional} containing the queried value.
     */
    public final Optional<String> findValue(String path) {
        return Optional.ofNullable(getValue(path));
    }
    
    /**
     * Allows to query for group existence.
     * @param path to query for within this configuration.
     * @return true if a group matching the path exists.
     */
    public abstract boolean groupExists(String path);
    
    /**
     * Protected method only used by the parser to add groups.
     * @param path that references the group.
     * @param group that shall be referenced by the path.
     */
    protected abstract void addGroup(String path, ConfigurationGroup group);
}
