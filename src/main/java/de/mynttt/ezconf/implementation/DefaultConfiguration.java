package de.mynttt.ezconf.implementation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import de.mynttt.ezconf.Configuration;
import de.mynttt.ezconf.ConfigurationGroup;

final class DefaultConfiguration extends Configuration {
    private final HashMap<String, ConfigurationGroup> groups = new HashMap<>();
    private final List<ConfigurationGroup> roots = new ArrayList<>();
    
    @Override
    public Iterator<ConfigurationGroup> iterator() {
        return Collections.unmodifiableCollection(groups.values()).iterator();
    }

    @Override
    public int hashCode() {
        return Objects.hash(groups);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        DefaultConfiguration other = (DefaultConfiguration) obj;
        return Objects.equals(groups, other.groups);
    }

    @Override
    public String toString() {
        return "DefaultConfiguration [groups=" + groups + "]";
    }

    @Override
    public ConfigurationGroup getGroup(String path) {
        return groups.get(path);
    }

    @Override
    public boolean groupExists(String path) {
        return groups.containsKey(path);
    }

    @Override
    protected void addGroup(String path, ConfigurationGroup group) {
        if(groups.containsKey(path))
            throw new IllegalArgumentException("Can't add group: '" + path + "'. Path already exists.");
        if(!path.contains("."))
            roots.add(group);
        groups.put(path, group);
    }

    @Override
    public List<ConfigurationGroup> getRootGroups() {
        return Collections.unmodifiableList(roots);
    }

    @Override
    public int getGroupCount() {
        return groups.size();
    }

    @Override
    public int getRootGroupCount() {
        return roots.size();
    }
}
