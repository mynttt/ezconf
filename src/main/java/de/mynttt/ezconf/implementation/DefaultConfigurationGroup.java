package de.mynttt.ezconf.implementation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import de.mynttt.ezconf.ConfigurationGroup;
import java.util.Objects;
import java.util.Set;

final class DefaultConfigurationGroup extends ConfigurationGroup {
    private final List<ConfigurationGroup> children = new ArrayList<>();
    private final Map<String, String> keyValue = new HashMap<>();
    private final String path;
    
    public DefaultConfigurationGroup(String identifier) {
        this.path = identifier;
    }

    @Override
    public String getValue(String key) {
        return keyValue.get(key);
    }

    @Override
    public boolean keyExists(String key) {
        return keyValue.containsKey(key);
    }

    @Override
    public Set<String> getKeys() {
        return Collections.unmodifiableSet(keyValue.keySet());
    }

    @Override
    public Collection<String> getValues() {
        return Collections.unmodifiableCollection(keyValue.values());
    }

    @Override
    public List<ConfigurationGroup> getChildren() {
        return Collections.unmodifiableList(children);
    }

    @Override
    public Iterator<Entry<String, String>> iterator() {
        return Collections.unmodifiableSet(keyValue.entrySet()).iterator();
    }

    @Override
    public int hashCode() {
        return Objects.hash(children, keyValue, path);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        DefaultConfigurationGroup other = (DefaultConfigurationGroup) obj;
        return Objects.equals(keyValue, other.keyValue) && Objects.equals(path, other.path)
                && (other.children.containsAll(children) && children.containsAll(other.children));
    }

    @Override
    public String toString() {
        return "DefaultConfigurationGroup [path=" + path + ", keyValue=" + keyValue + ", children=" + children
                + "]";
    }

    @Override
    protected void addKeyValue(String key, String value) {
        if(keyValue.containsKey(key))
            throw new IllegalArgumentException("key: '" + key + "' is already present.");
        keyValue.put(key, value);
    }

    @Override
    protected void addChild(ConfigurationGroup child) {
        children.add(child);
    }

    @Override
    public String getPath() {
        return path;
    }
}