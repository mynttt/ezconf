package de.mynttt.ezconf;

/**
 * A ConfigurationFactory allows the parser to spawn specific implementations of the {@link ConfigurationGroup} and {@link Configuration}.
 * 
 * @author mynttt
 *
 */
public interface ConfigurationFactory {
    
    /**
     * Create a {@link ConfigurationGroup} with a given path.
     * @param path of the group.
     * @return newly configuration group with the specified path
     */
    ConfigurationGroup newConfigurationGroup(String path);
    
    /**
     * Creates a new {@link Configuration} object.
     * @return newly created configuration.
     */
    Configuration newConfiguration();
}
